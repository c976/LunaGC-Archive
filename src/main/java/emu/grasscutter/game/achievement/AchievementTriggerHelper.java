package emu.grasscutter.game.achievement;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.achievement.AchievementData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wires combat / world events into AchievementExcel triggers.
 * Stock Grasscutter only grants achievements via command — never from damage.
 */
public final class AchievementTriggerHelper {
    private static volatile Map<WatcherTriggerType, List<AchievementData>> byTrigger;

    private AchievementTriggerHelper() {}

    private static void ensureIndex() {
        if (byTrigger != null) {
            return;
        }
        synchronized (AchievementTriggerHelper.class) {
            if (byTrigger != null) {
                return;
            }
            Map<WatcherTriggerType, List<AchievementData>> map = new HashMap<>();
            for (AchievementData data : GameData.getAchievementDataMap().values()) {
                if (data == null || !data.isUsed() || data.getTriggerConfig() == null) {
                    continue;
                }
                WatcherTriggerType type = data.getTriggerConfig().getTriggerType();
                if (type == null || type == WatcherTriggerType.TRIGGER_NONE) {
                    continue;
                }
                map.computeIfAbsent(type, t -> new ArrayList<>()).add(data);
            }
            byTrigger = map;
            Grasscutter.getLogger()
                    .debug(
                            "AchievementTriggerHelper indexed {} trigger types ({} achievements)",
                            map.size(),
                            map.values().stream().mapToInt(List::size).sum());
        }
    }

    /**
     * Player hit something — feed max-crit-damage achievements (82041-82043).
     */
    public static void onAttackResult(Player player, AttackResult result, GameEntity target) {
        if (player == null || result == null || target == null) {
            return;
        }
        float damage = result.getDamage();
        if (damage <= 0f) {
            return;
        }
        // Only count hits against monsters / world enemies, not other players.
        if (target instanceof EntityAvatar) {
            return;
        }
        if (!(target instanceof EntityMonster) && !(target instanceof EntityClientGadget)) {
            // Still allow gadgets/boss parts; skip pure scene props with no HP handled elsewhere.
            if (!(target instanceof EntityMonster)) {
                // EntityMonster is the common case; other damageable entities OK if not avatar.
            }
        }

        // Proto no longer exposes legacy is_crit flags; large hits still qualify.
        boolean likelyCrit = damage >= 1000f;
        // Private-server / obfuscated is_crit: still count big hits (official stages are 5k/20k/50k).
        if (!likelyCrit && damage < 1000f) {
            return;
        }

        int dmgInt = damage >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) damage;
        triggerMax(player, WatcherTriggerType.TRIGGER_MAX_CRITICAL_DAMAGE, dmgInt);

        try {
            if (player.getBattlePassManager() != null) {
                player.getBattlePassManager()
                        .triggerMission(WatcherTriggerType.TRIGGER_MAX_CRITICAL_DAMAGE, 0, dmgInt);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void onAttackResultFromAttacker(
            GameEntity attacker, AttackResult result, GameEntity target) {
        if (attacker == null || result == null || target == null) {
            return;
        }
        Player player = null;
        if (attacker instanceof EntityAvatar avatar) {
            player = avatar.getPlayer();
        } else if (attacker instanceof EntityClientGadget gadget) {
            player = gadget.getOwner();
        }
        if (player != null) {
            onAttackResult(player, result, target);
        }
    }

    /** Max-stat style: curProgress = max(cur, value). */
    public static void triggerMax(Player player, WatcherTriggerType type, int value) {
        if (player == null || type == null || value <= 0) {
            return;
        }
        ensureIndex();
        List<AchievementData> list = byTrigger.get(type);
        if (list == null || list.isEmpty()) {
            return;
        }
        Achievements achievements = player.getAchievements();
        if (achievements == null) {
            return;
        }

        // Prefer parent / lowest id so group sync covers stages 82041→82043.
        AchievementData best = null;
        for (AchievementData data : list) {
            if (best == null || data.getId() < best.getId()) {
                best = data;
            }
        }
        if (best == null) {
            return;
        }

        Achievement cur = achievements.getAchievement(best.getId());
        if (cur == null) {
            return;
        }
        if (value <= cur.getCurProgress()) {
            return;
        }
        try {
            achievements.progress(best.getId(), value);
            Grasscutter.getLogger()
                    .debug(
                            "AchievementTriggerHelper uid={} {} progress={} (dmg/max) achievement={}",
                            player.getUid(),
                            type,
                            value,
                            best.getId());
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn(
                            "AchievementTriggerHelper failed uid={} type={}: {}",
                            player.getUid(),
                            type,
                            t.toString());
        }
    }
}
