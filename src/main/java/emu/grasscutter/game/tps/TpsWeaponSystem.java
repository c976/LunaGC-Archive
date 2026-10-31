package emu.grasscutter.game.tps;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.FightPropData;
import emu.grasscutter.data.excels.EquipAffixData;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.TpsAmmunitionChangeNotify;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.TpsAmmunitionChangeNotifyEntry;
import emu.grasscutter.net.proto.TpsAmmunitionChangeOuterClass.TpsAmmunitionChange;
import emu.grasscutter.net.proto.TpsAmmunitionUpdateTypeOuterClass.TpsAmmunitionUpdateType;
import emu.grasscutter.net.proto.TpsWeapon._TpsWeapon;
import emu.grasscutter.net.proto.TpsWeaponAmmunitionInfoOuterClass.TpsWeaponAmmunitionInfo;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.server.packet.send.PacketAbilityInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketTpsEquipChangeNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import javax.annotation.Nullable;

/**
 * The 7.x third-person shooter weapons: guns and grenades an avatar wears next to its normal
 * weapon.
 *
 * <p>A worn TPS weapon is a weapon entity of its own (gadget 5001600x), listed in {@code
 * SceneAvatarInfo.tps_weapon_list} and {@code AvatarInfo.tps_weapon_list}, and its affixes'
 * openConfigs hand the avatar the {@code Avatar_TPS_*} abilities that aim, shoot and reload.
 * Ammunition is a reserve per TpsAmmunitionExcelConfigData id, shared by every slot it fills; the
 * client reports what it spends and picks up through {@code ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION}.
 */
public final class TpsWeaponSystem {
    /** CONST_VALUE_TPS_WEAPON_ITEM_LIMIT. */
    public static final int TPS_WEAPON_ITEM_LIMIT = 20;

    /** CONST_VALUE_TPS_SLOT_WEAR_NUM_LIMIT "1:2;2:1": two guns and one grenade. */
    private static final Int2IntMap WEAR_LIMIT = new Int2IntOpenHashMap(new int[] {1, 2}, new int[] {2, 1});

    private TpsWeaponSystem() {}

    /**
     * How TpsWeaponAmmunitionInfo is filled. What the client reads from it is not settled (see
     * docs/tps), so {@code /tps ammo} can switch these at runtime to try the candidates.
     */
    public enum AmmoCurrent { RESERVE, LIMIT, FIXED }

    public static volatile boolean ammoTypeIsSlot = true;
    public static volatile AmmoCurrent ammoCurrent = AmmoCurrent.RESERVE;
    public static volatile int ammoFixed = 45;

    public static boolean isTpsWeapon(@Nullable GameItem item) {
        return item != null
                && item.getItemData() != null
                && item.getItemType() == ItemType.ITEM_TPS_WEAPON;
    }

    /** The player's copy of a TPS weapon; there is at most one of each. */
    @Nullable public static GameItem findOwnedWeapon(Player player, int itemId) {
        for (GameItem item : player.getInventory().getItems().values()) {
            if (item.getItemId() == itemId && isTpsWeapon(item)) return item;
        }
        return null;
    }

    /** The TPS weapons the avatar wears, in wear order. Ids no longer in the bag are skipped. */
    public static List<GameItem> getWornWeapons(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null || avatar.getTpsWeaponIds().isEmpty()) return List.of();

