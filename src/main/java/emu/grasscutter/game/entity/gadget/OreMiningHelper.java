package emu.grasscutter.game.entity.gadget;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.WeaponType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AttackResultOuterClass;
import emu.grasscutter.net.proto.HitCollisionOuterClass;
import emu.grasscutter.net.proto.VectorOuterClass;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.List;
import java.util.Map;

/** Breakable world objects (ores / piles / vines): HP bar + multi-hit break. */
public final class OreMiningHelper {
    /** Base HP for breakables; damage is clamped to maxHp/hits so ATK cannot one-shot. */
    private static final float BREAK_HP = 100.0f;
    private static final float SEARCH_RANGE = 12.0f;

    private OreMiningHelper() {}

    public static boolean isBreakableOre(EntityGadget entityGadget) {
        return isBreakableWorldObject(entityGadget);
    }

    /** Dragonspine Scarlet Quartz (Dulin's Blood). */
    public static boolean isScarletQuartz(EntityGadget entityGadget) {
        if (entityGadget == null) {
            return false;
        }
        if (entityGadget.getGadgetId() == 70590025) {
            return true;
        }
        String json = jsonName(entityGadget);
        return json.contains("OreDulinsBlood") || json.contains("DulinsBlood");
    }

    /**
     * Fallback ground drop when content is not GadgetGatherObject (e.g. content not built yet).
     * Spawns item 101005 for the player to pick up — does not auto-apply the buff.
     */
    public static void dropScarletQuartzPickup(EntityGadget entityGadget, int killerId) {
        if (entityGadget == null || entityGadget.getScene() == null) {
            return;
        }
        try {
            emu.grasscutter.game.player.Player player = null;
            if (killerId > 0) {
                GameEntity ge = entityGadget.getScene().getEntityById(killerId);
                if (ge instanceof EntityAvatar avatar) {
                    player = avatar.getPlayer();
                } else if (ge instanceof EntityClientGadget cg && cg.getOwner() != null) {
                    player = cg.getOwner();
                }
            }
            if (player == null && entityGadget.getScene().getWorld() != null) {
                player = entityGadget.getScene().getWorld().getHost();
            }
            var itemData = emu.grasscutter.data.GameData.getItemDataMap().get(101005);
            if (itemData == null) {
                Grasscutter.getLogger().warn("ScarletQuartz drop: ItemData 101005 missing");
                return;
            }
            var drop =
                    new emu.grasscutter.game.entity.EntityItem(
                            entityGadget.getScene(),
                            player,
                            itemData,
                            entityGadget.getPosition().nearby2d(1.0f).addY(1.0f),
                            1,
                            true);
            entityGadget.getScene().addEntity(drop);
            try {
                emu.grasscutter.game.world.OpenWorldSpawnHelper.onGathered(entityGadget, 101005);
            } catch (Throwable ignored) {
            }
            Grasscutter.getLogger()
                    .info(
                            "ScarletQuartz ground drop uid={} gadgetId={} cfg={}",
                            player != null ? player.getUid() : 0,
                            entityGadget.getGadgetId(),
                            entityGadget.getConfigId());
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("ScarletQuartz drop failed: {}", t.toString());
        }
    }

    public static boolean isBreakableWorldObject(EntityGadget entityGadget) {
        if (entityGadget == null) {
            return false;
        }
        String json = jsonName(entityGadget);
        // Elemental flora are not mining targets.
        if (json.contains("FireFlower")
                || json.contains("IceFlower")
                || json.contains("Cherrypetal")
                || json.contains("ElementFlora")) {
            return false;
        }
        if (isOreJsonName(json) || isDestructibleJsonName(json)) {
            return true;
        }
        return entityGadget.getContent() instanceof GadgetGatherObject && looksLikeOreHp(entityGadget);
    }

