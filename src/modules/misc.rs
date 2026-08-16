use super::{MhyContext, MhyModule, ModuleType};
use anyhow::Result;
use ilhook::x64::Registers;
use crate::util;
pub struct Misc;

const SET_CUSTOM_PROPERTY_FLOAT: &str = "48 89 5C 24 ?? 48 89 74 24 ?? 57 48 83 EC ?? 0F 29 74 24 ?? 0F 28 F2 41 0F B6 F9 8B F2 48 8B D9 48 85 C9 74 ?? E8 ?? ?? ?? ?? 48 85 C0 74 ?? 40 84 FF 0F 28 D6 8B D6 48 8B C8 41 0F 95 C1 48 8B 5C 24 ?? 48 8B 74 24 ?? 0F 28 74 24 ?? 48 83 C4 ?? 5F E9 ?? ?? ?? ?? 48 8B CB E8 ?? ?? ?? ?? CC 48 89 5C 24 ??";

impl MhyModule for MhyContext<Misc> {
    unsafe fn init(&mut self) -> Result<()> {
        // Dither
        let set_custom_property_float = util::pattern_scan_code(self.assembly_name, SET_CUSTOM_PROPERTY_FLOAT);
        if let Some(addr) = set_custom_property_float {
            println!("set_custom_property_float: {:x}", addr as usize);
            self.interceptor.replace(
                addr as usize,
                set_custom_property_float_replacement,
            )?;
        }
        else
        {
            println!("Failed to find set_custom_property_float");
        }

        Ok(())
    }

    unsafe fn de_init(&mut self) -> Result<()> {
        Ok(())
    }

    fn get_module_type(&self) -> super::ModuleType {
        ModuleType::Misc
    }
}

unsafe extern "win64" fn set_custom_property_float_replacement(
    _: *mut Registers,
    _: usize,
    _: usize,
) -> usize {
    0
}