        var worn = new ArrayList<GameItem>(avatar.getTpsWeaponIds().size());
        for (int itemId : avatar.getTpsWeaponIds()) {
            var item = findOwnedWeapon(player, itemId);
            if (item != null) worn.add(item);
        }
        return worn;
    }

    /** Affix id to level: the weapon's base affix plus one per unlocked accessory. */
    public static Int2IntMap getAffixLevels(GameItem item) {
        var affixes = new Int2IntLinkedOpenHashMap();
        var weaponData = GameData.getTpsWeaponDataMap().get(item.getItemId());
        if (weaponData != null) {
            putAffix(affixes, weaponData.getEquipAffixId(), weaponData.getTpsWeaponBaseAffix());
        }
        for (int accessoryId : item.getTpsAccessoryIds()) {
            var accessory = GameData.getTpsWeaponAccessoryDataMap().get(accessoryId);
            if (accessory == null || accessory.getTpsWeaponId() != item.getItemId()) continue;
            putAffix(affixes, accessory.getEquipAffixId(), accessory.getTpsWeaponBaseAffix());
        }
        return affixes;
    }

    private static void putAffix(Int2IntMap affixes, int equipAffixId, List<Integer> baseAffixes) {
        if (equipAffixId > 0) {
            affixes.put(equipAffixId / 10, equipAffixId % 10);
        } else if (baseAffixes != null) {
            baseAffixes.stream().filter(id -> id > 0).forEach(id -> affixes.put((int) id, 0));
        }
    }

    public static _TpsWeapon toTpsWeaponProto(GameItem item) {
        return _TpsWeapon.newBuilder()
                .addAllAccessoryIdList(item.getTpsAccessoryIds())
                .putAllAffixMap(getAffixLevels(item))
                .build();
    }

    /** Gives the weapon an entity in the scene, reusing the one it has if it is already there. */
    public static void ensureWeaponEntity(GameItem item, @Nullable Scene scene) {
        if (scene == null || scene.getWorld() == null) return;
        var entity = item.getWeaponEntity();
        if (entity != null && entity.getScene() == scene) return;

        entity = new EntityWeapon(scene, item.getItemData().getGadgetId());
        item.setWeaponEntity(entity);
        scene.getWeaponEntities().put(entity.getId(), entity);
    }

    public static void ensureWeaponEntities(Avatar avatar, @Nullable Scene scene) {
        getWornWeapons(avatar).forEach(item -> ensureWeaponEntity(item, scene));
    }

    /**
     * Gives every worn weapon a new entity and drops the old one. A TpsEquipChangeNotify that
     * repeats the entity ids the client already holds made the gun vanish, and the shoot button
     * with it: the client replaces the old weapons with the listed ones, and the old ones had the
     * same ids.
     */
    private static void respawnWeaponEntities(Avatar avatar) {
        var avatarEntity = avatar.getAsEntity();
        var scene = avatarEntity != null ? avatarEntity.getScene() : null;
        for (GameItem item : getWornWeapons(avatar)) {
            var old = item.getWeaponEntity();
            if (old != null && old.getScene() != null) {
                old.getScene().getWeaponEntities().remove(old.getId());
            }
            item.setWeaponEntity(null);
            ensureWeaponEntity(item, scene);
        }
    }

    /** Owned avatars and the trial avatars of the current team (the TPS traveler) wearing TPS weapons. */
    public static List<Avatar> getTpsWearers(Player player) {
        var wearers = new ArrayList<Avatar>();
        player.getAvatars().forEach(wearers::add);
        wearers.addAll(player.getTeamManager().getTrialAvatars().values());
        wearers.removeIf(avatar -> avatar.getTpsWeaponIds().isEmpty());
        return wearers;
    }

    public static SceneWeaponInfo toSceneWeaponInfo(Player player, GameItem item) {
        var info =
                SceneWeaponInfo.newBuilder()
                        .setEntityId(item.getWeaponEntity() != null ? item.getWeaponEntity().getId() : 0)
                        .setGadgetId(item.getItemData().getGadgetId())
                        .setItemId(item.getItemId())
                        .setGuid(item.getGuid())
                        .setLevel(item.getLevel())
                        .setPromoteLevel(item.getPromoteLevel())
                        .putAllAffixMap(getAffixLevels(item))
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder().setIsInited(true));

        var weaponData = GameData.getTpsWeaponDataMap().get(item.getItemId());
        if (weaponData != null && weaponData.getAmmoSlotIds() != null) {
            for (int slotId : weaponData.getAmmoSlotIds()) {
                var ammunition = getAmmunitionForSlot(slotId);
                if (ammunition == null) continue;
                int current =
                        switch (ammoCurrent) {
                            case RESERVE -> getReserve(player, ammunition.getId());
                            case LIMIT -> ammunition.getTpsAmmoLimit();
                            case FIXED -> ammoFixed;
                        };
                info.addAmmunitionList(
                        TpsWeaponAmmunitionInfo.newBuilder()
                                .setAmmunitionType(ammoTypeIsSlot ? slotId : 1)
                                .setAmmunitionConfigId(ammunition.getId())
                                .setCurrentAmmunition(current));
            }
        }
        return info.build();
    }

    /**
     * {@code SceneAvatarInfo.tps_weapon_list} / {@code AvatarInfo.tps_weapon_list}. Weapon entities
     * are made when the avatar enters a scene, so an off-field avatar lists its weapons with entity 0.
     */
    public static List<SceneWeaponInfo> getSceneWeaponInfos(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null) return List.of();
        return getWornWeapons(avatar).stream().map(item -> toSceneWeaponInfo(player, item)).toList();
    }

    /**
     * Adds the stats and ability embryos of the worn TPS weapons. Called from {@link
     * Avatar#recalcStats}, which tells the client when the embryos change.
     */
    public static void applyAffixes(Avatar avatar) {
        for (GameItem item : getWornWeapons(avatar)) {
            for (var affix : getAffixLevels(item).int2IntEntrySet()) {
                EquipAffixData affixData =
                        GameData.getEquipAffixDataMap().get(affix.getIntKey() * 10 + affix.getIntValue());
                if (affixData == null) continue;
                if (affixData.getAddProps() != null) {
                    for (FightPropData prop : affixData.getAddProps()) {
                        if (prop.getProp() != null) avatar.addFightProperty(prop.getProp(), prop.getValue());
                    }
                }
                avatar.addToExtraAbilityEmbryos(affixData.getOpenConfig(), true);
            }
        }
    }

    /**
     * WearTpsEquipReq: the avatar now wears exactly {@code equipGuids}. Weapons are taken off
     * whoever wore them before. The avatar may be the TPS traveler, a trial avatar: its choice is
     * kept as the player's TPS loadout instead.
     *
     * @return a {@link Retcode} value.
     */
    public static int wear(Player player, long avatarGuid, List<Long> equipGuids) {
        var avatar = findAvatar(player, avatarGuid);
        if (avatar == null) return Retcode.RET_CAN_NOT_FIND_AVATAR_VALUE;

        var itemIds = new ArrayList<Integer>(equipGuids.size());
        var slotCounts = new Int2IntOpenHashMap();
        for (long guid : equipGuids) {
            var item = player.getInventory().getItemByGuid(guid);
            if (!isTpsWeapon(item)) return Retcode.RET_ITEM_NOT_EXIST_VALUE;
            if (itemIds.contains(item.getItemId())) continue;

            var weaponData = GameData.getTpsWeaponDataMap().get(item.getItemId());
            int slotType = weaponData != null ? weaponData.getWearSlotType() : 0;
            if (slotCounts.addTo(slotType, 1) + 1 > WEAR_LIMIT.getOrDefault(slotType, 0)) {
                return Retcode.RET_EQUIP_EXCEED_LIMIT_VALUE;
            }
            itemIds.add(item.getItemId());
        }

        // Nothing to change: answer, but do not resend weapons the client already shows.
        if (itemIds.equals(avatar.getTpsWeaponIds())) return Retcode.RET_SUCC_VALUE;

        for (Avatar other : player.getAvatars()) {
            if (other == avatar || !other.getTpsWeaponIds().removeIf(itemIds::contains)) continue;
            other.save();
            other.recalcStats();
            sendEquipChange(other);
        }

        var loadout = player.getTpsLoadout();
        if (TpsAvatarSystem.isTpsAvatar(avatar)) {
            loadout.clear();
            loadout.addAll(itemIds);
            player.save();
        } else if (loadout.removeIf(itemIds::contains)) {
            player.save();
        }

        avatar.getTpsWeaponIds().clear();
        avatar.getTpsWeaponIds().addAll(itemIds);
        if (avatar.getTrialAvatarId() == 0) avatar.save();
        avatar.recalcStats();
        sendEquipChange(avatar);
        return Retcode.RET_SUCC_VALUE;
    }

    /** An owned avatar, or one of the trial avatars in the current team. */
    @Nullable private static Avatar findAvatar(Player player, long avatarGuid) {
        var avatar = player.getAvatars().getAvatarByGuid(avatarGuid);
        if (avatar != null) return avatar;
        return player.getTeamManager().getTrialAvatars().values().stream()
                .filter(trial -> trial.getGuid() == avatarGuid)
                .findFirst()
                .orElse(null);
    }

    /**
     * TpsEquipChangeNotify to everyone in the scene when the avatar is on the field, with fresh
     * weapon entities (see {@link #respawnWeaponEntities}).
     */
    public static void sendEquipChange(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null || !player.hasSentLoginPackets()) return;

        respawnWeaponEntities(avatar);
        var packet = new PacketTpsEquipChangeNotify(avatar, getSceneWeaponInfos(avatar));
        var entity = avatar.getAsEntity();
        if (entity != null && entity.getScene() != null) {
            entity.getScene().broadcastPacket(packet);
        } else {
            player.sendPacket(packet);
        }
        sendWeaponAbilityBlocks(avatar);
    }

    /**
     * AbilityChangeNotify for each worn weapon entity, announcing its gadget-config abilities
     * ({@code TPS_Weapon_*_Innate_Ability}, fire, reload). Without them the gun has no ability
     * instance on the client: no shooting HUD and no shot.
     */
    public static void sendWeaponAbilityBlocks(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null || !player.hasSentLoginPackets()) return;
        for (GameItem item : getWornWeapons(avatar)) {
            var weapon = item.getWeaponEntity();
            if (weapon == null) continue;
            var block = weapon.getAbilityControlBlock();
            if (block.getAbilityEmbryoListCount() == 0) {
                Grasscutter.getLogger().debug("TPS weapon {} has no gadget abilities", weapon.getGadgetId());
                continue;
            }
            player.sendPacket(new PacketAbilityChangeNotify(weapon.getId(), block));
            Grasscutter.getLogger()
                    .debug(
                            "TPS weapon abilities: gadget {} entity {} embryos {}",
                            weapon.getGadgetId(),
                            weapon.getId(),
                            block.getAbilityEmbryoListCount());
        }
    }

    /** The client finished initialising an entity's abilities; give a TPS wearer's guns theirs. */
    public static void onClientAbilityInit(Player player, int entityId) {
        for (var entity : player.getTeamManager().getActiveTeam()) {
            if (entity == null || entity.getId() != entityId) continue;
            if (!entity.getAvatar().getTpsWeaponIds().isEmpty()) sendWeaponAbilityBlocks(entity.getAvatar());
        }
    }

    @Nullable public static TpsAmmunitionData getAmmunitionForSlot(int slotId) {
        for (var ammunition : GameData.getTpsAmmunitionDataMap().values()) {
            if (ammunition.getAmmoSlotIds() != null && ammunition.getAmmoSlotIds().contains(slotId)) {
                return ammunition;
            }
        }
        return null;
    }

    /** Reserve of one ammunition. A player who never used it has a full reserve. */
    public static int getReserve(Player player, int ammunitionId) {
        var data = GameData.getTpsAmmunitionDataMap().get(ammunitionId);
        int limit = data != null ? data.getTpsAmmoLimit() : 0;
        return player.getTpsAmmunition().getOrDefault(ammunitionId, limit);
    }

    /** Moves a reserve by {@code delta}, kept within [0, tpsAmmoLimit]. Returns the new reserve. */
    public static int changeReserve(Player player, int ammunitionId, int delta) {
        var data = GameData.getTpsAmmunitionDataMap().get(ammunitionId);
        if (data == null) return 0;
        int value =
                (int) Math.max(0, Math.min((long) getReserve(player, ammunitionId) + delta, data.getTpsAmmoLimit()));
        player.getTpsAmmunition().put(ammunitionId, value);
        return value;
    }

    /** Fills every reserve and tells the client (24371, and each worn weapon's ammunition list). */
    public static void refillAmmunition(Player player) {
        for (var data : GameData.getTpsAmmunitionDataMap().values()) {
            player.getTpsAmmunition().put(data.getId(), data.getTpsAmmoLimit());
        }
        player.save();
        getTpsWearers(player).forEach(TpsWeaponSystem::sendEquipChange);
        if (!getWornAmmunition(player).isEmpty()) sendAmmunitionNotify(player);
    }

    /** Every ammunition the player's worn weapons draw from, with its reserve. */
    private static Int2IntMap getWornAmmunition(Player player) {
        var pools = new Int2IntLinkedOpenHashMap();
        for (Avatar avatar : getTpsWearers(player)) {
            for (GameItem item : getWornWeapons(avatar)) {
                var data = GameData.getTpsWeaponDataMap().get(item.getItemId());
                if (data == null || data.getAmmoSlotIds() == null) continue;
                for (int slotId : data.getAmmoSlotIds()) {
                    var ammunition = getAmmunitionForSlot(slotId);
                    if (ammunition != null) pools.put(ammunition.getId(), getReserve(player, ammunition.getId()));
                }
            }
        }
        return pools;
    }

    /**
     * Gives the client its ammunition on entering a scene, the way a capture shows it: an empty
     * TpsRegionalPlaySupplyInfoNotify (6579), then TpsAmmunitionChangeNotify (24371) with the reserve
     * of each ammunition the worn weapons draw from ({1001: 1000, 1003: 12, 1005: 3} there), both
     * before EnterSceneDoneRsp. Without 24371 the client's reserves stay at 0: the HUD shows 0 and a
     * reload has nothing to load.
     */
    public static void sendSceneAmmunition(Player player) {
        if (getWornAmmunition(player).isEmpty()) return;
        player.sendPacket(new BasePacket(PacketOpcodes.TpsRegionalPlaySupplyInfoNotify));
        sendAmmunitionNotify(player);
    }

    /** TpsAmmunitionChangeNotify (24371) listing each worn ammunition with its reserve as the count. */
    public static int sendAmmunitionNotify(Player player) {
        var proto = TpsAmmunitionChangeNotify.newBuilder();
        getWornAmmunition(player)
                .forEach(
                        (id, count) ->
                                proto.addAmmunitionList(
                                        TpsAmmunitionChangeNotifyEntry.newBuilder()
                                                .setAmmunitionConfigId(id)
                                                .setChangeCount(count)));
        var packet = new BasePacket(PacketOpcodes.TpsAmmunitionChangeNotify);
        packet.setData(proto.build());
        player.sendPacket(packet);
        Grasscutter.getLogger().info("TPS ammo: sent 24371 {}", proto.build());
        return proto.getAmmunitionListCount();
    }

    /**
     * Experiment: a server-authored ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION (SUPPLY) on each field
     * wearer, routed to its Avatar_TPS_Ammo_Manager instance. Uses 7.1 field numbers.
     */
    public static int sendAmmunitionSupply(Player player) {
        var pools = getWornAmmunition(player);
        var meta = AbilityMetaUpdateTpsWeaponAmmunition.newBuilder()
                .setUpdateType(TpsAmmunitionUpdateType.TpsAmmunitionUpdateType_SUPPLY);
        pools.forEach(
                (id, count) ->
                        meta.addAmmunitionList(
                                TpsAmmunitionChange.newBuilder().setAmmunitionConfigId(id).setChangeCount(count)));

        int sent = 0;
        int managerHash = Utils.abilityHash("Avatar_TPS_Ammo_Manager");
        for (var entity : player.getTeamManager().getActiveTeam()) {
            if (entity.getAvatar().getTpsWeaponIds().isEmpty()) continue;
            int instancedId =
                    entity.getAbilityControlBlock().getAbilityEmbryoListList().stream()
                            .filter(embryo -> embryo.getAbilityNameHash() == managerHash)
                            .mapToInt(embryo -> embryo.getAbilityId())
                            .findFirst()
                            .orElse(0);
            var invoke =
                    AbilityInvokeEntry.newBuilder()
                            .setArgumentType(
                                    AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION)
                            .setEntityId(entity.getId())
                            .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(instancedId))
                            .setAbilityData(meta.build().toByteString())
                            .build();
            player.sendPacket(new PacketAbilityInvocationsNotify(invoke));
            Grasscutter.getLogger()
                    .info("TPS ammo: sent SUPPLY to entity {} (Ammo_Manager instance {}): {}", entity.getId(), instancedId, meta.build());
            sent++;
        }
        return sent;
    }

    /** ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION: the client spent, reloaded, picked up or was supplied. */
    public static void onAmmunitionInvoke(Player player, AbilityInvokeEntry invoke) throws Exception {
        var update = AbilityMetaUpdateTpsWeaponAmmunition.parseFrom(invoke.getAbilityData());
        // Until the fields are settled, every one of these is worth seeing whole.
        Grasscutter.getLogger()
                .info(
                        "TPS ammo invoke: entity {} instance {} hex {} -> {}",
                        invoke.getEntityId(),
                        invoke.getHead().getInstancedAbilityId(),
                        Utils.bytesToHex(invoke.getAbilityData().toByteArray()),
                        update.toString().replace('\n', ' '));
        for (var change : update.getAmmunitionListList()) {
            int reserve = changeReserve(player, change.getAmmunitionConfigId(), change.getChangeCount());
            Grasscutter.getLogger()
                    .debug(
                            "TPS ammunition {} {} {} -> {}",
                            update.getUpdateType(),
                            change.getAmmunitionConfigId(),
                            change.getChangeCount(),
                            reserve);
        }
        // Fields 12/14 of the accessory entries are not identified yet; log them so a capture can
        // settle which one is the magazine count.
        for (var accessory : update.getAccessoryListList()) {
            Grasscutter.getLogger()
                    .debug(
                            "TPS ammunition slot type={} ammunition={} f12={} f14={} (entity {})",
                            accessory.getAmmunitionType(),
                            accessory.getAmmunitionConfigId(),
                            accessory.getKAPHEDKKFGK(),
                            accessory.getMHFBBNKOBPK(),
                            invoke.getEntityId());
        }
    }
}
