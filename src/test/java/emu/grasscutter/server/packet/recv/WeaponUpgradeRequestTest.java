package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.weapon.WeaponLevelData;
import emu.grasscutter.data.excels.weapon.WeaponPromoteData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.ItemUseAddWeaponExp;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.CalcWeaponUpgradeReturnItemsReqOuterClass.CalcWeaponUpgradeReturnItemsReq;
import emu.grasscutter.net.proto.CalcWeaponUpgradeReturnItemsRspOuterClass.CalcWeaponUpgradeReturnItemsRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.StoreItemChangeNotifyOuterClass.StoreItemChangeNotify;
import emu.grasscutter.net.proto.WeaponUpgradeReqOuterClass.WeaponUpgradeReq;
import emu.grasscutter.net.proto.WeaponUpgradeRspOuterClass.WeaponUpgradeRsp;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(ServerResourceFixture.class)
@ResourceLock("GameData")
final class WeaponUpgradeRequestTest {
    private static final int UPGRADE_OPCODE = 21801;
    private static final int PREVIEW_OPCODE = 7145;
    private static final int WEAPON_ID = 11501;
    private static final int SMALL_ORE = 104011;
    private static final int LARGE_ORE = 104013;
    private static final int MORA = 202;
    private static final long TARGET = 0x100000001L;
    private static final long FOOD = 0x100000002L;
    private static final long SECOND_FOOD = 0x100000003L;
    private static final Gson GSON = new Gson();

    private Int2ObjectMap<ItemData> previousItems;
    private Int2ObjectMap<WeaponLevelData> previousLevels;
    private Int2ObjectMap<WeaponPromoteData> previousPromotes;
    private Int2IntMap refundMaterials;
    private Int2IntMap previousRefunds;
    private RecordingPlayer player;
    private RecordingSession session;
    private RecordingInventory inventory;
    private RecordingGameItem weapon;
    private GameServerPacketHandler dispatcher;

    @BeforeEach
    void prepareInventoryAndRealHandlers() throws Exception {
        previousItems = new Int2ObjectOpenHashMap<>(GameData.getItemDataMap());
        previousLevels = new Int2ObjectOpenHashMap<>(GameData.getWeaponLevelDataMap());
        previousPromotes = new Int2ObjectOpenHashMap<>(GameData.getWeaponPromoteDataMap());
        refundMaterials =
                (Int2IntMap) field(InventorySystem.class, "weaponRefundMaterials").get(null);
        previousRefunds = new Int2IntArrayMap(refundMaterials);
        GameData.getItemDataMap().clear();
        GameData.getWeaponLevelDataMap().clear();
        GameData.getWeaponPromoteDataMap().clear();
        refundMaterials.clear();

        var weaponData =
                GSON.fromJson(
                        "{\"id\":11501,\"itemType\":\"ITEM_WEAPON\",\"rankLevel\":5,"
                                + "\"weaponPromoteId\":11501,\"weaponBaseExp\":200}",
                        ItemData.class);
        GameData.getItemDataMap().put(WEAPON_ID, weaponData);
        addOre(SMALL_ORE, 100);
        addOre(LARGE_ORE, 1000);
        for (int level = 1; level <= 20; level++) {
            var data =
                    GSON.fromJson(
                            "{\"level\":" + level + ",\"requiredExps\":[1000,1000,1000,1000,1000]}",
                            WeaponLevelData.class);
            GameData.getWeaponLevelDataMap().put(level, data);
        }
        var promote =
                GSON.fromJson(
                        "{\"weaponPromoteId\":11501,\"promoteLevel\":0,\"unlockMaxLevel\":20}",
                        WeaponPromoteData.class);
        GameData.getWeaponPromoteDataMap().put(promote.getId(), promote);

        player = allocate(RecordingPlayer.class);
        player.packets = new ArrayList<>();
        inventory = allocate(RecordingInventory.class);
        inventory.items = new HashMap<>();
        inventory.counts = new HashMap<>(Map.of(SMALL_ORE, 10, LARGE_ORE, 10, MORA, 10000));
        player.inventory = inventory;
        weapon = item(TARGET, 1, 0, 0);

        var server = allocate(GameServer.class);
        var system = new InventorySystem(server);
        field(GameServer.class, "inventorySystem").set(server, system);
        session = allocate(RecordingSession.class);
        session.player = player;
        session.server = server;
        session.setState(SessionState.ACTIVE);
        dispatcher = allocate(GameServerPacketHandler.class);
        field(GameServerPacketHandler.class, "handlers")
                .set(dispatcher, new Int2ObjectOpenHashMap<PacketHandler>());
        dispatcher.registerPacketHandler(HandlerWeaponUpgradeReq.class);
        dispatcher.registerPacketHandler(HandlerCalcWeaponUpgradeReturnItemsReq.class);
    }

