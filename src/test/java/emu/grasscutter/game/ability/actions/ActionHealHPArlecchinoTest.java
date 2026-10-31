package emu.grasscutter.game.ability.actions;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.ArlecchinoBoLSync;
import emu.grasscutter.game.ability.ArlecchinoBoLUtil;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangHpReasonOuterClass.ChangHpReason;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.net.proto.EvtBeingHealedNotifyOuterClass.EvtBeingHealedNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ExtendWith(ServerResourceFixture.class)
public final class ActionHealHPArlecchinoTest {
    private static final String BURST_HEAL_TAG = "Arlecchino_ElementalBurst_Heal";
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(96_000);

    @Test
    void pendingBurstHealsAtOnceAndHoldsBondUntilTickClears() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            fixture.packets.clear();

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            // The heal lands immediately; only the Bond clear waits for the slash / lock end.
            fixture.assertState(3_000f, 3_000f);
            assertTrue(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
            fixture.assertHpUpdate(3_000f, 1_000f);
            assertEquals(-1, fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));

            fixture.packets.clear();
            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);

            fixture.assertState(3_000f, 0f);
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            assertTrue(
                    fixture.packets.stream()
                            .noneMatch(PacketEvtBeingHealedNotify.class::isInstance),
                    "Settling the Bond must not heal again");

            fixture.packets.clear();
            ArlecchinoBurstBoL.onTick(fixture.player);
            fixture.assertState(3_000f, 0f);
            assertTrue(fixture.packets.isEmpty(), "The burst must settle only once");
        }
    }

    @Test
    void burstHealDoesNotRepayBondAddedAfterCast() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 4_000f);
            fixture.packets.clear();

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));
            fixture.assertState(3_000f, 4_000f);
            fixture.assertHealNotification(1_000f, 1_000f);

            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);

            fixture.assertState(3_000f, 1_000f);
            assertEquals(0, fixture.entity.ordinaryHealCalls);
        }
    }

    @Test
    void otherPlayersTickDoesNotRepinOrSettlePendingBurst() throws Exception {
        try (var caster = fixture(10000096, 2_000f, 3_000f);
                var other = fixture(10000032, 5_000f, 0f)) {
            ArlecchinoBurstBoL.onBurstCast(caster.entity);
            assertTrue(execute(caster, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));
            caster.packets.clear();

            ArlecchinoBurstBoL.onTick(other.player);

            caster.assertState(3_000f, 3_000f);
            assertTrue(ArlecchinoBurstBoL.isPending(caster.entity.getId()));
            assertTrue(hasRecordedBurstHeal(caster.entity.getId()));
            assertTrue(caster.packets.isEmpty(), "Another player's tick must not repin the caster");

            expirePendingCast(caster.entity);
            ArlecchinoBurstBoL.onTick(other.player);

            caster.assertState(3_000f, 3_000f);
            assertTrue(ArlecchinoBurstBoL.isPending(caster.entity.getId()));
            assertTrue(hasRecordedBurstHeal(caster.entity.getId()));
            assertTrue(caster.packets.isEmpty(), "Another player's tick must not settle the caster");
            assertTrue(other.packets.isEmpty());

            ArlecchinoBurstBoL.onTick(caster.player);

            caster.assertState(3_000f, 0f);
            assertFalse(ArlecchinoBurstBoL.isPending(caster.entity.getId()));
            assertFalse(hasRecordedBurstHeal(caster.entity.getId()));
        }
    }

    @Test
    void teammateBurstAbilityDoesNotPrearmArlecchinosBondSettlement() throws Exception {
        try (var target = fixture(10000096, 2_000f, 3_000f);
                var teammate = fixture(10000032, 5_000f, 0f)) {
            ArlecchinoBurstBoL.tryPreArmFromAbility(
                    ability(teammate.entity, "Avatar_Bennett_ElementalBurst"), target.entity);

            assertFalse(ArlecchinoBurstBoL.isPending(target.entity.getId()));
            target.assertState(2_000f, 3_000f);
            assertTrue(target.packets.isEmpty());
        }
    }

    @Test
    void lateCastConfirmationKeepsThePrearmedSlashAndItsHeal() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.tryPreArmFromAbility(
                    ability(fixture.entity, "Avatar_Arlecchino_ElementalBurst"), fixture.entity);
            setPendingCast(
                    fixture.entity, System.currentTimeMillis() - 2_000L, 0L, -1f, 3_000f, 0L);
            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));
            ArlecchinoBurstBoL.onAttack(
                    fixture.entity,
                    AttackResult.newBuilder()
                            .setAttackerId(fixture.entity.getId())
                            .setAnimEventId("Arlecchino_ElementalBurst_Attack")
                            .setDamage(1_000f)
                            .build());
            var slash = pendingCast(fixture.entity);
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 4_000f);

            ArlecchinoBurstBoL.onBurstCast(fixture.entity);

            assertSame(slash, pendingCast(fixture.entity), "Late confirmation must preserve settlement");
            assertTrue(hasRecordedBurstHeal(fixture.entity.getId()));
            fixture.assertState(3_000f, 4_000f);
            fixture.packets.clear();
            setPendingCast(
                    fixture.entity,
                    System.currentTimeMillis() - 2_000L,
                    System.currentTimeMillis() - 1L,
                    3_000f,
                    3_000f,
                    System.currentTimeMillis() - 101L);

            ArlecchinoBurstBoL.onTick(fixture.player);

            fixture.assertState(3_000f, 1_000f);
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            assertFalse(hasRecordedBurstHeal(fixture.entity.getId()));
            assertTrue(
                    fixture.packets.stream()
                            .noneMatch(PacketEvtBeingHealedNotify.class::isInstance),
                    "The heal already landed with the cast");
            fixture.packets.clear();
            ArlecchinoBurstBoL.onTick(fixture.player);
            assertTrue(fixture.packets.isEmpty(), "The confirmed cast must settle only once");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lateConfirmationAfterSettlementDoesNotLockOrClearNewBond(boolean settleOnTick)
            throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            setPendingCast(
                    fixture.entity, System.currentTimeMillis() - 2_000L, 0L, -1f, 3_000f, 0L);
            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));
            ArlecchinoBurstBoL.onAttack(
                    fixture.entity,
                    AttackResult.newBuilder()
                            .setAttackerId(fixture.entity.getId())
                            .setAnimEventId("Arlecchino_ElementalBurst_Attack")
                            .setDamage(1_000f)
                            .build());
            setPendingCast(
                    fixture.entity,
                    System.currentTimeMillis() - 2_000L,
                    System.currentTimeMillis() - 1L,
                    3_000f,
                    3_000f,
                    System.currentTimeMillis() - 101L);

            if (settleOnTick) {
                ArlecchinoBurstBoL.onTick(fixture.player);
            } else {
                assertTrue(
                        execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));
            }
            fixture.assertState(3_000f, 0f);
            fixture.assertHealNotification(1_000f, 1_000f);
            fixture.packets.clear();
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 1_500f);

            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            ArlecchinoBurstBoL.tryPreArmFromAbility(
                    ability(fixture.entity, "Avatar_Arlecchino_ElementalBurst"), fixture.entity);
            var lateWipe = new AbilityModifierAction();
            lateWipe.ratio = new DynamicFloat(10_000f);
            assertTrue(
                    new ActionReduceHPDebts()
                            .execute(
                                    ability(fixture.entity, "Avatar_Arlecchino_ElementalBurst"),
                                    lateWipe,
                                    ByteString.EMPTY,
                                    fixture.entity));
            ArlecchinoBurstBoL.onTick(fixture.player);

            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            assertFalse(ArlecchinoBurstBoL.isConsumeBlocked(fixture.entity));
            assertFalse(hasRecordedBurstHeal(fixture.entity.getId()));
            fixture.assertState(3_000f, 1_500f);
            assertTrue(fixture.packets.isEmpty(), "Late confirmation must not restart settlement");
        }
    }

    @Test
    void completedCastGuardDoesNotBlockAnAvatarThatReusesTheEntityId() throws Exception {
        try (var original = fixture(10000096, 2_000f, 3_000f);
                var replacement = fixture(10000096, 5_000f, 2_000f)) {
            replacement.entity.setId(original.entity.getId());
            ArlecchinoBurstBoL.onBurstCast(original.entity);
            expirePendingCast(original.entity);
            ArlecchinoBurstBoL.onTick(original.player);

            ArlecchinoBurstBoL.tryPreArmFromAbility(
                    ability(replacement.entity, "Avatar_Arlecchino_ElementalBurst"),
                    replacement.entity);

            assertTrue(ArlecchinoBurstBoL.isPending(replacement.entity.getId()));
            assertTrue(ArlecchinoBurstBoL.isConsumeBlocked(replacement.entity));
            replacement.assertState(5_000f, 2_000f);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nextBurstCanSettleAfterTheCompletedCastGuardExpires(boolean expireOnTick)
            throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 1_500f);
            var field = ArlecchinoBurstBoL.class.getDeclaredField("COMPLETED_BURSTS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var completed = (Map<Integer, Object>) field.get(null);
            var constructor =
                    completed.get(fixture.entity.getId()).getClass()
                            .getDeclaredConstructor(EntityAvatar.class, long.class);
            constructor.setAccessible(true);
            completed.put(
                    fixture.entity.getId(),
                    constructor.newInstance(fixture.entity, System.currentTimeMillis() - 3_000L));
            if (expireOnTick) {
                ArlecchinoBurstBoL.onTick(fixture.player);
            }

            ArlecchinoBurstBoL.onBurstCast(fixture.entity);

            assertTrue(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);
            fixture.assertState(2_000f, 0f);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void clearingStateAllowsANewCastAfterSettlement(boolean clearPlayer) throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);
            if (clearPlayer) {
                ArlecchinoBurstBoL.clearPlayerState(fixture.player);
            } else {
                ArlecchinoBurstBoL.clearEntityState(fixture.entity.getId());
            }
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 1_500f);

            ArlecchinoBurstBoL.onBurstCast(fixture.entity);

            assertTrue(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            fixture.assertState(2_000f, 1_500f);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cleanupWaitsForSettlementAndRemovesItsCompletedState(boolean clearPlayer) throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));
            expirePendingCast(fixture.entity);
            var settlementEntered = new CountDownLatch(1);
            var releaseSettlement = new CountDownLatch(1);
            var tickCompletion = new CompletableFuture<Void>();
            var cleanupCompletion = new CompletableFuture<Void>();
            fixture.scene.beforeBroadcast =
                    () -> {
                        settlementEntered.countDown();
                        try {
                            assertTrue(releaseSettlement.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    };
            var tick =
                    new Thread(
                            () -> {
                                try {
                                    ArlecchinoBurstBoL.onTick(fixture.player);
                                    tickCompletion.complete(null);
                                } catch (Throwable failure) {
                                    tickCompletion.completeExceptionally(failure);
                                }
                            },
                            "arlecchino-cleanup-settlement-test");
            var cleanup =
                    new Thread(
                            () -> {
                                try {
                                    if (clearPlayer) {
                                        ArlecchinoBurstBoL.clearPlayerState(fixture.player);
                                    } else {
                                        ArlecchinoBurstBoL.clearEntityState(fixture.entity.getId());
                                    }
                                    cleanupCompletion.complete(null);
                                } catch (Throwable failure) {
                                    cleanupCompletion.completeExceptionally(failure);
                                }
                            },
                            "arlecchino-cleanup-state-test");
            tick.setDaemon(true);
            cleanup.setDaemon(true);
            try {
                tick.start();
                assertTrue(settlementEntered.await(5, TimeUnit.SECONDS));
                cleanup.start();
                awaitBlockedOn(cleanup, fixture.entity);
                releaseSettlement.countDown();
                tickCompletion.get(5, TimeUnit.SECONDS);
                cleanupCompletion.get(5, TimeUnit.SECONDS);

                fixture.assertState(3_000f, 0f);
                assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
                assertFalse(hasRecordedBurstHeal(fixture.entity.getId()));
                assertFalse(ArlecchinoBurstBoL.hasRecentlyCompletedCast(fixture.entity));
                fixture.assertHealNotification(1_000f, 1_000f);
            } finally {
                releaseSettlement.countDown();
                tick.join(5_000L);
                cleanup.join(5_000L);
                assertFalse(tick.isAlive(), "The settlement thread must terminate");
                assertFalse(cleanup.isAlive(), "The cleanup thread must terminate");
            }
        }
    }

    @Test
    void reusedEntityIdDoesNotInheritAnotherAvatarsBurstHeal() throws Exception {
        try (var original = fixture(10000096, 2_000f, 3_000f);
                var replacement = fixture(10000096, 5_000f, 2_000f)) {
            replacement.entity.setId(original.entity.getId());
            ArlecchinoBurstBoL.onBurstCast(original.entity);
            assertTrue(execute(original, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            ArlecchinoBurstBoL.tryPreArmFromAbility(
                    ability(replacement.entity, "Avatar_Arlecchino_ElementalBurst"),
                    replacement.entity);

            assertFalse(hasRecordedBurstHeal(replacement.entity.getId()));
            expirePendingCast(replacement.entity);
            original.packets.clear();
            replacement.packets.clear();
            ArlecchinoBurstBoL.onTick(original.player);
            assertTrue(ArlecchinoBurstBoL.isPending(replacement.entity.getId()));
            assertTrue(original.packets.isEmpty());

            ArlecchinoBurstBoL.onTick(replacement.player);

            // The original avatar healed at once; the replacement inherits nothing.
            original.assertState(3_000f, 3_000f);
            replacement.assertState(5_000f, 0f);
            assertFalse(ArlecchinoBurstBoL.isPending(replacement.entity.getId()));
            assertTrue(
                    replacement.packets.stream()
                            .noneMatch(PacketEvtBeingHealedNotify.class::isInstance));
        }
    }

    @Test
    void tickWaitingOnAvatarSettlesAfterTheHealExactlyOnce() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            fixture.packets.clear();
            var started = new CountDownLatch(1);
            var completion = new CompletableFuture<Void>();
            var tick =
                    new Thread(
                            () -> {
                                started.countDown();
                                try {
                                    ArlecchinoBurstBoL.onTick(fixture.player);
                                    completion.complete(null);
                                } catch (Throwable failure) {
                                    completion.completeExceptionally(failure);
                                }
                            },
                            "arlecchino-burst-heal-tick-test");
            tick.setDaemon(true);
            try {
                synchronized (fixture.entity) {
                    tick.start();
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    awaitBlockedOn(tick, fixture.entity);

                    assertTrue(
                            execute(
                                    fixture,
                                    "Avatar_Arlecchino_Common",
                                    heal(BURST_HEAL_TAG, 1_000f)));
                    fixture.assertState(3_000f, 3_000f);
                    fixture.assertHealNotification(1_000f, 1_000f);
                    expirePendingCast(fixture.entity);
                }
                completion.get(5, TimeUnit.SECONDS);

                fixture.assertState(3_000f, 0f);
                assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
                assertEquals(0, fixture.entity.ordinaryHealCalls);
                fixture.assertHealNotification(1_000f, 1_000f);
                assertFalse(hasRecordedBurstHeal(fixture.entity.getId()));
                fixture.packets.clear();
                ArlecchinoBurstBoL.onTick(fixture.player);
                assertTrue(fixture.packets.isEmpty());
            } finally {
                tick.join(5_000L);
                assertFalse(tick.isAlive(), "The settlement thread must terminate");
            }
        }
    }

    @Test
    void burstHealWithoutPendingAddsHpAndLeavesBondUnchanged() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            fixture.entity.setConvertToHpDebt(true);

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            fixture.assertState(3_000f, 3_000f);
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            assertTrue(fixture.entity.isConvertToHpDebtRaw());
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
            fixture.assertHpUpdate(3_000f, 1_000f);
        }
    }

    @Test
    void ordinaryHealStillRepaysBondBeforeRestoringHp() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(null, 1_000f)));

            fixture.assertState(2_000f, 2_000f);
            assertEquals(1, fixture.entity.ordinaryHealCalls);
            assertTrue(
                    fixture.packets.stream()
                            .noneMatch(PacketEvtBeingHealedNotify.class::isInstance));
            assertTrue(fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) >= 0);
            assertEquals(-1, fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP));
        }
    }

    @Test
    void missingHealTagUsesArlecchinoElementalBurstAbilityName() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            assertTrue(
                    execute(
                            fixture,
                            "Avatar_Arlecchino_ElementalBurst_HealDelay",
                            heal(null, 1_000f)));

            fixture.assertState(3_000f, 3_000f);
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
        }
    }

    @Test
    void obfuscatedHealTagIsParsedAndUsesBurstHealing() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            var action =
                    JsonUtils.decode(
                            "{\"$type\":\"HealHP\",\"FFNEJGGNAFF\":\"Arlecchino_ElementalBurst_Heal\","
                                + "\"amount\":1000,\"ignoreAbilityProperty\":true}",
                            AbilityModifierAction.class);
            assertEquals(BURST_HEAL_TAG, action.healTag);

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", action));

            fixture.assertState(3_000f, 3_000f);
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
        }
    }

    @Test
    void teammateElementalBurstHealUsesOrdinaryBondRepayment() throws Exception {
        try (var target = fixture(10000096, 2_000f, 3_000f);
                var caster = fixture(10000032, 5_000f, 0f)) {
            var ability = ability(caster.entity, "Avatar_Bennett_ElementalBurst");

            assertTrue(
                    new ActionHealHP()
                            .execute(
                                    ability,
                                    heal("Bennett_ElementalBurst_Heal", 1_000f),
                                    ByteString.EMPTY,
                                    target.entity));

            target.assertState(2_000f, 2_000f);
            assertEquals(1, target.entity.ordinaryHealCalls);
            assertFalse(ArlecchinoBurstBoL.isPending(target.entity.getId()));
        }
    }

    @Test
    void arlecchinoBurstTagDoesNotGiveOtherAvatarsSpecialHealing() throws Exception {
        try (var target = fixture(10000032, 2_000f, 3_000f);
                var caster = fixture(10000096, 5_000f, 0f)) {
            var ability = ability(caster.entity, "Avatar_Arlecchino_ElementalBurst_HealDelay");

            assertTrue(
                    new ActionHealHP()
                            .execute(
                                    ability,
                                    heal(BURST_HEAL_TAG, 1_000f),
                                    ByteString.EMPTY,
                                    target.entity));

            target.assertState(2_000f, 2_000f);
            assertEquals(1, target.entity.ordinaryHealCalls);
        }
    }

    @Test
    void burstHealDoesNotReviveAnAvatarWithZeroHp() throws Exception {
        try (var fixture = fixture(10000096, 0f, 3_000f)) {
            set(fixture.entity, GameEntity.class, "isDead", true);

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            fixture.assertState(0f, 3_000f);
            assertTrue(fixture.entity.isDead());
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            assertTrue(fixture.packets.isEmpty());
        }
    }

    @Test
    void cappedBurstHealReportsRequestedAndActualAmountsInTheirCorrectFields() throws Exception {
        try (var fixture = fixture(10000096, 9_500f, 3_000f)) {
            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            fixture.assertState(10_000f, 3_000f);
            fixture.assertHealNotification(1_000f, 500f);
            fixture.assertHpUpdate(10_000f, 500f);
        }
    }

    @Test
    void mutedBurstHealStillUpdatesHpWithoutBeingHealedNotification() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            fixture.packets.clear();
            var action = heal(BURST_HEAL_TAG, 1_000f);
            action.muteHealEffect = true;

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", action));
            fixture.assertState(3_000f, 3_000f);

            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);

            fixture.assertState(3_000f, 0f);
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            assertTrue(fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP) >= 0);
            assertTrue(
                    fixture.packets.stream()
                            .noneMatch(PacketEvtBeingHealedNotify.class::isInstance));
        }
    }

    @Test
    void flatAmountIsAddedOnceAlongsideRatios() throws Exception {
        try (var caster = fixture(10000032, 5_000f, 0f);
                var recipient = fixture(10000032, 1_000f, 0f)) {
            var action = heal(null, 1_000f);
            action.amountByCasterMaxHPRatio = new DynamicFloat(.1f);
            assertTrue(new ActionHealHP().execute(ability(caster.entity, "Test_Heal"),
                    action, ByteString.EMPTY, recipient.entity));
            recipient.assertState(3_000f, 0f);
        }
    }

    @Test
    void casterHealBonusAndRecipientHealedBonusApply() throws Exception {
        try (var caster = fixture(10000032, 5_000f, 0f);
                var recipient = fixture(10000032, 1_000f, 0f)) {
            caster.entity.setFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD, .5f);
            recipient.entity.setFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD, 2f);
            recipient.entity.setFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD, .25f);
            var action = heal(null, 1_000f);
            action.ignoreAbilityProperty = false;
            assertTrue(new ActionHealHP().execute(ability(caster.entity, "Test_Heal"),
                    action, ByteString.EMPTY, recipient.entity));
            recipient.assertState(2_750f, 0f);
        }
    }

    @Test
    void targetCurrentHpRatioScalesCurrentHp() throws Exception {
        try (var recipient = fixture(10000032, 1_000f, 0f)) {
            var action = heal(null, 0f);
            action.amountByTargetCurrentHPRatio = new DynamicFloat(.1f);
            assertTrue(execute(recipient, "Test_Heal", action));
            recipient.assertState(1_100f, 0f);
        }
    }

    private static boolean execute(
            Fixture fixture, String abilityName, AbilityModifierAction action) throws Exception {
        return new ActionHealHP()
                .execute(
                        ability(fixture.entity, abilityName),
                        action,
                        ByteString.EMPTY,
                        fixture.entity);
    }

    private static AbilityModifierAction heal(String tag, float amount) {
        var action = new AbilityModifierAction();
        action.healTag = tag;
        action.amount = new DynamicFloat(amount);
        action.ignoreAbilityProperty = true;
        return action;
    }

    private static Ability ability(GameEntity owner, String name) throws Exception {
        var ability = allocate(Ability.class);
        var data = new AbilityData();
        data.abilityName = name;
        set(ability, Ability.class, "owner", owner);
        set(ability, Ability.class, "data", data);
        set(ability, Ability.class, "abilitySpecials", new Object2FloatOpenHashMap<String>());
        return ability;
    }

    private static Fixture fixture(int avatarId, float hp, float debt) throws Exception {
        var fixture = new Fixture();
        fixture.entity = allocate(RecordingEntityAvatar.class);
        fixture.player = allocate(RecordingPlayer.class);
        var scene = allocate(RecordingScene.class);
        fixture.scene = scene;
        var world = allocate(RecordingWorld.class);
        var avatar = allocate(Avatar.class);
        fixture.entity.setId(ENTITY_IDS.incrementAndGet());
        scene.packets = world.packets = fixture.player.packets = fixture.packets;
        scene.world = world;
        scene.entity = fixture.entity;
        set(avatar, Avatar.class, "avatarId", avatarId);
        set(avatar, Avatar.class, "guid", (long) fixture.entity.getId());
        set(avatar, Avatar.class, "fightProperties", new Int2FloatOpenHashMap());
        avatar.setOwner(fixture.player);
        set(fixture.entity, EntityAvatar.class, "avatar", avatar);
        set(fixture.entity, GameEntity.class, "scene", scene);
        set(
                fixture.entity,
                GameEntity.class,
                "globalAbilityValues",
                new ConcurrentHashMap<String, Float>());
        set(fixture.entity, GameEntity.class, "instancedModifiers", new ConcurrentHashMap<>());
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10_000f);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, debt);
        return fixture;
    }

    // Move the real pending cast past its miss timeout without sleeping or bypassing settlement.
    private static void expirePendingCast(EntityAvatar entity) throws Exception {
        var original = pendingCast(entity);
        var snapshot = original.getClass().getDeclaredMethod("castSnapshot");
        snapshot.setAccessible(true);
        setPendingCast(
                entity,
                System.currentTimeMillis() - 10_000L,
                0L,
                -1f,
                (float) snapshot.invoke(original),
                0L);
    }

    private static Object pendingCast(EntityAvatar entity) throws Exception {
        var pendingField = ArlecchinoBurstBoL.class.getDeclaredField("PENDING");
        pendingField.setAccessible(true);
        var original = ((Map<?, ?>) pendingField.get(null)).get(entity.getId());
        assertNotNull(original);
        return original;
    }

    private static void setPendingCast(
            EntityAvatar entity,
            long castAt,
            long clearAt,
            float bolSnapshot,
            float castSnapshot,
            long slashAt)
            throws Exception {
        var pendingField = ArlecchinoBurstBoL.class.getDeclaredField("PENDING");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var pending = (Map<Integer, Object>) pendingField.get(null);
        var type = pendingCast(entity).getClass();
        var constructor =
                type.getDeclaredConstructor(
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
                        entity, castAt, clearAt, bolSnapshot, castSnapshot, slashAt));
    }

    private static void awaitBlockedOn(Thread thread, Object monitor) {
        var threads = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (!thread.isAlive()) fail("The tick ended before waiting on the avatar monitor");
            var info = threads.getThreadInfo(thread.threadId());
            if (info != null
                    && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockInfo() != null
                    && info.getLockInfo().getIdentityHashCode()
                            == System.identityHashCode(monitor)) {
                return;
            }
            Thread.yield();
        }
        fail("The tick must wait on the avatar monitor before healing is registered");
    }

    private static boolean hasRecordedBurstHeal(int entityId) throws Exception {
        var field = ArlecchinoBurstBoL.class.getDeclaredField("BURST_HEALED");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(entityId);
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
        private final ArrayList<BasePacket> packets = new ArrayList<>();
        private RecordingEntityAvatar entity;
        private RecordingPlayer player;
        private RecordingScene scene;

        private void assertState(float hp, float debt) {
            assertEquals(hp, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            assertEquals(debt, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        }

        private void assertHealNotification(float requested, float actual) throws Exception {
            var healed =
                    packets.stream().filter(PacketEvtBeingHealedNotify.class::isInstance).toList();
            assertEquals(1, healed.size());
            var combat = CombatInvocationsNotify.parseFrom(healed.get(0).getData());
            assertEquals(1, combat.getInvokeListCount());
            var notification =
                    EvtBeingHealedNotify.parseFrom(combat.getInvokeList(0).getCombatData());
            assertEquals(entity.getId(), notification.getTargetId());
            assertEquals(requested, notification.getHealAmount());
            assertEquals(actual, notification.getRealHealAmount());
        }

        private void assertHpUpdate(float hp, float change) throws Exception {
            int index = firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP);
            assertTrue(index >= 0);
            var update = EntityFightPropUpdateNotify.parseFrom(packets.get(index).getData());
            assertEquals(
                    hp, update.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_HP.getId()));
            var reasons = new ArrayList<EntityFightPropChangeReasonNotify>();
            for (var packet : packets) {
                if (packet instanceof PacketEntityFightPropChangeReasonNotify) {
                    var reason = EntityFightPropChangeReasonNotify.parseFrom(packet.getData());
                    if (reason.getPropType() == FightProperty.FIGHT_PROP_CUR_HP.getId())
                        reasons.add(reason);
                }
            }
            assertEquals(1, reasons.size());
            assertEquals(change, reasons.get(0).getPropDelta());
            assertEquals(
                    ChangHpReason.ChangHpReason_CHANGE_HP_ADD_ABILITY,
                    reasons.get(0).getChangeHpReason());
        }

        private int firstPropertyUpdate(FightProperty property) throws Exception {
            for (int index = 0; index < packets.size(); index++) {
                var packet = packets.get(index);
                if (packet instanceof PacketEntityFightPropUpdateNotify) {
                    var update = EntityFightPropUpdateNotify.parseFrom(packet.getData());
                    if (update.containsFightPropMap(property.getId())) return index;
                }
            }
            return -1;
        }

        @Override
        public void close() throws Exception {
            ArlecchinoBurstBoL.clearEntityState(entity.getId());
            ArlecchinoBoLUtil.clearEntityState(entity.getId());
            var field = ArlecchinoBoLSync.class.getDeclaredField("BLOCK_ADD_UNTIL");
            field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).remove(entity.getId());
        }
    }

    private static final class RecordingEntityAvatar extends EntityAvatar {
        private int ordinaryHealCalls;

        private RecordingEntityAvatar() {
            super((Avatar) null);
        }

        @Override
        public float heal(float amount, boolean mute) {
            ordinaryHealCalls++;
            return super.heal(amount, mute);
        }
    }

    private static final class RecordingScene extends Scene {
        private ArrayList<BasePacket> packets;
        private World world;
        private GameEntity entity;
        private Runnable beforeBroadcast;

        private RecordingScene() {
            super(null, null);
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public GameEntity getEntityById(int id) {
            return entity != null && entity.getId() == id ? entity : null;
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            Runnable callback = beforeBroadcast;
            if (callback != null) {
                beforeBroadcast = null;
                callback.run();
            }
            packets.add(packet);
        }
    }

    private static final class RecordingWorld extends World {
        private ArrayList<BasePacket> packets;

        private RecordingWorld() {
            super((Player) null);
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static final class RecordingPlayer extends Player {
        private ArrayList<BasePacket> packets;

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }
}
