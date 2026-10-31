package emu.grasscutter.game.dps;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMonster;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** A single DPS test. Occupies the scene challenge slot, ends on the timer or on killing the target, and
 * settles once either way. */
public class DPSChallenge extends WorldChallenge {

    /** Pseudo group owning the target monster. Backed by no lua script; used only for the challenge kill
     * check and for clearing the field at the end. */
    public static final int GROUP_ID = 50505051;

    /** Config id of the target monster; must match the SceneMonster registered in {@link #buildGroup}. */
    public static final int CONFIG_ID = 1;

    /** Generic hilichurl AI which, combined with sending no weapon, keeps the target standing still. */
    public static final int AI_CONFIG_ID = 12001001;

    private static final int CHALLENGE_ID = 180;
    private static final int CHALLENGE_INDEX = 180;

    private final Player targetPlayer;
    private final List<DPSEntity> targets;
    private final ScheduledExecutorService ticker;
    private final AtomicBoolean settled = new AtomicBoolean();

    /** Wall-clock start time. The open world often locks game time so scene seconds stop advancing, which
     * makes sceneTime unusable for settling. */
    private final long startedAtMs = System.currentTimeMillis();

    private final int configuredSeconds;

    private float reportedDamage;

    public DPSChallenge(
            Scene scene,
            SceneGroup group,
            List<ChallengeTrigger> challengeTriggers,
            Player targetPlayer,
            List<DPSEntity> targets,
            int timeLimit) {
        super(
                scene,
                group,
                CHALLENGE_ID,
                CHALLENGE_INDEX,
                List.of(targets.size(), timeLimit),
                timeLimit,
                targets.size(),
                challengeTriggers);
        this.targetPlayer = targetPlayer;
        this.targets = targets;
        this.configuredSeconds = timeLimit;
        this.ticker =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            var thread = new Thread(runnable, "DPSMeter-" + targetPlayer.getUid());
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /** Builds the pseudo group holding the target monster. {@code WorldChallenge#finish} uses it to clear
     * the field. */
    public static SceneGroup buildGroup() {
        var monster = new SceneMonster();
        monster.config_id = CONFIG_ID;

        var group = new SceneGroup();
        group.id = GROUP_ID;
        group.monsters = Map.of(CONFIG_ID, monster);
        return group;
    }

    /** Real seconds elapsed, unaffected by a game-time lock. */
    public int elapsedSeconds() {
        return (int) Math.max(0L, (System.currentTimeMillis() - this.startedAtMs) / 1000L);
    }

    /** Real seconds remaining. */
    public int remainingSeconds() {
        return Math.max(0, this.configuredSeconds - this.elapsedSeconds());
    }

    /**
     * No longer pushes ChallengeDataNotify every second: it made the timer flash "-1s" repeatedly and jump
     * from 17 back to 21.
     * The client counts down from the duration in BeginNotify's paramList; the wall clock does the actual
     * settling.
     */
    public void refreshClientTimer() {
        // intentionally empty - see DPSTimeTrigger / tick()
    }

    @Override
    public void start() {
        super.start();
        if (!this.inProgress()) return;
        this.ticker.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void done() {
        super.done();
        this.settle(true);
    }

    @Override
    public void fail() {
        super.fail();
        this.settle(false);
    }

    /** Pushes the current DPS once per second and refreshes the client countdown. */
    private void tick() {
        try {
            if (!this.inProgress()) {
                this.settle(false);
                return;
            }
            if (!this.targetPlayer.isOnline()) {
                this.fail();
                return;
            }
            // Settle from the wall clock; the sceneTime check fails while scene time is locked.
            if (this.elapsedSeconds() >= this.configuredSeconds) {
                this.done();
                return;
            }
            var total = this.totalDamage();
            int remain = this.remainingSeconds();
            DPSMeter.reply(
                    this.targetPlayer,
                    String.format(
                            "Current DPS - %.0f (%d seconds left)", total - this.reportedDamage, remain));
            this.reportedDamage = total;
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("The DPS test timer task threw.", throwable);
        }
    }

    private float totalDamage() {
        float total = 0;
        for (var target : this.targets) {
            total += target.getTotalDamage();
        }
        return total;
    }

    /**
     * Settles: stops the timer, clears the targets and releases the scene challenge slot. Runs once only.
     *
     * @param report whether to push the result. Suppressed when interrupted, e.g. the player disconnected
     *     or the challenge failed.
     */
    private void settle(boolean report) {
        if (!this.settled.compareAndSet(false, true)) return;
        this.ticker.shutdown();

        int seconds = Math.min(this.configuredSeconds, Math.max(this.elapsedSeconds(), 0));
        // When settling right on the timeout, showing the configured duration reads better.
        if (this.elapsedSeconds() >= this.configuredSeconds) {
            seconds = this.configuredSeconds;
        }

        float total = 0;
        float maxHit = 0;
        int hitCount = 0;
        Map<ElementType, Float> byElement = new EnumMap<>(ElementType.class);
        Map<String, Float> byReaction = new HashMap<>();
        for (var target : this.targets) {
            total += target.getTotalDamage();
            hitCount += target.getHitCount();
            maxHit = Math.max(maxHit, target.getMaxHit());
            target.getDamageByElement()
                    .forEach((element, value) -> byElement.merge(element, value, Float::sum));
            target.getDamageByReaction()
                    .forEach((reaction, value) -> byReaction.merge(reaction, value, Float::sum));
        }

        this.targets.forEach(target -> this.getScene().removeEntity(target));
        this.targets.clear();

        if (this.getScene().getChallenge() == this) {
            this.getScene().setChallenge(null);
        }

        if (!report) return;
        DPSMeter.reply(
                this.targetPlayer, summary(seconds, total, maxHit, hitCount, byElement, byReaction));
    }

    private static String summary(
            int seconds,
            float total,
            float maxHit,
            int hitCount,
            Map<ElementType, Float> byElement,
            Map<String, Float> byReaction) {
        var elapsed = Math.max(seconds, 1);
        var message = new StringBuilder("DPS test finished. Results:");
        message.append(String.format("\nDuration %d s", seconds));
        message.append(String.format("\nTotal damage %.0f", total));
        message.append(String.format("\nAverage DPS %.2f", total / elapsed));
        message.append(String.format("\nHits %d", hitCount));
        message.append(String.format("\nHighest single hit %.0f", maxHit));
        if (!byElement.isEmpty()) {
            message.append("\nElemental damage:");
            byElement.entrySet().stream()
                    .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                    .forEach(
                            entry ->
                                    message.append(
                                            String.format(
                                                    "\n  %s %.0f (%.1f%%)",
                                                    elementName(entry.getKey()),
                                                    entry.getValue(),
                                                    total <= 0
                                                            ? 0f
                                                            : entry.getValue() * 100f / total)));
        }
        if (!byReaction.isEmpty()) {
            message.append("\nReaction damage:");
            byReaction.entrySet().stream()
                    .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                    .forEach(
                            entry ->
                                    message.append(
                                            String.format(
                                                    "\n  %s %.0f (%.1f%%)",
                                                    entry.getKey(),
                                                    entry.getValue(),
                                                    total <= 0
                                                            ? 0f
                                                            : entry.getValue() * 100f / total)));
        }
        return message.toString();
    }

    private static String elementName(ElementType element) {
        return switch (element) {
            case Fire -> "Pyro";
            case Water -> "Hydro";
            case Grass -> "Dendro";
            case Electric -> "Electro";
            case Ice -> "Cryo";
            case Frozen -> "Frozen";
            case Wind -> "Anemo";
            case Rock -> "Geo";
            case AntiFire -> "Anti-Pyro";
            case None -> "Physical";
            default -> element.name();
        };
    }
}
