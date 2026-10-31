package emu.grasscutter.game.ability;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.actions.ActionReduceHPDebts;
import emu.grasscutter.game.ability.actions.ActionSetGlobalValue;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AvatarFightPropUpdateNotifyOuterClass.AvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@ExtendWith(ServerResourceFixture.class)
final class ArlecchinoGlobalSyncTest {
    private static final String BOND_KEY = "Cur_HPDebts";
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_960_000);

    @ParameterizedTest(name = "clear={0}, source={1}")
    @MethodSource("metaSources")
    void clientResetPreservesBondAndOnlyOwnBurstPrearms(boolean clear, Source source)
            throws Exception {
        try (var fixture = fixture(10000096)) {
            Ability ability = sourceAbility(fixture, source);
            int abilityId = source == Source.UNKNOWN ? 0 : 1;
            if (abilityId != 0) fixture.entity.getInstancedAbilities().add(ability);

            invokeMeta(fixture, clear, BOND_KEY, abilityId, 0);

            fixture.assertBond(3_000f);
            fixture.assertUiBond(3_000f);
            assertEquals(
                    source == Source.OWN_BURST,
                    ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void modifierSourceCanPrearmWithoutAnAbilityId(boolean clear) throws Exception {
        try (var fixture = fixture(10000096)) {
            var ability = sourceAbility(fixture, Source.OWN_BURST);
            fixture.entity
                    .getInstancedModifiers()
                    .put(7, new AbilityModifierController(ability, ability.getData(), null));

            invokeMeta(fixture, clear, BOND_KEY, 0, 7);

            fixture.assertBond(3_000f);
            fixture.assertUiBond(3_000f);
            assertTrue(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nonBurstModifierOverridesTheHeadBurstAbility(boolean clear) throws Exception {
        try (var fixture = fixture(10000096)) {
            fixture.entity.getInstancedAbilities().add(sourceAbility(fixture, Source.OWN_BURST));
            var modifierAbility = sourceAbility(fixture, Source.NORMAL_ATTACK);
            fixture.entity
                    .getInstancedModifiers()
                    .put(
                            7,
                            new AbilityModifierController(
                                    modifierAbility, modifierAbility.getData(), null));

            invokeMeta(fixture, clear, BOND_KEY, 1, 7);

            fixture.assertBond(3_000f);
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unknownResetKeepsAnAlreadyPendingBurst(boolean clear) throws Exception {
        try (var fixture = fixture(10000096)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            Object pending = pending(fixture.entity);
            fixture.packets.clear();

            invokeMeta(fixture, clear, BOND_KEY, 0, 0);

            fixture.assertBond(3_000f);
            fixture.assertUiBond(3_000f);
            assertSame(pending, pending(fixture.entity));
        }
    }

    @ParameterizedTest(name = "clear={0}, key={1}")
    @MethodSource("aliasKeys")
    void allBondAliasesAreRepinnedWithoutStartingABurst(boolean clear, String key)
            throws Exception {
        try (var fixture = fixture(10000096)) {
            invokeMeta(fixture, clear, key, 0, 0);

            fixture.assertBond(3_000f);
            assertEquals(3_000f, fixture.entity.getGlobalAbilityValues().get(key));
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
        }
    }

    @ParameterizedTest
    @EnumSource(Source.class)
    void setGlobalActionOnlyPrearmsForTheAvatarsOwnBurst(Source source) throws Exception {
        try (var fixture = fixture(10000096)) {
            assertTrue(
                    new ActionSetGlobalValue()
                            .execute(
                                    sourceAbility(fixture, source),
                                    setZero(),
                                    ByteString.EMPTY,
                                    fixture.entity));

            fixture.assertBond(3_000f);
            fixture.assertUiBond(3_000f);
            assertEquals(
                    source == Source.OWN_BURST,
                    ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
        }
    }

    @Test
    void unknownSetGlobalActionKeepsTheExistingPendingCast() throws Exception {
        try (var fixture = fixture(10000096)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            Object pending = pending(fixture.entity);

            assertTrue(
                    new ActionSetGlobalValue()
                            .execute(
                                    sourceAbility(fixture, Source.UNKNOWN),
                                    setZero(),
                                    ByteString.EMPTY,
                                    fixture.entity));

            fixture.assertBond(3_000f);
            assertSame(pending, pending(fixture.entity));
        }
    }

    @ParameterizedTest
    @EnumSource(Source.class)
    void largeNonBurstRepaymentIsAppliedImmediatelyWhileOwnBurstIsDeferred(Source source)
            throws Exception {
        try (var fixture = fixture(10000096)) {
            var action = new AbilityModifierAction();
            action.ratio = new DynamicFloat(2_900f);

            assertTrue(
                    new ActionReduceHPDebts()
                            .execute(
                                    sourceAbility(fixture, source),
                                    action,
                                    ByteString.EMPTY,
                                    fixture.entity));

            boolean ownBurst = source == Source.OWN_BURST;
            fixture.assertBond(ownBurst ? 3_000f : 100f);
            fixture.assertUiBond(ownBurst ? 3_000f : 100f);
            assertEquals(ownBurst, ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
        }
    }

    @Test
    void arlecchinosBurstAppliedToATeammateDoesNotStartItsSettlement() throws Exception {
        try (var arlecchino = fixture(10000096);
                var teammate = fixture(10000032)) {
            var ability = sourceAbility(arlecchino, Source.OWN_BURST);
            assertTrue(
                    new ActionSetGlobalValue()
                            .execute(ability, setZero(), ByteString.EMPTY, teammate.entity));
            assertFalse(ArlecchinoBurstBoL.isPending(teammate.entity.getId()));
            var reduce = new AbilityModifierAction();
            reduce.ratio = new DynamicFloat(2_900f);
            assertTrue(
                    new ActionReduceHPDebts()
                            .execute(ability, reduce, ByteString.EMPTY, teammate.entity));
            teammate.assertBond(100f);
            assertFalse(ArlecchinoBurstBoL.isPending(teammate.entity.getId()));
            assertFalse(ArlecchinoBurstBoL.isPending(arlecchino.entity.getId()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unrelatedGlobalValuesStillFollowNormalUpdates(boolean clear) throws Exception {
        try (var fixture = fixture(10000096)) {
            fixture.entity.getGlobalAbilityValues().put("UnrelatedValue", 17f);
            invokeMeta(fixture, clear, "UnrelatedValue", 0, 0);

            if (clear)
                assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("UnrelatedValue"));
            else assertEquals(0f, fixture.entity.getGlobalAbilityValues().get("UnrelatedValue"));
            fixture.assertBond(3_000f);
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"clear", "float", "set"})
    void ordinaryResetDoesNotClearBondWhenTheMissTimeoutHasPassed(String path) throws Exception {
        try (var fixture = fixture(10000096)) {
            if (path.equals("set")) {
                new ActionSetGlobalValue()
                        .execute(
                                sourceAbility(fixture, Source.UNKNOWN),
                                setZero(),
                                ByteString.EMPTY,
                                fixture.entity);
            } else {
                invokeMeta(fixture, path.equals("clear"), BOND_KEY, 0, 0);
            }
            expireAnyPendingCast(fixture.entity);

            ArlecchinoBurstBoL.onTick(fixture.player);

            fixture.assertBond(3_000f);
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
        }
    }

    private static Stream<Arguments> metaSources() {
        return Stream.of(false, true)
                .flatMap(
                        clear ->
                                Stream.of(Source.values())
                                        .map(source -> Arguments.of(clear, source)));
    }

    private static Stream<Arguments> aliasKeys() {
        return Stream.of(false, true)
                .flatMap(
                        clear ->
                                Stream.of("_HPDebts", "_ABILITY_Cur_HPDebts")
                                        .map(key -> Arguments.of(clear, key)));
    }

    private enum Source {
        UNKNOWN,
        NORMAL_ATTACK,
        TEAMMATE_BURST,
        OWN_BURST,
        MISLABELED_TEAMMATE_BURST
    }

    private static Ability sourceAbility(Fixture fixture, Source source) throws Exception {
        boolean teammate =
                source == Source.TEAMMATE_BURST || source == Source.MISLABELED_TEAMMATE_BURST;
        EntityAvatar owner =
                teammate ? avatar(fixture.player, fixture.scene, 10000032) : fixture.entity;
        String name =
                switch (source) {
                    case UNKNOWN -> "";
                    case NORMAL_ATTACK -> "Avatar_Arlecchino_NormalAttack";
                    case TEAMMATE_BURST -> "Avatar_Bennett_ElementalBurst";
                    case OWN_BURST, MISLABELED_TEAMMATE_BURST -> "Avatar_Arlecchino_ElementalBurst";
                };
        var ability = allocate(Ability.class);
        var data = new AbilityData();
        data.abilityName = name;
        set(ability, Ability.class, "owner", owner);
        set(ability, Ability.class, "data", data);
        set(ability, Ability.class, "abilitySpecials", new Object2FloatOpenHashMap<String>());
        return ability;
    }

    private static AbilityModifierAction setZero() {
        var action = new AbilityModifierAction();
        action.key = BOND_KEY;
        action.ratio = new DynamicFloat(0f);
        return action;
    }

    private static void invokeMeta(
            Fixture fixture, boolean clear, String key, int abilityId, int modifierId)
            throws Exception {
        var scalar =
                AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder().setStr(key))
                        .setFloatValue(0f)
                        .build();
        var invoke =
                AbilityInvokeEntry.newBuilder()
                        .setEntityId(fixture.entity.getId())
                        .setArgumentType(
                                clear
                                        ? AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE
                                        : AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                        .setHead(
                                AbilityInvokeEntryHead.newBuilder()
                                        .setInstancedAbilityId(abilityId)
                                        .setInstancedModifierId(modifierId))
                        .setAbilityData(scalar.toByteString())
                        .build();
        var method =
                AbilityManager.class.getDeclaredMethod(
                        clear ? "handleClearGlobalFloatValue" : "handleGlobalFloatValue",
                        AbilityInvokeEntry.class);
        method.setAccessible(true);
        try {
            method.invoke(fixture.manager, invoke);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Real ability handler failed", exception.getCause());
        }
    }

    private static Fixture fixture(int avatarId) throws Exception {
        var fixture = new Fixture();
        fixture.player = allocate(RecordingPlayer.class);
        fixture.scene = allocate(RecordingScene.class);
        var world = allocate(RecordingWorld.class);
        fixture.scene.world = world;
        fixture.scene.player = fixture.player;
        fixture.scene.packets = world.packets = fixture.player.packets = fixture.packets;
        world.player = fixture.player;
        fixture.player.scene = fixture.scene;
        fixture.entity = avatar(fixture.player, fixture.scene, avatarId);
        fixture.scene.entity = fixture.entity;
        fixture.manager = new AbilityManager(fixture.player);
        return fixture;
    }

    private static EntityAvatar avatar(Player owner, Scene scene, int avatarId) throws Exception {
        var entity = allocate(EntityAvatar.class);
        var avatar = allocate(Avatar.class);
        entity.setId(ENTITY_IDS.incrementAndGet());
        set(avatar, Avatar.class, "avatarId", avatarId);
        set(avatar, Avatar.class, "fightProperties", new Int2FloatOpenHashMap());
        avatar.setOwner(owner);
        set(entity, EntityAvatar.class, "avatar", avatar);
        set(entity, GameEntity.class, "scene", scene);
        set(
                entity,
                GameEntity.class,
                "globalAbilityValues",
                new ConcurrentHashMap<String, Float>());
        set(entity, GameEntity.class, "instancedModifiers", new ConcurrentHashMap<>());
        set(entity, GameEntity.class, "instancedAbilities", new CopyOnWriteArrayList<Ability>());
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 2_000f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10_000f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 3_000f);
        for (String key : new String[] {BOND_KEY, "_HPDebts", "_ABILITY_Cur_HPDebts"})
            entity.getGlobalAbilityValues().put(key, 3_000f);
        return entity;
    }

    private static Object pending(EntityAvatar entity) throws Exception {
        var field = ArlecchinoBurstBoL.class.getDeclaredField("PENDING");
        field.setAccessible(true);
        Object pending = ((Map<?, ?>) field.get(null)).get(entity.getId());
        assertNotNull(pending);
        return pending;
    }

    // Make a mistakenly created cast due without sleeping or inventing a cast in correct code.
    private static void expireAnyPendingCast(EntityAvatar entity) throws Exception {
        var field = ArlecchinoBurstBoL.class.getDeclaredField("PENDING");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var pending = (Map<Integer, Object>) field.get(null);
        Object existing = pending.get(entity.getId());
        if (existing == null) return;
        var constructor =
                existing.getClass()
                        .getDeclaredConstructor(
                                EntityAvatar.class,
                                long.class,
                                long.class,
                                float.class,
                                float.class,
                                long.class);
        constructor.setAccessible(true);
        pending.put(
                entity.getId(),
                constructor.newInstance(
                        entity, System.currentTimeMillis() - 10_000L, 0L, -1f, 3_000f, 0L));
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object instance, Class<?> owner, String name, Object value)
            throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class Fixture implements AutoCloseable {
        final ArrayList<BasePacket> packets = new ArrayList<>();
        EntityAvatar entity;
        RecordingPlayer player;
        RecordingScene scene;
        AbilityManager manager;

        void assertBond(float debt) {
            assertEquals(2_000f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            assertEquals(debt, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            assertEquals(debt, entity.getGlobalAbilityValues().get(BOND_KEY));
            assertEquals(debt, entity.getGlobalAbilityValues().get("_HPDebts"));
        }

        void assertUiBond(float debt) throws Exception {
            var updates =
                    packets.stream()
                            .filter(PacketAvatarFightPropUpdateNotify.class::isInstance)
                            .toList();
            assertFalse(
                    updates.isEmpty(), "The client Bond bar must receive an authoritative update");
            var update =
                    AvatarFightPropUpdateNotify.parseFrom(
                            updates.get(updates.size() - 1).getData());
            assertEquals(entity.getAvatar().getGuid(), update.getAvatarGuid());
            assertEquals(
                    debt,
                    update.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_HP_DEBTS.getId()));
        }

        @Override
        public void close() {
            ArlecchinoBurstBoL.clearEntityState(entity.getId());
            ArlecchinoBoLUtil.clearEntityState(entity.getId());
        }
    }

    private static final class RecordingPlayer extends Player {
        ArrayList<BasePacket> packets;
        Scene scene;

        @Override
        public Scene getScene() {
            return scene;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static final class RecordingScene extends Scene {
        ArrayList<BasePacket> packets;
        World world;
        Player player;
        GameEntity entity;

        private RecordingScene() {
            super(null, null);
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public Player getHost() {
            return player;
        }

        @Override
        public GameEntity getEntityById(int id) {
            return entity != null && entity.getId() == id ? entity : null;
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static final class RecordingWorld extends World {
        ArrayList<BasePacket> packets;
        Player player;

        private RecordingWorld() {
            super((Player) null);
        }

        @Override
        public Player getHost() {
            return player;
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }
}
