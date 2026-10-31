package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class SensitiveCommandSyntaxTest {
    @Test
    void unifiedBanTargetsReplaceLegacyRoutes() {
        var map = new CommandMap(false);
        map.registerCommand("ban", new BanCommand());
        map.registerCommand("unban", new UnbanCommand());
        assertNull(map.getHandler("banip"));
        assertNull(map.getHandler("unbanip"));
        var cli = new BanCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().isEmpty());
        assertFalse(BanCommand.class.getAnnotation(Command.class).inlineTarget());
        assertTrue(cli.getUsageMessage().contains("<playerSelector|IPv4>"));
        assertTrue(cli.getUsageMessage().contains("playerSelector: @UID"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs());

        var unbanCli = new UnbanCommand().createCommandLine(null, null);
        assertTrue(unbanCli.getSubcommands().isEmpty());
        assertFalse(UnbanCommand.class.getAnnotation(Command.class).inlineTarget());
        assertTrue(unbanCli.getUsageMessage().contains("unban <playerSelector|IPv4>"));
        assertThrows(CommandLine.ParameterException.class, () -> unbanCli.parseArgs());
    }

    @Test
    void killAllAcceptsOptionalSceneIdWithoutCertificatePassword() {
        var cli = new KillCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().containsKey("all"));
        assertFalse(cli.getSubcommands().containsKey("character"));
        assertFalse(cli.getSubcommands().containsKey("avatar"));

        var usage = cli.getSubcommands().get("all").getUsageMessage();
        assertTrue(usage.contains("[sceneId]"));
        assertFalse(usage.contains("<key>"));
        assertDoesNotThrow(() -> new KillCommand().createCommandLine(null, null).parseArgs("all"));
        assertDoesNotThrow(() -> new KillCommand().createCommandLine(null, null).parseArgs("all", "3"));
        assertThrows(CommandLine.ParameterException.class,
                () -> new KillCommand().createCommandLine(null, null).parseArgs("all", "key", "3"));
        assertThrows(CommandLine.ParameterException.class,
                () -> new KillCommand().createCommandLine(null, null).parseArgs("all", "not-a-scene"));
        assertThrows(CommandLine.ParameterException.class,
                () -> new KillCommand().createCommandLine(null, null).parseArgs("all", "3", "4"));
        assertDoesNotThrow(() -> new KillCommand().createCommandLine(null, null).parseArgs());

        var map = new CommandMap(false);
        map.registerCommand("kill", new KillCommand());
        assertNull(map.getHandler("killall"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("killcharacter"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("suicide"));
    }

    @Test
    void explicitBanSelectorsAndOptionalReason() {
        for (String target : List.of("@10001", "rino@", "rino@10001", "20261010@", "123.123.123.123")) {
            assertDoesNotThrow(
                    () -> new BanCommand().createCommandLine(null, null).parseArgs(target));
            assertDoesNotThrow(() -> BanCommand.parseBanArguments(target, List.of()));
        }
        assertEquals(BanCommand.TargetType.PLAYER, BanCommand.parseTarget("@10001").type());
        assertEquals("@10001", BanCommand.parseTarget("@10001").value());
        assertEquals(BanCommand.TargetType.IPV4, BanCommand.parseTarget("123.123.123.123").type());
        assertEquals(BanCommand.TargetType.PLAYER, BanCommand.parseTarget("rino@").type());
        assertEquals(BanCommand.TargetType.PLAYER, BanCommand.parseTarget("20261010@10001").type());
        assertEquals("rino@10001", BanCommand.parseTarget("rino@10001").value());
        assertThrows(IllegalArgumentException.class, () -> BanCommand.parseTarget("@legacy"));

        var timed = BanCommand.parseBanArguments("account_name@",
                List.of("1800000000", "Repeated", "abuse"));
        assertEquals(1800000000, timed.endTime());
        assertEquals("Repeated abuse", timed.reason());
        assertEquals("Cheating", BanCommand.parseBanArguments("@10001",
                List.of("Cheating")).reason());
        assertEquals("Reason not specified.", BanCommand.parseBanArguments(
                "123.123.123.123", List.of()).reason());
        assertEquals("Spam", BanCommand.parseBanArguments(
                "123.123.123.123", List.of("Spam")).reason());
        var ipTimed = BanCommand.parseBanArguments("123.123.123.123",
                List.of("2147483646", "Repeated", "abuse"));
        assertEquals(2147483646, ipTimed.endTime());
        assertEquals("Repeated abuse", ipTimed.reason());
        assertEquals(0, BanCommand.parseBanArguments(
                "123.123.123.123", List.of()).endTime());
        assertEquals(0, BanCommand.parseBanArguments(
                "123.123.123.123", List.of("0", "permanent")).endTime());
        assertDoesNotThrow(() -> new BanCommand().createCommandLine(null, null)
                .parseArgs("123.123.123.123", "2147483646", "Repeated", "abuse"));
    }

    @Test
    void rejectsInvalidSelectorsAndIpExpiration() {
        for (String invalid : List.of("@", "@0", "@-1", "@99999999999999",
                "rino@0", "rino@other", "123.123.123.999", "001.2.3.4",
                "rino", "account_name", "legacy.name", "10001")) {
            assertThrows(IllegalArgumentException.class, () -> BanCommand.parseTarget(invalid));
        }
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments(null, List.of()));
        var bareName = assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("rino", List.of("Cheating")));
        assertTrue(bareName.getMessage().contains("playerSelector"));
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("@10001", List.of("999999999999999999")));
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("123.123.123.123", List.of("1", "past")));
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("123.123.123.123", List.of("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("123.123.123.123", List.of("999999999999999999")));
    }

    @Test
    void canonicalCommandsDoNotRetainRemovedEntrypoints() {
        var map = new CommandMap(false);
        map.registerCommand("announce", new AnnounceCommand());
        map.registerCommand("say", new SayCommand());
        map.registerCommand("sayto", new SayToCommand());
        map.registerCommand("player", new PlayerCommand());
        map.registerCommand("info", new InfoCommand());

        assertSame(map.getHandler("announce"), map.getHandler("a"));
        assertTrue(new AnnounceCommand().createCommandLine(null, null).getSubcommands().containsKey("send"));
        assertNotNull(map.getHandler("say"));
        assertNotNull(map.getHandler("sayto"));
        assertNotNull(map.getHandler("player"));
        assertNotNull(map.getHandler("info"));
        for (var removed : List.of(
                "list", "players", "sendmessage", "sendservmsg", "sendservermessage", "b",
                "broadcast", "troubleshoot", "helpme")) {
            assertNull(map.getHandler(removed), removed);
            assertFalse(map.getCommandLine().getSubcommands().containsKey(removed), removed);
        }

        var players = new PlayerCommand().createCommandLine(null, null);
        assertEquals("player", PlayerCommand.class.getAnnotation(Command.class).label());
        assertFalse(PlayerCommand.class.getAnnotation(Command.class).inlineTarget());
        assertTrue(players.getSubcommands().containsKey("list"));
        assertTrue(players.getSubcommands().containsKey("info"));
        assertDoesNotThrow(() -> players.parseArgs("list"));
        for (String selector : List.of("@10001", "alice@", "alice@10001")) {
            assertDoesNotThrow(() -> new PlayerCommand().createCommandLine(null, null)
                    .parseArgs("info", selector), selector);
        }
        for (String invalid : List.of("@0", "@invalid", "alice", "10001", "alice@wrong")) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> new PlayerCommand().createCommandLine(null, null)
                            .parseArgs("info", invalid), invalid);
        }
        assertThrows(CommandLine.ParameterException.class, () -> players.parseArgs("info"));
        assertThrows(CommandLine.ParameterException.class,
                () -> players.parseArgs("info", "@10001", "extra"));
        assertFalse(players.getSubcommands().get("list").getUsageMessage().contains("--uid"));
        assertThrows(CommandLine.ParameterException.class, () -> players.parseArgs("list", "--uid"));
        assertThrows(CommandLine.ParameterException.class, () -> players.parseArgs("list", "uid"));
        assertThrows(CommandLine.ParameterException.class, () -> players.parseArgs("--uid"));
        assertEquals("Alice (10001)", PlayerCommand.formatPlayer("Alice", 10001, false));
        assertEquals("Alice <color=green>(10001)</color>",
                PlayerCommand.formatPlayer("Alice", 10001, true));
        var say = new SayCommand().createCommandLine(null, null);
        assertThrows(CommandLine.ParameterException.class, () -> say.parseArgs());
        assertDoesNotThrow(() -> say.parseArgs("hello", "world"));
    }

    @Test
    void coopAndMailUseIdenticalExplicitPlayerSelectors() {
        for (String selector : List.of("@10001", "rino@", "20261010@", "rino@10001")) {
            var coop = new CoopCommand().createCommandLine(null, null);
            assertDoesNotThrow(() -> coop.parseArgs(selector), selector);
            assertDoesNotThrow(() -> coop.parseArgs("@10002", selector), selector);
            assertDoesNotThrow(() -> new MailCommand()
                    .createCommandLine(null, null)
                    .parseArgs("send", selector, "--title", "Hello", "--body", "Test"), selector);
        }
        assertDoesNotThrow(() -> new MailCommand().createCommandLine(null, null)
                .parseArgs("send", "all", "--title", "Hello", "--body", "Test"));
        for (String invalid : List.of("@0", "rino@other", "@rino", "10001", "rino")) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> new CoopCommand().createCommandLine(null, null).parseArgs(invalid));
            assertThrows(CommandLine.ParameterException.class,
                    () -> new CoopCommand().createCommandLine(null, null).parseArgs("@10002", invalid));
            assertThrows(CommandLine.ParameterException.class,
                    () -> new CoopCommand().createCommandLine(null, null).parseArgs(invalid, "@10001"));
            assertThrows(CommandLine.ParameterException.class,
                    () -> new MailCommand().createCommandLine(null, null)
                            .parseArgs("send", invalid, "--title", "Hello", "--body", "Test"));
        }
    }

    @Test
    void coopRequiresHostAndMapsOptionalGuestCorrectly() {
        var cli = new CoopCommand().createCommandLine(null, null);
        assertTrue(cli.getUsageMessage().contains("coop [guestSelector] <hostSelector>"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs());
        assertThrows(CommandLine.ParameterException.class,
                () -> cli.parseArgs("@10001", "@10002", "@10003"));

        var one = CoopCommand.splitSelectors(
                List.of(CommandMap.parseExplicitTargetSelector("@10002")));
        assertNull(one.guest());
        assertEquals(10002, one.host().uid());

        var two = CoopCommand.splitSelectors(List.of(
                CommandMap.parseExplicitTargetSelector("@10001"),
                CommandMap.parseExplicitTargetSelector("@10002")));
        assertEquals(10001, two.guest().uid());
        assertEquals(10002, two.host().uid());

        var annotation = CoopCommand.class.getAnnotation(Command.class);
        assertEquals(Command.TargetRequirement.NONE, annotation.targetRequirement());
        assertFalse(annotation.inlineTarget());
        assertEquals("server.coop", annotation.permission());
    }

    @Test
    void unbanAndKickUseExplicitSelectorsWithoutKeystoreKeys() {
        var unban = new UnbanCommand().createCommandLine(null, null);
        var kick = new KickCommand().createCommandLine(null, null);
        for (String selector : List.of("@10001", "rino@", "rino@10001", "20261010@")) {
            assertDoesNotThrow(() -> unban.parseArgs(selector), selector);
            assertDoesNotThrow(() -> kick.parseArgs(selector), selector);
            assertEquals(BanCommand.TargetType.PLAYER, BanCommand.parseTarget(selector, "unban").type());
        }
        assertDoesNotThrow(() -> unban.parseArgs("127.0.0.1"));
        assertEquals(BanCommand.TargetType.IPV4, BanCommand.parseTarget("127.0.0.1", "unban").type());
        assertThrows(CommandLine.ParameterException.class,
                () -> unban.parseArgs("ip", "key", "127.0.0.1"));
        assertThrows(CommandLine.ParameterException.class,
                () -> unban.parseArgs("player", "@10001"));

        assertFalse(KickCommand.class.getAnnotation(Command.class).inlineTarget());
        assertEquals("server.kick", KickCommand.class.getAnnotation(Command.class).permission());
        assertEquals(Command.TargetRequirement.NONE,
                KickCommand.class.getAnnotation(Command.class).targetRequirement());
        assertTrue(kick.getUsageMessage().contains("kick <playerSelector>"));
        assertThrows(CommandLine.ParameterException.class, () -> kick.parseArgs());
        assertThrows(IllegalArgumentException.class,
                () -> CommandMap.parseExplicitTargetSelector("keystorePassword"));

        var map = new CommandMap(false);
        map.registerCommand("kick", new KickCommand());
        assertNull(map.getHandler("restart"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("restart"));
    }
}
