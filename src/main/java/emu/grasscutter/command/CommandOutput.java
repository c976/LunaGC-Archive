package emu.grasscutter.command;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;

/** Shared command-output helpers, separate from the command execution contract. */
public final class CommandOutput {
    private CommandOutput() {}

    /** Send command feedback to a player, or to the server console when {@code player} is null. */
    public static void sendMessage(Player player, String message) {
        var event = new ReceiveCommandFeedbackEvent(player, message);
        event.call();
        if (event.isCanceled()) return;

        if (player == null) {
            Grasscutter.getLogger().info(event.getMessage());
            CommandOutputCapture.offer(stripAnsi(event.getMessage()));
        } else {
            player.dropMessage(stripAnsi(event.getMessage()).replace("\n\t", "\n\n"));
        }
    }

    /**
     * Picocli renders usage help with ANSI styles whenever the server runs on a terminal. Those escape
     * codes are fine on the console but show up as literal "[1m" noise in game chat and the web console.
     */
    static String stripAnsi(String message) {
        return message == null ? null : ANSI_ESCAPE.matcher(message).replaceAll("");
    }

    private static final java.util.regex.Pattern ANSI_ESCAPE =
            java.util.regex.Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

    public static void sendTranslatedMessage(Player player, String messageKey, Object... args) {
        sendMessage(player, translate(player, messageKey, args));
    }
}
