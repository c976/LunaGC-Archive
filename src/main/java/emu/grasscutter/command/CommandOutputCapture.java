package emu.grasscutter.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the console feedback a command produces on the calling thread.
 *
 * <p>Console feedback normally only reaches the log. The web console runs a command through this
 * so it can hand the command's own output back with the response. A command that runs on its own
 * thread ({@code threading = true}) is not captured; its output still reaches the log.
 */
public final class CommandOutputCapture {
    private static final ThreadLocal<List<String>> current = new ThreadLocal<>();

    private CommandOutputCapture() {}

    public static List<String> capture(Runnable command) {
        var output = new ArrayList<String>();
        var previous = current.get();
        current.set(output);
        try {
            command.run();
        } finally {
            current.set(previous);
        }
        return output;
    }

    static void offer(String message) {
        var output = current.get();
        if (output != null) output.add(message);
    }
}