    public static void prepareOre(EntityGadget entityGadget) {
        if (entityGadget == null || !isBreakableWorldObject(entityGadget)) {
            return;
        }
        entityGadget.setLockHP(false);
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, BREAK_HP);
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, BREAK_HP);
        entityGadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, BREAK_HP);
        syncHpBar(entityGadget);
    }

    /**
     * Fix OSREL AttackResult: defense_id often missing/wrong; clamp ore damage for multi-hit.
     */
    public static AttackResultOuterClass.AttackResult fixAttackResult(
            Scene scene, AttackResultOuterClass.AttackResult result) {
        if (scene == null || result == null) {
            return result;
        }
        GameEntity target = resolveDefenseEntity(scene, result);
        AttackResultOuterClass.AttackResult.Builder builder = result.toBuilder();
        boolean changed = false;

        if (target != null && target.getId() != result.getDefenseId()) {
            builder.setDefenseId(target.getId());
            changed = true;
            Grasscutter.getLogger()
                    .info(
                            "WorldBreak remap defenseId {} -> {} (attackerId={} dmg={})",
                            result.getDefenseId(),
                            target.getId(),
                            result.getAttackerId(),
                            result.getDamage());
        }

        // After defense remap: clear bramble/frozen/rock seals before Scene drops chest attacks.
        if (target instanceof EntityGadget sealGadget) {
            try {
                ElementType elem = ElementType.getTypeByValue(result.getElementType());
                if (emu.grasscutter.game.entity.EnvironmentalSealHelper.tryProcessAttack(
                        sealGadget, result.getDamage(), elem)) {
                    return changed ? builder.build() : result;
                }
            } catch (Throwable ignored) {
            }
            try {
                ElementType elem = ElementType.getTypeByValue(result.getElementType());
                if (emu.grasscutter.game.entity.gadget.GatherInteractHelper.tryUnlockElementalFlora(
                        sealGadget, elem)) {
                    // Flora stays alive; zero damage so infinite-HP spawn is not "killed".
                    if (result.getDamage() != 0.0f) {
                        builder.setDamage(0.0f);
                        changed = true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (target instanceof EntityGadget gadget && isBreakableWorldObject(gadget)) {
            float clamped =
                    resolveMiningDamage(gadget, result.getDamage(), result.getAttackerId());
            if (clamped != result.getDamage()) {
                builder.setDamage(clamped);
                changed = true;
            }
        }

        return changed ? builder.build() : result;
    }

    public static GameEntity resolveDefenseEntity(
            Scene scene, AttackResultOuterClass.AttackResult result) {
        if (scene == null || result == null) {
            return null;
        }

        GameEntity byId = scene.getEntityById(result.getDefenseId());
        if (byId != null) {
            return byId;
        }

        GameEntity fromUnknown = resolveFromUnknownFields(scene, result);
        if (fromUnknown != null) {
            return fromUnknown;
        }

        Position hint = hitPosition(result);
        if (hint == null) {
            try {
                if (scene.getWorld() != null
                        && scene.getWorld().getHost() != null
                        && scene.getWorld().getHost().getTeamManager() != null) {
                    EntityAvatar avatar =
                            scene.getWorld().getHost().getTeamManager().getCurrentAvatarEntity();
                    if (avatar != null) {
                        hint = avatar.getPosition();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return findNearestBreakable(scene, hint);
    }

    private static GameEntity resolveFromUnknownFields(
            Scene scene, AttackResultOuterClass.AttackResult result) {
        try {
            UnknownFieldSet uf = result.getUnknownFields();
            if (uf == null || uf.asMap().isEmpty()) {
                return null;
            }
            GameEntity bestBreakable = null;
            GameEntity bestAny = null;
            for (Map.Entry<Integer, UnknownFieldSet.Field> entry : uf.asMap().entrySet()) {
                UnknownFieldSet.Field field = entry.getValue();
                for (Long varint : field.getVarintList()) {
                    int id = varint.intValue();
                    if (id <= 0) {
                        continue;
                    }
                    GameEntity entity = scene.getEntityById(id);
                    if (entity == null) {
                        continue;
                    }
                    if (bestAny == null) {
                        bestAny = entity;
                    }
                    if (entity instanceof EntityGadget gadget && isBreakableWorldObject(gadget)) {
                        bestBreakable = gadget;
                    }
                }
            }
            return bestBreakable != null ? bestBreakable : bestAny;
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("WorldBreak unknownFields parse: {}", t.toString());
            return null;
        }
    }

    private static Position hitPosition(AttackResultOuterClass.AttackResult result) {
        try {
            if (!result.hasHitCollision()) {
                return null;
            }
            HitCollisionOuterClass.HitCollision hit = result.getHitCollision();
            if (!hit.hasHitPoint()) {
                return null;
            }
            VectorOuterClass.Vector v = hit.getHitPoint();
            return new Position(v.getX(), v.getY(), v.getZ());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static EntityGadget findNearestBreakable(Scene scene, Position origin) {
        if (scene == null || origin == null) {
            return null;
        }
        EntityGadget nearest = null;
        float best = SEARCH_RANGE * SEARCH_RANGE;
        try {
            for (GameEntity entity : scene.getEntities().values()) {
                if (!(entity instanceof EntityGadget gadget) || !isBreakableWorldObject(gadget)) {
                    continue;
                }
                if (gadget.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0.0f) {
                    continue;
                }
                Position p = gadget.getPosition();
                float dx = origin.getX() - p.getX();
                float dy = origin.getY() - p.getY();
                float dz = origin.getZ() - p.getZ();
                float dist2 = dx * dx + dy * dy + dz * dz;
                if (dist2 < best) {
                    best = dist2;
                    nearest = gadget;
                }
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("WorldBreak nearest search: {}", t.toString());
        }
        return nearest;
    }

    public static float resolveMiningDamage(EntityGadget entityGadget, float incoming) {
        int attackerId = 0;
        try {
            if (entityGadget != null
                    && entityGadget.getScene() != null
                    && entityGadget.getScene().getWorld() != null
                    && entityGadget.getScene().getWorld().getHost() != null
                    && entityGadget.getScene().getWorld().getHost().getTeamManager() != null) {
                EntityAvatar avatar =
                        entityGadget.getScene().getWorld().getHost().getTeamManager().getCurrentAvatarEntity();
                if (avatar != null) {
                    attackerId = avatar.getId();
                }
            }
        } catch (Throwable ignored) {
        }
        return resolveMiningDamage(entityGadget, incoming, attackerId);
    }

    public static float resolveMiningDamage(EntityGadget entityGadget, float incoming, int attackerId) {
        if (!isBreakableWorldObject(entityGadget)) {
            return incoming;
        }
        float maxHp = entityGadget.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (entityGadget.isLockHP()
                || maxHp >= 90000.0f
                || !(maxHp > 0.0f)
                || maxHp == Float.POSITIVE_INFINITY) {
            prepareOre(entityGadget);
            maxHp = BREAK_HP;
        }
        float dmg = miningDamageForAttacker(entityGadget, attackerId, maxHp);
        Grasscutter.getLogger()
                .info(
                        "WorldBreak dmg={} (ignoreAtk={}) gadgetId={} cfg={} json={} hp={}/{}",
                        dmg,
                        incoming,
                        entityGadget.getGadgetId(),
                        entityGadget.getConfigId(),
                        jsonName(entityGadget),
                        entityGadget.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),
                        maxHp);
        return dmg;
    }

    private static float miningDamageForAttacker(EntityGadget entityGadget, int attackerId, float maxHp) {
        WeaponType weaponType = WeaponType.WEAPON_NONE;
        try {
            if (attackerId > 0 && entityGadget.getScene() != null) {
                GameEntity ge = entityGadget.getScene().getEntityById(attackerId);
                if (ge instanceof EntityAvatar avatar
                        && avatar.getAvatar() != null
                        && avatar.getAvatar().getAvatarData() != null) {
                    weaponType = avatar.getAvatar().getAvatarData().getWeaponType();
                } else if (ge instanceof EntityClientGadget) {
                    return maxHp / 3.0f + 0.01f;
                }
            }
        } catch (Throwable ignored) {
        }

        // Official feel: claymore mines fastest; catalyst/bow slowest.
        float hits = 6.0f;
        if (weaponType == WeaponType.WEAPON_CLAYMORE) {
            hits = 3.0f;
        } else if (weaponType == WeaponType.WEAPON_SWORD_ONE_HAND) {
            hits = 5.0f;
        } else if (weaponType == WeaponType.WEAPON_POLE) {
            hits = 6.0f;
        } else if (weaponType == WeaponType.WEAPON_CATALYST || weaponType == WeaponType.WEAPON_BOW) {
            hits = 12.0f;
        }
        return maxHp / hits + 0.01f;
    }

    public static void syncHpBar(EntityGadget entityGadget) {
        if (entityGadget == null
                || entityGadget.getScene() == null
                || !isBreakableWorldObject(entityGadget)) {
            return;
        }
        try {
            float cur = entityGadget.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            if (cur < 0.0f) {
                return;
            }
            entityGadget
                    .getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropUpdateNotify(
                                    entityGadget,
                                    List.of(
                                            FightProperty.FIGHT_PROP_MAX_HP,
                                            FightProperty.FIGHT_PROP_BASE_HP,
                                            FightProperty.FIGHT_PROP_CUR_HP)));
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("WorldBreak syncHpBar failed: {}", t.toString());
        }
    }

    private static boolean looksLikeOreHp(EntityGadget entityGadget) {
        float f = entityGadget.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        return f >= 90000.0f || (f > 0.0f && f <= 100.1f);
    }

    private static boolean isOreJsonName(String string) {
        if (string == null || string.isEmpty()) {
            return false;
        }
        return string.contains("Gather_Default_Ore")
                || string.contains("Gather_Advance_Ore")
                || string.contains("Gather_Small_Ore")
                || string.contains("Gather_MagicOre")
                || string.contains("Gather_Default_Mithril")
                || string.contains("OreFountaine")
                || string.contains("OreNatlan")
                || string.contains("OreSnezhnaya")
                || string.contains("OreNodKrai")
                || string.contains("OreFontaine")
                || string.contains("Prop_Ore")
                || string.contains("Ani_Prop_Ore")
                || string.contains("_Ore_")
                || (string.contains("Ore")
                        && (string.contains("Gather")
                                || string.contains("Prop")
                                || string.contains("Rock")
                                || string.contains("Crystal")
                                || string.contains("Mineral")));
    }

    private static boolean isDestructibleJsonName(String string) {
        if (string == null || string.isEmpty()) {
            return false;
        }
        if (string.contains("StonePile")
                || string.contains("StoneStack")
                || string.contains("Broken_StonePile")
                || string.contains("RuinStonePile")
                || string.contains("FireStonePile")) {
            return true;
        }
        if (string.contains("Environment_Rock")
                && (string.contains("Pile") || string.contains("Stack") || string.contains("Broken"))) {
            return true;
        }
        if (string.contains("WoodenObject")
                || string.contains("Woodenpile")
                || string.contains("WoodenBarrel")
                || string.contains("WoodenBox")) {
            return true;
        }
        // Keep Chest_Bramble findable via WorldBreak remap; seal burn is handled in fixAttackResult.
        if (string.contains("Chest_Bramble")
                || (string.contains("Bramble_Lv") && string.contains("Chest"))) {
            return true;
        }
        if (string.contains("Chest")) {
            return false;
        }
        if (string.contains("Bramble")
                || string.contains("Thorn")
                || string.contains("Vine")
                || string.contains("Ivy")
                || string.contains("LeafPile")
                || string.contains("WitherTree")
                || string.contains("GrassWall")
                || string.contains("Property_Prop_Thorn")) {
            return true;
        }
        return string.contains("Property_Prop_Barrel")
                || string.contains("Property_Prop_Box")
                || string.contains("Property_Prop_Crate")
                || string.contains("GeoBombBarrel")
                || string.contains("HealingBarrel");
    }

    private static String jsonName(EntityGadget entityGadget) {
        try {
            GadgetData gadgetData = entityGadget.getGadgetData();
            if (gadgetData != null && gadgetData.getJsonName() != null) {
                return gadgetData.getJsonName();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
