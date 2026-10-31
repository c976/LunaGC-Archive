/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.entity.gadget.chest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryGrantBuilder;
import emu.grasscutter.game.inventory.InventoryAddPolicy;
import emu.grasscutter.game.entity.EntityItem;
import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.game.reward.RewardClaimFlow;
import java.util.ArrayList;
import java.util.List;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.scripts.data.SceneGadget;

public final class WorldChestLootHelper {
    private WorldChestLootHelper() {
    }

    public static boolean grant(Player player, EntityGadget entityGadget,
            String operationId, Runnable confirmed) {
        var journal = new RewardGrantJournal();
        var key = new RewardGrantJournal.Key(RewardGrantJournal.Source.WORLD_CHEST,
                player.getUid(), operationId);
        if (journal.completed(key)) { confirmed.run(); return true; }
        var props = new java.util.HashMap<String, Object>();
        String chestType = ChestTypeResolver.resolve(entityGadget);
        // Search points and spincrystals share the Chest entity type, but their rewards
        // come from native Lua drop IDs/tags rather than ordinary chest grades.
        boolean nativeInteract = chestType == null && ChestTypeResolver.isNativeInteract(entityGadget);
        if (chestType == null && !nativeInteract) {
            throw new IllegalStateException(
                    "Unrecognized world chest type: gadgetId=" + entityGadget.getGadgetId()
                    + " jsonName=" + (entityGadget.getGadgetData() == null
                            ? null : entityGadget.getGadgetData().getJsonName())
                    + " group=" + entityGadget.getGroupId()
                    + " config=" + entityGadget.getConfigId());
        }
        if (nativeInteract) chestType = "native-interact";
        props.put("type", chestType);
        // Special chest types have no implicit common/luxurious grade.
        if ("normal".equals(chestType)) {
            props.put("grade", resolveTier(entityGadget).name().toLowerCase(java.util.Locale.ROOT));
        }
        props.put("scene", entityGadget.getScene().getId());
        props.put("gadget", entityGadget.getGadgetId());
        props.put("group", entityGadget.getGroupId());
        props.put("config", entityGadget.getConfigId());
        SceneGadget meta = entityGadget.getMetaGadget();
        if (meta != null) {
            props.put("level", meta.level);
        }
        var context = new emu.grasscutter.game.reward.RewardContext(
                entityGadget.getScene().getId(),
                meta != null ? meta.area_id : 0,
                meta != null ? meta.rewardRegion : null);
        emu.grasscutter.game.reward.RewardAreaResolver.resolve(context)
                .addAttributes(props);

        java.util.Map<Integer, Integer> items;
        boolean nativeAutoCollect = false;
        if (nativeInteract) {
            var nativeDrop = player.getServer().getDropSystem().rollNativeInteractDrop(meta);
            nativeAutoCollect = !nativeDrop.fallToGround();
            items = new java.util.LinkedHashMap<>();
            for (var item : nativeDrop.items()) {
                items.merge(item.getItemId(), item.getCount(), Math::addExact);
            }
        } else {
            items = emu.grasscutter.game.reward.RewardConfiguration.current().roll(
                    "chest", props, java.util.concurrent.ThreadLocalRandom.current(), context);
        }
        var automatic = new ArrayList<GameItem>();
        var ground = new ArrayList<EntityItem>();
        for (var entry : items.entrySet()) {
            int id = entry.getKey(), amount = entry.getValue();
            var item = GameData.getItemDataMap().get(id);
            if (item == null || item.getItemType() == null || amount <= 0) {
                throw new IllegalStateException("Invalid configured chest item: " + id + " x" + amount);
            }
            var dropRule = GameData.getDropMaterialDataMap().get(id);
            if (nativeAutoCollect || shouldAutoCollect(item.getItemType(), item.getGadgetId(),
                    dropRule != null && dropRule.isAutoPick())) {
                automatic.addAll(InventoryGrantBuilder.create(item, amount, 1));
            } else {
                ground.addAll(entityGadget.getScene().prepareItemEntities(id, amount, entityGadget));
            }
        }
        if (automatic.isEmpty() && ground.isEmpty()) {
            throw new IllegalStateException("Empty configured chest reward");
        }
        var snapshot = new ArrayList<RewardGrantJournal.Reward>();
        automatic.forEach(item -> snapshot.add(new RewardGrantJournal.Reward(
                item.getItemId(), item.getCount(), item.getLevel())));
        ground.forEach(entity -> snapshot.add(new RewardGrantJournal.Reward(
                entity.getItem().getItemId(), entity.getCount(), entity.getItem().getLevel())));
        boolean accepted = RewardClaimFlow.grant(
                RewardClaimFlow.receipt(journal, key, snapshot, List.of()),
                new RewardClaimFlow.Delivery() {
                    public boolean admit(java.util.function.BooleanSupplier authorize, Runnable claim) {
                        if (automatic.isEmpty()) {
                            if (!authorize.getAsBoolean()) return false;
                            claim.run();
                            return true;
                        }
                        return player.getInventory().addItems(automatic, ActionReason.OpenChest,
                                InventoryAddPolicy.ALL_OR_NOTHING, authorize, claim).allAccepted();
                    }
                    public void publish() { entityGadget.getScene().addEntities(ground); }
                }, () -> true, confirmed, () -> {});
        if (!accepted) {
            Grasscutter.getLogger().warn("World chest reward refused uid={} receipt={}",
                    player.getUid(), key.storageId());
            return false;
        }
        Grasscutter.getLogger().info("World chest rewards uid={} gadget={} type={} itemTypes={}",
                player.getUid(), entityGadget.getGadgetId(), props.get("type"), items.size());
        return true;
    }

