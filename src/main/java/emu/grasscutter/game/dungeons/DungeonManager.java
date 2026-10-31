package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.dungeon.*;
import emu.grasscutter.game.activity.trialavatar.TrialAvatarActivityHandler;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.tps.TpsAvatarSystem;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.event.player.PlayerFinishDungeonEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.stream.*;
import javax.annotation.Nullable;
import lombok.*;

/**
 * TODO handle time limits TODO handle respawn points TODO handle team wipes and respawns TODO check
 * monster level and levelConfigMap
 */
public final class DungeonManager {
    @Getter private final Scene scene;
    @Getter private final DungeonData dungeonData;
    @Getter private final DungeonPassConfigData passConfigData;

    @Getter private final int[] finishedConditions;
    private final IntSet rewardedPlayers = new IntOpenHashSet();
    private final DomainClaimRunIdentity claimRunIdentity = new DomainClaimRunIdentity();

    synchronized String getClaimOperationId() {
        return claimRunIdentity.operationId(dungeonData.getId());
    }

    // Called only after a proven unpaid attempt, under the claim monitor.
    synchronized void rotateClaimIdentityAfterUnpaidAttempt() {
        claimRunIdentity.restart();
    }

    /** The claim service uses this set while holding the DungeonManager monitor. */
    IntSet getRewardedPlayersForClaims() {
        return rewardedPlayers;
    }


    /** True only after a successful petrified-tree reward claim in this dungeon run. */
    synchronized boolean hasClaimedDomainReward(int uid) {
        return rewardedPlayers.contains(uid);
    }
    private final Set<Integer> activeDungeonWayPoints = new HashSet<>();
    private boolean ended = false;
    private int newestWayPoint = 0;
    @Getter private int startSceneTime = 0;
    @Setter @Getter private boolean towerDungeon = false;

    DungeonTrialTeam trialTeam = null;

    public DungeonManager(@NonNull Scene scene, @NonNull DungeonData dungeonData) {
        this.scene = scene;
        this.dungeonData = dungeonData;
        if (dungeonData.getPassCond() == 0) {
            this.passConfigData = new DungeonPassConfigData();
            this.passConfigData.setConds(new ArrayList<>());
        } else {
            this.passConfigData = GameData.getDungeonPassConfigDataMap().get(dungeonData.getPassCond());
        }
        this.finishedConditions = new int[this.passConfigData.getConds().size()];
    }

    public void triggerEvent(DungeonPassConditionType conditionType, int... params) {
        if (ended) {
            return;
        }
        for (int i = 0; i < passConfigData.getConds().size(); i++) {
            var cond = passConfigData.getConds().get(i);
            if (conditionType == cond.getCondType()) {
                if (getScene().getWorld().getServer().getDungeonSystem().triggerCondition(cond, params)) {
                    finishedConditions[i] = 1;
                }
            }
        }

        if (isFinishedSuccessfully()) {
            // Spiral Abyss mid-half: upper challenge success can satisfy pass conditions before
            // the lower team is applied. Settling now advances the chamber and cancels the swap.
            if (isTowerDungeon() && !scene.getPlayers().isEmpty()) {
                var tower = scene.getPlayers().get(0).getTowerManager();
                if (tower != null && tower.isMidHalfCutscenePending()) {
                    Grasscutter.getLogger()
                            .info(
                                    "Tower finishDungeon deferred uid={} - mid-half team swap pending",
                                    scene.getPlayers().get(0).getUid());
                    return;
                }
                if (scene.getLoadedGroups().stream()
                        .anyMatch(
                                g -> {
                                    var variables = scene.getScriptManager().getVariables(g.id);
                                    return variables != null
                                            && variables.containsKey("stage")
                                            && Integer.valueOf(1).equals(variables.get("stage"));
                                })) {
                    Grasscutter.getLogger()
                            .info(
                                    "Tower finishDungeon deferred uid={} - lua stage=1 (lower half pending)",
                                    scene.getPlayers().get(0).getUid());
                    return;
                }
            }
            finishDungeon();
        }
    }

