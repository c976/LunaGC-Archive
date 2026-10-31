package emu.grasscutter.game.dungeons.challenge;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DomainRewardStatueHelper;
import emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.*;

@Getter
@Setter
public class WorldChallenge {
    private final Scene scene;
    private final SceneGroup group;
    private final int challengeId;
    private final int challengeIndex;
    private final List<Integer> paramList;
    private int timeLimit;
    private GameEntity guardEntity;
    private final List<ChallengeTrigger> challengeTriggers;
    private final int goal;
    private final AtomicInteger score;
    private boolean progress;
    private boolean success;
    private int startedAt;
    private int finishedTime;

    /**
     * @param scene The scene the challenge is in.
     * @param group The group the challenge is in.
     * @param challengeId The challenge's id.
     * @param challengeIndex The challenge's index.
     * @param paramList The challenge's parameters.
     * @param timeLimit The challenge's time limit.
     * @param goal The challenge's goal.
     * @param challengeTriggers The challenge's triggers.
     */
    public WorldChallenge(
            Scene scene,
            SceneGroup group,
            int challengeId,
            int challengeIndex,
            List<Integer> paramList,
            int timeLimit,
            int goal,
            List<ChallengeTrigger> challengeTriggers) {
        this.scene = scene;
        this.group = group;
        this.challengeId = challengeId;
        this.challengeIndex = challengeIndex;
        this.paramList = paramList;
        this.timeLimit = timeLimit;
        this.challengeTriggers = challengeTriggers;
        this.goal = goal;
        this.score = new AtomicInteger(0);
        this.guardEntity = null;
    }

    public boolean inProgress() {
        return this.progress;
    }

    public void onCheckTimeOut() {
        if (!inProgress()) {
            return;
        }
        if (timeLimit <= 0) {
            return;
        }
        challengeTriggers.forEach(t -> t.onCheckTimeout(this));
    }

    public void start() {
        if (inProgress()) {
            Grasscutter.getLogger().debug("Could not start a in progress challenge.");
            return;
        }
        var player = scene.getPlayers().isEmpty() ? null : scene.getPlayers().get(0);
        var towerManager = player != null ? player.getTowerManager() : null;
        if (towerManager != null && towerManager.isAwaitingTeamReconfigure()) {
            Grasscutter.getLogger()
                    .info(
                            "WorldChallenge.start blocked — awaiting tower team reconfigure uid={}",
                            player.getUid());
            return;
        }
        this.progress = true;
        this.startedAt = getScene().getSceneTimeSeconds();
        getScene().broadcastPacket(new PacketDungeonChallengeBeginNotify(this));
        challengeTriggers.forEach(t -> t.onBegin(this));

        var dungeonManager = scene.getDungeonManager();
        if (dungeonManager != null && dungeonManager.isTowerDungeon() && towerManager != null) {
            towerManager.onBegin();
        }
    }

    public void done() {
        if (!this.inProgress()) return;
        this.finish(true);

        var scene = this.getScene();
        var scriptManager = scene.getScriptManager();
        var dungeonManager = scene.getDungeonManager();
        if (dungeonManager != null && dungeonManager.getDungeonData() != null) {
            scene
                    .getPlayers()
                    .forEach(
                            p ->
                                    p.getActivityManager()
                                            .triggerWatcher(
                                                    WatcherTriggerType.TRIGGER_FINISH_CHALLENGE,
                                                    String.valueOf(dungeonManager.getDungeonData().getId()),
                                                    String.valueOf(this.getGroup().id),
                                                    String.valueOf(this.getChallengeId())));
        }

        // Tower lua (TPL_TIME) expects REMAINING seconds in param2 (it records the remaining time).
        // Non-tower scripts historically used elapsed; keep that unless this is a tower dungeon.
        int param2 = finishedTime;
        boolean towerDungeon = dungeonManager != null && dungeonManager.isTowerDungeon();
        if (towerDungeon && this.timeLimit > 0) {
            param2 = Math.max(0, this.timeLimit - this.finishedTime);
            Grasscutter.getLogger()
                    .info(
                            "Tower challenge success param2=remaining {} (elapsed={}, limit={})",
                            param2,
                            finishedTime,
                            this.timeLimit);
            // Pin remaining on TowerManager — lower-half ActiveChallenge reads this if TPL_TIME races.
            if (!scene.getPlayers().isEmpty()) {
                var tm = scene.getPlayers().get(0).getTowerManager();
                if (tm != null) {
                    tm.rememberAbyssCarryRemaining(param2);
                }
            }
        }
        var challengeSuccess =
                scriptManager.callEvent(
                        new ScriptArgs(this.getGroup().id, EventType.EVENT_CHALLENGE_SUCCESS)
                                .setParam2(param2)
                                .setEventSource(this.getChallengeIndex()));

        // Lua TowerMirrorTeamSetUp / stage=1 is async via callEvent. Pass-condition settle must
        // wait, or the chamber settles on the upper half and the mid-half team swap is cancelled.
        if (towerDungeon && challengeSuccess != null) {
            try {
                challengeSuccess.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Throwable t) {
                Grasscutter.getLogger()
                        .warn(
                                "Tower wait EVENT_CHALLENGE_SUCCESS failed: {}",
                                t.toString());
            }
        }

        this.getScene()
                .triggerDungeonEvent(
                        DungeonPassConditionType.DUNGEON_COND_FINISH_CHALLENGE,
                        getChallengeId(),
                        getChallengeIndex());

        this.challengeTriggers.forEach(t -> t.onFinish(this));
    }

