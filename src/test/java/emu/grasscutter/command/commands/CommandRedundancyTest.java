package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** The public command grammar must not reintroduce duplicate routes. */
public final class CommandRedundancyTest {
    @Test
    void removedTopLevelCommandsStayUnregistered() {
        var map = new CommandMap(false);
        map.registerCommand("kill", new KillCommand());
        map.registerCommand("restore", new RestoreCommand());

        for (String removed : new String[] {"killall", "er", "e", "energy", "heal", "h"}) {
            assertNull(map.getHandler(removed), removed);
        }
        assertNull(map.getHandler("killCharacter"));
        assertNull(map.getHandler("suicide"));
        assertNotNull(map.getHandler("restore"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("restore"));
    }

    @Test
    void removedCommandNamesAreAbsentWhileRetainedRoutesWork() {
        var map = new CommandMap(false);
        map.registerCommand("barrier", new BarrierCommand());
        map.registerCommand("dungeon", new EnterDungeonCommand());
        map.registerCommand("avatar", new AvatarCommand());
        map.registerCommand("kill", new KillCommand());
        map.registerCommand("trackmat", new TrackMatCommand());
        map.registerCommand("waypoints", new WaypointsCommand());
        map.registerCommand("spawn", new SpawnCommand());

        for (String removed : new String[] {
                "pb", "enter_dungeon", "enterdungeon", "levelbreak",
                "killCharacter", "mattrack", "unlockwp", "suicide",
                "extralevel", "el", "constellation", "talent",
                "setfetterlevel", "setfetterlvl", "setfriendship",
                "max", "maxavatar", "maxchar", "drop"
        }) {
            assertNull(map.getHandler(removed), removed);
            assertFalse(map.getCommandLine().getSubcommands().containsKey(removed), removed);
        }
        for (String retained : new String[] {
                "barrier", "br", "dungeon", "avatar",
                "kill", "trackmat", "trackmaterial", "waypoints", "wp",
                "spawn", "s"
        }) {
            assertNotNull(map.getHandler(retained), retained);
            assertTrue(map.getCommandLine().getSubcommands().containsKey(retained), retained);
        }
    }

    @Test
    void primaryBattlepassAndRemainingAliasesHaveNoLegacyDuplicates() {
        var map = new CommandMap(false);
        map.registerCommand("battlepass", new BattlePassCommand());
        map.registerCommand("give", new GiveCommand());
        map.registerCommand("name", new NameCommand());
        map.registerCommand("namecard", new NameCardCommand());
        map.registerCommand("kill", new KillCommand());

        assertNotNull(map.getHandler("battlepass"));
        assertTrue(map.getHandler("battlepass") == map.getHandler("bp"));
        assertTrue(map.getHandler("give") == map.getHandler("g"));
        assertTrue(map.getHandler("name") == map.getHandler("rename"));
        assertTrue(map.getHandler("namecard") == map.getHandler("card"));
        for (String removed : new String[] {
                "item", "giveitem", "nickname", "setnamecard", "suicide"
        }) {
            assertNull(map.getHandler(removed), removed);
            assertFalse(map.getCommandLine().getSubcommands().containsKey(removed), removed);
        }

        assertTrue(new BattlePassCommand().createCommandLine(null, null)
                .getUsageMessage().contains("battlepass"));
        assertTrue(new BattlePassCommand().createCommandLine(null, null)
                .getSubcommands().containsKey("buy"));
        assertTrue(new BattlePassCommand().createCommandLine(null, null)
                .getSubcommands().containsKey("paid"));
    }

    @Test
    void avatarGroupReplacesRemovedTopLevelCharacterCommands() {
        var cli = new AvatarCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().containsKey("constellation"));
        assertTrue(cli.getSubcommands().containsKey("talent"));
        assertTrue(cli.getSubcommands().containsKey("friendship"));
        assertTrue(cli.getSubcommands().containsKey("extralevel"));
        assertTrue(cli.getSubcommands().containsKey("max"));
    }

    @Test
    void avatarStatHasSetLockAndUnlockWithoutTopLevelAliases() {
        var map = new CommandMap(false);
        map.registerCommand("avatar", new AvatarCommand());
        for (String removed : new String[] {"setStats", "stats", "stat"}) {
            assertNull(map.getHandler(removed), removed);
            assertFalse(map.getCommandLine().getSubcommands().containsKey(removed), removed);
        }

        CommandLine cli = new AvatarCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().containsKey("stat"));
        var stat = cli.getSubcommands().get("stat");
        for (String retained : new String[] {"set", "lock", "unlock"}) {
            assertTrue(stat.getSubcommands().containsKey(retained), retained);
        }
        for (String removed : new String[] {"freeze", "unfreeze"}) {
            assertFalse(stat.getSubcommands().containsKey(removed), removed);
        }
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> cli.parseArgs("stat", "set", "atk", "3000"));
    }

    @Test
    void achievementBulkActionsUseAllAsTargetRatherThanCompoundName() {
        CommandLine cli = new AchievementCommand().createCommandLine(null, null);
        for (String operation : new String[] {"grant", "revoke"}) {
            assertTrue(cli.getSubcommands().containsKey(operation));
            assertTrue(cli.getSubcommands().get(operation).getUsageMessage().contains("<achievementId|all>"));
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation, "12345"));
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation, "all"));
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation, "ALL"));
            org.junit.jupiter.api.Assertions.assertThrows(
                    CommandLine.ParameterException.class,
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation, "invalid"));
            org.junit.jupiter.api.Assertions.assertThrows(
                    CommandLine.ParameterException.class,
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation));
        }
        assertFalse(cli.getSubcommands().containsKey("grantall"));
        assertFalse(cli.getSubcommands().containsKey("revokeall"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("grantall"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("revokeall"));
    }

    @Test
    void sceneTagsKeepOneNamePerOperation() {
        CommandLine cli = new SetSceneTagCommand().createCommandLine(null, null);
        for (String retained : new String[] {"add", "remove", "reset", "unlock", "list"}) {
            assertTrue(cli.getSubcommands().containsKey(retained), retained);
        }
        for (String removed : new String[] {"set", "del", "restore", "unlockall"}) {
            assertFalse(cli.getSubcommands().containsKey(removed), removed);
        }
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> cli.parseArgs("unlock", "all"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("unlock"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("unlockall"));
    }

    @Test
    void mapUnlockIsUnderExistingUnlockCommand() {
        var cli = new UnlockAllCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().containsKey("map"));
        assertTrue(cli.getSubcommands().containsKey("all"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> new UnlockAllCommand().createCommandLine(null, null).parseArgs("map"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> new UnlockAllCommand().createCommandLine(null, null).parseArgs("map", "--force"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class,
                () -> new UnlockAllCommand().createCommandLine(null, null).parseArgs("map", "all"));
    }

    @Test
    void globalUnlockHasAllSubcommandWithLegacyPermissions() {
        var map = new CommandMap(false);
        var handler = new UnlockAllCommand();
        map.registerCommand("unlock", handler);
        assertNotNull(map.getHandler("unlock"));
        assertNull(map.getHandler("unlockall"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("unlock"));
        var cli = handler.createCommandLine(null, null);
        assertTrue(cli.getSubcommands().containsKey("all"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> cli.parseArgs("all"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("unlockall"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "player.unlockall",
                UnlockAllCommand.class.getAnnotation(emu.grasscutter.command.Command.class).permission());
        org.junit.jupiter.api.Assertions.assertEquals(
                "player.unlockall.others",
                UnlockAllCommand.class.getAnnotation(emu.grasscutter.command.Command.class).permissionTargeted());
    }
}