    static boolean shouldAutoCollect(ItemType type, int gadgetId, boolean autoPick) {
        return autoPick || (type == ItemType.ITEM_VIRTUAL && gadgetId == 0);
    }

    public static Tier resolveTier(EntityGadget entityGadget) {
        SceneGadget meta = entityGadget.getMetaGadget();
        if (meta != null && meta.chestGrade != null) {
            try {
                return Tier.valueOf(meta.chestGrade.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid source chest grade: " + meta.chestGrade
                        + " gadgetId=" + entityGadget.getGadgetId(), e);
            }
        }
        String jsonName = entityGadget.getGadgetData() == null
                ? null : entityGadget.getGadgetData().getJsonName();
        return resolveTier(entityGadget.getGadgetId(), jsonName);
    }

    static Tier resolveTier(int n, String jsonName) {
        switch (n) {
            case 70210001: {
                return Tier.COMMON;
            }
            case 70210002: {
                return Tier.EXQUISITE;
            }
            case 70210003: {
                return Tier.PRECIOUS;
            }
            case 70210004: 
            case 70210005: 
            case 70210006: {
                return Tier.LUXURIOUS;
            }
        }
        String string = jsonName == null ? "" : jsonName;
        if (WorldChestLootHelper.containsLv(string, 5) || string.contains("Drop_Chest_Lv3")) {
            return Tier.LUXURIOUS;
        }
        if (WorldChestLootHelper.containsLv(string, 4) || WorldChestLootHelper.containsLv(string, 3) || string.contains("Drop_Chest_Lv2")) {
            return Tier.PRECIOUS;
        }
        if (WorldChestLootHelper.containsLv(string, 2) || string.contains("Drop_Chest_Lv1")) {
            return Tier.EXQUISITE;
        }
        if (WorldChestLootHelper.containsLv(string, 1) || string.contains("NormalChest") || string.contains("Rock_Lv1")) {
            return Tier.COMMON;
        }
        throw new IllegalStateException("Unrecognized world chest grade: gadgetId=" + n
                + " jsonName=" + jsonName);
    }

    private static boolean containsLv(String string, int n) {
        return string.contains("_Lv" + n) || string.contains("Lv" + n + "_") || string.endsWith("Lv" + n);
    }

    public enum Tier {
        COMMON,
        EXQUISITE,
        PRECIOUS,
        LUXURIOUS
    }
}
