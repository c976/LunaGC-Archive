//! winhttp redirect, for the native account sdk

use super::{MhyContext, MhyModule, ModuleType};
use anyhow::Result;
use ilhook::x64::Registers;
use windows::{
    core::{s, PCSTR},
    Win32::System::LibraryLoader::{GetProcAddress, LoadLibraryA},
};

/// where the dispatch listens
const DISPATCH_HOST: &str = "127.0.0.1";
const DISPATCH_PORT: u64 = 8088;

/// host as utf-16, static so the pointer stays valid
static DISPATCH_HOST_W: [u16; 10] = [
    b'1' as u16,
    b'2' as u16,
    b'7' as u16,
    b'.' as u16,
    b'0' as u16,
    b'.' as u16,
    b'0' as u16,
    b'.' as u16,
    b'1' as u16,
    0,
];

/// the https bit
const WINHTTP_FLAG_SECURE: u32 = 0x0080_0000;

pub struct WinHttp;

impl MhyModule for MhyContext<WinHttp> {
    unsafe fn init(&mut self) -> Result<()> {
        // load it ourselves, HYPass pulls it in much later
        let winhttp = match LoadLibraryA(s!("winhttp.dll")) {
            Ok(handle) if !handle.is_invalid() => handle,
            _ => {
                println!("Failed to load winhttp.dll - the native SDK will NOT be redirected");
                return Ok(());
            }
        };

        for (name, routine) in [
            (
                s!("WinHttpConnect"),
                on_winhttp_connect as unsafe extern "win64" fn(*mut Registers, usize),
            ),
            (s!("WinHttpOpenRequest"), on_winhttp_open_request),
        ] {
            match GetProcAddress(winhttp, name) {
                Some(addr) => {
                    println!("{}: {:x}", pcstr_name(name), addr as usize);
                    self.interceptor.attach(addr as usize, routine)?;
                }
                None => println!("Failed to find {}", pcstr_name(name)),
            }
        }

        Ok(())
    }

    unsafe fn de_init(&mut self) -> Result<()> {
        Ok(())
    }

    fn get_module_type(&self) -> ModuleType {
        ModuleType::WinHttp
    }
}

/// reads a utf-16 arg
unsafe fn wide_ptr_to_string(ptr: *const u16) -> String {
    if ptr.is_null() {
        return "<null>".into();
    }
    let mut len = 0usize;
    while len < 2048 && *ptr.add(len) != 0 {
        len += 1;
    }
    String::from_utf16_lossy(std::slice::from_raw_parts(ptr, len))
}

/// export name, for logs
unsafe fn pcstr_name(name: PCSTR) -> String {
    name.to_string().unwrap_or_else(|_| "<winhttp export>".into())
}

/// rdx = host, r8 = port
unsafe extern "win64" fn on_winhttp_connect(reg: *mut Registers, _: usize) {
    let host = wide_ptr_to_string((*reg).rdx as *const u16);
    println!("Redirect: {host}:{} -> {DISPATCH_HOST}:{DISPATCH_PORT}", (*reg).r8 as u16);

    (*reg).rdx = DISPATCH_HOST_W.as_ptr() as u64;
    (*reg).r8 = DISPATCH_PORT;
}

/// drops https, dwFlags is arg 7 at [rsp+0x38]
unsafe extern "win64" fn on_winhttp_open_request(reg: *mut Registers, _: usize) {
    let flags_slot = ((*reg).rsp as usize + 0x38) as *mut u32;
    *flags_slot &= !WINHTTP_FLAG_SECURE;
}
