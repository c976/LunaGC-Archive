package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.event.entity.EntityDamageEvent;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(label = "entity", permission = "server.entity")
public final class EntityCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "entity")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<configId>")
        private int configId;

        @Option(names = "--entity-id", description = "Resolve the runtime entity ID instead of config ID")
        private boolean byEntityId;

        @Option(names = "--state")
        private Integer state;

        @Option(names = "--ai")
        private Integer ai;

        @Option(names = "--max-hp")
        private Integer maxHp;

        @Option(names = "--hp")
        private Integer hp;

        @Option(names = "--atk")
        private Integer atk;

        @Option(names = "--def")
        private Integer def;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (targetPlayer == null || !targetPlayer.isOnline() || targetPlayer.getScene() == null) {
                CommandOutput.sendMessage(sender, "Entity operations require an online player in a scene.");
                return;
            }
            if (configId <= 0) {
                CommandOutput.sendMessage(sender, "Entity identifier must be positive.");
                return;
            }
            GameEntity entity = byEntityId
                    ? targetPlayer.getScene().getEntityById(configId)
                    : targetPlayer.getScene().getFirstEntityByConfigId(configId);
            if (entity == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.entity.not_found_error"));
                return;
            }
            if (state == null && ai == null && maxHp == null && hp == null && atk == null && def == null) {
                CommandOutput.sendMessage(sender,
                        "Entity ID=" + entity.getId() + ", config ID=" + entity.getConfigId()
                                + ", type=" + entity.getClass().getSimpleName()
                                + ", HP=" + entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                                + "/" + entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
                return;
            }
            if ((state != null && !(entity instanceof EntityGadget))
                    || (ai != null && !(entity instanceof EntityMonster))) {
                CommandOutput.sendMessage(sender, "Requested state/AI operation is not supported by this entity type.");
                return;
            }
            if (!validStats(maxHp, hp, atk, def) || (state != null && state < 0)
                    || (ai != null && ai < 0)) {
                CommandOutput.sendMessage(sender, "Entity properties must be non-negative (max HP must be positive).");
                return;
            }
            applyFightProps(entity, maxHp, hp, atk, def);
            if (state != null && entity instanceof EntityGadget gadget) gadget.updateState(state);
            if (ai != null && entity instanceof EntityMonster monster) monster.setAiId(ai);
            CommandOutput.sendMessage(sender, translate(sender, "commands.status.success"));
        }
    }

    static boolean validStats(Integer maxHp, Integer hp, Integer atk, Integer def) {
        return (maxHp == null || maxHp > 0) && (hp == null || hp >= 0)
                && (atk == null || atk >= 0) && (def == null || def >= 0);
    }

    private static void applyFightProps(GameEntity entity, Integer maxHp, Integer hp, Integer atk, Integer def) {
        var changed = new ArrayList<FightProperty>();
        float previousHp = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float max = maxHp != null ? maxHp
                : entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp != null) {
            setFightProperty(entity, FightProperty.FIGHT_PROP_MAX_HP, max, changed);
        }
        // A lower maximum clamps the existing current HP as well.
        float newHp = hp != null ? hp : previousHp;
        if (max > 0) newHp = Math.min(newHp, max);
        if (hp != null || newHp != previousHp) {
            setFightProperty(entity, FightProperty.FIGHT_PROP_CUR_HP, newHp, changed);
            if (newHp < previousHp) {
                entity.runLuaCallbacks(new EntityDamageEvent(
                        entity, previousHp - newHp, ElementType.None, null));
            }
        }
        if (atk != null) {
            setFightProperty(entity, FightProperty.FIGHT_PROP_ATTACK, atk, changed);
            setFightProperty(entity, FightProperty.FIGHT_PROP_CUR_ATTACK, atk, changed);
        }
        if (def != null) {
            setFightProperty(entity, FightProperty.FIGHT_PROP_DEFENSE, def, changed);
            setFightProperty(entity, FightProperty.FIGHT_PROP_CUR_DEFENSE, def, changed);
        }
        if (!changed.isEmpty()) {
            entity.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, changed));
        }
    }

    private static void setFightProperty(
            GameEntity entity, FightProperty property, float value, List<FightProperty> changed) {
        entity.setFightProperty(property, value);
        changed.add(property);
    }
}