    public boolean isFinishedSuccessfully() {
        // Missing pass conditions mean completion is controlled by quest/script actions.
        // An empty quest-style AND must not settle the dungeon on its first unrelated event.
        if (passConfigData.getConds() == null || passConfigData.getConds().isEmpty()) return false;
        return LogicType.calculate(passConfigData.getLogicType(), finishedConditions);
    }

    public int getLevelForMonster(int id) {
        if (isTowerDungeon()) {
            // Tower dungeons have their own level setting in TowerLevelData
            return scene.getPlayers().get(0).getTowerManager().getCurrentMonsterLevel();
        } else {
            // TODO should use levelConfigMap? and how?
            return dungeonData.getShowLevel();
        }
    }

    public boolean isWayPointActive(int pointId) {
        return activeDungeonWayPoints.contains(pointId);
    }

    public boolean activateRespawnPoint(int pointId) {
        val respawnPoint = GameData.getScenePointEntryById(scene.getId(), pointId);

        if (respawnPoint == null) {
            Grasscutter.getLogger().warn("trying to activate unknown respawn point {}", pointId);
            return false;
        }

        scene.broadcastPacket(
                new PacketDungeonWayPointNotify(
                        activeDungeonWayPoints.add(pointId), activeDungeonWayPoints));
        newestWayPoint = pointId;

        Grasscutter.getLogger().debug("[unimplemented respawn] activated respawn point {}", pointId);
        return true;
    }

    @Nullable public Position getRespawnLocation() {
        if (newestWayPoint == 0) { // validity is checked before setting it, so if != 0 its always valid
            return null;
        }
        var pointData = GameData.getScenePointEntryById(scene.getId(), newestWayPoint).getPointData();
        return pointData.getTranPos() != null ? pointData.getTranPos() : pointData.getPos();
    }

    public Position getRespawnRotation() {
        if (newestWayPoint == 0) { // validity is checked before setting it, so if != 0 its always valid
            return null;
        }
        val pointData = GameData.getScenePointEntryById(scene.getId(), newestWayPoint).getPointData();
        return pointData.getRot() != null ? pointData.getRot() : null;
    }

    /** Keep legacy callers on the same guarded, receipted claim path as GadgetRewardStatue. */
    public boolean getStatueDrops(Player player, boolean useCondensed, int groupId) {
        return DomainStatueDropService.claim(
                player, this,
                useCondensed
                        ? DomainStatueClaimHelper.ClaimMode.CONDENSE
                        : DomainStatueClaimHelper.ClaimMode.NORMAL_1X,
                groupId,
                useCondensed ? 2 : 1);
    }

    public boolean handleCost(Player player, boolean useCondensed) {
        int resinCost = dungeonData.getStatueCostCount() != 0 ? dungeonData.getStatueCostCount() : 20;
        if (resinCost == 0) {
            return true;
        }
        if (useCondensed) {
            // Check if condensed resin is usable here.
            // For this, we use the following logic for now:
            // The normal resin cost of the dungeon has to be 20.
            if (resinCost != 20) {
                return false;
            }

            // Spend the condensed resin and only proceed if the transaction succeeds.
            return player.getResinManager().useCondensedResin(1);
        } else if (dungeonData.getStatueCostID() == 106) {
            // Spend the resin and only proceed if the transaction succeeds.
            return player.getResinManager().useResin(resinCost);
        }
        return true;
    }