    public void fail() {
        if (!this.inProgress()) return;
        this.finish(false);

        // TODO: Set 'eventSource' in script arguments.
        var scriptManager = this.getScene().getScriptManager();
        scriptManager.callEvent(
                new ScriptArgs(this.getGroup().id, EventType.EVENT_CHALLENGE_FAIL)
                        .setEventSource(this.getChallengeIndex()));
        challengeTriggers.forEach(t -> t.onFinish(this));
    }

    /**
     * Stop the challenge HUD without firing {@code EVENT_CHALLENGE_FAIL} and without despawning
     * monsters (despawn can make Lua ActiveChallenge again and kill the team-config UI).
     */
    public void abortQuiet() {
        this.progress = false;
        this.success = false;
        this.finishedTime =
                Math.max(0, (int) (this.scene.getSceneTimeSeconds() - this.startedAt));
        getScene().broadcastPacket(new PacketDungeonChallengeFinishNotify(this));
    }

    /**
     * Mid-half handoff: clear the upper-half challenge HUD as a success without re-firing Lua
     * success events or despawning (tide already unloaded by TowerMirrorTeamSetUp).
     */
    public void finishSuccessQuiet() {
        this.progress = false;
        this.success = true;
        this.finishedTime =
                Math.max(0, (int) (this.scene.getSceneTimeSeconds() - this.startedAt));
        getScene().broadcastPacket(new PacketDungeonChallengeFinishNotify(this));
    }

    private void finish(boolean success) {
        this.progress = false;
        this.success = success;
        this.finishedTime = (int) ((this.scene.getSceneTimeSeconds() - this.startedAt));

        // Despawn all leftover mobs in this challenge's SceneGroup
        getScene().getScriptManager().removeMonstersInGroup(group);

        getScene().broadcastPacket(new PacketDungeonChallengeFinishNotify(this));
        if (success) {
            DomainRewardStatueHelper.onChallengeSuccess(getScene());
        }
    }

    public int increaseScore() {
        return score.incrementAndGet();
    }

    public int getGuardEntityHpPercent() {
        if (guardEntity == null) {
            Grasscutter.getLogger()
                    .warn(
                            "getGuardEntityHpPercent: Could not find guardEntity for this challenge = {}", this);
            return 100;
        }

        var curHp = guardEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        var maxHp = guardEntity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 0f) {
            // No usable max hp to scale against - treat the entity as undamaged rather than
            // reporting 0% and instantly failing the challenge.
            return 100;
        }
        return (int) (curHp * 100 / maxHp);
    }

    public void onMonsterDeath(EntityMonster monster) {
        if (!inProgress()) {
            return;
        }
        if (monster.getGroupId() != getGroup().id) {
            return;
        }
        this.challengeTriggers.forEach(t -> t.onMonsterDeath(this, monster));
    }

    public void onGadgetDeath(EntityGadget gadget) {
        if (!inProgress()) {
            return;
        }
        if (gadget.getGroupId() != getGroup().id) {
            return;
        }
        this.challengeTriggers.forEach(t -> t.onGadgetDeath(this, gadget));
    }

    public void onGroupTriggerDeath(SceneTrigger trigger) {
        if (!this.inProgress()) return;

        var triggerGroup = trigger.getCurrentGroup();
        if (triggerGroup == null || triggerGroup.id != getGroup().id) {
            return;
        }

        this.challengeTriggers.forEach(t -> t.onGroupTrigger(this, trigger));
    }

    public void onGadgetDamage(EntityGadget gadget) {
        if (!inProgress()) {
            return;
        }
        if (gadget.getGroupId() != getGroup().id) {
            return;
        }
        this.challengeTriggers.forEach(t -> t.onGadgetDamage(this, gadget));
    }
}
