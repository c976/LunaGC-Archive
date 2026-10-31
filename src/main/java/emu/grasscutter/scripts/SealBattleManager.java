package emu.grasscutter.scripts;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.constants.SealBattleType;
import emu.grasscutter.scripts.data.ScriptArgs;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tracks seal battles independently of dungeon and ordinary world challenges. */
public final class SealBattleManager {
    private final SceneScriptManager scripts;
    private final Map<Long, Battle> battles = new HashMap<>();

    private static final class Battle {
        final int groupId;
        final int gadgetConfigId;
        final int monsterGroupId;
        final int goal;
        final int deadline;
        final double radiusSquared;
        final Position center;
        final Set<Integer> defeated = new HashSet<>();

        Battle(int groupId, int gadgetConfigId, int monsterGroupId, int goal,
                int deadline, double radius, Position center) {
            this.groupId = groupId;
            this.gadgetConfigId = gadgetConfigId;
            this.monsterGroupId = monsterGroupId;
            this.goal = goal;
            this.deadline = deadline;
            this.radiusSquared = radius * radius;
            this.center = center.clone();
        }
    }

    public SealBattleManager(SceneScriptManager scripts) {
        this.scripts = scripts;
    }

    public synchronized int start(int groupId, int gadgetConfigId, int monsterGroupId,
            int goal, int timeLimit, double radius, int battleType) {
        var scene = scripts.getScene();
        if (battleType != SealBattleType.KILL_MONSTER.ordinal() || goal <= 0
                || timeLimit <= 0 || !Double.isFinite(radius) || radius <= 0) return 1;
        var gadget = scene.getEntityByConfigId(gadgetConfigId, groupId);
        var monsterGroup = scripts.getGroupById(monsterGroupId);
        if (!(gadget instanceof EntityGadget) || monsterGroup == null
                || monsterGroup.monsters == null || monsterGroup.monsters.isEmpty()) return 1;
        long key = ((long) groupId << 32) | (gadgetConfigId & 0xffffffffL);
        if (battles.containsKey(key)) return 0;
        battles.put(key, new Battle(groupId, gadgetConfigId, monsterGroupId, goal,
                scene.getSceneTimeSeconds() + timeLimit, radius, gadget.getPosition()));
        scripts.callEvent(new ScriptArgs(groupId, EventType.EVENT_SEAL_BATTLE_BEGIN,
                gadgetConfigId, 0));
        return 0;
    }

    public synchronized void onMonsterDeath(int monsterGroupId, int configId) {
        if (configId <= 0) return;
        for (var entry : Map.copyOf(battles).entrySet()) {
            var battle = entry.getValue();
            if (battle.monsterGroupId != monsterGroupId) continue;
            if (scripts.getScene().getSceneTimeSeconds() >= battle.deadline
                    || !hasPlayerInRange(battle)) {
                finish(entry.getKey(), battle, false);
                continue;
            }
            var group = scripts.getGroupById(monsterGroupId);
            if (group == null || !group.monsters.containsKey(configId)) continue;
            if (battle.defeated.add(configId) && battle.defeated.size() >= battle.goal) {
                finish(entry.getKey(), battle, true);
            }
        }
    }

    public synchronized void onTick() {
        var scene = scripts.getScene();
        for (var entry : Map.copyOf(battles).entrySet()) {
            var battle = entry.getValue();
            if (scene.getSceneTimeSeconds() >= battle.deadline || !hasPlayerInRange(battle)
                    || scene.getEntityByConfigId(battle.gadgetConfigId, battle.groupId) == null) {
                finish(entry.getKey(), battle, false);
            }
        }
    }

    private void finish(long key, Battle battle, boolean success) {
        if (!battles.remove(key, battle)) return;
        scripts.callEvent(new ScriptArgs(battle.groupId, EventType.EVENT_SEAL_BATTLE_END,
                battle.gadgetConfigId, success ? 1 : 0));
    }

    private boolean hasPlayerInRange(Battle battle) {
        return scripts.getScene().getPlayers().stream().anyMatch(player -> {
            var pos = player.getPosition();
            double x = pos.getX() - battle.center.getX();
            double y = pos.getY() - battle.center.getY();
            double z = pos.getZ() - battle.center.getZ();
            return x * x + y * y + z * z <= battle.radiusSquared;
        });
    }

    public synchronized void clear() {
        battles.clear();
    }
}
