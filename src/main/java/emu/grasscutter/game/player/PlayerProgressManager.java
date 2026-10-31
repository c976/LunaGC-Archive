package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static emu.grasscutter.scripts.constants.EventType.EVENT_UNLOCK_TRANS_POINT;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.excels.OpenStateData;
import emu.grasscutter.data.excels.OpenStateData.OpenStateCondType;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

// @Entity
public final class PlayerProgressManager extends BasePlayerDataManager {
    /******************************************************************************************************************
     ******************************************************************************************************************
     * OPEN STATES
     ******************************************************************************************************************
     *****************************************************************************************************************/

    // Previously blacklisted OPEN_STATE_LIMIT_REGION_GLOBAL (48). Remote LunaGC forces it ON when
    // questing is disabled; keeping it blacklisted prevented default unlock and contributed to
    // region gating. Empty set = match remote unlock behaviour.
    public static final Set<Integer> BLACKLIST_OPEN_STATES = Set.of();

    /**
     * Hidden quests that gate open states but can never start: their 7.1 accept condition is the
     * exported "quest 0 finished" placeholder. Each finishes on the same talk as the shown quest it
     * maps to, so that quest finishing stands in for it.
     *
     * <pre>
     * 2010001 (Wish, 31)                  talk 35801 -> 35802
     * 35001   (LIMIT_REGION_FRESHMEAT 47) talks 30902/30904 -> 31101
     * </pre>
     */
    static final java.util.Map<Integer, Integer> HIDDEN_QUEST_SIBLINGS =
            java.util.Map.of(2010001, 35802, 35001, 31101);

    /**
     * OPEN_STATE_LIMIT_REGION_GLOBAL waits on unreleased test quest 99902, so the server owns it, as
     * LunaGC does; without it the 7.0 world region never opens past the starter boundary.
     */
    static final int OPEN_STATE_LIMIT_REGION_GLOBAL = 48;

    public static final Set<Integer> IGNORED_OPEN_STATES =
            Set.of(
                    1404 // OPEN_STATE_MENGDE_INFUSEDCRYSTAL, causes quest 'Mine Craft' to be given to the
                    // player at the start of the game.
                    // This should be removed when city reputation is implemented.
                    );

    /**
     * Login fallback states, only for genuine resource defaults plus the Paimon menu needed
     * for chat. Quest-, level- and region-gated features use the runtime condition checker.
     */
    public static final Set<Integer> DEFAULT_OPEN_STATES =
            GameData.getOpenStateList().stream()
                    .filter(PlayerProgressManager::isDefaultOpenState)
                    .map(OpenStateData::getId)
                    .filter(id -> !BLACKLIST_OPEN_STATES.contains(id))
                    .filter(id -> !IGNORED_OPEN_STATES.contains(id))
                    .collect(Collectors.toSet());

    static boolean isDefaultOpenState(OpenStateData state) {
        return (state.isDefaultState() && !state.isAllowClientOpen())
                || state.getId() == 1;
    }

    /**
     * Automatic progression needs real server-owned conditions or a genuine default.
     * An absent/unknown condition must not unlock a non-default feature at login.
     */
    static boolean isAutomaticOpenStateCandidate(OpenStateData state) {
        return !state.isAllowClientOpen()
                && (state.isDefaultState()
                        || (state.getCond() != null
                                && state.getCond().stream().anyMatch(c -> c.getCondType() != null)));
    }

    public PlayerProgressManager(Player player) {
        super(player);
    }

    record TransPointUnlockTransition(boolean changed, boolean newlyUnlocked, boolean wasForceLocked) {}

    /** Only a confirmed payout (including an intentional zero payout) clears pending state. */
    static boolean settlePendingTransPointReward(
            Set<Integer> pending, int pointId, java.util.function.BooleanSupplier grant) {
        if (!pending.contains(pointId) || !grant.getAsBoolean()) return false;
        pending.remove(pointId);
        return true;
    }

    static boolean applyQuestPointLock(Set<Integer> forceLockedPoints, int pointId) {
        return forceLockedPoints.add(pointId);
    }

    static TransPointUnlockTransition applyTransPointUnlock(
            Set<Integer> unlockedPoints, Set<Integer> forceLockedPoints, int pointId) {
        boolean wasForceLocked = forceLockedPoints.remove(pointId);
        boolean newlyUnlocked = unlockedPoints.add(pointId);
        return new TransPointUnlockTransition(
                wasForceLocked || newlyUnlocked, newlyUnlocked, wasForceLocked);
    }

    /**********
     * Handler for player login.
     **********/
    public void onPlayerLogin() {
        // Try unlocking open states on player login. This handles accounts where unlock conditions were
        // already met before certain open state unlocks were implemented.
        this.tryUnlockOpenStates(false);
        if (this.getOpenState(OPEN_STATE_LIMIT_REGION_GLOBAL) == 0) {
            this.setOpenState(OPEN_STATE_LIMIT_REGION_GLOBAL, 1, false);
        }

        // Disabling quests must not activate the first Statue of the Seven or reveal
        // its map area. Point (3,7) is unlocked only by normal gameplay/commands.

        if (GAME_OPTIONS.unlockAllOpenStatesOnLogin) {
            // Sandbox opt-in: preserve the former bulk unlock, but never tie it to questing.
            // Do this before OpenStateUpdateNotify so the login snapshot is complete.
            this.setOpenState(47, 1, false);
            this.setOpenState(48, 1, false);
            this.setOpenState(1101, 1, false);
            this.setOpenState(1102, 1, false);

            int unlocked = 0;
            for (var openState : GameData.getOpenStateList()) {
                int id = openState.getId();
                if (BLACKLIST_OPEN_STATES.contains(id) || IGNORED_OPEN_STATES.contains(id)) {
                    continue;
                }
                if (this.getOpenState(id) == 0) {
                    unlocked++;
                }
                // Always force 1 into the player map so OpenStateUpdateNotify below is complete
                // even for accounts that already had sparse/partial maps.
                this.player.getOpenStates().put(id, 1);
            }
            emu.grasscutter.Grasscutter.getLogger()
                    .debug(
                            "Configured OpenState bulk unlock uid={} mapSize={} newlySet={}",
                            this.player.getUid(),
                            this.player.getOpenStates().size(),
                            unlocked);
        }

        // Send notify after optional mass unlock so the login snapshot is complete.
        player.getSession().send(new PacketOpenStateUpdateNotify(this.player));

        // Offerings such as the Sacred Sakura: send PlayerOfferingDataNotify on login, otherwise the
        // client never shows the F prompt.
        try {
            emu.grasscutter.game.entity.gadget.OfferingHelper.onPlayerLogin(this.player);
        } catch (Throwable ignored) {
        }

        // Add statue quests if necessary.
        this.addStatueQuestsOnLogin();

        // Ensure spring volume props exist before first statue EnterTrans (tip/heal path).
        try {
            var sots = this.player.getSotsManager();
            if (sots.getMaxVolume() <= 0) {
                sots.setMaxVolume(Math.min(8500000, 5000 * 100)); // 5 statues worth baseline
            }
            if (sots.getCurrentVolume() <= 0) {
                sots.setCurrentVolume(sots.getMaxVolume());
            }
        } catch (Throwable ignored) {
        }

        // Retry only activations that were explicitly persisted as unpaid.
        retryPendingTransPointRewards();
    }

