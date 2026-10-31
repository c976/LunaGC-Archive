/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.ability.SkirkCunningHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class SkirkInvokeLog {
    private static final long BURST_DEBOUNCE_MS = 1500L;
    private static final ConcurrentHashMap<Integer, Long> lastBurstMs = new ConcurrentHashMap();

    private SkirkInvokeLog() {
    }

    public static void clearPlayerState(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        for (EntityAvatar entityAvatar : new ArrayList<>(player.getTeamManager().getActiveTeam())) {
            if (entityAvatar != null) {
                lastBurstMs.remove(entityAvatar.getId());
            }
        }
    }

    public static void clearEntityState(int entityId) {
        lastBurstMs.remove(entityId);
    }

    public static void maybeLog(Player player, AbilityInvokeEntryOuterClass.AbilityInvokeEntry abilityInvokeEntry, GameEntity gameEntity) {
        if (player == null || abilityInvokeEntry == null) {
            return;
        }
        if (!SkirkCunningHelper.isSkirk(gameEntity)) {
            return;
        }
        try {
            boolean bl;
            AbilityInvokeArgumentOuterClass.AbilityInvokeArgument abilityInvokeArgument = abilityInvokeEntry.getArgumentType();
            String string = abilityInvokeArgument == null ? "?" : abilityInvokeArgument.name();
            int n = abilityInvokeEntry.getArgumentTypeValue();
            int n2 = 0;
            int n3 = 0;
            try {
                AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead abilityInvokeEntryHead = abilityInvokeEntry.getHead();
                if (abilityInvokeEntryHead != null) {
                    n2 = abilityInvokeEntryHead.getLocalId();
                    n3 = abilityInvokeEntryHead.getInstancedAbilityId();
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            boolean bl2 = bl = n2 == 0 || n2 == 100899 || string.contains("MODIFIER_DURABILITY") || string.contains("SPECIAL_ENERGY") || string.contains("GLOBAL_FLOAT");
            if (!bl) {
                Grasscutter.getLogger().debug("SkirkInvoke: arg={} typeValue={} localId={} instAbility={} entity={} uid={} curSE={}", string, n, n2, n3, gameEntity.getId(), player.getUid(), Float.valueOf(gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY)));
            }
            if ((n2 == 98923 || n2 == 131691) && string != null && string.contains("NONE")) {
                float f = gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
                if (f < 49.5f) {
                    return;
                }
                int n4 = gameEntity.getId();
                long l = System.currentTimeMillis();
                Long l2 = lastBurstMs.get(n4);
                if (l2 != null && l - l2 < 1500L) {
                    return;
                }
                lastBurstMs.put(n4, l);
                if (gameEntity instanceof EntityAvatar) {
                    EntityAvatar entityAvatar = (EntityAvatar)gameEntity;
                    SkirkCunningBridge.onBurstSkill(player, entityAvatar);
                }
            }
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("SkirkInvoke log failed", throwable);
        }
    }
}
