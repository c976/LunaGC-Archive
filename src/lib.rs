use std::{sync::RwLock};

use lazy_static::lazy_static;
use windows::Win32::System::Console;
use windows::Win32::System::SystemServices::DLL_PROCESS_ATTACH;
use windows::Win32::{Foundation::HINSTANCE, System::LibraryLoader::GetModuleFileNameA};
use std::ffi::CStr;
use std::path::Path;

mod interceptor;
mod marshal;
mod modules;
mod util;

use crate::modules::{MhyContext, ModuleManager,
                     CcpBlocker, Http, Misc, Security, WinHttp};

unsafe fn thread_func() {
    let mut module_manager = MODULE_MANAGER.write().unwrap();

    // Block query_security_file ASAP
    module_manager.enable(MhyContext::<CcpBlocker>::new(""));

    util::disable_memprotect_guard();
    Console::AllocConsole().unwrap();

    println!("Genshin Impact encryption patch\nMade by xeondev, modified by Kitsxki_\n(Modded for all version >= 6.5)");

    let mut buffer = [0u8; 260];
    GetModuleFileNameA(None, &mut buffer);
    let exe_path = CStr::from_ptr(buffer.as_ptr() as *const i8).to_str().unwrap();
    let exe_name = Path::new(exe_path).file_name().unwrap().to_str().unwrap();
    println!("Current executable name: {}", exe_name);

    if exe_name != "GenshinImpact.exe" && exe_name != "YuanShen.exe" {
        println!("Executable is not Genshin. Skipping initialization.");
        return;
    }

    println!("Initializing modules...");

    module_manager.enable(MhyContext::<Security>::new(&exe_name));
    marshal::find();
    module_manager.enable(MhyContext::<Http>::new(&exe_name));
    module_manager.enable(MhyContext::<Misc>::new(&exe_name));
    module_manager.enable(MhyContext::<WinHttp>::new(&exe_name));

    println!("Successfully initialized!");
}

lazy_static! {
    static ref MODULE_MANAGER: RwLock<ModuleManager> = RwLock::new(ModuleManager::default());
}

#[no_mangle]
#[allow(non_snake_case)]
unsafe extern "system" fn DllMain(_: HINSTANCE, call_reason: u32, _: *mut ()) -> bool {
    if call_reason == DLL_PROCESS_ATTACH {
        #[cfg(debug_assertions)]
        {
            thread_func();
        }
        #[cfg(not(debug_assertions))]
        {
            std::thread::spawn(|| thread_func());
        }
    }

    true
}