    private List<GameItem> rollRewards(boolean useCondensed) {
        List<GameItem> rewards = new ArrayList<>();
        int dungeonId = this.dungeonData.getId();
        // If we have specific drop data for this dungeon, we use it.
        if (GameData.getDungeonDropDataMap().containsKey(dungeonId)) {
            List<DungeonDropEntry> dropEntries = GameData.getDungeonDropDataMap().get(dungeonId);

            // Roll for each drop group.
            for (var entry : dropEntries) {
                // Determine the number of drops we get for this entry.
                int start = entry.getCounts().get(0);
                int end = entry.getCounts().get(entry.getCounts().size() - 1);
                var candidateAmounts = IntStream.range(start, end + 1).boxed().collect(Collectors.toList());

                int amount = Utils.drawRandomListElement(candidateAmounts, entry.getProbabilities());

                if (useCondensed) {
                    amount += Utils.drawRandomListElement(candidateAmounts, entry.getProbabilities());
                }

                // Double rewards in multiply mode, if specified.
                if (entry.isMpDouble() && this.getScene().getPlayerCount() > 1) {
                    amount *= 2;
                }

                // Roll items for this group.
                // Here, we have to handle stacking, or the client will not display results correctly.
                // For now, we use the following logic: If the possible drop item are a list of multiple
                // items,
                // we roll them separately. If not, we stack them. This should work out in practice, at
                // least
                // for the currently existing set of dungeons.
                if (entry.getItems().size() == 1) {
                    rewards.add(new GameItem(entry.getItems().get(0), amount));
                } else {
                    for (int i = 0; i < amount; i++) {
                        // int itemIndex = ThreadLocalRandom.current().nextInt(0, entry.getItems().size());
                        // int itemId = entry.getItems().get(itemIndex);
                        int itemId =
                                Utils.drawRandomListElement(entry.getItems(), entry.getItemProbabilities());
                        rewards.add(new GameItem(itemId, 1));
                    }
                }
            }
        }
        // Otherwise, we fall back to the preview data.
        else {
            Grasscutter.getLogger()
                    .info("No drop data found or dungeon {}, falling back to preview data ...", dungeonId);
            for (ItemParamData param : dungeonData.getRewardPreviewData().getPreviewItems()) {
                rewards.add(new GameItem(param.getId(), Math.max(param.getCount(), 1)));
            }
        }

        return rewards;
    }

    public void applyTrialTeam(Player player) {
        if (getDungeonData() == null) return;

        // TPS dungeons only allow the TPS traveler, which differs per player (boy or girl).
        var tpsTeam = TpsAvatarSystem.getTrialTeam(player, this.scene);
        if (tpsTeam != null) {
            this.trialTeam = tpsTeam;
            player.getTeamManager().addTrialAvatars(tpsTeam.getTrialAvatarIds());
            return;
        }

        // Types this server does not know load as null.
        if (getDungeonData().getType() == null) return;

        switch (getDungeonData().getType()) {
                // case DUNGEON_PLOT is handled by quest execs
            case DUNGEON_ACTIVITY -> {
                switch (getDungeonData().getPlayType()) {
                    case DUNGEON_PLAY_TYPE_TRIAL_AVATAR -> {
                        val activityHandler =
                                player
                                        .getActivityManager()
                                        .getActivityHandlerAs(
                                                ActivityType.NEW_ACTIVITY_TRIAL_AVATAR, TrialAvatarActivityHandler.class);
                        activityHandler.ifPresent(
                                trialAvatarActivityHandler ->
                                        this.trialTeam = trialAvatarActivityHandler.getTrialAvatarDungeonTeam());
                    }
                }
            }
            case DUNGEON_ELEMENT_CHALLENGE -> {} // TODO
        }

        if (this.trialTeam != null) {
            player.getTeamManager().addTrialAvatars(trialTeam.trialAvatarIds);
        }
    }

    public void unsetTrialTeam(Player player) {
        if (this.trialTeam == null) return;

        player.getTeamManager().removeTrialAvatar();
        this.trialTeam = null;
    }