    /**********
     * Direct getters and setters for open states.
     **********/
    public int getOpenState(int openState) {
        return this.player.getOpenStates().getOrDefault(openState, 0);
    }

    private void setOpenState(int openState, int value, boolean sendNotify) {
        int previousValue = this.player.getOpenStates().getOrDefault(openState, -1 /* non-existent */);

        if (value != previousValue) {
            this.player.getOpenStates().put(openState, value);

            this.player
                    .getQuestManager()
                    .queueEvent(QuestCond.QUEST_COND_OPEN_STATE_EQUAL, openState, value);

            if (sendNotify) {
                player.getSession().send(new PacketOpenStateChangeNotify(openState, value));
            }
        }
    }

    private void setOpenState(int openState, int value) {
        this.setOpenState(openState, value, true);
    }

    /**********
     * Condition checking for setting open states.
     **********/
    private boolean isQuestFinished(int questId) {
        var quest = this.player.getQuestManager().getQuestById(questId);
        return quest != null && quest.getState() == QuestState.QUEST_STATE_FINISHED;
    }

    private boolean areConditionsMet(OpenStateData openState) {
        // Check all conditions and test if at least one of them is violated.
        for (var condition : openState.getCond()) {
            switch (condition.getCondType()) {
                    // For level conditions, check if the player has reached the necessary level.
                case OPEN_STATE_COND_PLAYER_LEVEL -> {
                    if (this.player.getLevel() < condition.getParam()) {
                        return false;
                    }
                }
                case OPEN_STATE_COND_QUEST -> {
                    // check sub quest id for quest finished met requirements
                    if (!this.isQuestFinished(condition.getParam())) {
                        int sibling = HIDDEN_QUEST_SIBLINGS.getOrDefault(condition.getParam(), 0);
                        if (sibling == 0 || !this.isQuestFinished(sibling)) return false;
                    }
                }
                case OPEN_STATE_COND_PARENT_QUEST -> {
                    // check main quest id for quest finished met requirements
                    // TODO not sure if its having or finished quest
                    var mainQuest = this.player.getQuestManager().getMainQuestById(condition.getParam());
                    if (mainQuest == null
                            || mainQuest.getState() != ParentQuestState.PARENT_QUEST_STATE_FINISHED) {
                        return false;
                    }
                }
                    // ToDo: Implement.
                case OPEN_STATE_OFFERING_LEVEL, OPEN_STATE_CITY_REPUTATION_LEVEL -> {
                    // Unsupported offering and reputation requirements must not auto-unlock.
                    return false;
                }
            }
        }

        // Done. If we didn't find any violations, all conditions are met.
        return true;
    }

    /**********
     * Setting open states from the client (via `SetOpenStateReq`).
     **********/
    public void setOpenStateFromClient(int openState, int value) {
        // Get the data for this open state.
        OpenStateData data = GameData.getOpenStateDataMap().get(openState);
        if (data == null) {
            this.player.sendPacket(new PacketSetOpenStateRsp(Retcode.RET_FAIL));
            return;
        }

        // Make sure that this is an open state that the client is allowed to set,
        // and that it doesn't have any further conditions attached.
        if (!data.isAllowClientOpen() || !this.areConditionsMet(data)) {
            this.player.sendPacket(new PacketSetOpenStateRsp(Retcode.RET_FAIL));
            return;
        }

        // Set.
        this.setOpenState(openState, value);
        this.player.sendPacket(new PacketSetOpenStateRsp(openState, value));
    }

    /** This force sets an open state, ignoring all conditions and permissions */
    public void forceSetOpenState(int openState, int value) {
        this.setOpenState(openState, value);
    }

    /**********
     * Triggered unlocking of open states (unlock states whose conditions have been met.)
     **********/
    public void tryUnlockOpenStates(boolean sendNotify) {
        // Get list of open states that are not yet unlocked.
        var lockedStates =
                GameData.getOpenStateList().stream()
                        .filter(s -> this.player.getOpenStates().getOrDefault(s.getId(), 0) == 0)
                        .toList();

        // Try unlocking all of them.
        for (var state : lockedStates) {
            // Client-owned, conditionless non-default and unsupported states stay locked.
            if (isAutomaticOpenStateCandidate(state)
                    && this.areConditionsMet(state)
                    && !BLACKLIST_OPEN_STATES.contains(state.getId())
                    && !IGNORED_OPEN_STATES.contains(state.getId())) {
                this.setOpenState(state.getId(), 1, sendNotify);
            }
        }
    }

    public void tryUnlockOpenStates() {
        this.tryUnlockOpenStates(true);
    }

    /******************************************************************************************************************
     ******************************************************************************************************************
     * MAP AREAS AND POINTS
     ******************************************************************************************************************
     *****************************************************************************************************************/
    private void addStatueQuestsOnLogin() {
        var data = GameData.getMainQuestDataMap().get(303);
        if (data == null || data.getSubQuests() == null) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn("Missing statue activation quest 303 for uid={}", this.player.getUid());
            return;
        }

        var quests = this.player.getQuestManager();
        var main = quests.getMainQuestById(303);
        if (main == null) {
            quests.addQuest(30302);
            main = quests.getMainQuestById(303);
        }
        if (main == null) return;

