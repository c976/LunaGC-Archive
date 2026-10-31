package emu.grasscutter.data.excels;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins how the 7.1 burst rows for Mavuika and Skirk spell their special energy. */
public final class SpecialEnergySkillTest {
    @Test
    @DisplayName("Mavuika's 7.1 burst row gives a 200 bar with a 100 cost")
    public void mavuikaBurst() {
        var skill =
                JsonUtils.decode(
                        "{\"id\":11065,\"costElemType\":\"Fire\",\"specialEnergyCostStart\":100,"
                                + "\"specialEnergyCostType\":\"SPECIAL_ENERGY_MAVUIKA\",\"specialEnergyCostMax\":200}",
                        AvatarSkillData.class);
        assertEquals(200f, skill.getSpecialEnergyMin());
        assertEquals(100f, skill.getSpecialEnergyMax());
    }

    @Test
    @DisplayName("Skirk's 7.1 burst row gives a 100 bar with a 50 cost")
    public void skirkBurst() {
        var skill =
                JsonUtils.decode(
                        "{\"id\":11145,\"costElemType\":\"Ice\",\"specialEnergyCostStart\":50,"
                                + "\"specialEnergyCostType\":\"SPECIAL_ENERGY_SKIRK\",\"specialEnergyCostMax\":100}",
                        AvatarSkillData.class);
        assertEquals(100f, skill.getSpecialEnergyMin());
        assertEquals(50f, skill.getSpecialEnergyMax());
    }
}
