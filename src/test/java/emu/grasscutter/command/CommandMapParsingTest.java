package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.Parser;
import org.jline.reader.Reference;
import org.jline.reader.impl.LineReaderImpl;
import emu.grasscutter.Grasscutter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class CommandMapParsingTest {
    @Test
    public void parserPreservesQuotedAndNegativeArguments() throws Exception {
        assertEquals(
                List.of("give", "1001", "hello world", "-5"),
                CommandMap.parseCommandTokens("give 1001 \"hello world\" -5"));
    }

    @Test
    public void explicitTargetSelectorsDistinguishUidUsernameAndBoth() {
        assertEquals(new CommandMap.TargetSelector(null, 10001),
                CommandMap.parseTargetSelector("@10001"));
        assertEquals(new CommandMap.TargetSelector("rino", null),
                CommandMap.parseTargetSelector("rino@"));
        assertEquals(new CommandMap.TargetSelector("rino", 10001),
                CommandMap.parseTargetSelector("rino@10001"));
        assertEquals(new CommandMap.TargetSelector("20261010", null),
                CommandMap.parseTargetSelector("20261010@"));
        assertEquals(new CommandMap.TargetSelector("20261010", 10001),
                CommandMap.parseTargetSelector("20261010@10001"));
        assertEquals(new CommandMap.TargetSelector(null, 10001),
                CommandMap.parseTargetSelector("10001"));
        assertEquals(new CommandMap.TargetSelector(null, 10001),
                CommandMap.parseTargetSelector(CommandMap.normalizeTargetSelector("@10001")));
        assertTrue(CommandMap.targetMatches(
                CommandMap.parseTargetSelector("rino@10001"), "rino", 10001));
        assertFalse(CommandMap.targetMatches(
                CommandMap.parseTargetSelector("rino@10001"), "alice", 10001));
        assertFalse(CommandMap.targetMatches(
                CommandMap.parseTargetSelector("rino@10001"), "rino", 10002));
        assertTrue(CommandMap.targetMatches(
                CommandMap.parseTargetSelector("20261010@"), "20261010", 10002));
    }

    @Test
    public void malformedTargetsNeverResolveViaAnotherInterpretation() {
        for (String bad : new String[] {
                "", "@", "@rino", "rino", "@0", "@-1", "rino@0",
                "rino@abc", "rino@@10001", "@2147483648", "name:20261010",
                "@+123", "rino@+123"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> CommandMap.parseTargetSelector(bad), bad);
        }
    }

    @Test
    public void explicitSelectorsRejectBareNamesAndUids() {
        assertEquals(new CommandMap.TargetSelector(null, 10001),
                CommandMap.parseExplicitTargetSelector("@10001"));
        assertEquals(new CommandMap.TargetSelector("rino", null),
                CommandMap.parseExplicitTargetSelector("rino@"));
        assertEquals(new CommandMap.TargetSelector("20261010", 10001),
                CommandMap.parseExplicitTargetSelector("20261010@10001"));
        for (String invalid : List.of("rino", "10001", "someone@example.com", "@")) {
            assertThrows(IllegalArgumentException.class,
                    () -> CommandMap.parseExplicitTargetSelector(invalid), invalid);
        }
    }

    @Test
    public void inlineSelectorRecognitionDoesNotConsumeEmailArguments() {
        assertTrue(CommandMap.isTargetSelector("@10001"));
        assertTrue(CommandMap.isTargetSelector("rino@"));
        assertTrue(CommandMap.isTargetSelector("rino@10001"));
        assertTrue(CommandMap.isTargetSelector("@"));
        assertFalse(CommandMap.isTargetSelector("rino"));
        assertFalse(CommandMap.isTargetSelector("someone@example.com"));
        assertFalse(CommandMap.isTargetSelector("rino@other"));

        var inlineArgs = new ArrayList<>(List.of("give", "rino@10001", "someone@example.com"));
        assertEquals("rino@10001", CommandMap.takeInlineTargetSelector(inlineArgs, true));
        assertEquals(List.of("give", "someone@example.com"), inlineArgs);

        var localArgs = new ArrayList<>(List.of("coop", "@10001"));
        assertNull(CommandMap.takeInlineTargetSelector(localArgs, false));
        assertEquals(List.of("coop", "@10001"), localArgs);
    }

    @Test
    public void consolePromptShowsSelectedTargetAccountAndUid() {
        var map = new CommandMap(false);
        assertEquals("asta> ", map.getConsolePrompt());
        assertEquals("rino@10001> ", CommandMap.formatConsolePrompt("rino", 10001));
        assertEquals("another_account@987654> ",
                CommandMap.formatConsolePrompt("another_account", 987654));
    }

    @Test
    public void tabCompletionUsesTheActiveConsoleReader() {
        var map = new CommandMap(false);
        map.registerCommand("probe", new ProbeCommand());
        map.installConsoleCompleter();

        var reader = (LineReaderImpl) Grasscutter.getConsole();
        var tab = reader.getKeyMaps().get(LineReader.MAIN).getBound("\t");
        assertTrue(tab instanceof Reference);
        assertEquals(LineReader.COMPLETE_WORD, ((Reference) tab).name());

        var candidates = new ArrayList<Candidate>();
        var parsed = reader.getParser().parse("pro", 3, Parser.ParseContext.COMPLETE);
        reader.getCompleter().complete(reader, parsed, candidates);
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.value().equals("probe")));
    }

    @Test
    public void parameterErrorsIncludeBothCauseAndSubcommandUsage() {
        var dungeon = new emu.grasscutter.command.commands.EnterDungeonCommand()
                .createCommandLine(null, null);
        var invalidId = assertThrows(CommandLine.ParameterException.class,
                () -> dungeon.parseArgs("not-an-integer"));
        var converted = CommandMap.parameterErrorMessages(invalidId);
        assertEquals(1, converted.size());
        assertTrue(converted.getFirst().contains("not-an-integer"));

        var teleport = new emu.grasscutter.command.commands.TeleportCommand()
                .createCommandLine(null, null);
        var missing = assertThrows(CommandLine.ParameterException.class,
                () -> teleport.parseArgs("pos", "1", "2"));
        var messages = CommandMap.parameterErrorMessages(missing);
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("<z>"));

        var extra = assertThrows(CommandLine.UnmatchedArgumentException.class,
                () -> teleport.parseArgs("pos", "1", "2", "3", "4", "5"));
        assertEquals(2, CommandMap.parameterErrorMessages(extra).size());
    }

    @Test
    public void rootDisablesPicocliArgumentFiles() {
        assertFalse(CommandMap.createRootCommandLine().isExpandAtFiles());
    }

    @Test
    public void targetRequirementsRejectMissingPlayersBeforeCommandExecution() {
        assertTrue(CommandMap.targetUsable(Command.TargetRequirement.NONE, null));
        assertFalse(CommandMap.targetUsable(Command.TargetRequirement.PLAYER, null));
        assertFalse(CommandMap.targetUsable(Command.TargetRequirement.ONLINE, null));
        assertFalse(CommandMap.targetUsable(Command.TargetRequirement.OFFLINE, null));
    }

    @Test
    public void rejectedThreadedCommandsDoNotRun() {
        var ran = new AtomicBoolean();
        assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> CommandMap.executeCommand(
                        () -> ran.set(true),
                        true,
                        command -> {
                            throw new java.util.concurrent.RejectedExecutionException("executor shut down");
                        }));
        assertFalse(ran.get());
    }

    @Test
    public void threadedExecutionUsesProvidedExecutor() {
        var submitted = new AtomicBoolean();
        var ran = new AtomicBoolean();

        CommandMap.executeCommand(
                () -> ran.set(true),
                true,
                command -> {
                    submitted.set(true);
                    command.run();
                });

        assertTrue(submitted.get());
        assertTrue(ran.get());
    }

    @Test
    public void synchronousExecutionDoesNotUseExecutor() {
        var submitted = new AtomicBoolean();
        var ran = new AtomicBoolean();

        CommandMap.executeCommand(
                () -> ran.set(true), false, command -> submitted.set(true));

        assertFalse(submitted.get());
        assertTrue(ran.get());
    }

    @Test
    public void aliasesFollowRegistrationAndUnregistration() {
        var map = new CommandMap(false);
        var handler = new ProbeCommand();

        map.registerCommand("probe", handler);
        assertSame(handler, map.getHandler("probe"));
        assertSame(handler, map.getHandler("p"));

        map.unregisterCommand("probe");
        assertNull(map.getHandler("probe"));
        assertNull(map.getHandler("p"));
    }

    @Test
    public void registrationBuildsPicocliRoutingTree() {
        var map = new CommandMap(false);
        map.registerCommand("probe", new ProbeCommand());

        var root = map.getCommandLine();
        var probe = root.getSubcommands().get("probe");
        assertTrue(root.getSubcommands().containsKey("p"));
        assertSame(probe, root.getSubcommands().get("p"));
        assertEquals("probe", probe.getCommandName());
        assertFalse(probe.isExpandAtFiles());

        map.unregisterCommand("probe");
        assertFalse(map.getCommandLine().getSubcommands().containsKey("probe"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("p"));
    }

    @Test
    public void runtimeRegistrationAndUnregistrationUpdateTreeIncrementally() {
        var map = new CommandMap(false);
        var root = map.getCommandLine();
        var first = new BatchFirstCommand();
        var second = new BatchSecondCommand();

        map.registerCommand("batch-first", first);
        assertSame(root, map.getCommandLine());
        assertEquals(1, first.completionBuilds.get());

        map.registerCommand("batch-second", second);
        assertSame(root, map.getCommandLine());
        assertEquals(1, first.completionBuilds.get());
        assertEquals(1, second.completionBuilds.get());
        assertTrue(root.getSubcommands().containsKey("batch-first"));
        assertTrue(root.getSubcommands().containsKey("batch-second"));

        map.unregisterCommand("batch-second");
        assertSame(root, map.getCommandLine());
        assertEquals(1, first.completionBuilds.get());
        assertEquals(1, second.completionBuilds.get());
        assertTrue(root.getSubcommands().containsKey("batch-first"));
        assertFalse(root.getSubcommands().containsKey("batch-second"));
    }

    @Test
    public void batchRegistrationBuildsEachCompletionModelOnce() {
        var map = new CommandMap(false);
        var first = new BatchFirstCommand();
        var second = new BatchSecondCommand();

        map.registerCommands(List.of(first, second));

        assertEquals(1, first.completionBuilds.get());
        assertEquals(1, second.completionBuilds.get());
        assertSame(first, map.getHandler("batch-first"));
        assertSame(second, map.getHandler("batch-second"));
    }

    @Test
    public void batchRegistrationRollsBackOnCollision() {
        var map = new CommandMap(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommands(List.of(new ProbeCommand(), new AliasCollisionCommand())));

        assertNull(map.getHandler("probe"));
        assertNull(map.getHandler("other"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("probe"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("other"));
    }

    @Test
    public void builtinTargetRoutingCannotBeShadowed() {
        var map = new CommandMap(false);
        assertTrue(CommandMap.isReservedCommandName("target"));
        assertTrue(CommandMap.isReservedCommandName("@10001"));
        assertTrue(CommandMap.isReservedCommandName("rino@"));
        assertFalse(CommandMap.isReservedCommandName("email"));

        assertThrows(IllegalArgumentException.class,
                () -> map.registerCommand("target", new TargetNameCollisionCommand()));
        assertThrows(IllegalArgumentException.class,
                () -> map.registerCommand("alias-target", new TargetAliasCollisionCommand()));
        assertNull(map.getHandler("target"));
        assertNull(map.getHandler("alias-target"));
    }

    @Test
    public void registrationRejectsUnannotatedHandlers() {
        var map = new CommandMap(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommand("invalid", new UnannotatedCommand()));
        assertNull(map.getHandler("invalid"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("invalid"));
    }

    @Test
    public void registrationRejectsExistingLabelWithoutChangingRouting() {
        var map = new CommandMap(false);
        var original = new ProbeCommand();
        map.registerCommand("probe", original);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommand("probe", new ReplacementProbeCommand()));

        assertSame(original, map.getHandler("probe"));
        assertSame(original, map.getHandler("p"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("probe"));
    }

    @Test
    public void registrationRejectsAliasCollisionWithExistingCommand() {
        var map = new CommandMap(false);
        var original = new ProbeCommand();
        map.registerCommand("probe", original);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommand("other", new AliasCollisionCommand()));

        assertSame(original, map.getHandler("probe"));
        assertNull(map.getHandler("other"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("other"));
    }

    @Test
    public void registrationRejectsDuplicateAliasesIgnoringCase() {
        var map = new CommandMap(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommand("duplicate", new DuplicateAliasCommand()));
        assertNull(map.getHandler("duplicate"));
        assertNull(map.getHandler("d"));
    }

    @Test
    public void handlerMapIsASnapshot() {
        var map = new CommandMap(false);
        var handler = new ProbeCommand();
        map.registerCommand("probe", handler);

        map.getHandlers().clear();

        assertSame(handler, map.getHandler("probe"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("probe"));
    }

    @Command(label = "probe", aliases = {"p"}, targetRequirement = Command.TargetRequirement.NONE)
    private static final class ProbeCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(label = "probe", targetRequirement = Command.TargetRequirement.NONE)
    private static final class ReplacementProbeCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(
            label = "other",
            aliases = {"probe"},
            targetRequirement = Command.TargetRequirement.NONE)
    private static final class AliasCollisionCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(
            label = "duplicate",
            aliases = {"d", "D"},
            targetRequirement = Command.TargetRequirement.NONE)
    private static final class DuplicateAliasCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(label = "batch-first", targetRequirement = Command.TargetRequirement.NONE)
    private static final class BatchFirstCommand implements CommandHandler {
        private final AtomicInteger completionBuilds = new AtomicInteger();

        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            if (sender == null && targetPlayer == null) this.completionBuilds.incrementAndGet();
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(label = "batch-second", targetRequirement = Command.TargetRequirement.NONE)
    private static final class BatchSecondCommand implements CommandHandler {
        private final AtomicInteger completionBuilds = new AtomicInteger();

        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            if (sender == null && targetPlayer == null) this.completionBuilds.incrementAndGet();
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(label = "target", targetRequirement = Command.TargetRequirement.NONE)
    private static final class TargetNameCollisionCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(
            label = "alias-target",
            aliases = {"target"},
            targetRequirement = Command.TargetRequirement.NONE)
    private static final class TargetAliasCollisionCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    private static final class UnannotatedCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }
}