        // Quest 303 owns activation. Never rewrite completed quests or scene-point state at login.
        for (var sub : data.getSubQuests()) {
            var quest = main.getChildQuestById(sub.getSubId());
            if (quest != null && quest.getState() == QuestState.QUEST_STATE_UNSTARTED) {
                quests.addQuest(sub.getSubId());
            }
        }
    }

    /**
     * [v32] 把头像表里“需要任务/道具/角色才能解锁”的条目，在客户端侧全部伪造为已满足，
     * 让头像选择界面不再画锁：
     *  - BY_PARENT_QUEST -> forgeQuest(questId, 0, 3=FINISHED)
     *  - BY_ITEM        -> 直接发放对应道具
     *  - BY_AVATAR      -> 跳过
     */
    public void forgeProfilePictureUnlocks() {
        try {
            java.util.List<Integer> quests = new java.util.ArrayList<>();
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            int items = 0, avatars = 0;
            for (BeyondProfilePictureTable.Entry e : BeyondProfilePictureTable.all()) {
                if (e.unlockParam <= 0) continue;
                String t = e.unlockType == null ? "" : e.unlockType;
                if (t.contains("BY_PARENT_QUEST")) {
                    if (seen.add(e.unlockParam)) {
                        quests.add(e.unlockParam);
                    }
                } else if (t.contains("BY_ITEM")) {
                    try {
                        boolean ok = this.player.getInventory().addItem(e.unlockParam, 1);
                        if (ok) {
                            items++;
                            var gi = this.player.getInventory().getItemById(e.unlockParam);
                            if (gi != null) {
                                var used =
                                        this.player
                                                .getServer()
                                                .getInventorySystem()
                                                .useItem(this.player, 0, gi.getGuid(), 1, 0, false);
                                if (used != null) {
                                    this.player.sendPacket(
                                            new emu.grasscutter.server.packet.send.PacketUseItemRsp(
                                                    0, used));
                                    this.player.getInventory().addItem(e.unlockParam, 1);
                                }
                            }
                        }
                    } catch (Throwable ex) {
                        emu.grasscutter.Grasscutter.getLogger()
                                .warn("MIAO BY_ITEM fail item={} {}", e.unlockParam, ex.toString());
                    }
                }
            }
            if (!quests.isEmpty()) {
                int[] ids = new int[quests.size()];
                for (int i = 0; i < ids.length; i++) ids[i] = quests.get(i);
                this.player.sendPacket(new emu.grasscutter.server.packet.send.PacketFinishedParentQuestUpdateNotify(ids));
            }
            try {
                this.player.sendPacket(new emu.grasscutter.server.packet.send.PacketProfilePictureUnlockNotify(this.player));
            } catch (Throwable t) {
                emu.grasscutter.Grasscutter.getLogger().warn("MIAO unlockNotify fail: {}", t.toString());
            }
            emu.grasscutter.Grasscutter.getLogger()
                    .debug("MIAO forgeProfilePicture parentQuests={} items={} avatars={}", quests.size(), items, avatars);
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger().warn("MIAO forgeProfilePicture fail: {}", t.toString());
        }
    }

    /** Refresh post-activation interactions without modifying quest or area progression. */
    public void refreshStatueGoddessInteractions(int sceneId, int pointId) {
        var entry = GameData.getScenePointEntryById(sceneId, pointId);
        if (entry == null || entry.getPointData() == null
                || !emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(entry.getPointData())
                || !this.player.getUnlockedScenePoints(sceneId).contains(pointId)
                || this.player.isScenePointForceLocked(sceneId, pointId)) return;
        this.refreshStatueGoddessNpc(sceneId, pointId);
    }

    /**
     * Load goddess NPC suite once per visit (F tip). Always unload on exit - leaving suites
     * loaded is what made Fontaine ambient/SFX intermittent after visiting a statue.
     *
     * <p>Nod-Krai City 7 (1515-1517) has no SceneNpcBorn for 790x - spawn an invisible goddess
     * NPC entity at the statue as fallback.
     */
    public void refreshStatueGoddessNpc(int sceneId, int pointId) {
        var spe = GameData.getScenePointEntryById(sceneId, pointId);
        if (spe == null || spe.getPointData() == null) return;
        var pd = spe.getPointData();
        if (!emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(pd)) return;

        int npcId = pd.getNpcId();
        if (npcId <= 0) {
            npcId = emu.grasscutter.game.managers.StatueTalkQuests.npcForArea(pd.getAreaId());
        }
        var statuePos = pd.getPos() != null ? pd.getPos() : pd.getTranPos();

        var bornData = GameData.getSceneNpcBornData().get(sceneId);
        if (bornData != null && bornData.getBornPosList() != null) {
            for (var born : bornData.getBornPosList()) {
                boolean match =
                        (npcId > 0 && born.getConfigId() == npcId)
                                || (statuePos != null
                                        && born.getPos() != null
                                        && statuePos.computeDistance(born.getPos()) < 5f
                                        && emu.grasscutter.game.managers.StatueTalkQuests
                                                .isGoddessNpc(born.getConfigId()));
                if (!match) continue;
                if (born.getGroupId() <= 0) continue;

                var notified = this.player.getGoddessSuiteNotified();
                if (!notified.add(born.getGroupId())) {
                    return; // already loaded this visit
                }

                int suite =
                        (born.getSuiteIdList() != null && !born.getSuiteIdList().isEmpty())
                                ? born.getSuiteIdList().get(0)
                                : 1;
                this.player.sendPacket(
                        new emu.grasscutter.server.packet.send.PacketGroupSuiteNotify(
                                born.getGroupId(), suite));
                emu.grasscutter.Grasscutter.getLogger()
                        .debug(
                                "Force goddess suite uid={} point={} group={} npc={} suite={}",
                                this.player.getUid(),
                                pointId,
                                born.getGroupId(),
                                born.getConfigId(),
                                suite);
                // Natlan Pyro is quest-gated in the CLIENT talk tree ("cannot resonate with Pyro").
                // Spawn a Talk-style Worktop resonate that skips the gate without force-finishing
                // the Archon quest - players keep the related quest receivable, no GM command needed.
                this.maybeSpawnQuestBypassResonateProxy(sceneId, pointId, pd, statuePos);
                return;
            }
        }

        this.spawnStatueGoddessNpc(sceneId, pointId, pd, npcId, statuePos);
    }

    /**
     * For nations whose statue Talk blocks element resonate behind a quest (Natlan Pyro), spawn a
     * small Worktop ring with option {@code 1159} "resonate with the statue" so normal players can switch without
     * {@code /se} and without {@code forcefinish}.
     */
    private void maybeSpawnQuestBypassResonateProxy(
            int sceneId,
            int pointId,
            emu.grasscutter.data.common.PointData pd,
            emu.grasscutter.game.world.Position statuePos) {
        int areaId = pd.getAreaId();
        var sots = this.player.getSotsManager();
        if (sots == null || areaId <= 0) return;
        var city = sots.getCityByAreaId(areaId);
        // City 6 = Natlan (Fire). Extend here if another nation gets a similar client gate.
        if (city == null || city.getCityId() != 6) return;
        if (this.player.getGoddessNpcEntityByPoint().containsKey(pointId)) return;

        var scene = this.player.getScene();
        if (scene == null || scene.getId() != sceneId) return;

        var rot =
                pd.getTranRot() != null
                        ? pd.getTranRot()
                        : (pd.getRot() != null
                                ? pd.getRot()
                                : new emu.grasscutter.game.world.Position());

        var spawnPoints = new java.util.ArrayList<emu.grasscutter.game.world.Position>();
        // 代理只放在锚点（雕像）本体位置，不再跟玩家、不再锦环
        {
            var c0 = pd.getPos() != null ? pd.getPos() : pd.getTranPos();
            if (c0 != null) spawnPoints.add(c0.clone());
        }
        var unique = new java.util.ArrayList<emu.grasscutter.game.world.Position>();
        for (var p : spawnPoints) {
            boolean near = false;
            for (var u : unique) {
                if (u.computeDistance(p) < 2f) {
                    near = true;
                    break;
                }
            }
            if (!near) unique.add(p);
        }
        if (unique.isEmpty()) return;

        int synthGroup = syntheticGoddessGroupId(pointId);
        this.player.getGoddessSuiteNotified().add(synthGroup);

        var entityIds = new java.util.ArrayList<Integer>();
        for (int i = 0; i < unique.size(); i++) {
            var gadget =
                    new emu.grasscutter.game.entity.EntityGadget(
                            scene, 70360001, unique.get(i), rot);
            gadget.setConfigId(pointId * 100 + 50 + i);
            gadget.setGroupId(synthGroup);
            gadget.setInteractEnabled(true);
            gadget.buildContent();
            if (gadget.getContent()
                    instanceof emu.grasscutter.game.entity.gadget.GadgetWorktop worktop) {
                worktop.addWorktopOptions(new int[] {1159}); // resonate with the statue
                worktop.setOnSelectWorktopOptionEvent(
                        (ctx, option) -> {
                            try {
                                this.handleStatueResonate(areaId);
                            } catch (Throwable t) {
                                emu.grasscutter.Grasscutter.getLogger()
                                        .warn(
                                                "Natlan resonate proxy failed uid={} point={}",
                                                this.player.getUid(),
                                                pointId,
                                                t);
                            }
                            return false;
                        });
            }
            scene.addEntity(gadget);
            scene.broadcastPacket(
                    new emu.grasscutter.server.packet.send.PacketWorktopOptionNotify(gadget));
            entityIds.add(gadget.getId());
        }
        this.player.getGoddessNpcEntityByPoint().put(pointId, entityIds);
        emu.grasscutter.Grasscutter.getLogger()
                .debug(
                        "Spawn Natlan pyro-resonate proxies uid={} point={} count={} area={}",
                        this.player.getUid(),
                        pointId,
                        entityIds.size(),
                        areaId);
    }

    /** Synthetic group id for spawned goddess NPCs (avoids colliding with real 133xxxxxx groups). */
    private static int syntheticGoddessGroupId(int pointId) {
        return 910_000_000 + pointId;
    }

    private void spawnStatueGoddessNpc(
            int sceneId,
            int pointId,
            emu.grasscutter.data.common.PointData pd,
            int npcId,
            emu.grasscutter.game.world.Position statuePos) {
        if (npcId <= 0) {
            npcId = emu.grasscutter.game.managers.StatueTalkQuests.npcForArea(pd.getAreaId());
        }

        var scene = this.player.getScene();
        if (scene == null || scene.getId() != sceneId) return;

        int synthGroup = syntheticGoddessGroupId(pointId);
        if (!this.player.getGoddessSuiteNotified().add(synthGroup)) {
            return;
        }
        if (this.player.getGoddessNpcEntityByPoint().containsKey(pointId)) {
            return;
        }

        var rot =
                pd.getTranRot() != null
                        ? pd.getTranRot()
                        : (pd.getRot() != null
                                ? pd.getRot()
                                : new emu.grasscutter.game.world.Position());

        // Thick Nod-Krai pillars block standing on center - ring of proxies + player/tranPos.
        var spawnPoints = new java.util.ArrayList<emu.grasscutter.game.world.Position>();
        // 代理只放在锚点（柱子）本体位置，不再锦 8/12m 大环
        {
            var c0 = pd.getPos() != null ? pd.getPos() : pd.getTranPos();
            if (c0 != null) spawnPoints.add(c0.clone());
        }
        if (spawnPoints.isEmpty()) return;

        // Deduplicate near-identical positions (<2m).
        var unique = new java.util.ArrayList<emu.grasscutter.game.world.Position>();
        for (var p : spawnPoints) {
            boolean near = false;
            for (var u : unique) {
                if (u.computeDistance(p) < 2f) {
                    near = true;
                    break;
                }
            }
            if (!near) unique.add(p);
        }

        int areaId = pd.getAreaId();
        var entityIds = new java.util.ArrayList<Integer>();
        for (int i = 0; i < unique.size(); i++) {
            var spawnPos = unique.get(i);
            var gadget =
                    new emu.grasscutter.game.entity.EntityGadget(scene, 70360001, spawnPos, rot);
            gadget.setConfigId(pointId * 100 + i); // unique config per proxy
            gadget.setGroupId(synthGroup);
            gadget.setInteractEnabled(true);
            gadget.buildContent();
            if (gadget.getContent()
                    instanceof emu.grasscutter.game.entity.gadget.GadgetWorktop worktop) {
                // Client OptionExcel labels (stock client):
                // 10010001 = touch (Talk) - heal + offer
                // 1159 = resonate with the statue (Talk) - Traveler element resonate
                // Do NOT use 1/2 (open door / unlock wind field gear icons).
                worktop.addWorktopOptions(new int[] {10010001, 1159});
                worktop.setOnSelectWorktopOptionEvent(
                        (ctx, option) -> {
                            try {
                                this.handleStatueProxyOption(areaId, sceneId, option);
                            } catch (Throwable t) {
                                emu.grasscutter.Grasscutter.getLogger()
                                        .warn(
                                                "Statue proxy option failed uid={} point={} opt={}",
                                                this.player.getUid(),
                                                pointId,
                                                option,
                                                t);
                            }
                            return false;
                        });
            }
            scene.addEntity(gadget);
            scene.broadcastPacket(
                    new emu.grasscutter.server.packet.send.PacketWorktopOptionNotify(gadget));
            entityIds.add(gadget.getId());
        }

        this.player.getGoddessNpcEntityByPoint().put(pointId, entityIds);
        emu.grasscutter.Grasscutter.getLogger()
                .debug(
                        "Spawn statue worktop proxies uid={} point={} npc={} count={} area={}",
                        this.player.getUid(),
                        pointId,
                        npcId,
                        entityIds.size(),
                        areaId);
    }

    /**
     * Statue Worktop proxy (Nod-Krai / Snezhnaya). Uses client OptionExcel ids so the F-menu
     * shows Talk-style labels instead of the gear "open door / unlock wind field" ones:
     * <ul>
     *   <li>{@code 10010001} touch - heal + offer (plus auto-resonate if the Traveler is on-field)
     *   <li>{@code 1159} resonate with the statue - Traveler element resonate only
     * </ul>
     */
    private void handleStatueProxyOption(int areaId, int sceneId, int option) {
        // 1159 = resonate only; also accept legacy option 2 for old sessions.
        if (option == 1159 || option == 2) {
            this.handleStatueResonate(areaId);
            return;
        }

        var sots = this.player.getSotsManager();
        if (sots == null) return;

        int beforeHpSum = 0;
        for (var avatar : this.player.getTeamManager().getActiveTeam()) {
            beforeHpSum +=
                    (int) avatar.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP);
        }
        sots.refillSpringVolume();
        for (var avatar : this.player.getTeamManager().getActiveTeam()) {
            sots.checkAndHealAvatar(avatar);
        }
        int afterHpSum = 0;
        for (var avatar : this.player.getTeamManager().getActiveTeam()) {
            afterHpSum +=
                    (int) avatar.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP);
        }

        if (areaId <= 0) {
            this.player.dropMessage("Statue proxy: attempted to heal.");
            this.handleStatueResonate(areaId);
            return;
        }
        var city = sots.getCityByAreaId(areaId);
        if (city == null) {
            this.player.dropMessage(
                    "Statue proxy: healed (area=" + areaId + " has no city binding, cannot offer).");
            this.handleStatueResonate(areaId);
            return;
        }

        int cityId = city.getCityId();
        String cityName =
                switch (cityId) {
                    case 7 -> "Nod-Krai";
                    case 8 -> "Snezhnaya";
                    case 6 -> "Natlan";
                    case 5 -> "Fontaine";
                    default -> ("City " + cityId);
                };
        var cityInfo = sots.getCityInfo(cityId);

        // Resolve cost item (StatuePromote or CityLevelup).
        int costItemId = 0;
        var promote =
                emu.grasscutter.data.GameData.getStatuePromoteData(cityId, cityInfo.getLevel() + 1);
        var cityLevelup =
                emu.grasscutter.data.GameData.getCityLevelupData(cityId, cityInfo.getLevel() + 1);
        if (promote != null
                && promote.getCostItems() != null
                && promote.getCostItems().length > 0
                && promote.getCostItems()[0] != null) {
            costItemId = promote.getCostItems()[0].getId();
        } else if (cityLevelup != null) {
            costItemId = cityLevelup.getCostItemId();
        }

        int have = costItemId > 0 ? this.player.getInventory().getItemCountById(costItemId) : 0;
        if (costItemId <= 0) {
            this.player.dropMessage("Statue proxy: healed (" + cityName + " is max level or has no next level).");
        } else if (have <= 0) {
            this.player.dropMessage(
                    "Statue proxy: healed. "
                            + cityName
                            + " Requires oculus id="
                            + costItemId
                            + " (Snezhnaya=107035, Nod-Krai=107030), which the inventory does not have.");
        } else {
            sots.levelUpSotS(areaId, sceneId, have);
            cityInfo = sots.getCityInfo(cityId);
            this.player.dropMessage(
                    String.format(
                            "Statue proxy: %s area=%d heal%s; oculi %d x%d -> city%d Lv.%d (crystals %d)",
                            cityName,
                            areaId,
                            afterHpSum > beforeHpSum ? "ok" : "(already full)",
                            costItemId,
                            have,
                            cityId,
                            cityInfo.getLevel(),
                            cityInfo.getNumCrystal()));
        }

        // After heal/offer, also resonate if Traveler is on-field (official goddess flow).
        this.handleStatueResonate(areaId);
    }

    /** Change on-field Traveler to this statue region's element. */
    private void handleStatueResonate(int areaId) {
        var element = this.resolveStatueElement(areaId);
        if (element == null || element.getDepotIndex() <= 0) {
            this.player.dropMessage("Statue resonance: this area has no resonatable element configured.");
            return;
        }

        var entity = this.player.getTeamManager().getCurrentAvatarEntity();
        if (entity == null || entity.getAvatar() == null) {
            this.player.dropMessage("Statue resonance: no active character.");
            return;
        }
        var avatar = entity.getAvatar();
        int avatarId = avatar.getAvatarId();
        if (avatarId != emu.grasscutter.GameConstants.MAIN_CHARACTER_MALE
                && avatarId != emu.grasscutter.GameConstants.MAIN_CHARACTER_FEMALE) {
            this.player.dropMessage(
                    "Statue resonance: make the Traveler the active character first, then pick resonate.");
            return;
        }

        String elemName =
                switch (element) {
                    case Wind -> "Anemo";
                    case Rock -> "Geo";
                    case Electric -> "Electro";
                    case Grass -> "Dendro";
                    case Water -> "Hydro";
                    case Fire -> "Pyro";
                    case Ice -> "Cryo";
                    default -> element.name();
                };

        if (!avatar.changeElement(element, true)) {
            // Already that element, or depot missing.
            if (this.player.getMainCharacterElement() == element
                    || (avatar.getSkillDepot() != null
                            && avatar.getSkillDepot().getElementType() == element)) {
                this.player.dropMessage("Statue resonance: the Traveler is already " + elemName + ".");
            } else {
                this.player.dropMessage("Statue resonance: cannot switch to " + elemName + " (missing skill depot?).");
            }
            return;
        }

        avatar.save();
        this.player.save();
        // No quest force-finish - Archon quests stay receivable.
        this.player.dropMessage(
                "Statue resonance: the Traveler switched to " + elemName + " (no quest progress consumed).");
    }

    /**
     * Prefer WorldAreaConfig element for the statue area; fall back to city defaults when the
     * area row is missing (e.g. some Snezhnaya sub-areas).
     */
    private emu.grasscutter.game.props.ElementType resolveStatueElement(int areaId) {
        if (areaId > 0) {
            var area = emu.grasscutter.data.GameData.getWorldAreaDataMap().get(areaId);
            if (area != null
                    && area.getElementType() != null
                    && area.getElementType().getDepotIndex() > 0) {
                return area.getElementType();
            }
        }
        var sots = this.player.getSotsManager();
        if (sots == null || areaId <= 0) return null;
        var city = sots.getCityByAreaId(areaId);
        if (city == null) return null;
        return switch (city.getCityId()) {
            case 1 -> emu.grasscutter.game.props.ElementType.Wind;
            case 2 -> emu.grasscutter.game.props.ElementType.Rock;
            case 3 -> emu.grasscutter.game.props.ElementType.Electric;
            case 4 -> emu.grasscutter.game.props.ElementType.Grass;
            case 5 -> emu.grasscutter.game.props.ElementType.Water;
            case 6 -> emu.grasscutter.game.props.ElementType.Fire;
            case 7, 8 -> emu.grasscutter.game.props.ElementType.Ice; // Nod-Krai / Snezhnaya, per the current table
            default -> null;
        };
    }

    /** Unload goddess client suite on leave so ambient/SFX are not left half-broken. */
    public void clearStatueGoddessNotify(int sceneId, int pointId) {
        var spe = GameData.getScenePointEntryById(sceneId, pointId);
        if (spe == null || spe.getPointData() == null) return;
        if (!emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(spe.getPointData())) {
            return;
        }

        int npcId = spe.getPointData().getNpcId();
        if (npcId <= 0) {
            npcId = emu.grasscutter.game.managers.StatueTalkQuests.npcForArea(
                    spe.getPointData().getAreaId());
        }
        var statuePos =
                spe.getPointData().getPos() != null
                        ? spe.getPointData().getPos()
                        : spe.getPointData().getTranPos();
        var bornData = GameData.getSceneNpcBornData().get(sceneId);
        if (bornData != null && bornData.getBornPosList() != null) {
            for (var born : bornData.getBornPosList()) {
                boolean match =
                        (npcId > 0 && born.getConfigId() == npcId)
                                || (statuePos != null
                                        && born.getPos() != null
                                        && statuePos.computeDistance(born.getPos()) < 5f
                                        && emu.grasscutter.game.managers.StatueTalkQuests
                                                .isGoddessNpc(born.getConfigId()));
                if (!match) continue;
                if (!this.player.getGoddessSuiteNotified().remove(born.getGroupId())) {
                    // Suite already cleared this visit - still drop resonate proxies if any.
                    this.removeStatueWorktopProxies(pointId);
                    return;
                }
                this.player.sendPacket(
                        new emu.grasscutter.server.packet.send.PacketGroupUnloadNotify(
                                java.util.List.of(born.getGroupId())));
                emu.grasscutter.Grasscutter.getLogger()
                        .debug(
                                "Unload goddess suite uid={} point={} group={}",
                                this.player.getUid(),
                                pointId,
                                born.getGroupId());
                this.removeStatueWorktopProxies(pointId);
                return;
            }
        }

        // Spawned-entity fallback (Nod-Krai / thick-pillar proxy ring / Natlan resonate).
        this.removeStatueWorktopProxies(pointId);
    }

    private void removeStatueWorktopProxies(int pointId) {
        int synthGroup = syntheticGoddessGroupId(pointId);
        this.player.getGoddessSuiteNotified().remove(synthGroup);
        var entityIds = this.player.getGoddessNpcEntityByPoint().remove(pointId);
        if (entityIds == null || entityIds.isEmpty()) return;
        var scene = this.player.getScene();
        if (scene == null) return;
        for (int entityId : entityIds) {
            var entity = scene.getEntityById(entityId);
            if (entity != null) {
                scene.removeEntity(entity);
            }
        }
        emu.grasscutter.Grasscutter.getLogger()
                .debug(
                        "Remove statue worktop proxies uid={} point={} count={}",
                        this.player.getUid(),
                        pointId,
                        entityIds.size());
    }

    /**
     * Lock a quest-controlled scene point on the client and in persisted player state.
     * ScenePointUnlockNotify's generated field 4 is the 7.1 locked-point wire field.
     */
    public boolean lockTransPoint(int sceneId, int pointId) {
        if (sceneId <= 0 || pointId <= 0) return false;
        applyQuestPointLock(this.player.getForceLockedScenePoints(sceneId), pointId);
        this.player.sendPacket(PacketScenePointUnlockNotify.lock(sceneId, pointId));
        this.player.save();
        return true;
    }

    public boolean unlockTransPoint(int sceneId, int pointId, boolean isStatue) {
        ScenePointEntry scenePointEntry = GameData.getScenePointEntryById(sceneId, pointId);
        if (scenePointEntry == null) {
            return false;
        }

        var transition =
                applyTransPointUnlock(
                        this.player.getUnlockedScenePoints(sceneId),
                        this.player.getForceLockedScenePoints(sceneId),
                        pointId);
        if (!transition.changed()) {
            tryPendingTransPointReward(sceneId, pointId);
            return false;
        }
        boolean newlyUnlocked = transition.newlyUnlocked();

        var pointData = scenePointEntry.getPointData();
        if (!isStatue
                && emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(pointData)) {
            isStatue = true;
        }

        // An eligible first-unlock creates a pending reward, not a prerequisite
        // for permanent progress. Domain rewards begin on client activation.
        boolean pendingReward = false;
        if (newlyUnlocked) {
            var kind = emu.grasscutter.game.reward.TransPointRewardEligibility.kindOf(pointData, isStatue);
            if (kind != null && kind != emu.grasscutter.game.reward.TransPointRewardEligibility.Kind.DOMAIN) {
                this.player.getPendingTransPointRewards(sceneId).add(pointId);
                freezeTransPointReward(sceneId, pointId, kind);
                pendingReward = true;
            }
        }
        if (newlyUnlocked && isStatue && pointData != null && pointData.getAreaId() > 0) {
            this.unlockSceneAreaHierarchy(sceneId, pointData.getAreaId());
        }

        // Fire quest trigger for trans point unlock.
        this.player
                .getQuestManager()
                .queueEvent(QuestContent.QUEST_CONTENT_UNLOCK_TRANS_POINT, sceneId, pointId);
        try {
            if (this.player.getScene() != null) {
                this.player
                        .getScene()
                        .getScriptManager()
                        .callEvent(new ScriptArgs(0, EVENT_UNLOCK_TRANS_POINT, sceneId, pointId));
            }
        } catch (Throwable ignored) {
        }

        // Once the native activation quest unlocks the point, expose the post-activation NPC.
        if (isStatue) {
            this.refreshStatueGoddessInteractions(sceneId, pointId);
        }

        // Send packet.
        this.player.sendPacket(new PacketScenePointUnlockNotify(sceneId, pointId));
        try {
            int total = 0;
            if (this.player.getUnlockedScenePoints() != null) {
                for (var pts : this.player.getUnlockedScenePoints().values()) {
                    if (pts != null) total += pts.size();
                }
            }
            InvestigationHandbookHelper.trigger(
                    this.player,
                    emu.grasscutter.game.props.WatcherTriggerType.TRIGGER_UNLOCK_TRANS_POINT,
                    0,
                    total);
        } catch (Throwable ignored) {
        }
        this.player.save();
        if (pendingReward) tryPendingTransPointReward(sceneId, pointId);
        return true;
    }

    /** Retry only recorded first activations, never arbitrary already-unlocked map points. */
    public void retryPendingTransPointRewards() {
        for (var scene : java.util.Map.copyOf(player.getPendingTransPointRewards()).entrySet()) {
            for (int pointId : Set.copyOf(scene.getValue())) {
                tryPendingTransPointReward(scene.getKey(), pointId);
            }
        }
    }

    private boolean tryPendingTransPointReward(int sceneId, int pointId) {
        synchronized (this.player) {
            var pending = this.player.getPendingTransPointRewards(sceneId);
            if (!pending.contains(pointId)) return false;

            var entry = GameData.getScenePointEntryById(sceneId, pointId);
            if (entry == null) return false;
            var pointData = entry.getPointData();
            var kind = emu.grasscutter.game.reward.TransPointRewardEligibility.kindOf(
                    pointData,
                    emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(pointData));
            if (kind == null) return false;

            try {
                boolean settled = settlePendingTransPointReward(
                        pending, pointId, () -> grantTransPointReward(sceneId, pointId, kind));
                if (!settled) {
                    emu.grasscutter.Grasscutter.getLogger().warn(
                            "Scene-point reward remains pending uid={} scene={} point={} kind={}",
                            player.getUid(), sceneId, pointId, kind);
                    return false;
                }
                if (kind == emu.grasscutter.game.reward.TransPointRewardEligibility.Kind.DOMAIN) {
                    player.getRewardedDungeonEntries(sceneId).add(pointId);
                }
                player.getTransPointRewardSnapshots().remove(transPointSnapshotKey(sceneId, pointId));
                player.save();
                return true;
            } catch (RuntimeException error) {
                // Ambiguous journal or inventory failures must not trigger blind replay.
                emu.grasscutter.Grasscutter.getLogger().error(
                        "Scene-point reward needs attention uid={} scene={} point={} kind={}",
                        player.getUid(), sceneId, pointId, kind, error);
                return false;
            }
        }
    }

    /** MongoDB/Morphia map field names cannot contain dots. */
    static String transPointSnapshotKey(int sceneId, int pointId) {
        return sceneId + ":" + pointId;
    }

    /** A frozen zero payout is a value, not an absent snapshot. */
    static java.util.Map<Integer, Integer> freezeRewardSnapshot(
            java.util.Map<String, java.util.Map<Integer, Integer>> snapshots,
            String identity,
            java.util.function.Supplier<java.util.Map<Integer, Integer>> roll) {
        return snapshots.computeIfAbsent(identity, unused -> java.util.Map.copyOf(roll.get()));
    }

    /** A successful first roll is immutable across config reloads and admission retries. */
    private void freezeTransPointReward(int sceneId, int pointId,
            emu.grasscutter.game.reward.TransPointRewardEligibility.Kind kind) {
        String id = transPointSnapshotKey(sceneId, pointId);
        var frozen = player.getTransPointRewardSnapshots();
        if (frozen.containsKey(id)) return;
        try {
            freezeRewardSnapshot(frozen, id, () ->
                    emu.grasscutter.game.reward.RewardConfiguration.current().roll(
                            "transpoint", java.util.Map.of(
                                    "type", kind.name().toLowerCase(java.util.Locale.ROOT),
                                    "scene", sceneId, "point", pointId),
                            java.util.concurrent.ThreadLocalRandom.current()));
        } catch (RuntimeException error) {
            // Keep progress and pending entitlement. A roll cannot be frozen until
            // the configuration works; retries must not invent a payout.
            emu.grasscutter.Grasscutter.getLogger().error(
                    "Cannot freeze scene-point reward uid={} scene={} point={}",
                    player.getUid(), sceneId, pointId, error);
        }
    }

    private boolean grantTransPointReward(
            int sceneId,
            int pointId,
            emu.grasscutter.game.reward.TransPointRewardEligibility.Kind kind) {
        // The settled receipt is authoritative, including after a LootCSS edit.
        var journal = new emu.grasscutter.database.RewardGrantJournal();
        var key = new emu.grasscutter.database.RewardGrantJournal.Key(
                emu.grasscutter.database.RewardGrantJournal.Source.TRANSPOINT,
                player.getUid(), "point." + sceneId + "." + pointId);
        if (journal.completed(key)) return true;
        freezeTransPointReward(sceneId, pointId, kind);
        var reward = player.getTransPointRewardSnapshots().get(transPointSnapshotKey(sceneId, pointId));
        if (reward == null) return false;
        var items = reward.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new emu.grasscutter.game.inventory.GameItem(
                        entry.getKey(), entry.getValue()))
                .toList();
        // Even a zero-payout activation receives a durable receipt, so changing
        // LootCSS later cannot pay this event retroactively.

        var snapshot = items.stream().map(item -> new emu.grasscutter.database.RewardGrantJournal.Reward(
                item.getItemId(), item.getCount(), item.getLevel())).toList();
        return emu.grasscutter.game.reward.RewardClaimFlow.grant(
                emu.grasscutter.game.reward.RewardClaimFlow.receipt(journal, key, snapshot, java.util.List.of()),
                new emu.grasscutter.game.reward.RewardClaimFlow.Delivery() {
                    public boolean admit(java.util.function.BooleanSupplier authorize, Runnable confirmed) {
                        if (items.isEmpty()) {
                            if (!authorize.getAsBoolean()) return false;
                            confirmed.run();
                            return true;
                        }
                        return player.getInventory().addItems(items, ActionReason.UnlockPointReward,
                                emu.grasscutter.game.inventory.InventoryAddPolicy.ALL_OR_NOTHING,
                                authorize, confirmed).allAccepted();
                    }
                    public void publish() {}
                }, () -> true, () -> {}, () -> {});
    }

    /**
     * Claim a dungeon entrance's first activation reward on UnlockTransPointReq.
     * DomainHandbookHelper may already have opened the map point for UI navigation;
     * that synthetic unlock must never consume this independent activation receipt.
     */
    public boolean rewardDomainActivation(int sceneId, int pointId) {
        var entry = GameData.getScenePointEntryById(sceneId, pointId);
        if (entry == null
                || emu.grasscutter.game.reward.TransPointRewardEligibility.kindOf(
                        entry.getPointData(), false)
                        != emu.grasscutter.game.reward.TransPointRewardEligibility.Kind.DOMAIN) {
            return false;
        }

        synchronized (this.player) {
            if (this.player.getRewardedDungeonEntries(sceneId).contains(pointId)) return false;
            // Activation is recorded independently. A later login can retry an
            // unpaid claim; ambiguous receipts remain blocked by RewardGrantJournal.
            if (this.player.getPendingTransPointRewards(sceneId).add(pointId)) {
                freezeTransPointReward(sceneId, pointId,
                        emu.grasscutter.game.reward.TransPointRewardEligibility.Kind.DOMAIN);
                this.player.save();
            }
            return tryPendingTransPointReward(sceneId, pointId);
        }
    }

    public void unlockSceneArea(int sceneId, int areaId) {
        // Add the area to the list of unlocked areas in its scene.
        this.player.getUnlockedSceneAreas(sceneId).add(areaId);

        // A quest may request area 1 before statue (3,7) is activated.
        // Preserve quest progress but do not reveal the area on the client yet.
        if (emu.grasscutter.server.packet.send.SceneAreaVisibility
                .forPlayer(this.player, sceneId).contains(areaId)) {
            this.player.sendPacket(new PacketSceneAreaUnlockNotify(sceneId, areaId));
        }
        try {
            InvestigationHandbookHelper.trigger(
                    this.player,
                    emu.grasscutter.game.props.WatcherTriggerType.TRIGGER_UNLOCK_AREA,
                    areaId,
                    1);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Unlock a world area and its hierarchy so statue fog matches the official "one statue gives one
     * region" behaviour (LEVEL_1 parent + LEVEL_2 children).
     */
    public void unlockSceneAreaHierarchy(int sceneId, int areaId) {
        if (areaId <= 0) return;
        var newly = new java.util.LinkedHashSet<Integer>();
        var areas = this.player.getUnlockedSceneAreas(sceneId);
        if (areas.add(areaId)) newly.add(areaId);

        for (var worldArea : GameData.getWorldAreaDataMap().values()) {
            if (worldArea.getSceneId() != sceneId) continue;
            // Children of this LEVEL_1 area.
            if (worldArea.getParentArea() == areaId && worldArea.getChildArea() > 0) {
                if (areas.add(worldArea.getChildArea())) newly.add(worldArea.getChildArea());
            }
            // If this id is a child, also unlock its parent.
            if (worldArea.getChildArea() == areaId && worldArea.getParentArea() > 0) {
                if (areas.add(worldArea.getParentArea())) newly.add(worldArea.getParentArea());
            }
        }

        // A prior quest/old build might have stored area 1 before the statue
        // unlocked. Re-send it when the statue really activates even if the
        // area flag is not newly inserted.
        var notifyAreas = new java.util.LinkedHashSet<>(newly);
        if (sceneId == 3 && areaId == 1) {
            notifyAreas.add(1);
        }
        // A different statue's region hierarchy can contain area 1. Do not
        // announce it until its own point (3,7) is active.
        notifyAreas.retainAll(
                emu.grasscutter.server.packet.send.SceneAreaVisibility
                        .forPlayer(this.player, sceneId));
        if (!notifyAreas.isEmpty()) {
            this.player.sendPacket(new PacketSceneAreaUnlockNotify(sceneId, notifyAreas));
            this.player.save();
            try {
                for (int unlockedAreaId : newly) {
                    InvestigationHandbookHelper.trigger(
                            this.player,
                            emu.grasscutter.game.props.WatcherTriggerType.TRIGGER_UNLOCK_AREA,
                            unlockedAreaId,
                            1);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Rebuild scene areas from unlocked statues only so map fog tracks SotS unlocks, not waypoints.
     */
    public void syncSceneAreasFromUnlockedPoints(int sceneId) {
        var pointIds = GameData.getScenePointsPerScene().get(sceneId);
        if (pointIds == null) return;

        var justified = new java.util.LinkedHashSet<Integer>();
        var unlockedPoints = this.player.getUnlockedScenePoints(sceneId);
        for (int pointId : pointIds) {
            if (!unlockedPoints.contains(pointId)) continue;
            if (this.player.isScenePointForceLocked(sceneId, pointId)) continue;
            var entry = GameData.getScenePointEntryById(sceneId, pointId);
            if (entry == null || entry.getPointData() == null) continue;
            // Waypoints share areaId with statues - only SotS should justify fog.
            if (!emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(
                    entry.getPointData())) {
                continue;
            }
            int areaId = entry.getPointData().getAreaId();
            if (areaId <= 0) continue;
            justified.add(areaId);
            for (var worldArea : GameData.getWorldAreaDataMap().values()) {
                if (worldArea.getSceneId() != sceneId) continue;
                if (worldArea.getParentArea() == areaId && worldArea.getChildArea() > 0) {
                    justified.add(worldArea.getChildArea());
                }
                if (worldArea.getChildArea() == areaId && worldArea.getParentArea() > 0) {
                    justified.add(worldArea.getParentArea());
                }
            }
        }

        var areas = this.player.getUnlockedSceneAreas(sceneId);
        areas.clear();
        areas.addAll(justified);
        this.player.save();
        this.player.sendPacket(new PacketSceneAreaUnlockNotify(
                sceneId, emu.grasscutter.server.packet.send.SceneAreaVisibility
                        .forPlayer(this.player, sceneId)));
        emu.grasscutter.Grasscutter.getLogger()
                .debug(
                        "Synced scene areas from points uid={} scene={} areas={}",
                        this.player.getUid(),
                        sceneId,
                        justified.size());
    }

    /** Give replace costume to player (Amber, Jean, Mona, Rosaria) */
    public void addReplaceCostumes() {
        var currentPlayerCostumes = player.getCostumeList();
        GameData.getAvatarReplaceCostumeDataMap()
                .keySet()
                .forEach(
                        costumeId -> {
                            if (GameData.getAvatarCostumeDataMap().get(costumeId) == null
                                    || currentPlayerCostumes.contains(costumeId)) {
                                return;
                            }
                            this.player.addCostume(costumeId);
                        });
    }

    /** Quest progress */
    public void addQuestProgress(int id, int count) {
        var newCount = player.getPlayerProgress().addToCurrentProgress(String.valueOf(id), count);
        player.save();
        player
                .getQuestManager()
                .queueEvent(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, id, newCount);
    }

    /** Item history */
    public void addItemObtainedHistory(int id, int count) {
        var newCount = player.getPlayerProgress().addToItemHistory(id, count);
        player.save();
        player.getQuestManager().queueEvent(QuestCond.QUEST_COND_HISTORY_GOT_ANY_ITEM, id, newCount);
    }

    /******************************************************************************************************************
     ******************************************************************************************************************
     * SCENETAGS
     ******************************************************************************************************************
     *****************************************************************************************************************/
    public void addSceneTag(int sceneId, int sceneTagId) {
        player.getSceneTags().computeIfAbsent(sceneId, k -> new HashSet<>()).add(sceneTagId);
        player.sendPacket(new PacketPlayerWorldSceneInfoListNotify(player));
    }

    public void delSceneTag(int sceneId, int sceneTagId) {
        // Sanity check
        if (player.getSceneTags().get(sceneId) == null) {
            // Can't delete something that doesn't exist
            return;
        }
        player.getSceneTags().get(sceneId).remove(sceneTagId);
        player.sendPacket(new PacketPlayerWorldSceneInfoListNotify(player));
    }

    public boolean checkSceneTag(int sceneId, int sceneTagId) {
        return player.getSceneTags().get(sceneId).contains(sceneTagId);
    }
}
