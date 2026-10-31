package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CommandOutputAnsiTest {
    @Test
    void picocliStylesAreStrippedForChat() {
        // What a terminal-styled usage line looked like in game chat.
        String styled = "Usage: \u001B[1mteleport\u001B[21m\u001B[0m [\u001B[33m[pointId]\u001B[39m\u001B[0m]";
        assertEquals("Usage: teleport [[pointId]]", CommandOutput.stripAnsi(styled));
        assertEquals("plain", CommandOutput.stripAnsi("plain"));
        assertNull(CommandOutput.stripAnsi(null));
    }
}
