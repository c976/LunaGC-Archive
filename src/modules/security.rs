use std::ffi::CString;

use crate::marshal;

use super::{MhyContext, MhyModule, ModuleType};
use anyhow::Result;
use ilhook::x64::Registers;
use crate::util;

const MHYRSA_PERFORM_CRYPTO_ACTION: &str = "E8 ? ? ? ? 48 83 C4 20 66 41 C7 06 30 82";
const KEY_SIGN_CHECK: &str = "E8 ? ? ? ? 48 83 C4 30 84 C0 74 ? 41 8B 04 24";
const KEY_SIGN_CHECK_OFFSET: usize = 0x5;
const SDK_UTIL_RSA_ENCRYPT: &str = "41 57 41 56 41 55 41 54 56 57 55 53 48 83 EC ?? 49 89 D6 48 89 CE 48 8B 0D ?? ?? ?? ?? E8 ?? ?? ?? ?? 49 89 C5";

const KEY_SIZE: usize = 268;
static SERVER_PUBLIC_KEY: &[u8] = include_bytes!("../../server_public_key.bin");
static SDK_PUBLIC_KEY: &str = include_str!("../../sdk_public_key.xml");

pub struct Security;

impl MhyModule for MhyContext<Security> {
    unsafe fn init(&mut self) -> Result<()> {

        let mhyrsa_perform_crypto_action = util::pattern_scan_code(self.assembly_name, MHYRSA_PERFORM_CRYPTO_ACTION);
        if let Some(addr) = mhyrsa_perform_crypto_action {
            crate::plog!("mhyrsa_perform_crypto_action: {:x}", addr as usize);
            self.interceptor.attach(
                addr as usize,
                on_mhy_rsa,
            )?;
        }
        else
        {
            crate::plog!("Failed to find mhyrsa_perform_crypto_action");
        }

        let key_sign_check = util::pattern_scan_code(self.assembly_name, KEY_SIGN_CHECK);
        if let Some(addr) = key_sign_check {
            let addr_offset = addr as usize + KEY_SIGN_CHECK_OFFSET;
            crate::plog!("key_sign_check: {:x}", addr_offset as usize);
            self.interceptor.attach(
                addr_offset as usize,
                after_key_sign_check,
            )?;
        }
        else
        {
            crate::plog!("Failed to find key_sign_check");
        }


        let sdk_util_rsa_encrypt = util::pattern_scan_il2cpp(self.assembly_name, SDK_UTIL_RSA_ENCRYPT);
        if let Some(addr) = sdk_util_rsa_encrypt {
            crate::plog!("sdk_util_rsa_encrypt: {:x}", addr as usize);
            self.interceptor.attach(
                addr as usize,
                on_sdk_util_rsa_encrypt,
            )?;
        }
        else
        {
            crate::plog!("Failed to find sdk_util_rsa_encrypt");
        }

        Ok(())
    }

    unsafe fn de_init(&mut self) -> Result<()> {
        Ok(())
    }

    fn get_module_type(&self) -> super::ModuleType {
        ModuleType::Security
    }
}

unsafe extern "win64" fn after_key_sign_check(reg: *mut Registers, _: usize) {
    crate::plog!("key sign check!");
    (*reg).rax = 1
}

unsafe extern "win64" fn on_mhy_rsa(reg: *mut Registers, _: usize) {
    crate::plog!("key: {:X}", *((*reg).r12 as *const u64));
    crate::plog!("len: {:X}", (*reg).r8 -3);

    if ((*reg).r8 as usize) - 3 == KEY_SIZE {
        crate::plog!("[*] key replaced");

        std::ptr::copy_nonoverlapping(
            SERVER_PUBLIC_KEY.as_ptr(),
            (*reg).r13 as *mut u8,
            SERVER_PUBLIC_KEY.len(),
        );
    }
}

unsafe extern "win64" fn on_sdk_util_rsa_encrypt(reg: *mut Registers, _: usize) {
    // rcx = key, rdx = plaintext
    crate::plog!("[*] SDK RSA: key replaced");
    (*reg).rcx =
        marshal::ptr_to_string_ansi(CString::new(SDK_PUBLIC_KEY).unwrap().as_c_str()) as u64;
}