    @AfterEach
    void restoreResourceTables() {
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(previousItems);
        GameData.getWeaponLevelDataMap().clear();
        GameData.getWeaponLevelDataMap().putAll(previousLevels);
        GameData.getWeaponPromoteDataMap().clear();
        GameData.getWeaponPromoteDataMap().putAll(previousPromotes);
        refundMaterials.clear();
        refundMaterials.putAll(previousRefunds);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeWireFieldsDecodeTargetPackedFoodAndOreCount(boolean preview) throws Exception {
        var payload = request(preview, List.of(FOOD, SECOND_FOOD), SMALL_ORE, 3);
        assertEquals(UPGRADE_OPCODE, PacketOpcodes.WeaponUpgradeReq);
        assertEquals(PREVIEW_OPCODE, PacketOpcodes.CalcWeaponUpgradeReturnItemsReq);
        if (preview) {
            var req = CalcWeaponUpgradeReturnItemsReq.parseFrom(payload);
            assertEquals(TARGET, req.getTargetWeaponGuid());
            assertEquals(List.of(FOOD, SECOND_FOOD), req.getFoodWeaponGuidListList());
            assertEquals(List.of(param(SMALL_ORE, 3)), req.getItemParamListList());
            assertTrue(req.getUnknownFields().asMap().isEmpty());
        } else {
            var req = WeaponUpgradeReq.parseFrom(payload);
            assertEquals(TARGET, req.getTargetWeaponGuid());
            assertEquals(List.of(FOOD, SECOND_FOOD), req.getFoodWeaponGuidListList());
            assertEquals(List.of(param(SMALL_ORE, 3)), req.getItemParamListList());
            assertTrue(req.getUnknownFields().asMap().isEmpty());
        }
    }

    @Test
    void nativePreviewRoutesAndReturnsLeftoversWithoutChangingInventory() throws Exception {
        weapon.setLevel(19);
        weapon.setExp(500);
        weapon.setTotalExp(18500);
        var food = item(FOOD, 2, 0, 500);
        var counts = Map.copyOf(inventory.counts);

        route(true, List.of(FOOD), SMALL_ORE, 3);

        assertEquals(1, player.packets.size());
        var packet = player.packets.get(0);
        assertEquals(5849, packet.getOpcode());
        assertEquals(PacketOpcodes.CalcWeaponUpgradeReturnItemsRsp, packet.getOpcode());
        var rsp = CalcWeaponUpgradeReturnItemsRsp.parseFrom(packet.getData());
        assertEquals(TARGET, rsp.getTargetWeaponGuid());
        assertEquals(0, rsp.getRetcode());
        assertEquals(List.of(param(SMALL_ORE, 4)), rsp.getItemParamListList());
        var wire = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(List.of(TARGET), wire.getField(10).getVarintList());
        assertEquals(1, wire.getField(3).getLengthDelimitedList().size());
        assertEquals(
                param(SMALL_ORE, 4),
                ItemParam.parseFrom(wire.getField(3).getLengthDelimitedList().get(0)));
        assertFalse(wire.hasField(6));
        assertEquals(19, weapon.getLevel());
        assertEquals(500, weapon.getExp());
        assertEquals(18500, weapon.getTotalExp());
        assertEquals(0, weapon.saves);
        assertEquals(counts, inventory.counts);
        assertSame(food, inventory.items.get(FOOD));
        assertEquals(1, food.getCount());
    }

    @Test
    void nativeUpgradeRoutesOrePaymentPartialExperienceAndItemNotification() throws Exception {
        weapon.setExp(400);
        weapon.setTotalExp(400);

        route(false, List.of(), SMALL_ORE, 3);

        assertEquals(1, weapon.getLevel());
        assertEquals(700, weapon.getExp());
        assertEquals(700, weapon.getTotalExp());
        assertEquals(7, inventory.counts.get(SMALL_ORE));
        assertEquals(9970, inventory.counts.get(MORA));
        assertEquals(1, weapon.saves);
        assertUpgradePackets(1, 1, List.of());
    }

    @Test
    void foodAndOresUpgradeMultipleLevelsWithoutChargingForInheritedExperience() throws Exception {
        var food = item(FOOD, 2, 0, 1000);

        route(false, List.of(FOOD), LARGE_ORE, 3);

        assertEquals(5, weapon.getLevel());
        assertEquals(0, weapon.getExp());
        assertEquals(4000, weapon.getTotalExp());
        assertEquals(7, inventory.counts.get(LARGE_ORE));
        assertEquals(9680, inventory.counts.get(MORA));
        assertFalse(inventory.items.containsKey(FOOD));
        assertEquals(0, food.getCount());
        assertEquals(1, weapon.saves);
        assertUpgradePackets(1, 5, List.of());
    }

    @Test
    void levelCapRefundMatchesThePreviewAndStopsAtTheAscensionLimit() throws Exception {
        weapon.setLevel(19);
        weapon.setExp(500);
        weapon.setTotalExp(18500);
        route(true, List.of(), LARGE_ORE, 2);
        var preview = CalcWeaponUpgradeReturnItemsRsp.parseFrom(player.packets.get(0).getData());
        var leftovers = List.of(param(LARGE_ORE, 1), param(SMALL_ORE, 5));
        assertEquals(leftovers, preview.getItemParamListList());
        player.packets.clear();

        route(false, List.of(), LARGE_ORE, 2);

        assertEquals(20, weapon.getLevel());
        assertEquals(0, weapon.getExp());
        assertEquals(19000, weapon.getTotalExp());
        assertEquals(9, inventory.counts.get(LARGE_ORE));
        assertEquals(15, inventory.counts.get(SMALL_ORE));
        assertEquals(9800, inventory.counts.get(MORA));
        assertEquals(1, weapon.saves);
        assertUpgradePackets(19, 20, leftovers);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void insufficientOresOrMoraLeaveTheWeaponAndFoodUntouched(boolean insufficientMora)
            throws Exception {
        var food = item(FOOD, 2, 0, 1000);
        inventory.counts.put(insufficientMora ? MORA : LARGE_ORE, insufficientMora ? 100 : 2);
        var counts = Map.copyOf(inventory.counts);

        route(false, List.of(FOOD), LARGE_ORE, 3);

        assertEquals(1, weapon.getLevel());
        assertEquals(0, weapon.getExp());
        assertEquals(0, weapon.getTotalExp());
        assertEquals(0, weapon.saves);
        assertEquals(counts, inventory.counts);
        assertSame(food, inventory.items.get(FOOD));
        assertEquals(1, food.getCount());
        assertTrue(player.packets.isEmpty());
    }

    private void addOre(int itemId, int exp) throws Exception {
        var data =
                GSON.fromJson(
                        "{\"id\":"
                                + itemId
                                + ",\"itemType\":\"ITEM_MATERIAL\","
                                + "\"materialType\":\"MATERIAL_WEAPON_EXP_STONE\",\"stackLimit\":9999}",
                        ItemData.class);
        field(ItemData.class, "itemUseActions")
                .set(data, List.of(new ItemUseAddWeaponExp(new String[] {Integer.toString(exp)})));
        GameData.getItemDataMap().put(itemId, data);
    }

    private RecordingGameItem item(long guid, int level, int exp, int totalExp) throws Exception {
        var item = new RecordingGameItem(GameData.getItemDataMap().get(WEAPON_ID));
        field(GameItem.class, "guid").set(item, guid);
        item.setLevel(level);
        item.setExp(exp);
        item.setTotalExp(totalExp);
        inventory.items.put(guid, item);
        return item;
    }

    private void route(boolean preview, List<Long> foods, int oreId, int count) throws Exception {
        dispatcher.handle(
                session,
                preview ? PREVIEW_OPCODE : UPGRADE_OPCODE,
                new byte[0],
                request(preview, foods, oreId, count));
    }

    private void assertUpgradePackets(int oldLevel, int newLevel, List<ItemParam> leftovers)
            throws Exception {
        assertEquals(
                List.of(PacketOpcodes.StoreItemChangeNotify, PacketOpcodes.WeaponUpgradeRsp),
                player.packets.stream().map(BasePacket::getOpcode).toList());
        var changed = StoreItemChangeNotify.parseFrom(player.packets.get(0).getData());
        assertEquals(1, changed.getItemListCount());
        assertEquals(TARGET, changed.getItemList(0).getGuid());
        assertEquals(newLevel, changed.getItemList(0).getEquip().getWeapon().getLevel());
        assertEquals(weapon.getExp(), changed.getItemList(0).getEquip().getWeapon().getExp());
        var packet = player.packets.get(1);
        assertEquals(25465, packet.getOpcode());
        var response = WeaponUpgradeRsp.parseFrom(packet.getData());
        assertEquals(0, response.getRetcode());
        assertEquals(TARGET, response.getTargetWeaponGuid());
        assertEquals(oldLevel, response.getOldLevel());
        assertEquals(newLevel, response.getCurLevel());
        assertEquals(leftovers, response.getItemParamListList());
        var wire = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(List.of(TARGET), wire.getField(10).getVarintList());
        assertEquals(List.of((long) oldLevel), wire.getField(14).getVarintList());
        assertEquals(List.of((long) newLevel), wire.getField(2).getVarintList());
        assertEquals(leftovers.size(), wire.getField(4).getLengthDelimitedList().size());
        for (int index = 0; index < leftovers.size(); index++) {
            assertEquals(
                    leftovers.get(index),
                    ItemParam.parseFrom(wire.getField(4).getLengthDelimitedList().get(index)));
        }
        assertFalse(wire.hasField(9));
    }

    // Independent of generated builders so a stale schema cannot make the request test pass.
    private static byte[] request(boolean preview, List<Long> foods, int oreId, int count)
            throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        out.writeUInt64(preview ? 1 : 5, TARGET);
        if (!foods.isEmpty()) {
            var packed = new ByteArrayOutputStream();
            var foodOut = CodedOutputStream.newInstance(packed);
            for (long guid : foods) foodOut.writeUInt64NoTag(guid);
            foodOut.flush();
            out.writeByteArray(preview ? 14 : 15, packed.toByteArray());
        }
        var ore = new ByteArrayOutputStream();
        var oreOut = CodedOutputStream.newInstance(ore);
        oreOut.writeUInt32(1, oreId);
        oreOut.writeUInt32(2, count);
        oreOut.flush();
        out.writeByteArray(preview ? 10 : 8, ore.toByteArray());
        out.flush();
        return bytes.toByteArray();
    }

    private static ItemParam param(int itemId, int count) {
        return ItemParam.newBuilder().setItemId(itemId).setCount(count).build();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        return type.cast(unsafe.allocateInstance(type));
    }

    private static final class RecordingGameItem extends GameItem {
        private int saves;

        private RecordingGameItem(ItemData data) {
            super(data);
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class RecordingInventory extends Inventory {
        private Map<Long, GameItem> items;
        private Map<Integer, Integer> counts;

        private RecordingInventory() {
            super(null);
        }

        @Override
        public GameItem getItemByGuid(long guid) {
            return items.get(guid);
        }

        @Override
        public boolean payItems(Iterable<ItemParamData> costs) {
            var totals = new HashMap<Integer, Integer>();
            costs.forEach(cost -> totals.merge(cost.getId(), cost.getCount(), Integer::sum));
            if (totals.entrySet().stream()
                    .anyMatch(cost -> counts.getOrDefault(cost.getKey(), 0) < cost.getValue())) {
                return false;
            }
            totals.forEach((id, amount) -> counts.merge(id, -amount, Integer::sum));
            return true;
        }

        @Override
        public void removeItems(List<GameItem> foods) {
            foods.forEach(
                    food -> {
                        items.remove(food.getGuid());
                        food.setCount(0);
                    });
        }

        @Override
        public void addItemParams(Collection<ItemParam> refunds) {
            refunds.forEach(
                    refund -> counts.merge(refund.getItemId(), refund.getCount(), Integer::sum));
        }
    }

    private static final class RecordingPlayer extends Player {
        private RecordingInventory inventory;
        private List<BasePacket> packets;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public int getUid() {
            return 900001;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static final class RecordingSession extends GameSession {
        private RecordingPlayer player;
        private GameServer server;

        private RecordingSession() {
            super(null);
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void send(BasePacket packet) {
            player.packets.add(packet);
        }
    }
}
