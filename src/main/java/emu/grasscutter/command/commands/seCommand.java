package emu.grasscutter.command.commands;

import emu.grasscutter.GameConstants;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.command.ConstellationsHandler;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "switchelement", aliases = {"se"}, threading = true)
public final class seCommand implements CommandHandler {
    private record ElementArg(Element value) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.registerConverter(
                ElementArg.class,
                value -> {
                    Element element = parseElement(value);
                    if (element == null) {
                        throw new CommandLine.TypeConversionException("Invalid element: " + value);
                    }
                    return new ElementArg(element);
                });
        return commandLine;
    }

    @CommandLine.Command(name = "switchelement")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<none|pyro|hydro|anemo|cryo|geo|electro|dendro>")
        private ElementArg element;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[constellation]")
        private Integer constellation;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int constLevel = constellation == null ? 0 : Math.max(0, Math.min(6, constellation));
            var currentEntity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
            if (currentEntity == null) {
                CommandOutput.sendMessage(sender, "Switch failed: no active character");
                return;
            }

            int activeAvatarId = currentEntity.getAvatar().getAvatarId();
            if (activeAvatarId != GameConstants.MAIN_CHARACTER_MALE
                    && activeAvatarId != GameConstants.MAIN_CHARACTER_FEMALE) {
                CommandOutput.sendMessage(
                        sender,
                        "Switch failed: the active character is "
                                + activeAvatarId
                                + ", switch to the Traveler first");
                return;
            }

            String failure = changeAvatarElement(targetPlayer, activeAvatarId, element.value());
            if (failure != null) {
                CommandOutput.sendMessage(sender, "Switch failed: " + failure);
                return;
            }

            ConstellationsHandler.change(targetPlayer, element.value(), constLevel);
            int sceneId = targetPlayer.getSceneId();
            try {
                Position position = targetPlayer.getPosition();
                targetPlayer.getWorld().transferPlayerToScene(targetPlayer, 1, position);
                targetPlayer.getWorld().transferPlayerToScene(targetPlayer, sceneId, position);
                targetPlayer.getScene().broadcastPacket(new PacketSceneEntityAppearNotify(targetPlayer));
                CommandOutput.sendMessage(sender, "Switched to " + element.value().name());
            } catch (Exception ignored) {
                CommandOutput.sendMessage(sender, "Failed to switch to " + element.value().name());
            }
        }
    }

    private static Element parseElement(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none", "white", "common", "elementless" -> Element.elementless;
            case "fire", "pyro" -> Element.pyro;
            case "water", "hydro" -> Element.hydro;
            case "wind", "anemo", "air" -> Element.anemo;
            case "ice", "cryo" -> Element.cryo;
            case "rock", "geo" -> Element.geo;
            case "electric", "electro" -> Element.electro;
            case "grass", "dendro", "plant" -> Element.dendro;
            default -> null;
        };
    }

    private static String changeAvatarElement(Player player, int avatarId, Element element) {
        Avatar avatar = player.getAvatars().getAvatarById(avatarId);
        if (avatar == null) {
            return "you do not own avatar " + avatarId;
        }

        int depotId = element.getSkillRepoId(avatarId);
        AvatarSkillDepotData skillDepot = GameData.getAvatarSkillDepotDataMap().get(depotId);
        if (skillDepot == null) {
            return "skill depot " + depotId + " for " + element.name() + " is not loaded";
        }

        avatar.setSkillDepotData(skillDepot);
        avatar.setCurrentEnergy(1000);
        avatar.save();
        return null;
    }
}
