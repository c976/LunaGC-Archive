package emu.grasscutter.data.binout.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import emu.grasscutter.utils.JsonUtils;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@code combat.die} block of a gadget's config.
 *
 * <p>{@link ConfigEntityGadget} had a combat type but the type carried no {@code die} member, so everything
 * the data says about how a gadget dies - whether it has its own death animation, and how long that runs -
 * parsed away silently. Whatever needed the length fell back to "no delay" and looked correct while doing
 * the wrong thing.
 */
public final class ConfigCombatDieParseTest {

    @Test
    @DisplayName("combat.die parses, with its own field names")
    void dieBlockParses() {
        var config =
                JsonUtils.decode(
                        "{\"combat\":{\"die\":{\"hasAnimatorDie\":true,\"dieEndTime\":1.5,"
                                + "\"dieForceDisappearTime\":2.5}}}",
                        ConfigEntityGadget.class);

        assertNotNull(config.getCombat(), "combat itself did not parse");
        var die = config.getCombat().getDie();
        assertNotNull(die, "combat.die did not parse");
        assertTrue(die.isHasAnimatorDie(), "hasAnimatorDie did not parse");
        assertEquals(1.5d, die.getDieEndTime(), 0.001d);
        assertEquals(2.5d, die.getDieForceDisappearTime(), 0.001d);
    }

    @Test
    @DisplayName("a gadget with no die block still parses")
    void absentDieBlockIsNotAnError() {
        var config = JsonUtils.decode("{\"combat\":{\"property\":{}}}", ConfigEntityGadget.class);

        assertNotNull(config.getCombat());
        assertNull(config.getCombat().getDie());
    }

    @Test
    @DisplayName("the values a real ridden Saurian dies on")
    void natlanMountDieValues() throws Exception {
        // The fixture above proves the member parses; this proves the shipped file uses those very key
        // names. A mismatch here is what makes a mount vanish with no collapse instead of playing it.
        var path = Path.of("resources/BinOutput/Gadget/ConfigGadget_Vehicle_Natsaurus_Drillhead_01.json");
        assumeTrue(Files.exists(path), "needs the extracted 7.1 resource data");

        var config =
                JsonParser.parseReader(Files.newBufferedReader(path))
                        .getAsJsonObject()
                        .getAsJsonObject("Natsaurus_Drillhead_Vehicle_01");
        var die =
                JsonUtils.decode(config, ConfigEntityGadget.class).getCombat().getDie();

        assertNotNull(die, "combat.die did not parse out of the real row");
        assertTrue(die.isHasAnimatorDie(), "the Natlan mount is flagged as having an animator death");
        assertEquals(1.5d, die.getDieEndTime(), 0.001d);
        assertEquals(2.5d, die.getDieForceDisappearTime(), 0.001d);
    }
}
