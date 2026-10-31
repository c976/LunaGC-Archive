package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.inventory.GameItem;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ClearDryRunTest {
    @Test
    void dryRunIsAvailableForEveryExistingScopeAndWithThresholds() {
        var handler = new ClearCommand();
        for (String scope : List.of("all", "weapons", "artifacts", "materials")) {
            CommandLine cli = handler.createCommandLine(null, null);
            var result = cli.parseArgs(scope, "--dry-run", "--level", "20", "--rarity", "5");
            assertTrue(result.hasMatchedOption("--dry-run"), scope);
            assertEquals(20, result.matchedOptionValue("--level", 0));
            assertEquals(5, result.matchedOptionValue("--rarity", 0));
        }
        var defaultArgs = handler.createCommandLine(null, null).parseArgs("materials");
        assertFalse(defaultArgs.hasMatchedOption("--dry-run"));
    }

    @Test
    void previewSummarizesStacksAndQuantitiesWithoutChangingThem() {
        var weapon = new GameItem();
        weapon.setCount(1);
        var materials = new GameItem();
        materials.setCount(200);

        var preview = ClearCommand.summarize(List.of(weapon, materials));
        assertEquals(2, preview.stacks());
        assertEquals(201, preview.quantity());
        assertEquals(1, weapon.getCount());
        assertEquals(200, materials.getCount());
        assertEquals(new ClearCommand.Preview(0, 0), ClearCommand.summarize(List.of()));
    }
}
