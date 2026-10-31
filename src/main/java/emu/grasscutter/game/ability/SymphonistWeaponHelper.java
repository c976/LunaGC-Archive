/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.EquipAffixData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public final class SymphonistWeaponHelper {
    public static final int WEAPON_ID = 13514;
    public static final int AFFIX_BASE_ID = 1135140;
    public static final String OPEN_CONFIG = "Weapon_Pole_Trident";
    private static final ConcurrentHashMap<Long, Float> APPLIED_ATK_PERCENT = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Long, Integer> EXPIRE_TASK_IDS = new ConcurrentHashMap();

    private SymphonistWeaponHelper() {
    }

    public static void clearPlayerState(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        for (EntityAvatar entityAvatar : new java.util.ArrayList<>(player.getTeamManager().getActiveTeam())) {
            if (entityAvatar == null || entityAvatar.getAvatar() == null) {
                continue;
            }
            Avatar avatar = entityAvatar.getAvatar();
            Float applied = APPLIED_ATK_PERCENT.get(avatar.getGuid());
            if (applied != null && applied > 0.0f) {
                avatar.addFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT, -applied);
                SymphonistWeaponHelper.recomputeCurAttack(avatar);
            }
            clearAvatarState(avatar.getGuid());
        }
    }

    public static void clearAvatarState(long guid) {
        APPLIED_ATK_PERCENT.remove(guid);
        Integer taskId = EXPIRE_TASK_IDS.remove(guid);
        if (taskId != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(taskId);
            } catch (Throwable ignored) {
                // The scheduler may already be stopping during logout.
            }
        }
    }

    public static void onHealPerformed(Player player, EntityAvatar entityAvatar, Collection<EntityAvatar> collection) {
        if (player == null || entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        float[] fArray = SymphonistWeaponHelper.readWeaponParams(entityAvatar.getAvatar());
        if (fArray == null) {
            return;
        }
        float f = fArray[0];
        int n = Math.max(1, Math.round(fArray[1]));
        if (f <= 0.0f) {
            return;
        }
        HashSet<Long> hashSet = new HashSet<Long>();
        SymphonistWeaponHelper.applyOrRefresh(player, entityAvatar, f, n);
        hashSet.add(entityAvatar.getAvatar().getGuid());
        if (collection != null) {
            for (EntityAvatar entityAvatar2 : collection) {
                long l;
                if (entityAvatar2 == null || entityAvatar2.getAvatar() == null || hashSet.contains(l = entityAvatar2.getAvatar().getGuid())) continue;
                SymphonistWeaponHelper.applyOrRefresh(player, entityAvatar2, f, n);
                hashSet.add(l);
            }
        }
        Grasscutter.getLogger().info("[Symphonist] heal buff ATK%={} dura={}s targets={}", Float.valueOf(f), n, hashSet.size());
    }

    public static void onAfterRecalc(Avatar avatar) {
        if (avatar == null) {
            return;
        }
        Float f = APPLIED_ATK_PERCENT.get(avatar.getGuid());
        if (f == null || f.floatValue() <= 0.0f) {
            return;
        }
        avatar.addFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT, f.floatValue());
        SymphonistWeaponHelper.recomputeCurAttack(avatar);
        Player player = avatar.getPlayer();
        EntityAvatar entityAvatar = SymphonistWeaponHelper.findEntity(player, avatar.getGuid());
        if (entityAvatar != null) {
            SymphonistWeaponHelper.notifyProps(player, entityAvatar);
        } else if (player != null) {
            player.sendPacket(new PacketAvatarFightPropNotify(avatar));
        }
    }

    private static float[] readWeaponParams(Avatar avatar) {
        GameItem gameItem = avatar.getWeapon();
        if (gameItem == null || gameItem.getItemId() != 13514) {
            return null;
        }
        int n = Math.max(0, Math.min(4, gameItem.getRefinement()));
        EquipAffixData equipAffixData = (EquipAffixData)GameData.getEquipAffixDataMap().get(1135140 + n);
        if (equipAffixData == null || equipAffixData.getParamList() == null || equipAffixData.getParamList().length < 4) {
            equipAffixData = SymphonistWeaponHelper.findAffixByLevel(n);
        }
        if (equipAffixData == null || equipAffixData.getParamList() == null || equipAffixData.getParamList().length < 4) {
            return new float[]{0.32f, 3.0f};
        }
        float[] fArray = equipAffixData.getParamList();
        return new float[]{fArray[2], fArray[3]};
    }

    private static EquipAffixData findAffixByLevel(int n) {
        for (EquipAffixData equipAffixData : GameData.getEquipAffixDataMap().values()) {
            if (equipAffixData == null || !OPEN_CONFIG.equals(equipAffixData.getOpenConfig()) || equipAffixData.getLevel() != n || equipAffixData.getParamList() == null || equipAffixData.getParamList().length < 4) continue;
            return equipAffixData;
        }
        return null;
    }

    private static void applyOrRefresh(Player player, EntityAvatar entityAvatar, float f, int n) {
        Avatar avatar = entityAvatar.getAvatar();
        if (avatar == null) {
            return;
        }
        long l = avatar.getGuid();
        SymphonistWeaponHelper.clearBonusOnly(avatar);
        avatar.addFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT, f);
        SymphonistWeaponHelper.recomputeCurAttack(avatar);
        APPLIED_ATK_PERCENT.put(l, Float.valueOf(f));
        SymphonistWeaponHelper.notifyProps(player, entityAvatar);
        Integer n2 = EXPIRE_TASK_IDS.remove(l);
        if (n2 != null) {
            Grasscutter.getGameServer().getScheduler().cancelTask(n2);
        }
        int n3 = player.getUid();
        int n4 = Grasscutter.getGameServer().getScheduler().scheduleDelayedTask(() -> {
            EXPIRE_TASK_IDS.remove(l);
            Player currentPlayer = Grasscutter.getGameServer().getPlayerByUid(n3);
            if (currentPlayer == null) {
                APPLIED_ATK_PERCENT.remove(l);
                return;
            }
            EntityAvatar currentEntityAvatar = SymphonistWeaponHelper.findEntity(currentPlayer, l);
            if (currentEntityAvatar != null && currentEntityAvatar.getAvatar() != null) {
                SymphonistWeaponHelper.removeBonus(currentPlayer, currentEntityAvatar);
            } else {
                APPLIED_ATK_PERCENT.remove(l);
            }
        }, n);
        EXPIRE_TASK_IDS.put(l, n4);
    }

    private static void clearBonusOnly(Avatar avatar) {
        Float f = APPLIED_ATK_PERCENT.remove(avatar.getGuid());
        if (f != null && f.floatValue() > 0.0f) {
            avatar.addFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT, -f.floatValue());
            SymphonistWeaponHelper.recomputeCurAttack(avatar);
        }
    }

    private static void removeBonus(Player player, EntityAvatar entityAvatar) {
        Avatar avatar = entityAvatar.getAvatar();
        if (avatar == null) {
            return;
        }
        Float f = APPLIED_ATK_PERCENT.remove(avatar.getGuid());
        if (f == null || f.floatValue() <= 0.0f) {
            return;
        }
        avatar.addFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT, -f.floatValue());
        SymphonistWeaponHelper.recomputeCurAttack(avatar);
        SymphonistWeaponHelper.notifyProps(player, entityAvatar);
    }

    private static void recomputeCurAttack(Avatar avatar) {
        float f = avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK);
        float f2 = avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT);
        float f3 = avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, f * (1.0f + f2) + f3);
    }

    private static void notifyProps(Player player, EntityAvatar entityAvatar) {
        Avatar avatar = entityAvatar.getAvatar();
        if (player != null && avatar != null) {
            player.sendPacket(new PacketAvatarFightPropNotify(avatar));
        }
        if (entityAvatar.getScene() != null) {
            ObjectArrayList<FightProperty> objectArrayList = new ObjectArrayList<FightProperty>(2);
            objectArrayList.add(FightProperty.FIGHT_PROP_ATTACK_PERCENT);
            objectArrayList.add(FightProperty.FIGHT_PROP_CUR_ATTACK);
            entityAvatar.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, objectArrayList));
        }
    }

    private static EntityAvatar findEntity(Player player, long l) {
        if (player == null || player.getTeamManager() == null) {
            return null;
        }
        for (EntityAvatar entityAvatar : player.getTeamManager().getActiveTeam()) {
            if (entityAvatar == null || entityAvatar.getAvatar() == null || entityAvatar.getAvatar().getGuid() != l) continue;
            return entityAvatar;
        }
        return null;
    }
}
