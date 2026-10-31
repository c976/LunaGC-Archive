package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindSeedClientNotify;
import emu.grasscutter.utils.LuaShell;
import emu.grasscutter.server.packet.send.PacketWindy;
import emu.grasscutter.server.packet.send.UidWatermarkScript;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "windy")
public final class WindyCommand implements CommandHandler {
    private static final Set<Double> SUPPORTED_SPEEDS =
            Set.of(0.1, 0.2, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0);

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender, targetPlayer));
        commandLine.addSubcommand("fps", new Packaged(sender, targetPlayer, "fps", "FPS script sent."));
        commandLine.addSubcommand("speed", new Speed(sender, targetPlayer));
        commandLine.addSubcommand(
                "hide-ui", new Packaged(sender, targetPlayer, "HideUI", "UI hidden successfully."));
        commandLine.addSubcommand(
                "no-fog", new Packaged(sender, targetPlayer, "fog", "No-fog script sent."));
        commandLine.addSubcommand(
                "remove-censorship",
                new Packaged(
                        sender,
                        targetPlayer,
                        "RemoveCensorship",
                        "Remove-censorship script sent."));
        //commandLine.addSubcommand("uid", new Uid(sender, targetPlayer));
        return commandLine;
    }

    /** Compiled chunks of the server's own, read from {@code lua/} beside the server as before the port. */
    static final Path CUSTOM_SCRIPTS = Path.of("lua");

    @CommandLine.Command(name = "windy")
    private final class Root implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[lua]",
                description = "A compiled chunk under lua/ beside the server")
        private String script;

        private Root(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (script == null) {
                WindyCommand.this.sendUsageMessage(sender);
                return;
            }
            // Any chunk on disk runs in the client, so this keeps the pre-port server.windy gate.
            if (!hasPermission(sender, targetPlayer) || !hasNode(sender, "server.windy")) return;
            Path path = resolveCustomScript(script);
            if (path == null) {
                CommandOutput.sendMessage(sender, "Invalid Lua script name: " + script);
                return;
            }
            final byte[] chunk;
            try {
                chunk = Files.readAllBytes(path);
            } catch (IOException e) {
                CommandOutput.sendMessage(sender, "Error reading Lua script: " + e.getMessage());
                return;
            }
            //targetPlayer.sendPacket(new PacketWindSeedClientNotify(chunk));
            CommandOutput.sendMessage(sender, "Read BYTECODE from Lua script: " + path.toAbsolutePath());
        }
    }

    /** A file inside {@link #CUSTOM_SCRIPTS}, or null when the name would leave it. */
    static Path resolveCustomScript(String name) {
        if (name == null || name.isBlank()) return null;
        Path root = CUSTOM_SCRIPTS.toAbsolutePath().normalize();
        Path path = root.resolve(name).normalize();
        return path.startsWith(root) && !path.equals(root) ? path : null;
    }

    @CommandLine.Command(name = "packaged")
    private static final class Packaged implements Runnable {
        private final Player sender;
        private final Player targetPlayer;
        private final String script;
        private final String success;

        private Packaged(Player sender, Player targetPlayer, String script, String success) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
            this.script = script;
            this.success = success;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer)) return;
            targetPlayer.sendPacket(new PacketWindy(script));
            CommandOutput.sendMessage(sender, success);
        }
    }

    @CommandLine.Command(name = "speed")
    private final class Speed implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<speed>")
        private double speed;

        private Speed(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer)) return;
            if (!SUPPORTED_SPEEDS.contains(speed)) {
                WindyCommand.this.sendUsageMessage(sender);
                return;
            }
            String text = Double.toString(speed);
            targetPlayer.sendPacket(new PacketWindy("GameSpeed/speed" + text));
            CommandOutput.sendMessage(sender, "GameSpeed changed to " + text + " successfully!");
        }
    }

    @CommandLine.Command(name = "uid")
    private static final class Uid implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0..*", arity = "0..*", paramLabel = "[text|default]",
                description = "Custom watermark text; omit it or pass default for the login Lua shell")
        private List<String> text = List.of();

        private Uid(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer)) return;
            if (text.isEmpty() || (text.size() == 1 && text.get(0).equalsIgnoreCase("default"))) {
                //targetPlayer.sendPacket(new PacketWindSeedClientNotify(LuaShell.getLuaShell()));
                CommandOutput.sendMessage(sender, "Loaded the default login Lua shell watermark.");
                return;
            }
            String joined = String.join(" ", text);
            byte[] chunk = UidWatermarkScript.forText(joined);
            if (chunk == null) {
                CommandOutput.sendMessage(sender, "Watermark text is limited to "
                        + UidWatermarkScript.MAX_TEXT_BYTES + " UTF-8 bytes.");
                return;
            }
            //targetPlayer.sendPacket(new PacketWindSeedClientNotify(chunk));
            CommandOutput.sendMessage(sender, "UID watermark set to " + joined);
        }
    }

    private static boolean hasPermission(Player sender, Player targetPlayer) {
        return hasNode(sender, targetPlayer != sender ? "player.windy.others" : "player.windy");
    }

    private static boolean hasNode(Player sender, String required) {
        if (sender == null) return true;
        var account = sender.getAccount();
        if (account != null && account.hasPermission(required)) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
