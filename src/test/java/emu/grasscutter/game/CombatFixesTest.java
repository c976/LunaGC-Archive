package emu.grasscutter.game;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.actions.ActionReviveElemEnergy;
import emu.grasscutter.game.ability.mixins.CostStaminaMixin;
import emu.grasscutter.game.ability.mixins.ReviveElemEnergyMixin;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.managers.SatiationManager;
import emu.grasscutter.game.managers.energy.EnergyManager;
import emu.grasscutter.game.managers.stamina.Consumption;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.player.InvokeHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import emu.grasscutter.net.proto.CombatTypeArgumentOuterClass.CombatTypeArgument;
import emu.grasscutter.net.proto.EntityMoveInfoOuterClass.EntityMoveInfo;
import emu.grasscutter.net.proto.EvtBeingHitInfoOuterClass.EvtBeingHitInfo;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.server.event.entity.EntityDamageEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerCombatInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketAbilityInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketCombatInvocationsNotify;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sun.misc.Unsafe;

@ExtendWith(ServerResourceFixture.class)
class CombatFixesTest {
    // One handler serves every session, as in the server.
    private static final HandlerCombatInvocationsNotify COMBAT = new HandlerCombatInvocationsNotify();

    @Test
    void lockHpAlsoStopsLethalHits() throws Exception {
        var f = fixture();
        f.entity.setLockHP(true);
        f.entity.damage(150, 0, ElementType.None);
        assertEquals(100, f.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void standaloneCombatNotifyAppliesItsHits() throws Exception {
        var f = fixture();
        var attack = AttackResult.newBuilder()
                .setAttackerId(f.entity.getId()).setDefenseId(f.entity.getId()).setDamage(10);
        var hit = EvtBeingHitInfo.newBuilder().setAttackResult(attack).build();
        send(f, CombatInvokeEntry.newBuilder()
                .setArgumentType(CombatTypeArgument.CombatTypeArgument_COMBAT_EVT_BEING_HIT)
                .setCombatData(hit.toByteString()));
        assertEquals(90, f.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void fallDamageUsesOnlyTheLandingSessionsSpeed() throws Exception {
        var a = fixture();
        var b = fixture();
        move(a, MotionState.MotionState_MOTION_LAND_SPEED, -27);
        move(b, MotionState.MotionState_MOTION_FALL_ON_GROUND, 0);
        assertEquals(100, b.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));

        move(a, MotionState.MotionState_MOTION_FALL_ON_GROUND, 0);
        assertEquals(34, a.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP), .001f);
        move(a, MotionState.MotionState_MOTION_FALL_ON_GROUND, 0);
        assertEquals(34, a.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP), .001f);
    }

    @Test
    void drowningKillsOnlyTheCurrentAvatarThatLastMoved() throws Exception {
        var f = fixture();
        set(f.stamina, StaminaManager.class, "currentState", MotionState.MotionState_MOTION_SWIM_MOVE);
        f.stamina.handleCombatInvocationsNotify(f.session, EntityMoveInfo.getDefaultInstance(), f.entity);
        // Movement of an entity the player doesn't control is ignored.
        f.stamina.handleCombatInvocationsNotify(f.session, EntityMoveInfo.getDefaultInstance(), avatar(f, 2));
        drown(f);
        assertSame(f.entity, f.stamina.killed);

        f.stamina.killed = null;
        f.team.getActiveTeam().add(avatar(f, 3));
        f.team.setCurrentCharacterIndex(1);
        drown(f);
        assertNull(f.stamina.killed);
    }

    @Test
    void cannotSwitchToADeadAvatar() throws Exception {
        var f = fixture();
        var dead = avatar(f, 2);
        dead.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        set(dead, GameEntity.class, "isDead", true);
        f.team.getActiveTeam().add(dead);
        f.team.changeAvatar(dead.getAvatar().getGuid());
        assertEquals(0, f.team.getCurrentCharacterIndex());
    }

    @Test
    void canSwitchToAnAvatarWithHpEvenIfItsDeadFlagIsStale() throws Exception {
        var f = fixture();
        var revived = avatar(f, 2);
        revived.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 10);
        set(revived, GameEntity.class, "isDead", true);
        f.team.getActiveTeam().add(revived);
        f.team.changeAvatar(revived.getAvatar().getGuid());
        assertEquals(1, f.team.getCurrentCharacterIndex());
    }

