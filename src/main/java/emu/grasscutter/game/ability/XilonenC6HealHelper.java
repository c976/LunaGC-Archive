/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.entity.EntityClientGadget
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.props.FightProperty
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class XilonenC6HealHelper {
    public static final int XILONEN_ID = 10000103;
    private static final String SHOW_TIME_KEY = "_ABILITY_Xilonen_Constellation_6_IsShowTime";
    private static final String NYX_STATE_KEY = "_ABILITY_Xilonen_IsNyxState";
    private static final String NYX_VALUE_KEY = "NyxValue";
    private static final float HEAL_DEF_RATIO = 1.2f;
    private static final long INTERVAL_MS = 1500L;
    private static final long DURATION_MS = 5000L;
    private static final long SHOWTIME_CD_MS = 15000L;
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "xilonen-c6-heal");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Integer, ScheduledFuture<?>> running = new ConcurrentHashMap();
    private static final Map<Integer, Long> lastShowtimeStartMs = new ConcurrentHashMap<Integer, Long>();

    private XilonenC6HealHelper() {
    }

    public static void onGlobalFloat(Player player, GameEntity gameEntity, String string, float f) {
        if (player == null || gameEntity == null || string == null) {
            return;
        }
        if (!SHOW_TIME_KEY.equals(string)) {
            return;
        }
        EntityAvatar entityAvatar = XilonenC6HealHelper.asXilonen(gameEntity);
        if (entityAvatar == null) {
            return;
        }
        if (f >= 0.5f) {
            XilonenC6HealHelper.start(player, entityAvatar, "gv");
        } else {
            XilonenC6HealHelper.stop(player.getUid());
        }
    }

    public static void onAttackHit(GameEntity gameEntity) {
        EntityAvatar entityAvatar;
        EntityAvatar entityAvatar2 = XilonenC6HealHelper.asXilonen(gameEntity);
        if (entityAvatar2 == null) {
            return;
        }
        Player player = entityAvatar2.getPlayer();
        if (player == null || !XilonenC6HealHelper.hasC6(entityAvatar2.getAvatar())) {
            return;
        }
        if (!XilonenC6HealHelper.inNightsoul(entityAvatar2) && (entityAvatar = player.getTeamManager().getCurrentAvatarEntity()) != entityAvatar2) {
            return;
        }
        int n = player.getUid();
        if (running.containsKey(n)) {
            return;
        }
        Long l = lastShowtimeStartMs.get(n);
        long l2 = System.currentTimeMillis();
        if (l != null && l2 - l < 15000L) {
            return;
        }
        Map map = entityAvatar2.getGlobalAbilityValues();
        if (map != null) {
            map.put(SHOW_TIME_KEY, Float.valueOf(1.0f));
            entityAvatar2.onAbilityValueUpdate();
        }
        XilonenC6HealHelper.start(player, entityAvatar2, "combat");
    }

    private static EntityAvatar asXilonen(GameEntity gameEntity) {
        if (gameEntity == null) {
            return null;
        }
        GameEntity resolved = gameEntity;
        if (gameEntity instanceof EntityClientGadget entityClientGadget) {
            try {
                if (entityClientGadget.getScene() != null) {
                    GameEntity owner =
                            entityClientGadget.getScene().getEntityById(entityClientGadget.getOwnerEntityId());
                    if (owner != null) {
                        resolved = owner;
                    }
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if (!(resolved instanceof EntityAvatar entityAvatar)) {
            return null;
        }
        if (entityAvatar.getAvatar() == null || entityAvatar.getAvatar().getAvatarId() != 10000103) {
            return null;
        }
        return entityAvatar;
    }

    private static boolean hasC6(Avatar avatar) {
        if (avatar == null) {
            return false;
        }
        if (avatar.getCoreProudSkillLevel() >= 6) {
            return true;
        }
        return avatar.getTalentIdList() != null && avatar.getTalentIdList().contains(1036);
    }

    private static boolean inNightsoul(EntityAvatar entityAvatar) {
        Map map = entityAvatar.getGlobalAbilityValues();
        if (map == null) {
            return false;
        }
        Float f = (Float)map.get(NYX_STATE_KEY);
        if (f != null && f.floatValue() >= 0.5f) {
            return true;
        }
        Float f2 = (Float)map.get(NYX_VALUE_KEY);
        return f2 != null && f2.floatValue() > 0.5f;
    }

    private static void start(Player player, EntityAvatar entityAvatar, String string) {
        int n = player.getUid();
        XilonenC6HealHelper.stop(n);
        lastShowtimeStartMs.put(n, System.currentTimeMillis());
        long l = System.currentTimeMillis();
        try {
            XilonenC6HealHelper.healTeam(player, entityAvatar);
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("Xilonen C6 immediate heal failed", throwable);
        }
        ScheduledFuture<?> scheduledFuture = SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                if (System.currentTimeMillis() - l > 5200L) {
                    Map map = entityAvatar.getGlobalAbilityValues();
                    if (map != null) {
                        map.put(SHOW_TIME_KEY, Float.valueOf(0.0f));
                    }
                    XilonenC6HealHelper.stop(n);
                    return;
                }
                Player tickPlayer = entityAvatar.getPlayer();
                if (tickPlayer == null || tickPlayer.getScene() == null) {
                    XilonenC6HealHelper.stop(n);
                    return;
                }
                XilonenC6HealHelper.healTeam(tickPlayer, entityAvatar);
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().warn("Xilonen C6 heal tick failed", throwable);
            }
        }, 1500L, 1500L, TimeUnit.MILLISECONDS);
        running.put(n, scheduledFuture);
        Grasscutter.getLogger().info("Xilonen C6 ShowTime heal started uid={} reason={}", (Object)n, (Object)string);
    }

    private static void stop(int n) {
        ScheduledFuture<?> scheduledFuture = running.remove(n);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    private static void healTeam(Player player, EntityAvatar entityAvatar) {
        float f;
        float f2 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE);
        if (f2 <= 1.0f && entityAvatar.getAvatar() != null) {
            f2 = entityAvatar.getAvatar().getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE);
        }
        if ((f = f2 * 1.2f) <= 1.0f) {
            Grasscutter.getLogger().warn("Xilonen C6 heal skipped def={} amount={}", (Object)Float.valueOf(f2), (Object)Float.valueOf(f));
            return;
        }
        int n = 0;
        for (EntityAvatar entityAvatar2 : player.getTeamManager().getActiveTeam()) {
            float f3;
            float f4;
            if (entityAvatar2 == null) continue;
            float f5 = entityAvatar2.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            float f6 = entityAvatar2.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            if (f6 <= 1.0f && entityAvatar2.getAvatar() != null) {
                f6 = entityAvatar2.getAvatar().getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            }
            if (f5 <= 0.0f || f6 <= 1.0f || f5 >= f6 - 0.5f || (f4 = (f3 = Math.min(f6, f5 + f)) - f5) <= 0.5f) continue;
            entityAvatar2.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, f3);
            if (entityAvatar2.getAvatar() != null) {
                entityAvatar2.getAvatar().setCurrentHp(f3);
                try {
                    player.sendPacket((BasePacket)new PacketAvatarFightPropUpdateNotify(entityAvatar2.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
            if (entityAvatar2.getScene() != null) {
                try {
                    entityAvatar2.getScene().broadcastPacket((BasePacket)new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar2, FightProperty.FIGHT_PROP_CUR_HP));
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
            try {
                if (entityAvatar2.getScene() != null && entityAvatar2.getScene().getWorld() != null) {
                    entityAvatar2.getScene().getWorld().broadcastPacket((BasePacket)new PacketEvtBeingHealedNotify((GameEntity)entityAvatar2, (GameEntity)entityAvatar, f, f4));
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            ++n;
        }
        Grasscutter.getLogger().info("Xilonen C6 healed members={} amount={} def={}", new Object[]{n, Float.valueOf(f), Float.valueOf(f2)});
    }

    public static void clearPlayerState(int uid) {
        stop(uid);
        lastShowtimeStartMs.remove(uid);
    }
}

