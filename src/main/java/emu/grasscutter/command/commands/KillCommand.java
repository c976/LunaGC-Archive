package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "kill",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class KillCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender, targetPlayer));
        commandLine.addSubcommand("all", new KillAll(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "kill")
    private static final class Root implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Root(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            new KillCharacter(sender, targetPlayer).run();
        }
    }

    @CommandLine.Command(name = "all")
    private static final class KillAll implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[sceneId]")
        private Integer sceneId;

        private KillAll(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer, "server.killall", "server.killall.others")) return;
            Scene scene =
                    sceneId == null
                            ? targetPlayer.getScene()
                            : targetPlayer.getWorld().getSceneById(sceneId);
            if (scene == null) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.killall.scene_not_found_in_player_world"));
                return;
            }

            List<GameEntity> toKill =
                    scene.getEntities().values().stream()
                            .filter(EntityMonster.class::isInstance)
                            .toList();
            toKill.forEach(KillCommand::killEntity);
            CommandOutput.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.killall.kill_monsters_in_scene",
                            toKill.size(),
                            scene.getId()));
        }
    }

    /** Default kill action; no separate character subcommand. */
    private static final class KillCharacter implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private KillCharacter(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(
                    sender,
                    targetPlayer,
                    "player.killcharacter",
                    "player.killcharacter.others")) return;

            EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
            if (entity == null) {
                CommandOutput.sendMessage(sender, "No active character.");
                return;
            }

            boolean wasAlive = !entity.isDead();
            entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
            entity.checkIfDead();
            if (wasAlive && entity.isDead()) {
                targetPlayer
                        .getStaminaManager()
                        .killAvatar(
                                targetPlayer.getSession(),
                                entity,
                                PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
            }

            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.killCharacter.success", targetPlayer.getNickname()));
        }
    }

    private static void killEntity(GameEntity entity) {
        boolean wasAlive = !entity.isDead();
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
        entity.checkIfDead();
        boolean diedNow = wasAlive && entity.isDead();
        entity.getWorld()
                .broadcastPacket(
                        new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
        if (diedNow) {
            entity.getScene().killEntity(entity, 0);
        }
    }

    private static boolean hasPermission(
            Player sender, Player targetPlayer, String permission, String permissionTargeted) {
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = targetPlayer != sender ? permissionTargeted : permission;
        if (account != null && account.hasPermission(required)) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
