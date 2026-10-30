use std::collections::HashSet;
use std::sync::Mutex;
use anyhow::{anyhow, Result};
use windows::{
    core::s,
    Win32::System::LibraryLoader::{LoadLibraryA, GetProcAddress},
};
use ilhook::x64::Registers;
use lazy_static::lazy_static;
use super::{MhyModule, MhyContext, ModuleType};

const TARGET_HOST: &str = "127.0.0.1";
const TARGET_PORT: u16 = 8088;
const REDIRECTED_DOMAINS: [&str; 4] = ["hoyoverse.com", "mihoyo.com", "yuanshen.com", "hoyolab.com"];

const WINHTTP_FLAG_SECURE: u32 = 0x0080_0000;

lazy_static! {
    static ref TARGET_HOST_WIDE: Vec<u16> = crate::util::wide_str(TARGET_HOST);
    static ref REDIRECTED_CONNECTIONS: Mutex<HashSet<usize>> = Mutex::new(HashSet::new());
}

type WinHttpConnectFn = unsafe extern "system" fn(usize, *const u16, u16, u32) -> usize;
type WinHttpOpenRequestFn = unsafe extern "system" fn(
    usize, // hConnect (rcx)
    *const u16, // pwszVerb (rdx)
    *const u16, // pwszObjectName (r8)
    *const u16, // pwszVersion (r9)
    *const u16, // pwszReferrer (stack +0x28)
    *const *const u16, // pwszAcceptTypes (stack +0x30)
    u32, // dwFlags (stack +0x38)
) -> usize;

pub struct WinHttp;

impl MhyModule for MhyContext<WinHttp> {
    unsafe fn init(&mut self) -> Result<()> {
        let winhttp = LoadLibraryA(s!("winhttp.dll"))?;

        let connect = GetProcAddress(winhttp, s!("WinHttpConnect"))
            .ok_or_else(|| anyhow!("Failed to get WinHttpConnect"))?;
        let open_request = GetProcAddress(winhttp, s!("WinHttpOpenRequest"))
            .ok_or_else(|| anyhow!("Failed to get WinHttpOpenRequest"))?;
        let close_handle = GetProcAddress(winhttp, s!("WinHttpCloseHandle"))
            .ok_or_else(|| anyhow!("Failed to get WinHttpCloseHandle"))?;

        println!("WinHttpConnect: {:x}", connect as usize);
        println!("WinHttpOpenRequest: {:x}", open_request as usize);
        println!("WinHttpCloseHandle: {:x}", close_handle as usize);

        self.interceptor.replace(connect as usize, on_connect)?;
        self.interceptor.replace(open_request as usize, on_open)?;
        self.interceptor.attach(close_handle as usize, on_close)?;

        println!("WinHttp redirection enabled");

        Ok(())
    }

    unsafe fn de_init(&mut self) -> Result<()> {
        Ok(())
    }

    fn get_module_type(&self) -> ModuleType {
        ModuleType::WinHttp
    }
}

unsafe extern "win64" fn on_connect(reg: *mut Registers, original: usize, _: usize) -> usize {
    let original = std::mem::transmute::<usize, WinHttpConnectFn>(original);

    let session = (*reg).rcx as usize;
    let server_name = (*reg).rdx as *const u16;
    let port = (*reg).r8 as u16;
    let reserved = (*reg).r9 as u32;

    let host = read_wide(server_name);
    if !should_redirect(&host) {
        return original(session, server_name, port, reserved);
    }

    let connection = original(session, TARGET_HOST_WIDE.as_ptr(), TARGET_PORT, reserved);
    println!("WinHttpConnect: {host}:{port} -> {TARGET_HOST}:{TARGET_PORT}");

    if connection != 0 {
        REDIRECTED_CONNECTIONS.lock().unwrap().insert(connection);
    }

    connection
}

unsafe extern "win64" fn on_open(reg: *mut Registers, original: usize, _: usize) -> usize {
    let original = std::mem::transmute::<usize, WinHttpOpenRequestFn>(original);

    let connection = (*reg).rcx as usize;
    let verb = (*reg).rdx as *const u16;
    let object_name = (*reg).r8 as *const u16;
    let version = (*reg).r9 as *const u16;
    let referrer = (*reg).get_stack(5) as *const u16;
    let accept_types = (*reg).get_stack(6) as *const *const u16;
    let flags = (*reg).get_stack(7) as u32;

    let redirected = REDIRECTED_CONNECTIONS.lock().unwrap().contains(&connection);
    if redirected {
        println!("WinHttpOpenRequest: WINHTTP_FLAG_SECURE stripped (handle {connection:#x})");
    }
    let flags = if redirected { flags & !WINHTTP_FLAG_SECURE } else { flags };

    original(connection, verb, object_name, version, referrer, accept_types, flags)
}

unsafe extern "win64" fn on_close(reg: *mut Registers, _: usize) {
    let handle = (*reg).rcx as usize;
    if REDIRECTED_CONNECTIONS.lock().unwrap().remove(&handle) {
        println!("WinHttpCloseHandle: removed redirected handle {handle:#x}");
    }
}

unsafe fn read_wide(ptr: *const u16) -> String {
    if ptr.is_null() {
        return String::new();
    }

    let mut len = 0;
    while *ptr.add(len) != 0 {
        len += 1;
    }

    let slice = std::slice::from_raw_parts(ptr, len);
    String::from_utf16_lossy(slice)
}

fn should_redirect(host: &str) -> bool {
    let normalized_host = host.to_ascii_lowercase();
    for redirected_domain in REDIRECTED_DOMAINS {
        if normalized_host == redirected_domain
            || normalized_host.ends_with(&format!(".{redirected_domain}")) {
            return true;
        }
    }

    false
}
