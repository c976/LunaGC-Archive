package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import java.util.Collections;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "announce",
        permission = "server.announce",
        aliases = {"a"},
        targetRequirement = Command.TargetRequirement.NONE,
        inlineTarget = false)
public final class AnnounceCommand implements CommandHandler {

    static boolean validId(int id) {
        return id > 0;
    }

    static boolean validContent(String content) {
        return content != null && !content.isBlank();
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("send", new Broadcast(sender));
        commandLine.addSubcommand("template", new Template(sender), "tpl");
        commandLine.addSubcommand("refresh", new Refresh(sender));
        commandLine.addSubcommand("revoke", new Revoke(sender));
        return commandLine;
    }

    @CommandLine.Command(name = "announce")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            AnnounceCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "send")
    private static final class Broadcast implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<content...>")
        private List<String> content = List.of();

        private Broadcast(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            String text = String.join(" ", content);
            if (!validContent(text)) {
                CommandOutput.sendMessage(sender, "Announcement content must not be blank.");
                return;
            }
            int id = Grasscutter.getGameServer().getAnnouncementSystem().broadcastTemporary(text);
            CommandOutput.sendMessage(sender, translate(sender, "commands.announce.send_success", id));
        }
    }

    @CommandLine.Command(name = "template")
    private static final class Template implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<templateId>")
        private int templateId;

        private Template(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            var manager = Grasscutter.getGameServer().getAnnouncementSystem();
            if (!validId(templateId)) {
                CommandOutput.sendMessage(sender, "Announcement ID must be positive.");
                return;
            }
            var template = manager.getAnnounceConfigItemMap().get(templateId);
            if (template == null) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.announce.not_found", templateId));
                return;
            }
            manager.broadcast(Collections.singletonList(template));
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.announce.send_success", template.getTemplateId()));
        }
    }

    @CommandLine.Command(name = "refresh")
    private static final class Refresh implements Runnable {
        private final Player sender;

        private Refresh(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = Grasscutter.getGameServer().getAnnouncementSystem().refresh();
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.announce.refresh_success", count));
        }
    }

    @CommandLine.Command(name = "revoke")
    private static final class Revoke implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<templateId>")
        private int templateId;

        private Revoke(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (!validId(templateId)) {
                CommandOutput.sendMessage(sender, "Announcement ID must be positive.");
                return;
            }
            if (!Grasscutter.getGameServer().getAnnouncementSystem().revokeKnown(templateId)) {
                CommandOutput.sendMessage(sender, "Announcement " + templateId + " does not exist or has expired.");
                return;
            }
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.announce.revoke_done", templateId));
        }
    }
}
