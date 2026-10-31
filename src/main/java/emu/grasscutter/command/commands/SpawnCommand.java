package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandHelpers;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.NameIndex;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.entity.EntityBaseGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityItem;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGroup;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        label = "spawn",
        aliases = {"s"},
        permission = "server.spawn",
        permissionTargeted = "server.spawn.others")
public final class SpawnCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "spawn")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<entityId|name>")
        private String entity;

        @Option(names = {"-n", "--amount"}, defaultValue = "1")
        private int amount;

        @Option(names = {"-l", "--level"}, defaultValue = "1")
        private int level;

        @Option(names = "--state", defaultValue = "-1")
        private int state;

        @Option(names = "--ai", defaultValue = "-1")
        private int ai;

        @Option(names = "--block", defaultValue = "-1")
        private int blockId;

        @Option(names = "--group", defaultValue = "-1")
        private int groupId;

        @Option(names = "--config", defaultValue = "-1")
        private int configId;

        @Option(names = "--max-hp", defaultValue = "-1")
        private int maxHp;

        @Option(names = "--hp", defaultValue = "-1")
        private int hp;

        @Option(names = "--atk", defaultValue = "-1")
        private int atk;

        @Option(names = "--def", defaultValue = "-1")
        private int def;

        @Option(names = "--pos", arity = "3", paramLabel = "<x y z>")
        private List<String> position;

        @Option(names = "--rot", arity = "3", paramLabel = "<x y z>")
        private List<String> rotation;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (targetPlayer.getScene() == null) {
                CommandOutput.sendMessage(sender, "Target player has no active scene.");
                return;
            }
            if (amount <= 0 || level <= 0) {
                CommandOutput.sendMessage(sender, "amount and level must be positive");
                return;
            }

            int id = resolveEntityId(entity);
            if (id == 0) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.generic.invalid.entityId"));
                return;
            }

            Position pos = new Position(targetPlayer.getPosition());
            Position rot = new Position(targetPlayer.getRotation());
            try {
                if (position != null) {
                    pos = CommandHelpers.parsePosition(
                            position.get(0), position.get(1), position.get(2), pos, rot);
                }
                if (rotation != null) {
                    rot.setX(CommandHelpers.parseRelative(rotation.get(0), rot.getX()));
                    rot.setY(CommandHelpers.parseRelative(rotation.get(1), rot.getY()));
                    rot.setZ(CommandHelpers.parseRelative(rotation.get(2), rot.getZ()));
                }
            } catch (NumberFormatException ignored) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.execution.argument_error"));
                return;
            }

            MonsterData monsterData = GameData.getMonsterDataMap().get(id);
            GadgetData gadgetData = GameData.getGadgetDataMap().get(id);
            ItemData itemData = GameData.getItemDataMap().get(id);
            if (monsterData == null && gadgetData == null && itemData == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.generic.invalid.entityId"));
                return;
            }

            SpawnParameters param = new SpawnParameters();
            param.id = id;
            param.amount = amount;
            param.lvl = level;
            param.state = state;
            param.ai = ai;
            param.blockId = blockId;
            param.groupId = groupId;
            param.configId = configId;
            param.maxHP = maxHp;
            param.hp = hp;
            param.atk = atk;
            param.def = def;
            param.pos = pos;
            param.rot = rot;
            param.scene = targetPlayer.getScene();

            if (param.scene.getEntities().size() + param.amount > GAME_OPTIONS.sceneEntityLimit) {
                param.amount = Math.max(
                        Math.min(
                                GAME_OPTIONS.sceneEntityLimit - param.scene.getEntities().size(),
                                param.amount),
                        0);
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.spawn.limit_reached", param.amount));
                if (param.amount <= 0) return;
            }

            double maxRadius = Math.sqrt(param.amount * 0.2 / Math.PI);
            for (int i = 0; i < param.amount; i++) {
                Position spawnPos = randomPositionInCircle(param.pos, maxRadius).addY(3);
                GameEntity created;
                if (itemData != null) {
                    created = createItem(itemData, param, spawnPos);
                } else if (gadgetData != null) {
                    spawnPos.addY(-3);
                    created = createGadget(gadgetData, param, spawnPos, targetPlayer);
                } else {
                    created = createMonster(monsterData, param, spawnPos);
                }
                applyCommonParameters(created, param);
                param.scene.addEntity(created);
            }
            CommandOutput.sendMessage(
                    sender,
                    translate(sender, "commands.spawn.success", param.amount, NameIndex.describe(param.id)));
        }
    }

    private static int resolveEntityId(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return NameIndex.resolveEntity(input, new ArrayList<>());
        }
    }

    private static EntityItem createItem(ItemData data, SpawnParameters param, Position pos) {
        return new EntityItem(param.scene, null, data, pos, param.rot, 1, true);
    }

    private static EntityMonster createMonster(MonsterData data, SpawnParameters param, Position pos) {
        var entity = new EntityMonster(param.scene, data, pos, param.rot, param.lvl);
        if (param.ai != -1) entity.setAiId(param.ai);
        return entity;
    }

    private static EntityBaseGadget createGadget(
            GadgetData data, SpawnParameters param, Position pos, Player targetPlayer) {
        if (data.getType() == EntityType.Vehicle) {
            return new EntityVehicle(param.scene, targetPlayer, param.id, 0, pos, param.rot);
        }

        var gadget = new EntityGadget(param.scene, param.id, pos, param.rot);
        if (param.groupId != -1 && param.configId != -1) {
            var group = SceneGroup.of(param.groupId).load(param.scene.getId());
            if (group != null && group.gadgets != null) {
                var sceneGadget = group.gadgets.get(param.configId);
                if (sceneGadget != null) {
                    gadget.setMetaGadget(sceneGadget);
                    gadget.setGroupId(group.id);
                    gadget.setBlockId(param.blockId != -1 ? param.blockId : group.block_id);
                    gadget.setConfigId(sceneGadget.config_id);
                    if (param.state == -1) gadget.setState(sceneGadget.state);
                }
            }
        }
        gadget.buildContent();
        if (param.state != -1) gadget.setState(param.state);
        // Spawned ley-line gadgets need a worktop option and challenge callback.
        // Scene spawn loading does this already; /spawn must do it too.
        if (param.scene.getSceneType() == SceneType.SCENE_WORLD) {
            param.scene.getBlossomManager().initBlossom(gadget);
        }
        return gadget;
    }

    private static void applyCommonParameters(GameEntity entity, SpawnParameters param) {
        if (param.blockId != -1) entity.setBlockId(param.blockId);
        if (param.groupId != -1) entity.setGroupId(param.groupId);
        if (param.configId != -1) entity.setConfigId(param.configId);
        if (param.maxHP != -1) {
            entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, param.maxHP);
            entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, param.maxHP);
        }
        if (param.hp != -1) {
            entity.setFightProperty(
                    FightProperty.FIGHT_PROP_CUR_HP,
                    param.hp == 0 ? Float.MAX_VALUE : param.hp);
        }
        if (param.atk != -1) {
            entity.setFightProperty(FightProperty.FIGHT_PROP_ATTACK, param.atk);
            entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, param.atk);
        }
        if (param.def != -1) {
            entity.setFightProperty(FightProperty.FIGHT_PROP_DEFENSE, param.def);
            entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, param.def);
        }
    }

    private static Position randomPositionInCircle(Position origin, double radius) {
        Position target = origin.clone();
        double angle = Math.random() * Math.PI * 2.0;
        double r = Math.sqrt(Math.random()) * radius;
        return target.addX((float) (r * Math.cos(angle))).addZ((float) (r * Math.sin(angle)));
    }

    private static final class SpawnParameters {
        private int id;
        private int lvl = 1;
        private int amount = 1;
        private int blockId = -1;
        private int groupId = -1;
        private int configId = -1;
        private int state = -1;
        private int hp = -1;
        private int maxHP = -1;
        private int atk = -1;
        private int def = -1;
        private int ai = -1;
        private Position pos;
        private Position rot;
        private Scene scene;
    }
}
