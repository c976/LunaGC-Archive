package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.mail.SystemMailHelper;
import emu.grasscutter.game.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        label = "mail",
        permission = "server.sendmail",
        targetRequirement = Command.TargetRequirement.NONE,
        inlineTarget = false)
public final class MailCommand implements CommandHandler {
    private record Recipient(CommandMap.TargetSelector target) {
        private boolean all() {
            return target == null;
        }
    }

    private record Attachment(int itemId, int count, int level) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.setExpandAtFiles(false);
        commandLine.addSubcommand("send", new Send(sender));
        commandLine.addSubcommand("system", createSystemCommand(sender));
        CommandHandler.registerConverterTree(commandLine,
                Recipient.class,
                value -> {
                    if (value.equalsIgnoreCase("all")) return new Recipient(null);
                    try {
                        return new Recipient(CommandMap.parseExplicitTargetSelector(value));
                    } catch (IllegalArgumentException invalid) {
                        throw new CommandLine.TypeConversionException(invalid.getMessage());
                    }
                });
        CommandHandler.registerConverterTree(commandLine,
                Attachment.class,
                value -> {
                    String[] parts = value.split(":");
                    if (parts.length < 2 || parts.length > 3) {
                        throw new CommandLine.TypeConversionException(
                                "Attachment must be <itemId>:<count>[:level]");
                    }
                    try {
                        int itemId = Integer.parseInt(parts[0]);
                        int count = Integer.parseInt(parts[1]);
                        int level = parts.length == 3 ? Integer.parseInt(parts[2]) : 1;
                        if (itemId <= 0 || count <= 0 || level <= 0) throw new NumberFormatException();
                        return new Attachment(itemId, count, level);
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException(
                                "Attachment values must be positive integers");
                    }
                });
        return commandLine;
    }

    private CommandLine createSystemCommand(Player sender) {
        var system = new CommandLine(new SystemRoot(sender));
        system.addSubcommand("send", new SystemSend(sender));
        system.addSubcommand("update", new SystemUpdate(sender));
        system.addSubcommand("retitle", new SystemRetitle(sender));
        system.addSubcommand("delete", new SystemDelete(sender));
        system.addSubcommand("clear", new SystemClear(sender));
        return system;
    }

    @CommandLine.Command(name = "mail")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            MailCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "system")
    private static final class SystemRoot implements Runnable {
        private final Player sender;

        private SystemRoot(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            CommandOutput.sendMessage(sender, "Usage: mail system <send|update|retitle|delete|clear> ...");
        }
    }

    @CommandLine.Command(name = "send")
    private static final class Send implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<playerSelector|all>")
        private Recipient recipient;

        @Option(names = "--title", required = true, arity = "1..*", paramLabel = "<title>")
        private List<String> title;

        @Option(names = "--body", required = true, arity = "1..*", paramLabel = "<body>")
        private List<String> body;

        @Option(names = "--sender", arity = "1..*", paramLabel = "<sender>")
        private List<String> senderName;

        @Option(names = "--item", paramLabel = "<itemId:count[:level]>")
        private List<Attachment> attachments = new ArrayList<>();

        private Send(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Mail mail = new Mail();
            fillMail(mail, title, body, senderName, attachments);

            if (recipient.all()) {
                int[] count = {0};
                DatabaseHelper.getByGameClass(Player.class)
                        .forEach(
                                storedPlayer -> {
                                    Player target =
                                            Objects.requireNonNullElse(
                                                    Grasscutter.getGameServer()
                                                            .getPlayerByUid(storedPlayer.getUid(), false),
                                                    storedPlayer);
                                    target.sendMail(cloneMail(mail, false));
                                    count[0]++;
                                });
                CommandOutput.sendMessage(
                        sender,
                        translate(sender, "commands.sendMail.send_all_done")
                                + " ("
                                + count[0]
                                + ")");
                return;
            }

            final Player stored;
            try {
                stored = CommandMap.findPlayer(recipient.target());
            } catch (IllegalArgumentException mismatch) {
                CommandOutput.sendMessage(sender, mismatch.getMessage());
                return;
            }
            if (stored == null) {
                CommandOutput.sendMessage(
                        sender,
                        translate(
                                sender,
                                "commands.sendMail.user_not_exist",
                                recipient.target().toString()));
                return;
            }
            int uid = stored.getUid();
            Player target =
                    Objects.requireNonNullElse(
                            Grasscutter.getGameServer().getPlayerByUid(uid, false), stored);
            target.sendMail(mail);
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.sendMail.send_done", uid));
        }
    }

    @CommandLine.Command(name = "send")
    private static final class SystemSend implements Runnable {
        private final Player sender;

        @Option(names = "--title", required = true, arity = "1..*", paramLabel = "<title>")
        private List<String> title;

        @Option(names = "--body", required = true, arity = "1..*", paramLabel = "<body>")
        private List<String> body;

        @Option(names = "--sender", arity = "1..*", paramLabel = "<sender>")
        private List<String> senderName;

        @Option(names = "--item", paramLabel = "<itemId:count[:level]>")
        private List<Attachment> attachments = new ArrayList<>();

        private SystemSend(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Mail mail = SystemMailHelper.newSystemMail();
            fillMail(mail, title, body, senderName, attachments);
            mail.mailContent.title = SystemMailHelper.normalizeTitle(mail.mailContent.title);

            int[] sent = {0};
            DatabaseHelper.getByGameClass(Player.class)
                    .forEach(
                            storedPlayer -> {
                                Player target =
                                        Objects.requireNonNullElse(
                                                Grasscutter.getGameServer()
                                                        .getPlayerByUid(storedPlayer.getUid(), false),
                                                storedPlayer);
                                target.sendMail(cloneMail(mail, true));
                                sent[0]++;
                            });
            CommandOutput.sendMessage(
                    sender,
                    "Sent a system mail to " + sent[0] + " player(s): " + mail.mailContent.title);
        }
    }

    @CommandLine.Command(name = "update")
    private static final class SystemUpdate implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<titleKeyword>")
        private String keyword;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "<newBody>")
        private List<String> body;

        private SystemUpdate(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = SystemMailHelper.updateContentByTitle(keyword, String.join(" ", body));
            CommandOutput.sendMessage(
                    sender,
                    count > 0
                            ? "Updated the body of " + count + " system mail(s)."
                            : "No matching system mail.");
        }
    }

    @CommandLine.Command(name = "retitle")
    private static final class SystemRetitle implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<titleKeyword>")
        private String keyword;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "<newTitle>")
        private List<String> title;

        private SystemRetitle(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = SystemMailHelper.updateTitle(keyword, String.join(" ", title));
            CommandOutput.sendMessage(
                    sender,
                    count > 0
                            ? "Updated the title of " + count + " system mail(s)."
                            : "No matching system mail.");
        }
    }

    @CommandLine.Command(name = "delete")
    private static final class SystemDelete implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<titleKeyword>")
        private List<String> keyword;

        private SystemDelete(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = SystemMailHelper.deleteByTitle(String.join(" ", keyword));
            CommandOutput.sendMessage(
                    sender,
                    count > 0
                            ? "Deleted " + count + " system mail(s)."
                            : "No matching system mail.");
        }
    }

    @CommandLine.Command(name = "clear")
    private static final class SystemClear implements Runnable {
        private final Player sender;

        private SystemClear(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = SystemMailHelper.deleteAllProtected();
            CommandOutput.sendMessage(
                    sender,
                    count > 0
                            ? "Deleted all " + count + " system mail(s)."
                            : "There is no system mail to delete.");
        }
    }

    private static void fillMail(
            Mail mail,
            List<String> title,
            List<String> body,
            List<String> senderName,
            List<Attachment> attachments) {
        mail.mailContent.title = String.join(" ", title);
        mail.mailContent.content = String.join(" ", body);
        if (senderName != null && !senderName.isEmpty()) {
            mail.mailContent.sender = String.join(" ", senderName);
        }
        for (Attachment attachment : attachments) {
            mail.itemList.add(
                    new Mail.MailItem(
                            attachment.itemId(), attachment.count(), attachment.level()));
        }
    }

    private static Mail cloneMail(Mail source, boolean system) {
        Mail copy = system ? SystemMailHelper.newSystemMail() : new Mail();
        copy.mailContent.title = source.mailContent.title;
        copy.mailContent.content = source.mailContent.content;
        copy.mailContent.sender = source.mailContent.sender;
        copy.itemList.addAll(source.itemList);
        return copy;
    }
}
