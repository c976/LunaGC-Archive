package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketSetPlayerNameRsp;
import emu.grasscutter.utils.RichTextUtils;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Sets a player's nickname server-side, including rich-text names. */
@Command(
        label = "name",
        aliases = {"rename"},
        permission = "player.name",
        permissionTargeted = "player.name.others")
public final class NameCommand implements CommandHandler {
    private static final String DEFAULT_NICKNAME = "Traveler";
    private static final int MAX_GRADIENT_LENGTH = 32;
    private static final int MAX_STORED_LENGTH = 1024;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new NameArgs(sender, targetPlayer));
        commandLine.addSubcommand("reset", new Reset(sender, targetPlayer));
        commandLine.addSubcommand("uid", new Uid(sender, targetPlayer));
        commandLine.addSubcommand("gradient", new Gradient(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "name")
    private final class NameArgs implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<text>")
        private List<String> text;

        private NameArgs(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            apply(sender, targetPlayer, String.join(" ", text));
        }
    }

    @CommandLine.Command(name = "reset")
    private final class Reset implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Reset(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            apply(sender, targetPlayer, DEFAULT_NICKNAME);
        }
    }

    @CommandLine.Command(name = "uid")
    private final class Uid implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Uid(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            apply(sender, targetPlayer, String.valueOf(targetPlayer.getUid()));
        }
    }

    @CommandLine.Command(name = "gradient")
    private final class Gradient implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<text>")
        private String text;

        @Parameters(index = "1", paramLabel = "<#startColor>")
        private String startColor;

        @Parameters(index = "2", paramLabel = "<#endColor>")
        private String endColor;

        private Gradient(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            String value = text.equalsIgnoreCase("uid") ? String.valueOf(targetPlayer.getUid()) : text;
            if (value.length() > MAX_GRADIENT_LENGTH) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.name.too_long", MAX_GRADIENT_LENGTH));
                return;
            }

            int start = RichTextUtils.parseColor(startColor);
            int end = RichTextUtils.parseColor(endColor);
            if (start < 0 || end < 0) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.name.bad_color"));
                return;
            }

            apply(sender, targetPlayer, RichTextUtils.gradient(value, start, end));
        }
    }

    private void apply(Player sender, Player targetPlayer, String nickname) {
        if (nickname.isBlank()) {
            sendUsageMessage(sender);
            return;
        }

        if (nickname.length() > MAX_STORED_LENGTH) {
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.name.too_long", MAX_STORED_LENGTH));
            return;
        }

        targetPlayer.setNickname(nickname);
        targetPlayer.save();
        targetPlayer.sendPacket(new PacketSetPlayerNameRsp(targetPlayer));
        CommandOutput.sendMessage(sender, translate(sender, "commands.name.success", nickname.length()));
    }
}
