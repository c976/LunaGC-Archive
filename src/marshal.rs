use std::ffi::CStr;
use crate::util;

use windows::Win32::{System::LibraryLoader::GetModuleFileNameA};
use std::path::Path;

const PTR_TO_STRING_ANSI: &str = "0F 1F 80 00 00 00 00 E9 ?? ?? ?? ?? 66 66 2E 0F 1F 84 00 00 00 00 00 56 48 83 EC 20 48 85 C9";
const PTR_TO_STRING_ANSI_OFFSET: usize = 0x17;
type MarshalPtrToStringAnsi = unsafe extern "fastcall" fn(*const u8) -> *const u8;
static mut PTR_TO_STRING_ANSI_ADDR: Option<usize> = None;

pub unsafe fn ptr_to_string_ansi(content: &CStr) -> *const u8 {
    if PTR_TO_STRING_ANSI_ADDR.is_none() {
        find();
    }

    let func = std::mem::transmute::<usize, MarshalPtrToStringAnsi>(PTR_TO_STRING_ANSI_ADDR.unwrap());
    func(content.to_bytes_with_nul().as_ptr())
}

pub unsafe fn find() {
    let ptr_to_string_ansi = util::pattern_scan_il2cpp(module(), PTR_TO_STRING_ANSI);
    if let Some(addr) = ptr_to_string_ansi {
        let addr_offset = addr as usize + PTR_TO_STRING_ANSI_OFFSET;
        PTR_TO_STRING_ANSI_ADDR = Some(addr_offset);
        crate::plog!("ptr_to_string_ansi: {:x}", addr_offset);
    } else {
        crate::plog!("Failed to find ptr_to_string_ansi");
    }
}

unsafe fn module() -> &'static str {
    let mut buffer = [0u8; 260];
    GetModuleFileNameA(None, &mut buffer);
    let exe_path = CStr::from_ptr(buffer.as_ptr() as *const i8).to_str().unwrap();
    Box::leak(Box::new(Path::new(exe_path).file_name().unwrap().to_str().unwrap().to_string()))
}