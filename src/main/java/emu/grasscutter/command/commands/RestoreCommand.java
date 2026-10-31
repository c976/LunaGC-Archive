package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReason._ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Command(label = "restore")
public final class RestoreCommand implements CommandHandler {
    private static final FightProperty[][] ENERGY_PROPERTIES = {
        {FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_ELEC_ENERGY, FightProperty.FIGHT_PROP_MAX_ELEC_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_WATER_ENERGY, FightProperty.FIGHT_PROP_MAX_WATER_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY, FightProperty.FIGHT_PROP_MAX_GRASS_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_WIND_ENERGY, FightProperty.FIGHT_PROP_MAX_WIND_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_ICE_ENERGY, FightProperty.FIGHT_PROP_MAX_ICE_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_ROCK_ENERGY, FightProperty.FIGHT_PROP_MAX_ROCK_ENERGY}
    };

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("hp", new Hp(sender, targetPlayer));
        commandLine.addSubcommand("energy", new Energy(sender, targetPlayer));
        commandLine.addSubcommand("all", new All(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "restore")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            RestoreCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "hp")
    private static final class Hp implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Option(names = "--all", description = "Restore HP for all owned characters, not only the active team")
        private boolean all;

        private Hp(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer, "player.heal", "player.heal.others")) return;
            restoreHp(targetPlayer);
            if (all) {
                int offTeam = targetPlayer.getTeamManager().healAllAvatars();
                CommandOutput.sendMessage(sender,
                        "Restored active-team HP and " + offTeam + " off-team character(s).");
            } else {
                CommandOutput.sendMessage(sender, translate(sender, "commands.heal.success"));
            }
        }
    }

    @CommandLine.Command(name = "energy")
    private static final class Energy implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Energy(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer, "player.setprop", "player.setprop.others")) return;
            restoreEnergy(targetPlayer);
            CommandOutput.sendMessage(sender, "Restored elemental energy successfully.");
        }
    }

    @CommandLine.Command(name = "all")
    private static final class All implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private All(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer, "player.heal", "player.heal.others")) return;
            if (!hasPermission(sender, targetPlayer, "player.setprop", "player.setprop.others")) return;
            restoreHp(targetPlayer);
            restoreEnergy(targetPlayer);
            CommandOutput.sendMessage(sender, "Restored HP and elemental energy successfully.");
        }
    }

    static void restoreHp(Player player) {
        player.getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            boolean wasAlive = entity.isAlive();
                            if (wasAlive) {
                                entity.setFightProperty(
                                        FightProperty.FIGHT_PROP_CUR_HP,
                                        entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
                            } else {
                                entity.reviveToRatio(1f);
                            }

                            if (entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) > 0) {
                                entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0.0f);
                                entity.getWorld()
                                        .broadcastPacket(
                                                new PacketEntityFightPropUpdateNotify(
                                                        entity,
                                                        FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
                                entity.getWorld()
                                        .broadcastPacket(
                                                new PacketEntityFightPropChangeReasonNotify(
                                                        entity,
                                                        FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                                                        0f,
                                                        PropChangeReason.PropChangeReason_PROP_CHANGE_NONE,
                                                        _ChangeHpDebtsReason
                                                                ._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH));
                            }

                            if (wasAlive) {
                                entity.getWorld()
                                        .broadcastPacket(
                                                new PacketAvatarFightPropUpdateNotify(
                                                        entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                            }
                        });
    }

    static void restoreEnergy(Player player) {
        player.getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            boolean alive = entity.isAlive();
                            for (FightProperty[] pair : ENERGY_PROPERTIES) {
                                entity.setFightProperty(pair[0], entity.getFightProperty(pair[1]));
                                entity.getWorld()
                                        .broadcastPacket(
                                                new PacketAvatarFightPropUpdateNotify(
                                                        entity.getAvatar(), pair[0]));
                            }

                            var avatar = entity.getAvatar();
                            avatar.setFightProperty(
                                    FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY,
                                    avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
                            player.sendPacket(new PacketAvatarFightPropNotify(avatar));
                            if (!alive) {
                                entity.getWorld()
                                        .broadcastPacket(
                                                new PacketAvatarLifeStateChangeNotify(
                                                        entity.getAvatar()));
                            }
                        });
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
