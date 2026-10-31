package emu.grasscutter.game.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.ItemData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class InventoryRewardAdmissionTest {
    private static final Gson GSON = new Gson();

    private static InventoryAddResult accepted(java.util.Collection<GameItem> items) {
        var rows = new java.util.ArrayList<InventoryAddResult.Entry>();
        for (var item : items) {
            rows.add(new InventoryAddResult.Entry(
                    item.getItemId(), item.getCount(), item.getCount(),
                    InventoryAddResult.Status.ADDED));
        }
        return new InventoryAddResult(rows);
    }

    private static GameItem item(int id, ItemType type, int count, int stackLimit) {
        ItemData data = GSON.fromJson(
                "{\"id\":" + id + ",\"itemType\":\"" + type.name() +
                "\",\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":" + stackLimit + "}",
                ItemData.class);
        var item = new GameItem();
        item.setItemId(id);
        item.setItemData(data);
        item.setCount(count);
        return item;
    }

    @Test
    void rejectsFullReliquaryInventoryBeforePayment() {
        var relics = new EquipInventoryTab(1);
        relics.onAddItem(item(1001, ItemType.ITEM_RELIQUARY, 1, 1));
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(item(1002, ItemType.ITEM_RELIQUARY, 1, 1)),
                type -> type == ItemType.ITEM_RELIQUARY ? relics : null));
    }

    @Test
    void reservesAllEquipmentSlotsInOneRoll() {
        var relics = new EquipInventoryTab(1);
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(
                        item(1001, ItemType.ITEM_RELIQUARY, 1, 1),
                        item(1002, ItemType.ITEM_RELIQUARY, 1, 1)),
                type -> type == ItemType.ITEM_RELIQUARY ? relics : null));
    }

    @Test
    void detectsCombinedStackOverflow() {
        var materials = new MaterialInventoryTab(2);
        materials.onAddItem(item(2001, ItemType.ITEM_MATERIAL, 7, 10));
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(
                        item(2001, ItemType.ITEM_MATERIAL, 2, 10),
                        item(2001, ItemType.ITEM_MATERIAL, 2, 10)),
                type -> type == ItemType.ITEM_MATERIAL ? materials : null));
    }

    @Test
    void rejectsMoreNewMaterialIdsThanAvailableSlots() {
        var materials = new MaterialInventoryTab(1);
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(
                        item(2001, ItemType.ITEM_MATERIAL, 1, 10),
                        item(2002, ItemType.ITEM_MATERIAL, 1, 10)),
                type -> type == ItemType.ITEM_MATERIAL ? materials : null));
    }

    @Test
    void permitsExistingMaterialStackInFullTabAndVirtualReward() {
        var materials = new MaterialInventoryTab(1);
        materials.onAddItem(item(2001, ItemType.ITEM_MATERIAL, 3, 10));
        assertTrue(InventoryRewardAdmission.canAccept(
                List.of(
                        item(2001, ItemType.ITEM_MATERIAL, 4, 10),
                        item(202, ItemType.ITEM_VIRTUAL, 100, 1)),
                Map.of(ItemType.ITEM_MATERIAL, (InventoryTab) materials)::get));
    }

    @Test
    void rejectsCombinedCurrencyOverflowBeforeAuthorization() {
        var events = new java.util.ArrayList<String>();
        var result = InventoryRewardAdmission.grantIfAccepted(
                List.of(
                        item(202, ItemType.ITEM_VIRTUAL, 15, 1),
                        item(202, ItemType.ITEM_VIRTUAL, 10, 1)),
                type -> null,
                id -> id == 202 ? Integer.MAX_VALUE - 20 : 0,
                () -> { events.add("charge"); return true; },
                () -> events.add("confirm"),
                InventoryRewardAdmissionTest::accepted);
        assertFalse(result.allAccepted());
        assertEquals(InventoryAddResult.Status.STACK_LIMIT, result.entries().get(0).status());
        assertTrue(events.isEmpty());
    }

    @Test
    void rejectsResinAndLegendaryKeyOverflowBeforeAuthorization() {
        for (int virtualId : List.of(106, 107)) {
            var events = new java.util.ArrayList<String>();
            var result = InventoryRewardAdmission.grantIfAccepted(
                    List.of(item(virtualId, ItemType.ITEM_VIRTUAL, 3, 1)),
                    type -> null,
                    id -> id == virtualId ? Integer.MAX_VALUE - 2 : 0,
                    () -> { events.add("charge"); return true; },
                    () -> events.add("confirm"),
                    InventoryRewardAdmissionTest::accepted);
            assertEquals(InventoryAddResult.Status.STACK_LIMIT, result.entries().get(0).status());
            assertTrue(events.isEmpty());
        }
    }

    @Test
    void acceptsCurrencyWithinIntegerCapacity() {
        var result = InventoryRewardAdmission.grantIfAccepted(
                List.of(item(201, ItemType.ITEM_VIRTUAL, 10, 1)),
                type -> null,
                id -> Integer.MAX_VALUE - 10,
                () -> true,
                () -> {},
                InventoryRewardAdmissionTest::accepted);
        assertTrue(result.allAccepted());
    }

    @Test
    void rejectsUnknownVirtualReward() {
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(item(99999, ItemType.ITEM_VIRTUAL, 1, 1)),
                type -> null));
    }

    @Test
    void resultDistinguishesPartialFromTotalDelivery() {
        var result = new InventoryAddResult(List.of(
                new InventoryAddResult.Entry(202, 100, 100, InventoryAddResult.Status.ADDED),
                new InventoryAddResult.Entry(35611, 1, 0, InventoryAddResult.Status.CAPACITY_FULL)));
        assertFalse(result.allAccepted());
        assertTrue(result.partiallyAccepted());
        assertEquals(0, result.entries().get(1).added());
    }

    @Test
    void constructorKeepsOversizedQuantityForAdmissionToReject() {
        ItemData data = GSON.fromJson(
                "{\"id\":2001,\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":10}",
                ItemData.class);
        var oversized = new GameItem(data, 12);
        assertEquals(12, oversized.getCount());
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(oversized), type -> new MaterialInventoryTab(5)));
    }

    @Test
    void duplicateItemReferenceFailsBeforePayment() {
        var materials = new MaterialInventoryTab(4);
        var shared = item(2001, ItemType.ITEM_MATERIAL, 1, 10);
        var events = new java.util.ArrayList<String>();
        var result = InventoryRewardAdmission.grantIfAccepted(
                List.of(shared, shared),
                type -> type == ItemType.ITEM_MATERIAL ? materials : null,
                ignored -> 0,
                () -> { events.add("authorize"); return true; },
                () -> events.add("confirmed"),
                items -> { events.add("grant"); return accepted(items); });
        assertFalse(result.allAccepted());
        assertEquals(InventoryAddResult.Status.INVALID_ITEM, result.entries().get(0).status());
        assertTrue(events.isEmpty());
    }

    @Test
    void rejectedBatchReportsSpecificReason() {
        var relics = new EquipInventoryTab(1);
        relics.onAddItem(item(1001, ItemType.ITEM_RELIQUARY, 1, 1));
        var result = InventoryRewardAdmission.grantIfAccepted(
                List.of(item(1002, ItemType.ITEM_RELIQUARY, 1, 1)),
                type -> type == ItemType.ITEM_RELIQUARY ? relics : null,
                ignored -> 0,
                () -> true,
                () -> {},
                InventoryRewardAdmissionTest::accepted);
        assertEquals(InventoryAddResult.Status.CAPACITY_FULL, result.entries().get(0).status());
        assertFalse(result.allAccepted());
    }

    @Test
    void fullBagDoesNotInvokeAuthorizationOrGrant() {
        var relics = new EquipInventoryTab(1);
        relics.onAddItem(item(1001, ItemType.ITEM_RELIQUARY, 1, 1));
        var events = new java.util.ArrayList<String>();
        assertFalse(InventoryRewardAdmission.grantIfAccepted(
                List.of(item(1002, ItemType.ITEM_RELIQUARY, 1, 1)),
                type -> type == ItemType.ITEM_RELIQUARY ? relics : null,
                ignored -> 0,
                () -> { events.add("authorize"); return true; },
                () -> events.add("confirmed"),
                items -> { events.add("grant"); return accepted(items); }).allAccepted());
        assertTrue(events.isEmpty());
    }

    @Test
    void rejectedAuthorizationDoesNotConfirmOrGrant() {
        var materials = new MaterialInventoryTab(2);
        var events = new java.util.ArrayList<String>();
        assertFalse(InventoryRewardAdmission.grantIfAccepted(
                List.of(item(2001, ItemType.ITEM_MATERIAL, 1, 10)),
                type -> type == ItemType.ITEM_MATERIAL ? materials : null,
                ignored -> 0,
                () -> { events.add("authorize"); return false; },
                () -> events.add("confirmed"),
                items -> { events.add("grant"); return accepted(items); }).allAccepted());
        assertEquals(List.of("authorize"), events);
    }

    @Test
    void successfulGrantConfirmsChargeBeforeWriting() {
        var materials = new MaterialInventoryTab(2);
        var events = new java.util.ArrayList<String>();
        assertTrue(InventoryRewardAdmission.grantIfAccepted(
                List.of(item(2001, ItemType.ITEM_MATERIAL, 1, 10)),
                type -> type == ItemType.ITEM_MATERIAL ? materials : null,
                ignored -> 0,
                () -> { events.add("authorize"); return true; },
                () -> events.add("confirmed"),
                items -> { events.add("grant"); return accepted(items); }).allAccepted());
        assertEquals(List.of("authorize", "confirmed", "grant"), events);
    }

    @Test
    void grantExceptionStillHappensAfterPaymentConfirmation() {
        var materials = new MaterialInventoryTab(2);
        var events = new java.util.ArrayList<String>();
        assertThrows(IllegalStateException.class, () -> InventoryRewardAdmission.grantIfAccepted(
                List.of(item(2001, ItemType.ITEM_MATERIAL, 1, 10)),
                type -> type == ItemType.ITEM_MATERIAL ? materials : null,
                ignored -> 0,
                () -> { events.add("authorize"); return true; },
                () -> events.add("confirmed"),
                items -> { throw new IllegalStateException("write failed"); }));
        assertEquals(List.of("authorize", "confirmed"), events);
    }

    private static GameItem directUseCard(int id, String materialType, int count) {
        ItemData data = GSON.fromJson(
                "{\"id\":" + id + ",\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\""
                        + materialType + "\",\"useOnGain\":true,\"itemUse\":[{\"useOp\":"
                        + "\"ITEM_USE_GAIN_NAME_CARD\",\"useParam\":[\"210001\"]}]}",
                ItemData.class);
        data.onLoad();
        GameItem card = new GameItem();
        card.setItemId(id);
        card.setItemData(data);
        card.setCount(count);
        return card;
    }

    @Test
    void admitsSingleUseUnlockCardsWithoutReservingBagSlots() {
        GameItem namecard = directUseCard(210001, "MATERIAL_NAMECARD", 1);
        var events = new java.util.ArrayList<String>();
        var result = InventoryRewardAdmission.grantIfAccepted(
                List.of(namecard), ignored -> null, ignored -> 0,
                () -> { events.add("authorize"); return true; },
                () -> events.add("confirmed"),
                items -> { events.add("grant"); return accepted(items); });
        assertTrue(result.allAccepted());
        assertEquals(List.of("authorize", "confirmed", "grant"), events);
    }

    @Test
    void rejectsUnsupportedOrMultiCountDirectUseBeforeClaim() {
        GameItem multi = directUseCard(210001, "MATERIAL_NAMECARD", 2);
        GameItem unrelated = directUseCard(210002, "MATERIAL_NONE", 1);
        GameItem wrongAction = directUseCard(210003, "MATERIAL_AVATAR", 1);
        var events = new java.util.ArrayList<String>();
        for (GameItem rejected : List.of(multi, unrelated, wrongAction)) {
            var result = InventoryRewardAdmission.grantIfAccepted(
                    List.of(rejected), ignored -> null, ignored -> 0,
                    () -> { events.add("charge"); return true; },
                    () -> events.add("confirm"), InventoryRewardAdmissionTest::accepted);
            assertFalse(result.allAccepted());
        }
        assertTrue(events.isEmpty());
    }
}
