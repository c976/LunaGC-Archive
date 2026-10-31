package emu.grasscutter.command;

import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.game.ExecuteCommandEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.reflections.Reflections;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.shell.jline3.PicocliJLineCompleter;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class CommandMap {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CommandMap.class);
    private static final int INVALID_UID = Integer.MIN_VALUE;
    private static final String CONSOLE_ID = "console";
    private static final Parser COMMAND_PARSER = new DefaultParser();

    private record SelectedTarget(int uid, String username) {}

    /** A target identifies a player by UID, account username, or both explicitly. */
    public record TargetSelector(String username, Integer uid) {}

    private final Map<String, CommandHandler> commands = new TreeMap<>();
    private final Map<String, CommandHandler> aliases = new TreeMap<>();
    private final Map<String, Command> annotations = new TreeMap<>();
    private final ConcurrentMap<String, SelectedTarget> selectedTargets = new ConcurrentHashMap<>();
    private final Object picocliLock = new Object();

    private volatile CommandLine commandLine = createRootCommandLine();

    public CommandMap() {
        this(false);
    }

    public CommandMap(boolean scan) {
        if (scan) {
            this.scan();
            this.installConsoleCompleter();
        }
    }

    public static CommandMap getInstance() {
        return Grasscutter.getCommandMap();
    }

    public CommandLine getCommandLine() {
        return this.commandLine;
    }

    /** Console prompt reflects the remembered default target, not a one-command @UID override. */
    public String getConsolePrompt() {
        SelectedTarget target = selectedTargets.get(CONSOLE_ID);
        return target == null ? "asta> " : formatConsolePrompt(target.username(), target.uid());
    }

    static String formatConsolePrompt(String username, int uid) {
        return username + "@" + uid + "> ";
    }

    static CommandLine createRootCommandLine() {
        var root = new CommandLine(CommandSpec.create().name("astaps"));
        // @UID is AstaPS targeting syntax, never a picocli argument file.
        root.setExpandAtFiles(false);
        return root;
    }

    static List<String> parseCommandTokens(String rawMessage) throws SyntaxError {
        return new ArrayList<>(
                COMMAND_PARSER
                        .parse(rawMessage, rawMessage.length(), Parser.ParseContext.ACCEPT_LINE)
                        .words());
    }

    static String normalizeTargetSelector(String selector) {
        return selector.startsWith("@") ? selector.substring(1) : selector;
    }

    /** Recognize only explicit selector tokens; do not steal ordinary email arguments. */
    static boolean isTargetSelector(String token) {
        if (token == null) return false;
        int separator = token.indexOf('@');
        if (separator < 0) return false;
        return separator == 0
                || separator == token.length() - 1
                || isDecimalUid(token.substring(separator + 1));
    }

    static String takeInlineTargetSelector(List<String> args, boolean inlineTarget) {
        if (!inlineTarget) return null;

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (isTargetSelector(arg)) return args.remove(i);
        }
        return null;
    }

    /** Check again immediately before execution: threaded commands may sit in the queue while a player disconnects. */
    static boolean targetUsable(Command.TargetRequirement requirement, Player target) {
        if (requirement == Command.TargetRequirement.NONE) return true;
        if (target == null) return false;
        return switch (requirement) {
            case NONE, PLAYER -> true;
            case ONLINE -> target.isOnline()
                    && target.getSession() != null
                    && target.getSession().isActive();
            case OFFLINE -> !target.isOnline();
        };
    }

    private static void sendUnavailableTarget(Player sender, Command.TargetRequirement requirement) {
        String key = switch (requirement) {
            case ONLINE -> "commands.execution.need_target_online";
            case OFFLINE -> "commands.execution.need_target_offline";
            default -> "commands.execution.need_target";
        };
        CommandOutput.sendTranslatedMessage(sender, key);
    }

    static void executeCommand(Runnable runnable, boolean threaded, Executor executor) {
        if (threaded) executor.execute(runnable);
        else runnable.run();
    }

    private static String normalizeCommandName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Command names must not be blank.");
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static String[] normalizeAliases(Command annotation) {
        String[] declaredAliases = annotation.aliases();
        String[] normalizedAliases = new String[declaredAliases.length];
        for (int i = 0; i < declaredAliases.length; i++) {
            normalizedAliases[i] = normalizeCommandName(declaredAliases[i]);
        }
        return normalizedAliases;
    }

    /** The separators make numeric usernames unambiguous: 20261010@ is a name. */
    public static TargetSelector parseTargetSelector(String selector) {
        if (selector == null || selector.isEmpty() || selector.equals("@")) {
            throw new IllegalArgumentException("Use @UID, username@, or username@UID.");
        }

        int separator = selector.indexOf('@');
        if (separator < 0) {
            // Legacy 'target 10001' is an explicit UID; no bare username lookup.
            return new TargetSelector(null, parsePositiveUid(selector));
        }
        if (separator != selector.lastIndexOf('@')) {
            throw new IllegalArgumentException("Use @UID, username@, or username@UID.");
        }

        String username = separator == 0 ? null : selector.substring(0, separator);
        String uidText = selector.substring(separator + 1);
        Integer uid = uidText.isEmpty() ? null : parsePositiveUid(uidText);
        return new TargetSelector(username, uid);
    }

    /** Positional player references must spell their intent with an @ separator. */
    public static TargetSelector parseExplicitTargetSelector(String selector) {
        if (!isTargetSelector(selector)) {
            throw new IllegalArgumentException("Use @UID, username@, or username@UID.");
        }
        return parseTargetSelector(selector);
    }

    private static boolean isDecimalUid(String text) {
        return !text.isEmpty() && text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static int parsePositiveUid(String text) {
        if (isDecimalUid(text)) {
            try {
                int uid = Integer.parseInt(text);
                if (uid > 0) return uid;
            } catch (NumberFormatException ignored) {
                // Values outside the int range are invalid.
            }
        }
        throw new IllegalArgumentException("Invalid player UID: " + text);
    }

    public static boolean targetMatches(TargetSelector selector, String username, int uid) {
        return (selector.uid() == null || selector.uid() == uid)
                && (selector.username() == null || selector.username().equals(username));
    }

    /** Resolve the same explicit player identity for commands and positional arguments. */
    public static Player findPlayer(TargetSelector selector) {
        final Player player;
        if (selector.uid() != null) {
            player = Grasscutter.getGameServer().getPlayerByUid(selector.uid(), true);
        } else {
            var account = DatabaseHelper.getAccountByName(selector.username());
            player = account == null ? null : DatabaseHelper.getPlayerByAccount(account, Player.class);
        }
        if (player != null && !targetMatches(selector, player.getAccount().getUsername(), player.getUid())) {
            throw new IllegalArgumentException("Account username and UID do not match.");
        }
        return player;
    }

    /** Unlike a player, an account may exist with just a reserved UID. */
    public static Account findAccount(TargetSelector selector) {
        final Account account;
        if (selector.uid() != null) {
            Player player = Grasscutter.getGameServer().getPlayerByUid(selector.uid(), true);
            account = player == null
                    ? DatabaseHelper.getAccountByPlayerId(selector.uid())
                    : player.getAccount();
        } else {
            account = DatabaseHelper.getAccountByName(selector.username());
        }
        if (account != null && selector.username() != null
                && !selector.username().equals(account.getUsername())) {
            throw new IllegalArgumentException("Account username and UID do not match.");
        }
        return account;
    }

    private static Player resolveTarget(String selector, Player sender) {
        final TargetSelector parsed;
        try {
            parsed = parseTargetSelector(selector);
        } catch (IllegalArgumentException invalid) {
            CommandOutput.sendMessage(sender, invalid.getMessage());
            throw invalid;
        }

        final Player target;
        try {
            target = findPlayer(parsed);
        } catch (IllegalArgumentException mismatch) {
            CommandOutput.sendMessage(sender, mismatch.getMessage());
            throw mismatch;
        }
        if (target == null) {
            CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_exist_error");
            throw new IllegalArgumentException("Player not found");
        }
        return target;
    }

    /** Show usage for structural syntax errors, but keep invalid values to one clear message. */
    static List<String> parameterErrorMessages(CommandLine.ParameterException exception) {
        Throwable cause = exception.getCause();
        String detail = cause instanceof CommandLine.TypeConversionException conversion
                        && conversion.getMessage() != null
                        && !conversion.getMessage().isBlank()
                ? conversion.getMessage()
                : exception.getMessage();
        boolean syntaxError = exception instanceof CommandLine.MissingParameterException
                || exception instanceof CommandLine.UnmatchedArgumentException;
        if (!syntaxError) return detail == null || detail.isBlank() ? List.of() : List.of(detail);
        String syntax = exception.getCommandLine().getUsageMessage().stripTrailing();
        return detail == null || detail.isBlank() ? List.of(syntax) : List.of(detail, syntax);
    }

    private static CommandLine configureCommandLine(
            CommandLine cli, Player sender, CommandHandler handler) {
        cli.setExpandAtFiles(false);
        // Old forms such as "teapot setLevel" and "trial toggleDungeon" were case-insensitive.
        cli.setSubcommandsCaseInsensitive(true);
        cli.setParameterExceptionHandler(
                (exception, argv) -> {
                    parameterErrorMessages(exception)
                            .forEach(message -> CommandOutput.sendMessage(sender, message));
                    return exception.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
                });
        cli.setExecutionExceptionHandler(
                (exception, commandLine, parseResult) -> {
                    LOGGER
                            .error("Failed to execute command " + handler.getLabel() + ".", exception);
                    String message = exception.getMessage();
                    CommandOutput.sendMessage(
                            sender,
                            message == null || message.isBlank()
                                    ? "Command execution failed."
                                    : message);
                    return commandLine.getCommandSpec().exitCodeOnExecutionException();
                });
        return cli;
    }

    /** Legacy argument rewrites only target the built-in grammars, never a plugin's own. */
    private static boolean isBuiltIn(CommandHandler handler) {
        return handler.getClass().getName().startsWith("emu.grasscutter.command.commands.");
    }

    private static CommandLine createCompletionCommandLine(
            String label, CommandHandler handler) {
        CommandLine child = handler.createCompletionCommandLine();
        child.getCommandSpec().name(label);
        child.setExpandAtFiles(false);
        return child;
    }

    private void rebuildPicocliTree() {
        synchronized (this.picocliLock) {
            var root = createRootCommandLine();

            for (var entry : this.commands.entrySet()) {
                String label = entry.getKey();
                CommandHandler handler = entry.getValue();
                Command annotation = this.annotations.get(label);
                CommandLine child = createCompletionCommandLine(label, handler);
                root.addSubcommand(label, child, normalizeAliases(annotation));
            }

            root.setExpandAtFiles(false);
            this.commandLine = root;
        }
    }

    void installConsoleCompleter() {
        var reader = Grasscutter.getConsole();
        if (reader instanceof LineReaderImpl lineReader) {
            lineReader.getKeyMaps().get(LineReader.MAIN)
                    .bind(new Reference(LineReader.COMPLETE_WORD), "\t");
            lineReader.setCompleter(
                    (currentReader, parsedLine, candidates) -> {
                        synchronized (this.picocliLock) {
                            new PicocliJLineCompleter(this.commandLine.getCommandSpec())
                                    .complete(currentReader, parsedLine, candidates);
                        }
                    });
        }
    }

    private String resolveCommandLabel(String label) {
        synchronized (this.picocliLock) {
            var child = this.commandLine.getSubcommands().get(label);
            return child == null ? null : child.getCommandSpec().name();
        }
    }

    /** These tokens are handled before Picocli routing and can never reach a registered command. */
    static boolean isReservedCommandName(String name) {
        return "target".equals(name) || isTargetSelector(name);
    }

    private void validateRegistration(String label, Command annotation) {
        String annotationLabel = normalizeCommandName(annotation.label());
        if (!label.equals(annotationLabel)) {
            throw new IllegalArgumentException(
                    "Registered command label '"
                            + label
                            + "' must match @Command label '"
                            + annotationLabel
                            + "'.");
        }

        if (isReservedCommandName(label)) {
            throw new IllegalArgumentException("Reserved command name: " + label);
        }

        if (this.commands.containsKey(label) || this.aliases.containsKey(label)) {
            throw new IllegalArgumentException("Command name already registered: " + label);
        }

        var registrationNames = new HashSet<String>();
        registrationNames.add(label);
        for (String alias : annotation.aliases()) {
            String normalized = normalizeCommandName(alias);
            if (isReservedCommandName(normalized)) {
                throw new IllegalArgumentException("Reserved command alias: " + normalized);
            }
            if (!registrationNames.add(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate command label or alias in registration: " + normalized);
            }
            if (this.commands.containsKey(normalized) || this.aliases.containsKey(normalized)) {
                throw new IllegalArgumentException("Command name already registered: " + normalized);
            }
        }
    }

    private void addRegistration(String label, CommandHandler command, Command annotation) {
        this.annotations.put(label, annotation);
        this.commands.put(label, command);

        for (String alias : annotation.aliases()) {
            String normalized = normalizeCommandName(alias);
            this.aliases.put(normalized, command);
            this.annotations.put(normalized, annotation);
        }
    }

    private void removeRegistration(String label, Command annotation) {
        this.annotations.remove(label);
        this.commands.remove(label);

        for (String alias : annotation.aliases()) {
            String normalized = normalizeCommandName(alias);
            this.aliases.remove(normalized);
            this.annotations.remove(normalized);
        }
    }

    private void removePicocliCommand(String label, Command annotation) {
        CommandSpec root = this.commandLine.getCommandSpec();
        CommandLine removed = root.removeSubcommand(label);
        if (removed == null) {
            throw new IllegalStateException("Picocli command missing from routing tree: " + label);
        }

        for (String alias : normalizeAliases(annotation)) {
            root.removeSubcommand(alias);
        }
    }

    private void restoreRegistry(
            Map<String, CommandHandler> previousCommands,
            Map<String, CommandHandler> previousAliases,
            Map<String, Command> previousAnnotations,
            CommandLine previousTree) {
        this.commands.clear();
        this.commands.putAll(previousCommands);
        this.aliases.clear();
        this.aliases.putAll(previousAliases);
        this.annotations.clear();
        this.annotations.putAll(previousAnnotations);
        this.commandLine = previousTree;
    }

    public CommandMap registerCommand(String label, CommandHandler command) {
        label = normalizeCommandName(label);
        LOGGER.trace("Registered command: " + label);

        synchronized (this.picocliLock) {
            Command annotation = command.getClass().getAnnotation(Command.class);
            if (annotation == null) {
                throw new IllegalArgumentException("Command handler must be annotated with @Command.");
            }

            this.validateRegistration(label, annotation);
            CommandLine child = createCompletionCommandLine(label, command);
            this.commandLine.addSubcommand(label, child, normalizeAliases(annotation));
            this.addRegistration(label, command, annotation);
        }

        return this;
    }

    CommandMap registerCommands(List<? extends CommandHandler> commandHandlers) {
        if (commandHandlers.isEmpty()) return this;

        synchronized (this.picocliLock) {
            var previousCommands = new TreeMap<>(this.commands);
            var previousAliases = new TreeMap<>(this.aliases);
            var previousAnnotations = new TreeMap<>(this.annotations);
            CommandLine previousTree = this.commandLine;

            try {
                for (CommandHandler command : commandHandlers) {
                    Command annotation = command.getClass().getAnnotation(Command.class);
                    if (annotation == null) {
                        throw new IllegalArgumentException(
                                "Command handler must be annotated with @Command.");
                    }

                    String label = normalizeCommandName(annotation.label());
                    LOGGER.trace("Registered command: " + label);
                    this.validateRegistration(label, annotation);
                    this.addRegistration(label, command, annotation);
                }

                this.rebuildPicocliTree();
            } catch (RuntimeException exception) {
                this.restoreRegistry(
                        previousCommands, previousAliases, previousAnnotations, previousTree);
                throw exception;
            }
        }

        return this;
    }

    public CommandMap unregisterCommand(String label) {
        label = normalizeCommandName(label);
        LOGGER.trace("Un-registered command: " + label);

        synchronized (this.picocliLock) {
            CommandHandler handler = this.commands.get(label);
            if (handler == null) return this;

            Command annotation = handler.getClass().getAnnotation(Command.class);
            this.removePicocliCommand(label, annotation);
            this.removeRegistration(label, annotation);
        }
        return this;
    }

    public List<Command> getAnnotationsAsList() {
        synchronized (this.picocliLock) {
            return new ArrayList<>(this.annotations.values());
        }
    }

    public Map<String, Command> getAnnotations() {
        synchronized (this.picocliLock) {
            return new LinkedHashMap<>(this.annotations);
        }
    }

    public List<CommandHandler> getHandlersAsList() {
        synchronized (this.picocliLock) {
            return new ArrayList<>(this.commands.values());
        }
    }

    public Map<String, CommandHandler> getHandlers() {
        synchronized (this.picocliLock) {
            return new LinkedHashMap<>(this.commands);
        }
    }

    public CommandHandler getHandler(String label) {
        String normalized = normalizeCommandName(label);
        synchronized (this.picocliLock) {
            CommandHandler handler = this.commands.get(normalized);
            if (handler == null) handler = this.aliases.get(normalized);
            return handler;
        }
    }

    private Player getTargetPlayer(
            String playerId,
            Player player,
            Player targetPlayer,
            List<String> args,
            boolean inlineTarget) {
        String inlineSelector = takeInlineTargetSelector(args, inlineTarget);
        if (inlineSelector != null) return resolveTarget(inlineSelector, player);

        if (targetPlayer != null) return targetPlayer;

        SelectedTarget rememberedTarget = selectedTargets.get(playerId);
        if (rememberedTarget != null) {
            targetPlayer = Grasscutter.getGameServer().getPlayerByUid(rememberedTarget.uid(), true);
            if (targetPlayer == null) {
                CommandOutput.sendTranslatedMessage(player, "commands.execution.player_exist_error");
                throw new IllegalArgumentException();
            }
            return targetPlayer;
        }

        return player;
    }

    private boolean setPlayerTarget(String playerId, Player player, String selector) {
        if (selector.isEmpty() || selector.equals("@")) {
            selectedTargets.remove(playerId);
            CommandOutput.sendTranslatedMessage(player, "commands.execution.clear_target");
            return true;
        }

        final Player targetPlayer;
        try {
            targetPlayer = resolveTarget(selector, player);
        } catch (IllegalArgumentException invalid) {
            return false;
        }

        int uid = targetPlayer.getUid();
        String username = targetPlayer.getAccount().getUsername();
        selectedTargets.put(playerId, new SelectedTarget(uid, username));
        String target = uid + " (" + username + ")";
        CommandOutput.sendTranslatedMessage(player, "commands.execution.set_target", target);
        CommandOutput.sendTranslatedMessage(
                player,
                targetPlayer.isOnline()
                        ? "commands.execution.set_target_online"
                        : "commands.execution.set_target_offline",
                target);
        return true;
    }

    public void invoke(Player player, Player targetPlayer, String rawMessage) {
        var event = new ExecuteCommandEvent(player, targetPlayer, rawMessage);
        if (!event.call()) return;

        player = event.getSender();
        targetPlayer = event.getTarget();
        rawMessage = event.getCommand();

        if (SERVER.logCommands) {
            if (player != null) {
                LOGGER
                        .info(
                                "Command used by ["
                                        + player.getAccount().getUsername()
                                        + " (Player UID: "
                                        + player.getUid()
                                        + ")]: "
                                        + rawMessage);
            } else {
                LOGGER.info("Command used by server console: " + rawMessage);
            }
        }

        rawMessage = rawMessage.trim();
        if (rawMessage.isEmpty()) {
            CommandOutput.sendTranslatedMessage(player, "commands.generic.not_specified");
            return;
        }

        final List<String> tokens;
        try {
            tokens = parseCommandTokens(rawMessage);
        } catch (SyntaxError error) {
            CommandOutput.sendMessage(player, error.getMessage());
            return;
        }
        if (tokens.isEmpty()) return;

        String rawLabel = tokens.remove(0);
        String label = rawLabel.toLowerCase(Locale.ROOT);
        List<String> args = tokens;
        String playerId = (player == null) ? CONSOLE_ID : player.getAccount().getId();

        if (isTargetSelector(rawLabel)) {
            this.setPlayerTarget(playerId, player, rawLabel);
            return;
        }
        if (label.equals("target")) {
            if (args.size() > 1) {
                CommandOutput.sendMessage(player, "Usage: target [UID|@UID|username@|username@UID]");
                return;
            }
            this.setPlayerTarget(
                    playerId, player, args.isEmpty() ? "" : normalizeTargetSelector(args.get(0)));
            return;
        }

        String typedLabel = label;
        List<String> typedArgs = List.copyOf(args);
        String resolvedLabel = this.resolveCommandLabel(label);
        if (resolvedLabel == null) {
            List<String> rewritten = LegacyCommandSyntax.rewriteRemovedLabel(label, args);
            if (rewritten != null) {
                label = rewritten.get(0);
                args = new ArrayList<>(rewritten.subList(1, rewritten.size()));
                resolvedLabel = this.resolveCommandLabel(label);
            }
        }
        if (resolvedLabel == null) {
            CommandOutput.sendTranslatedMessage(player, "commands.generic.unknown_command", typedLabel);
            return;
        }
        CommandHandler routed;
        synchronized (this.picocliLock) {
            routed = this.commands.get(resolvedLabel);
        }
        if (routed != null && isBuiltIn(routed)) {
            List<String> rerouted = LegacyCommandSyntax.rerouteBuiltIn(resolvedLabel, args);
            String reroutedLabel = rerouted == null ? null : this.resolveCommandLabel(rerouted.get(0));
            if (reroutedLabel != null) {
                label = rerouted.get(0);
                args = new ArrayList<>(rerouted.subList(1, rerouted.size()));
                resolvedLabel = reroutedLabel;
            }
        }

        final CommandHandler handler;
        final Command annotation;
        synchronized (this.picocliLock) {
            handler = this.commands.get(resolvedLabel);
            annotation = this.annotations.get(resolvedLabel);
        }
        if (handler == null || annotation == null) {
            CommandOutput.sendTranslatedMessage(player, "commands.generic.unknown_command", typedLabel);
            return;
        }

        if (isBuiltIn(handler)) {
            args = new ArrayList<>(LegacyCommandSyntax.rewriteArguments(resolvedLabel, args));
        }
        if (!label.equals(typedLabel) || !args.equals(typedArgs)) {
            String current = label + (args.isEmpty() ? "" : " " + String.join(" ", args));
            LOGGER.debug("Legacy command syntax '{} {}' routed as '{}'",
                    typedLabel, String.join(" ", typedArgs), current);
            CommandOutput.sendMessage(player, "Old syntax; current form: /" + current);
        }

        try {
            targetPlayer =
                    getTargetPlayer(
                            playerId, player, targetPlayer, args, annotation.inlineTarget());
        } catch (IllegalArgumentException e) {
            return;
        }

        if (!Grasscutter.getPermissionHandler()
                .checkPermission(
                        player,
                        targetPlayer,
                        annotation.permission(),
                        annotation.permissionTargeted())) {
            return;
        }

        Command.TargetRequirement targetRequirement = annotation.targetRequirement();
        if (!targetUsable(targetRequirement, targetPlayer)) {
            sendUnavailableTarget(player, targetPlayer == null
                    ? Command.TargetRequirement.PLAYER : targetRequirement);
            return;
        }

        final Player sender = player;
        final Player target = targetPlayer;
        final String[] commandArgs = args.toArray(String[]::new);
        Runnable runnable = () -> {
            // Permission and target selection happened on dispatch; queued work must not
            // operate on a player who logged out or changed online state in the meantime.
            if (!targetUsable(targetRequirement, target)) {
                sendUnavailableTarget(sender, targetRequirement);
                return;
            }
            try {
                CommandLine cli = configureCommandLine(
                        handler.createCommandLine(sender, target), sender, handler);
                cli.execute(commandArgs);
            } catch (RuntimeException exception) {
                // Picocli handles command.run() failures, but model construction itself
                // is outside its error handler. Keep those exceptions off the game loop.
                LOGGER.error(
                        "Failed to construct or run command " + handler.getLabel() + ".", exception);
                CommandOutput.sendMessage(sender, "Command execution failed.");
            }
        };

        try {
            executeCommand(runnable, annotation.threading(), Grasscutter.getThreadPool());
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            LOGGER.warn(
                    "Command executor rejected " + handler.getLabel() + ".", exception);
            CommandOutput.sendMessage(sender, "Command executor is unavailable.");
        }
    }

    private void scan() {
        Reflections reflector = Grasscutter.reflector;
        Set<Class<?>> classes = reflector.getTypesAnnotatedWith(Command.class);
        var handlers = new ArrayList<CommandHandler>(classes.size());

        for (Class<?> annotated : classes) {
            try {
                Object object = annotated.getDeclaredConstructor().newInstance();
                if (object instanceof CommandHandler handler) {
                    handlers.add(handler);
                } else {
                    LOGGER
                            .error("Class " + annotated.getName() + " is not a CommandHandler!");
                }
            } catch (Exception exception) {
                LOGGER
                        .error(
                                "Failed to instantiate command handler for "
                                        + annotated.getSimpleName(),
                                exception);
            }
        }

        this.registerCommands(handlers);
    }
}
