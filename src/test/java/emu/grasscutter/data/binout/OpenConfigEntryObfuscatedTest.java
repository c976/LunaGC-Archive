package emu.grasscutter.data.binout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

/**
 * 7.x talent entries (the TPS weapons, Cryo Traveler) are written with obfuscated type and key
 * names. Read as plain AddAbility entries they carried no ability name, so TPS_Weapon_IceGun gave
 * the avatar nothing to aim or shoot with.
 */
public final class OpenConfigEntryObfuscatedTest {
    private static OpenConfigEntry load(String name, String json) {
        return new OpenConfigEntry(name, JsonUtils.decode(json, OpenConfigData[].class));
    }

    @Test
    public void obfuscatedAddAbilityAddsTheAbility() {
        // Excerpt of BinOutput/Talent/EquipTalents/33e619be.json.
        var entry =
                load(
                        "TPS_Weapon_IceGun",
                        """
                        [{"$type": "PHNIGFHBFMD", "LCNJMPKIAPA": true, "NCCKLDFFDOH": "Avatar_TPS_IceGun_PressAim"},
                         {"$type": "PHNIGFHBFMD", "LCNJMPKIAPA": true, "NCCKLDFFDOH": "Avatar_TPS_Ammo_Manager"}]
                        """);

        assertArrayEquals(
                new String[] {"Avatar_TPS_IceGun_PressAim", "Avatar_TPS_Ammo_Manager"},
                entry.getAddAbilities());
    }

    @Test
    public void obfuscatedModifyAndUnlockEntriesAreRead() {
        var entry =
                load(
                        "TPS_Weapon_IceGun_Accessory_Magazine",
                        """
                        [{"$type": "CMGFNDNMFFO", "AAAENHAPNLB": "Unlock_Accessory_Magazine", "LCNJMPKIAPA": true, "NCCKLDFFDOH": "TPS_IceGun_Accessory_Manager"},
                         {"$type": "GMOELNAHCOH", "IJFENBIJGLJ": "%1", "LCNJMPKIAPA": true, "LDACNDBDKBA": "IceGun_Accessory_Magazine_FireIntervalRatio", "NCCKLDFFDOH": "TPS_IceGun_Accessory_Manager"}]
                        """);

        assertNotNull(entry.getTalentParamEntries());
        assertEquals(1, entry.getTalentParamEntries().length);
        assertEquals("Unlock_Accessory_Magazine", entry.getTalentParamEntries()[0].getParamName());

        assertNotNull(entry.getAbilityVarSetters());
        assertEquals(1, entry.getAbilityVarSetters().length);
        var setter = entry.getAbilityVarSetters()[0];
        assertEquals("TPS_IceGun_Accessory_Manager", setter.getAbilityName());
        assertEquals("IceGun_Accessory_Magazine_FireIntervalRatio", setter.getVarName());
        assertEquals(0, setter.getParamIndex());
    }
}
