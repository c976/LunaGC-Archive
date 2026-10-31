package emu.grasscutter.game.ability;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.net.proto.EvtBeingHealedNotifyOuterClass.EvtBeingHealedNotify;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@ExtendWith(ServerResourceFixture.class)
final class ArlecchinoBurstHealTest {
    @Test
    void defaultHealKeepsBondAndReportsRequestedAndActualAmounts() throws Exception {
        var avatar = avatar(90f, 100f, 60f);

        assertEquals(10f, ArlecchinoBoLUtil.applyBurstHeal(avatar, 25f));

        assertEquals(100f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(60f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertTrue(avatar.isConvertToHpDebt());
        assertEquals(
                PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, reason(avatar).getReason());
        var event = healEvent(avatar);
        assertEquals(25f, event.getHealAmount());
        assertEquals(10f, event.getRealHealAmount());
    }

    @Test
    void muteHealStillUpdatesHpButSuppressesTheHealEvent() throws Exception {
        var avatar = avatar(50f, 100f, 60f);

        assertEquals(25f, ArlecchinoBoLUtil.applyBurstHeal(avatar, 25f, true));

        var update =
                EntityFightPropUpdateNotify.parseFrom(
                        packet(avatar, PacketEntityFightPropUpdateNotify.class).getData());
        assertEquals(
                75f,
                update.getFightPropMapMap()
                        .get(FightProperty.FIGHT_PROP_CUR_HP.getId())
                        .floatValue());
        assertEquals(60f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(
                PropChangeReason.PropChangeReason_PROP_CHANGE_NONE, reason(avatar).getReason());
        assertFalse(
                avatar.scene.packets.stream()
                        .anyMatch(PacketEvtBeingHealedNotify.class::isInstance));
        assertTrue(avatar.isConvertToHpDebt());
    }

    @Test
    void fullHpOnlyReportsAnUnmutedZeroActualHeal() throws Exception {
        var avatar = avatar(100f, 100f, 60f);

        assertEquals(0f, ArlecchinoBoLUtil.applyBurstHeal(avatar, 25f, true));
        assertTrue(avatar.scene.packets.isEmpty());
        assertEquals(0f, ArlecchinoBoLUtil.applyBurstHeal(avatar, 25f));

        assertEquals(1, avatar.scene.packets.size());
        assertEquals(25f, healEvent(avatar).getHealAmount());
        assertEquals(0f, healEvent(avatar).getRealHealAmount());
        assertEquals(60f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertTrue(avatar.isConvertToHpDebt());
    }

    @ParameterizedTest
    @ValueSource(floats = {0f, -10f})
    void burstHealDoesNotReviveAnAvatarWithoutHp(float hp) throws Exception {
        var avatar = avatar(hp, 100f, 60f);
        avatar.setDeadForTest();

        assertEquals(0f, ArlecchinoBoLUtil.applyBurstHeal(avatar, 25f, false));

        assertEquals(hp, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(60f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertTrue(avatar.isDead());
        assertTrue(avatar.isConvertToHpDebt());
        assertTrue(avatar.scene.packets.isEmpty());
    }

    @Test
    void conversionFlagIsRestoredWhenTheHpUpdateThrows() throws Exception {
        var avatar = avatar(50f, 100f, 60f);
        avatar.failWrites = true;

        assertThrows(
                IllegalStateException.class,
                () -> ArlecchinoBoLUtil.applyBurstHeal(avatar, 25f, true));

        assertTrue(avatar.isConvertToHpDebt());
        assertEquals(50f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(60f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentBurstAndOtherHealsUseTheSameAvatarMonitor(boolean ordinaryHeal)
            throws Exception {
        var avatar = avatar(100f, 1000f, 0f);
        Worker first;
        Worker second;
        synchronized (avatar) {
            first =
                    start(
                            () ->
                                    assertEquals(
                                            100f,
                                            ArlecchinoBoLUtil.applyBurstHeal(avatar, 100f, true)));
            second =
                    start(
                            () ->
                                    assertEquals(
                                            200f,
                                            ordinaryHeal
                                                    ? avatar.heal(200f, true)
                                                    : ArlecchinoBoLUtil.applyBurstHeal(
                                                            avatar, 200f, true)));
            awaitBlocked(first.thread());
            awaitBlocked(second.thread());
            assertEquals(100f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        }
        first.done().get(5, TimeUnit.SECONDS);
        second.done().get(5, TimeUnit.SECONDS);

        assertEquals(400f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertTrue(avatar.isConvertToHpDebt());
    }

    private static EntityFightPropChangeReasonNotify reason(RecordingAvatar avatar)
            throws Exception {
        return EntityFightPropChangeReasonNotify.parseFrom(
                packet(avatar, PacketEntityFightPropChangeReasonNotify.class).getData());
    }

    private static EvtBeingHealedNotify healEvent(RecordingAvatar avatar) throws Exception {
        var invoke =
                CombatInvocationsNotify.parseFrom(
                                packet(avatar, PacketEvtBeingHealedNotify.class).getData())
                        .getInvokeList(0);
        return EvtBeingHealedNotify.parseFrom(invoke.getCombatData());
    }

    private static BasePacket packet(RecordingAvatar avatar, Class<? extends BasePacket> type) {
        return avatar.scene.packets.stream().filter(type::isInstance).findFirst().orElseThrow();
    }

    private static RecordingAvatar avatar(float hp, float maxHp, float bond) throws Exception {
        var avatar = allocate(RecordingAvatar.class);
        avatar.scene = allocate(RecordingScene.class);
        avatar.scene.packets = new CopyOnWriteArrayList<>();
        avatar.properties = new Int2FloatOpenHashMap();
        avatar.properties.put(FightProperty.FIGHT_PROP_CUR_HP.getId(), hp);
        avatar.properties.put(FightProperty.FIGHT_PROP_MAX_HP.getId(), maxHp);
        avatar.properties.put(FightProperty.FIGHT_PROP_CUR_HP_DEBTS.getId(), bond);
        avatar.convertToHpDebt = true;
        return avatar;
    }

    private static Worker start(Runnable action) {
        var done = new CompletableFuture<Void>();
        var thread =
                new Thread(
                        () -> {
                            try {
                                action.run();
                                done.complete(null);
                            } catch (Throwable error) {
                                done.completeExceptionally(error);
                            }
                        },
                        "arlecchino-heal-test");
        thread.setDaemon(true);
        thread.start();
        return new Worker(thread, done);
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (thread.getState() != Thread.State.BLOCKED
                && thread.isAlive()
                && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(
                Thread.State.BLOCKED, thread.getState(), "Healing must acquire the avatar monitor");
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private record Worker(Thread thread, CompletableFuture<Void> done) {}

    private static final class RecordingAvatar extends EntityAvatar {
        private RecordingScene scene;
        private Int2FloatMap properties;
        private boolean convertToHpDebt;
        private boolean failWrites;

        private RecordingAvatar() {
            super(null);
        }

        @Override
        public Scene getScene() {
            return scene;
        }

        @Override
        public Int2FloatMap getFightProperties() {
            return properties;
        }

        @Override
        public boolean isConvertToHpDebt() {
            return convertToHpDebt;
        }

        @Override
        public void setConvertToHpDebt(boolean value) {
            convertToHpDebt = value;
        }

        @Override
        public void setFightProperty(int id, float value) {
            assertTrue(Thread.holdsLock(this), "Healing mutations must hold the avatar monitor");
            if (failWrites) throw new IllegalStateException("HP update failed");
            super.setFightProperty(id, value);
        }

        private void setDeadForTest() {
            setDead(true);
        }
    }

    private static final class RecordingScene extends Scene {
        private List<BasePacket> packets;

        private RecordingScene() {
            super(null, null);
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }
}
