package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketSetNameCardRsp;
import emu.grasscutter.server.packet.send.PacketUnlockNameCardNotify;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "namecard",
        aliases = {"card"},
        permission = "player.namecard",
        permissionTargeted = "player.namecard.others")
public final class NameCardCommand implements CommandHandler {
    private static final int DEFAULT_NAMECARD_ID = 210001;
    private static boolean loadedNameCardIds = false;
    private static final Set<Integer> validNameCardIds = new HashSet<>();

    private record NameCardArg(int id) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.registerConverter(
                NameCardArg.class,
                value -> {
                    String normalized = value.trim().toLowerCase();
                    if (Set.of("clear", "default", "reset", "remove", "none").contains(normalized)) {
                        return new NameCardArg(DEFAULT_NAMECARD_ID);
                    }
                    try {
                        return new NameCardArg(Integer.parseInt(normalized));
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException("Invalid namecard ID.");
                    }
                });
        return commandLine;
    }

    @CommandLine.Command(name = "namecard")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<nameCardId|clear|default|reset>")
        private NameCardArg nameCard;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int nameCardId = nameCard.id();
            if (!isValidNameCardId(nameCardId)) {
                CommandOutput.sendMessage(sender, "Invalid namecard ID: " + nameCardId);
                return;
            }

            if (targetPlayer.getNameCardId() == nameCardId) {
                CommandOutput.sendMessage(
                        sender,
                        "No change needed. Player "
                                + targetPlayer.getUid()
                                + " is already using namecard "
                                + nameCardId
                                + ".");
                return;
            }

            boolean newlyUnlocked = targetPlayer.getNameCardList().add(nameCardId);
            if (newlyUnlocked) {
                targetPlayer.sendPacket(new PacketUnlockNameCardNotify(nameCardId));
            }

            targetPlayer.setNameCardId(nameCardId);
            targetPlayer.sendPacket(new PacketSetNameCardRsp(nameCardId));
            targetPlayer.save();

            String action = newlyUnlocked ? "Unlocked and equipped" : "Equipped";
            CommandOutput.sendMessage(
                    sender,
                    action
                            + " namecard "
                            + nameCardId
                            + " for player "
                            + targetPlayer.getUid()
                            + ".");
        }
    }

    private static boolean isValidNameCardId(int nameCardId) {
        loadNameCardIds();
        if (!validNameCardIds.isEmpty()) {
            return validNameCardIds.contains(nameCardId);
        }
        return nameCardId >= 210000 && nameCardId <= 219999;
    }

    private static synchronized void loadNameCardIds() {
        if (loadedNameCardIds) {
            return;
        }
        loadedNameCardIds = true;

        try {
            var path = FileUtils.getExcelPath("NameCardExcelConfigData.json");
            if (!Files.exists(path)) {
                return;
            }

            List<NameCardExcelEntry> entries = JsonUtils.loadToList(path, NameCardExcelEntry.class);
            for (NameCardExcelEntry entry : entries) {
                if (entry.id > 0) {
                    validNameCardIds.add(entry.id);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static final class NameCardExcelEntry {
        private int id;
    }
}