    public void startDungeon() {
        this.startSceneTime = scene.getSceneTimeSeconds();
        scene
                .getPlayers()
                .forEach(
                        p -> {
                            p.getQuestManager()
                                    .queueEvent(QuestContent.QUEST_CONTENT_ENTER_DUNGEON, dungeonData.getId());
                            applyTrialTeam(p);
                        });
    }

    public void finishDungeon() {
        // Set ended first: EVENT_DUNGEON_SETTLE can re-enter, and reward statues call this again.
        if (ended) return;
        ended = true;
        this.notifyEndDungeon(true);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.COMPLETED);

        // Call PlayerFinishDungeonEvent.
        new PlayerFinishDungeonEvent(this.getScene().getPlayers(), this.getScene(), this).call();

        // jump players to next dungeon if available
        if (this.dungeonData.getPassJumpDungeon() != 0) {
            for (var player : this.getScene().getPlayers()) {
                player
                        .getServer()
                        .getDungeonSystem()
                        .enterDungeon(player, 0, this.dungeonData.getPassJumpDungeon(), false);
            }
        }
    }

    public void quitDungeon() {
        // Leaving a dungeon that already settled - many have no pass condition and are completed by
        // their script - is not a quit, and must not fail the quests waiting on it.
        if (this.ended) return;
        this.notifyEndDungeon(false);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.QUIT);
    }

    public void failDungeon() {
        this.notifyEndDungeon(false);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.FAILED);
    }

    public void notifyEndDungeon(boolean successfully) {
        scene
                .getPlayers()
                .forEach(
                        p -> {
                            // Trigger the fail and success event.
                            if (successfully) {
                                var dungeonId = this.getDungeonData().getId();
                                p.getPlayerProgress().markDungeonAsComplete(dungeonId);
                                try {
                                    emu.grasscutter.game.player.InvestigationHandbookHelper.trigger(
                                            p,
                                            WatcherTriggerType.TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD,
                                            dungeonId,
                                            1);
                                } catch (Throwable ignored) {
                                }
                            } else {
                                p.getQuestManager()
                                        .queueEvent(QuestContent.QUEST_CONTENT_FAIL_DUNGEON, dungeonData.getId());
                            }

                            // Battle pass trigger
                            if (dungeonData.getType().isCountsToBattlepass() && successfully) {
                                p.getBattlePassManager().triggerMission(WatcherTriggerType.TRIGGER_FINISH_DUNGEON);
                            }
                        });
        var future =
                scene
                        .getScriptManager()
                        .callEvent(new ScriptArgs(0, EventType.EVENT_DUNGEON_SETTLE, successfully ? 1 : 0));
        // Note: There is a possible race condition with calling
        //       EVENT_DUNGEON_SETTLE here asynchronously:
        // 1. EVENT_DUNGEON_SETTLE triggers some Lua-side logic,
        //    which may happen after 2 (below) finishes.
        // 2. Some DungeonSettleListener could be comparing some
        //    Lua variable before its setting in 1 (above) finishes.
        // For safety, ensure all events have finished before returning.
        try {
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (successfully) {
            try {
                WeeklyBossModelCleanup.sweepAfterSettle(scene);
            } catch (Throwable ignored) {
            }
        }
    }

    public void endDungeon(BaseDungeonResult.DungeonEndReason endReason) {
        if (scene.getDungeonSettleListeners() != null) {
            scene.getDungeonSettleListeners().forEach(o -> o.onDungeonSettle(this, endReason));
        }
        if (isTowerDungeon()) {
            scene.getPlayers().get(0).getTowerManager().onEnd();
        }
        ended = true;
    }

    public synchronized void restartDungeon() {
        this.scene.setKilledMonsterCount(0);
        this.rewardedPlayers.clear();
        this.claimRunIdentity.restart();
        Arrays.fill(finishedConditions, 0);
        this.ended = false;
        this.activeDungeonWayPoints.clear();
    }

    public void cleanUpScene() {
        this.scene.setDungeonManager(null);
        this.scene.setKilledMonsterCount(0);
    }
}
