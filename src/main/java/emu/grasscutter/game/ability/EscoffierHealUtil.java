/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.SymphonistWeaponHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class EscoffierHealUtil {
    public static final int ESCOFFIER_AVATAR_ID = 10000112;
    public static final int BURST_SKILL_ID = 11125;
    public static final int BURST_PROUD_GROUP = 11239;
    public static final int A1_PROUD_GROUP = 11221;
    public static final String STRIKE_MODIFIER = "Avatar_Escoffier_ElementalBurst_Strike";
    private static final int INSTANT_DELAY_SEC = 1;
    private static final int HOT_INTERVAL_SEC = 1;
    private static final long INSTANT_ICD_MS = 8000L;
    private static final Int2LongMap LAST_INSTANT_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());

    private EscoffierHealUtil() {
    }

    public static void clearPlayerState(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        for (EntityAvatar entityAvatar : new ArrayList<>(player.getTeamManager().getActiveTeam())) {
            if (entityAvatar != null) {
                synchronized (LAST_INSTANT_MS) {
                    LAST_INSTANT_MS.remove(entityAvatar.getId());
                }
            }
        }
    }

    public static void clearEntityState(int entityId) {
        synchronized (LAST_INSTANT_MS) {
            LAST_INSTANT_MS.remove(entityId);
        }
    }

    public static void onBurstSkill(Player player, EntityAvatar entityAvatar) {
        if (player == null || entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000112) {
            return;
        }
        EscoffierHealUtil.scheduleInstantHeal(player, entityAvatar);
        if (EscoffierHealUtil.hasA1(entityAvatar.getAvatar())) {
            EscoffierHealUtil.scheduleHotHeal(player, entityAvatar);
        }
    }

    public static void onStrikeModifier(Ability ability) {
        if (ability == null || ability.getPlayerOwner() == null) {
            return;
        }
        Player player = ability.getPlayerOwner();
        EntityAvatar entityAvatar = player.getTeamManager().getCurrentAvatarEntity();
        if (entityAvatar == null || entityAvatar.getAvatar() == null || entityAvatar.getAvatar().getAvatarId() != 10000112) {
            return;
        }
        EscoffierHealUtil.doInstantHeal(player, entityAvatar);
    }

    public static boolean isStrikeModifier(String string) {
        return STRIKE_MODIFIER.equals(string);
    }

    private static void scheduleInstantHeal(Player player, EntityAvatar entityAvatar) {
        ServerTaskScheduler serverTaskScheduler = Grasscutter.getGameServer().getScheduler();
        int n = entityAvatar.getId();
        int n2 = player.getUid();
        serverTaskScheduler.scheduleDelayedTask(() -> {
            Player currentPlayer = Grasscutter.getGameServer().getPlayerByUid(n2);
            if (currentPlayer == null) {
                return;
            }
            EntityAvatar currentEntityAvatar = EscoffierHealUtil.findCaster(currentPlayer, n);
            if (currentEntityAvatar != null) {
                EscoffierHealUtil.doInstantHeal(currentPlayer, currentEntityAvatar);
            }
        }, 1);
    }

    private static void scheduleHotHeal(Player player, EntityAvatar entityAvatar) {
        final int[] nArray = new int[1];
        float[] fArray = EscoffierHealUtil.readA1Params(entityAvatar.getAvatar());
        float f = fArray[0];
        int n = Math.max(1, Math.round(fArray[1]));
        if (f <= 0.0f) {
            return;
        }
        ServerTaskScheduler serverTaskScheduler = Grasscutter.getGameServer().getScheduler();
        int n2 = entityAvatar.getId();
        int n3 = player.getUid();
        AtomicInteger atomicInteger = new AtomicInteger(n);
        nArray[0] = serverTaskScheduler.scheduleDelayedRepeatingTask(() -> {
            Player currentPlayer = Grasscutter.getGameServer().getPlayerByUid(n3);
            if (currentPlayer == null || atomicInteger.decrementAndGet() < 0) {
                Grasscutter.getGameServer().getScheduler().cancelTask(nArray[0]);
                return;
            }
            EntityAvatar currentEntityAvatar = EscoffierHealUtil.findCaster(currentPlayer, n2);
            if (currentEntityAvatar == null) {
                Grasscutter.getGameServer().getScheduler().cancelTask(nArray[0]);
                return;
            }
            EscoffierHealUtil.healTeam(currentPlayer, currentEntityAvatar, currentEntityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK) * f);
        }, 1, 1);
    }

    private static void doInstantHeal(Player player, EntityAvatar entityAvatar) {
        int n = entityAvatar.getId();
        long l = System.currentTimeMillis();
        if (LAST_INSTANT_MS.containsKey(n) && l - LAST_INSTANT_MS.get(n) < 8000L) {
            return;
        }
        LAST_INSTANT_MS.put(n, l);
        float[] fArray = EscoffierHealUtil.readBurstHealParams(entityAvatar.getAvatar());
        float f = fArray[0];
        float f2 = fArray[1];
        float f3 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK);
        float f4 = f3 * f + f2;
        if (f4 <= 0.0f) {
            Grasscutter.getLogger().warn("[EscoffierHeal] instant amount<=0 atk={} ratio={} flat={}", Float.valueOf(f3), Float.valueOf(f), Float.valueOf(f2));
            return;
        }
        EscoffierHealUtil.healTeam(player, entityAvatar, f4);
        Grasscutter.getLogger().info("[EscoffierHeal] instant heal amount={} (atk={} * {} + {})", Float.valueOf(f4), Float.valueOf(f3), Float.valueOf(f), Float.valueOf(f2));
    }

    private static void healTeam(Player player, EntityAvatar entityAvatar, float f) {
        if (f <= 0.0f || player.getWorld() == null) {
            return;
        }
        List<EntityAvatar> list = player.getTeamManager().getActiveTeam();
        if (list == null || list.isEmpty()) {
            return;
        }
        ArrayList<EntityAvatar> arrayList = new ArrayList<EntityAvatar>();
        float f2 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD);
        for (EntityAvatar entityAvatar2 : list) {
            float f3;
            float f4;
            if (entityAvatar2 == null || (f4 = f * (1.0f + f2 + (f3 = entityAvatar2.getFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD)))) <= 0.0f) continue;
            float f5 = entityAvatar2.heal(f4, false);
            if (f5 > 0.0f) {
                arrayList.add(entityAvatar2);
                player.getWorld().broadcastPacket(new PacketEvtBeingHealedNotify(entityAvatar, entityAvatar2, f4, f5));
                continue;
            }
            arrayList.add(entityAvatar2);
        }
        SymphonistWeaponHelper.onHealPerformed(player, entityAvatar, arrayList);
    }

    private static float[] readBurstHealParams(Avatar avatar) {
        float[] fArray = new float[]{1.72032f, 1078.5255f};
        ProudSkillData proudSkillData = EscoffierHealUtil.lookupProud(avatar, 11125, 11239);
        if (proudSkillData != null && proudSkillData.getParamList() != null && proudSkillData.getParamList().length >= 3) {
            fArray[0] = proudSkillData.getParamList()[1];
            fArray[1] = proudSkillData.getParamList()[2];
        }
        return fArray;
    }

    private static float[] readA1Params(Avatar avatar) {
        float[] fArray = new float[]{1.3824f, 9.0f};
        ProudSkillData proudSkillData = null;
        Set<Integer> set = avatar.getProudSkillList();
        if (set != null) {
            for (int n : set) {
                if (n / 100 != 11221) continue;
                proudSkillData = (ProudSkillData)GameData.getProudSkillDataMap().get(n);
                break;
            }
        }
        if (proudSkillData == null) {
            proudSkillData = (ProudSkillData)GameData.getProudSkillDataMap().get(1122101);
        }
        if (proudSkillData != null && proudSkillData.getParamList() != null && proudSkillData.getParamList().length >= 2) {
            fArray[0] = proudSkillData.getParamList()[0];
            fArray[1] = proudSkillData.getParamList()[1];
        }
        return fArray;
    }

    private static boolean hasA1(Avatar avatar) {
        Set<Integer> set = avatar.getProudSkillList();
        if (set != null) {
            for (int n : set) {
                if (n / 100 != 11221) continue;
                return true;
            }
        }
        return avatar.getPromoteLevel() >= 1;
    }

    private static ProudSkillData lookupProud(Avatar avatar, int n, int n2) {
        AvatarSkillDepotData avatarSkillDepotData;
        int n3 = 1;
        if (avatar.getSkillLevelMap() != null) {
            n3 = avatar.getSkillLevelMap().getOrDefault(n, 1);
        }
        if ((avatarSkillDepotData = (AvatarSkillDepotData)GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId())) != null && avatarSkillDepotData.getEnergySkill() == n && avatarSkillDepotData.getEnergySkillData() != null) {
            n2 = avatarSkillDepotData.getEnergySkillData().getProudSkillGroupId();
        }
        return (ProudSkillData)GameData.getProudSkillDataMap().get(n2 * 100 + n3);
    }

    private static EntityAvatar findCaster(Player player, int n) {
        for (EntityAvatar entityAvatar : player.getTeamManager().getActiveTeam()) {
            if (entityAvatar == null || entityAvatar.getId() != n) continue;
            return entityAvatar;
        }
        EntityAvatar entityAvatar = player.getTeamManager().getCurrentAvatarEntity();
        if (entityAvatar != null && entityAvatar.getAvatar() != null && entityAvatar.getAvatar().getAvatarId() == 10000112) {
            return entityAvatar;
        }
        return null;
    }
}