    @Test
    void energyRefundsAreFlatCappedAndSkipSpecialEnergy() throws Exception {
        boolean energyUsage = GAME_OPTIONS.energyUsage;
        GAME_OPTIONS.energyUsage = true;
        try {
            var f = fixture();
            var action = new AbilityModifierAction();
            action.ratio = new DynamicFloat(3);
            var mixin = new AbilityMixinData();
            mixin.ratio = new DynamicFloat(3);
            set(f.player, Player.class, "energyManager", new TestEnergy(f.player));

            f.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, 70);
            assertTrue(new ActionReviveElemEnergy().execute(ability(f), action, ByteString.EMPTY, f.entity));
            assertEquals(73, f.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
            f.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, 79);
            assertTrue(new ReviveElemEnergyMixin().execute(ability(f), mixin, ByteString.EMPTY, f.entity));
            assertEquals(80, f.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));

            var special = new AvatarSkillData();
            special.specialEnergyMin = 100;
            set(f.entity.getAvatar().getSkillDepot(), AvatarSkillDepotData.class, "energySkillData", special);
            f.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, 0);
            new ActionReviveElemEnergy().execute(ability(f), action, ByteString.EMPTY, f.entity);
            assertEquals(0, f.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        } finally {
            GAME_OPTIONS.energyUsage = energyUsage;
        }
    }

    @Test
    void staminaMixinChargesElapsedTimeAndRefundsNegativeDeltas() throws Exception {
        var f = fixture();
        var mixin = new AbilityMixinData();
        mixin.costStaminaDelta = new DynamicFloat(1);
        f.stamina.setLastCostStaminaTime(System.currentTimeMillis() - 600);
        new CostStaminaMixin().execute(ability(f), mixin, ByteString.EMPTY, f.entity);
        assertEquals(-60, f.stamina.spent);

        // The first tick after a long pause bills nothing.
        f.stamina.setLastCostStaminaTime(System.currentTimeMillis() - 60_000);
        new CostStaminaMixin().execute(ability(f), mixin, ByteString.EMPTY, f.entity);
        assertEquals(-60, f.stamina.spent);

        mixin.costStaminaDelta = new DynamicFloat(-1);
        f.stamina.setLastCostStaminaTime(System.currentTimeMillis() - 600);
        new CostStaminaMixin().execute(ability(f), mixin, ByteString.EMPTY, f.entity);
        assertEquals(0, f.stamina.spent);
    }

    @Test
    void queuedActionIsSkippedOnceTheOwnerLeavesTheScene() throws Exception {
        boolean energyUsage = GAME_OPTIONS.energyUsage;
        GAME_OPTIONS.energyUsage = true;
        try {
            var f = fixture();
            // Live when queued, gone by the time the executor runs it.
            var team = new LeavingTeam();
            team.addAll(f.team.getActiveTeam());
            set(f.team, TeamManager.class, "avatars", team);
            f.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, 10);
            var action = new AbilityModifierAction();
            action.type = AbilityModifierAction.Type.ReviveElemEnergy;
            action.ratio = new DynamicFloat(3);
            f.player.getAbilityManager().executeAction(ability(f), action, ByteString.EMPTY, f.entity);

            for (int i = 0; i < 200 && team.lookups < 2; i++) Thread.sleep(10);
            Thread.sleep(100);
            assertTrue(team.lookups >= 2);
            assertEquals(10, f.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        } finally {
            GAME_OPTIONS.energyUsage = energyUsage;
        }
    }

    @Test
    void onlyTheHpDebtMixinMarksArlecchino() throws Exception {
        var f = fixture();
        set(f.entity.getAvatar(), Avatar.class, "avatarId", 10000096);
        var mixin = new AbilityMixinData();
        mixin.type = AbilityMixinData.Type.KENHGCLICPB; // another mixin without a handler
        new AbilityManager(f.player).executeMixin(ability(f), mixin, ByteString.EMPTY);
        assertFalse(f.entity.isConvertToHpDebt());

        mixin.type = AbilityMixinData.Type.SwitchHealToHPDebtsMixin;
        new AbilityManager(f.player).executeMixin(ability(f), mixin, ByteString.EMPTY);
        assertTrue(f.entity.isConvertToHpDebt());
    }

    private static void send(Fixture f, CombatInvokeEntry.Builder entry) throws Exception {
        var notify = CombatInvocationsNotify.newBuilder().addInvokeList(entry).build();
        COMBAT.handle(f.session, new byte[0], notify.toByteArray());
    }

    private static void move(Fixture f, MotionState state, float speed) throws Exception {
        var motion = MotionInfo.newBuilder().setState(state).setSpeed(Vector.newBuilder().setY(speed));
        var info = EntityMoveInfo.newBuilder().setEntityId(f.entity.getId()).setMotionInfo(motion).build();
        send(f, CombatInvokeEntry.newBuilder()
                .setArgumentType(CombatTypeArgument.CombatTypeArgument_ENTITY_MOVE)
                .setCombatData(info.toByteString()));
    }

    private static void drown(Fixture f) throws Exception {
        var method = StaminaManager.class.getDeclaredMethod("handleDrowning");
        method.setAccessible(true);
        method.invoke(f.stamina);
    }

    private static Ability ability(Fixture f) throws Exception {
        var ability = allocate(Ability.class);
        set(ability, Ability.class, "owner", f.entity);
        set(ability, Ability.class, "playerOwner", f.player);
        set(ability, Ability.class, "data", new AbilityData());
        set(ability, Ability.class, "abilitySpecials", new Object2FloatOpenHashMap<String>());
        return ability;
    }

    private static Fixture fixture() throws Exception {
        var f = new Fixture();
        f.player = allocate(TestPlayer.class);
        f.scene = allocate(TestScene.class);
        f.scene.packets = new ArrayList<>();
        f.scene.entities = new HashMap<>();
        f.scene.players = new ArrayList<>(List.of(f.player));
        f.scene.world = allocate(TestWorld.class);
        f.scene.world.packets = f.scene.packets;
        f.player.scene = f.scene;
        f.team = new TeamManager(f.player);
        f.stamina = new TestStamina(f.player);
        set(f.player, Player.class, "teamManager", f.team);
        set(f.player, Player.class, "staminaManager", f.stamina);
        set(f.player, Player.class, "satiationManager", new SatiationManager(f.player));
        set(f.player, Player.class, "energyManager", new TestEnergy(f.player));
        set(f.player, Player.class, "abilityManager", new AbilityManager(f.player));
        set(f.player, Player.class, "attackResults", new ArrayDeque<>());
        set(f.player, Player.class, "combatInvokeHandler", new InvokeHandler<>(PacketCombatInvocationsNotify.class));
        set(f.player, Player.class, "abilityInvokeHandler", new InvokeHandler<>(PacketAbilityInvocationsNotify.class));
        f.session = allocate(GameSession.class);
        set(f.session, GameSession.class, "player", f.player);
        f.player.setSession(f.session);
        f.entity = avatar(f, 1);
        f.team.getActiveTeam().add(f.entity);
        f.team.setCurrentCharacterIndex(0);
        return f;
    }

    private static TestEntity avatar(Fixture f, int id) throws Exception {
        var avatar = allocate(TestAvatar.class);
        set(avatar, Avatar.class, "fightProperties", new Int2FloatOpenHashMap());
        set(avatar, Avatar.class, "avatarId", 10000032);
        set(avatar, Avatar.class, "guid", (long) id);
        avatar.setOwner(f.player);
        var depot = allocate(AvatarSkillDepotData.class);
        set(depot, AvatarSkillDepotData.class, "elementType", ElementType.Fire);
        set(avatar, Avatar.class, "skillDepot", depot);
        var entity = allocate(TestEntity.class);
        set(entity, EntityAvatar.class, "avatar", avatar);
        set(entity, GameEntity.class, "scene", f.scene);
        set(entity, GameEntity.class, "globalAbilityValues", new ConcurrentHashMap<String, Float>());
        set(entity, GameEntity.class, "instancedModifiers", new ConcurrentHashMap<>());
        entity.setId(id);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY, 80);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 3);
        f.scene.entities.put(id, entity);
        return entity;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object instance, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class Fixture {
        TestPlayer player;
        TestEntity entity;
        TeamManager team;
        TestStamina stamina;
        TestScene scene;
        GameSession session;
    }

    /** Reports membership on the first lookup only, as if the avatar left the team afterwards. */
    static class LeavingTeam extends java.util.ArrayList<EntityAvatar> {
        volatile int lookups;
        @Override public boolean contains(Object o) { return ++lookups <= 1 && super.contains(o); }
    }

    static class TestAvatar extends Avatar {
        @Override public void save() {}
    }

    static class TestEntity extends EntityAvatar {
        TestEntity() { super((Avatar) null); }
        @Override public Position getPosition() { return new Position(); }
        @Override public Position getRotation() { return new Position(); }
        @Override public void checkIfDead() {}
        @Override public void runLuaCallbacks(EntityDamageEvent event) {}
    }

    static class TestPlayer extends Player {
        TestScene scene;
        @Override public Scene getScene() { return scene; }
        @Override public World getWorld() { return scene.world; }
        @Override public Position getPosition() { return new Position(); }
        @Override public void sendPacket(BasePacket packet) { scene.packets.add(packet); }
    }

    static class TestWorld extends World {
        List<BasePacket> packets;
        TestWorld() { super((Player) null); }
        @Override public void broadcastPacket(BasePacket packet) { packets.add(packet); }
    }

    static class TestScene extends Scene {
        List<BasePacket> packets;
        Map<Integer, GameEntity> entities;
        List<Player> players;
        TestWorld world;
        TestScene() { super(null, null); }
        @Override public World getWorld() { return world; }
        @Override public List<Player> getPlayers() { return players; }
        @Override public GameEntity getEntityById(int id) { return entities.get(id); }
        @Override public Map<Integer, GameEntity> getEntities() { return entities; }
        @Override public synchronized void replaceEntity(EntityAvatar oldEntity, EntityAvatar newEntity) {}
        @Override public void broadcastPacket(BasePacket packet) { packets.add(packet); }
        @Override public void handleAttack(AttackResult attack) {
            var entity = entities.get(attack.getDefenseId());
            if (entity != null) entity.damage(attack.getDamage(), attack.getAttackerId(), ElementType.None);
        }
    }

    static class TestEnergy extends EnergyManager {
        TestEnergy(Player player) { super(player); }
        @Override public void handleAttackHit(EvtBeingHitInfo hit) {}
    }

    static class TestStamina extends StaminaManager {
        GameEntity killed;
        int spent;
        TestStamina(Player player) { super(player); }
        @Override public int updateStaminaRelative(GameSession session, Consumption consumption, boolean character) {
            spent += consumption.amount;
            return 0;
        }
        @Override public int getCurrentCharacterStamina() { return 0; }
        @Override public int getMaxCharacterStamina() { return 24000; }
        @Override public void killAvatar(GameSession session, GameEntity entity, PlayerDieType reason) { killed = entity; }
        @Override public void startSustainedStaminaHandler() {}
    }
}
