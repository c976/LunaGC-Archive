/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.entity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.event.entity.EntityDamageEvent;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EnvironmentalSealHelper {
    private static final float BRAMBLE_BURN_HP = 600.0f;
    private static final float BRAMBLE_MIN_TICK = 35.0f;
    private static final ConcurrentHashMap<Integer, Float> burnLeftByEntityId = new ConcurrentHashMap();
    private static final Set<Integer> finishedBrambleChests = ConcurrentHashMap.newKeySet();

    private EnvironmentalSealHelper() {
    }

    public static void clearEntityState(int entityId) {
        burnLeftByEntityId.remove(entityId);
        finishedBrambleChests.remove(entityId);
    }

    public static void tryProcess(EntityGadget entityGadget, EntityDamageEvent entityDamageEvent) {
        if (entityGadget == null || entityDamageEvent == null) {
            return;
        }
        EnvironmentalSealHelper.tryProcessAttack(
                entityGadget, entityDamageEvent.getDamage(), entityDamageEvent.getAttackElementType());
    }

    /**
     * Handle fire/ice/geo attacks that clear environmental seals on chests (and bramble walls).
     * Returns true when the attack was consumed so callers must not kill the chest gadget.
     */
    public static boolean tryProcessAttack(
            EntityGadget entityGadget, float damage, ElementType elementType) {
        if (entityGadget == null) {
            return false;
        }
        if (elementType == null) {
            elementType = ElementType.None;
        }
        String string = EnvironmentalSealHelper.jsonName(entityGadget);
        int n = entityGadget.getState();
        // Bramble chests: OSREL often omits element (None). Accept Fire and None/Default.
        if (EnvironmentalSealHelper.isBrambleChestPrefab(string)) {
            if (finishedBrambleChests.contains(entityGadget.getId())) {
                EnvironmentalSealHelper.reviveAsIndestructible(entityGadget);
                return true;
            }
            if (EnvironmentalSealHelper.isSealedBrambleChest(entityGadget, n, string)
                    && EnvironmentalSealHelper.isBrambleBurnElement(elementType)) {
                Grasscutter.getLogger()
                        .info(
                                "BrambleBurn tick gadgetId={} entityId={} elem={} dmg={} state={} json={}",
                                entityGadget.getGadgetId(),
                                entityGadget.getId(),
                                elementType,
                                damage,
                                n,
                                string);
                EnvironmentalSealHelper.applyBrambleChestBurn(entityGadget, damage);
                return true;
            }
        }
        if (elementType == ElementType.Fire && EnvironmentalSealHelper.isBrambleWall(string)) {
            EnvironmentalSealHelper.applyWallBurn(entityGadget, damage);
            return true;
        }
        if ((elementType == ElementType.Ice || elementType == ElementType.Frozen) && n == 105) {
            entityGadget.updateState(0);
            EnvironmentalSealHelper.reviveAsIndestructible(entityGadget);
            return true;
        }
        if (elementType == ElementType.Rock && n == 106) {
            entityGadget.updateState(0);
            EnvironmentalSealHelper.reviveAsIndestructible(entityGadget);
            return true;
        }
        return false;
    }

    /** Fire clears vines; None/Default cover OSREL packets that omit element. */
    private static boolean isBrambleBurnElement(ElementType elementType) {
        return elementType == ElementType.Fire
                || elementType == ElementType.None
                || elementType == ElementType.Default;
    }

    /** True while vines/ice/rock still seal a world chest (interact should be blocked). */
    public static boolean isSealedWorldChest(EntityGadget entityGadget) {
        if (entityGadget == null) {
            return false;
        }
        int n = entityGadget.getState();
        if (n == 104 || n == 105 || n == 106) {
            return true;
        }
        String string = EnvironmentalSealHelper.jsonName(entityGadget);
        return EnvironmentalSealHelper.isBrambleChestPrefab(string)
                && !finishedBrambleChests.contains(entityGadget.getId());
    }

    public static void tryClear(EntityGadget entityGadget, ElementType elementType) {
    }

    private static boolean isBrambleChestPrefab(String string) {
        return string.contains("Chest_Bramble") || string.contains("Bramble_Lv") && string.contains("Chest");
    }

    private static boolean isSealedBrambleChest(EntityGadget entityGadget, int n, String string) {
        if (finishedBrambleChests.contains(entityGadget.getId())) {
            return false;
        }
        if (n == 104) {
            return true;
        }
        return EnvironmentalSealHelper.isBrambleChestPrefab(string);
    }

    private static void applyBrambleChestBurn(EntityGadget entityGadget, float f) {
        int n2 = entityGadget.getId();
        float f2 = burnLeftByEntityId.computeIfAbsent(n2, n -> Float.valueOf(600.0f)).floatValue();
        float f3 = Math.max(f, 35.0f);
        if (f3 > 600.0f) {
            f3 = 150.0f;
        }
        if ((f2 = Math.max(0.0f, f2 - f3)) <= 0.01f) {
            burnLeftByEntityId.remove(n2);
            EnvironmentalSealHelper.finishBrambleChest(entityGadget);
            return;
        }
        burnLeftByEntityId.put(n2, Float.valueOf(f2));
        EnvironmentalSealHelper.syncBurnBar(entityGadget, Math.max(1.0f, f2));
        entityGadget.setDead(false);
    }

    private static void finishBrambleChest(EntityGadget entityGadget) {
        finishedBrambleChests.add(entityGadget.getId());
        if (entityGadget.getState() != 104) {
            entityGadget.updateState(104);
        }
        entityGadget.updateState(0);
        EnvironmentalSealHelper.reviveAsIndestructible(entityGadget);
        Grasscutter.getLogger().info("BrambleBurn vines cleared, chest kept gadgetId={} entityId={}", (Object)entityGadget.getGadgetId(), (Object)entityGadget.getId());
    }

    private static void reviveAsIndestructible(EntityGadget entityGadget) {
        float f = 99999.0f;
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, f);
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, f);
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, f);
        entityGadget.setLockHP(true);
        entityGadget.setDead(false);
        EnvironmentalSealHelper.broadcastHp(entityGadget);
    }

    private static void syncBurnBar(EntityGadget entityGadget, float f) {
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 600.0f);
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, 600.0f);
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, f);
        entityGadget.setLockHP(true);
        EnvironmentalSealHelper.broadcastHp(entityGadget);
    }

    private static void broadcastHp(EntityGadget entityGadget) {
        if (entityGadget.getScene() == null) {
            return;
        }
        entityGadget.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify((GameEntity)entityGadget, FightProperty.FIGHT_PROP_MAX_HP));
        entityGadget.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify((GameEntity)entityGadget, FightProperty.FIGHT_PROP_CUR_HP));
    }

    private static void applyWallBurn(EntityGadget entityGadget, float f) {
        int n2 = entityGadget.getId();
        float f2 = burnLeftByEntityId.computeIfAbsent(n2, n -> Float.valueOf(600.0f)).floatValue();
        float f3 = Math.max(f, 35.0f);
        if (f3 > 600.0f) {
            f3 = 150.0f;
        }
        if ((f2 = Math.max(0.0f, f2 - f3)) <= 0.01f) {
            burnLeftByEntityId.remove(n2);
            if (entityGadget.getScene() != null) {
                entityGadget.getScene().killEntity(entityGadget);
            }
            return;
        }
        burnLeftByEntityId.put(n2, Float.valueOf(f2));
        EnvironmentalSealHelper.syncBurnBar(entityGadget, Math.max(1.0f, f2));
        entityGadget.setDead(false);
    }

    private static boolean isBrambleWall(String string) {
        return string.contains("BrambleWall") || string.contains("BrambleWorld") || string.contains("ThornObstacle") || string.contains("ThornWall");
    }

    private static String jsonName(EntityGadget entityGadget) {
        try {
            if (entityGadget.getGadgetData() != null && entityGadget.getGadgetData().getJsonName() != null) {
                return entityGadget.getGadgetData().getJsonName();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return "";
    }
}
