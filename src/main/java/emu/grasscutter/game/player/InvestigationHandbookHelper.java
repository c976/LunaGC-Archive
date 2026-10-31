package emu.grasscutter.game.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.server.packet.send.PacketOpenStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketPlayerInvestigationAllInfoNotify;
import emu.grasscutter.server.packet.send.PacketPlayerInvestigationAllInfoNotify.InvestigationInfo;
import emu.grasscutter.server.packet.send.PacketPlayerInvestigationAllInfoNotify.TargetInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adventurer Handbook Investigation progress sync plus live triggers.
 *
 * <p>Progress is persisted on {@link PlayerProgress} and recomputed from world/inventory state on
 * login so already-completed milestones show correctly.
 */
public final class InvestigationHandbookHelper {
    private static final Path CONFIG_PATH =
            Paths.get("resources", "ExcelBinOutput", "InvestigationConfigData.json");
    private static final Path TARGET_PATH =
            Paths.get("resources", "ExcelBinOutput", "InvestigationTargetConfigData.json");

    /** OPEN_ADVENTURE_MANUAL */
    private static final int OPEN_STATE_MANUAL = 1100;
    /** OPEN_ADVENTURE_MANUAL_CITY_MENGDE */
    private static final int OPEN_STATE_MENGDE_MANUAL = 1101;
    /** OPEN_ADVENTURE_MANUAL_EDUCATION (preparation). */
    private static final int OPEN_STATE_EDUCATION = 2801;

    private static final int STATE_IN_PROGRESS = 1;
    private static final int STATE_COMPLETE = 2;
    private static final int STATE_REWARD_TAKEN = 3;

    /** Adventurer Handbook claim multiplier for investigation and preparation rewards. */
    private static final int REWARD_MULTIPLIER = 3;

    private static volatile boolean loaded;
    private static final List<ChapterDef> CHAPTERS = new ArrayList<>();
    private static final Map<Integer, List<TargetDef>> TARGETS_BY_CHAPTER = new HashMap<>();
    private static final Map<Integer, TargetDef> TARGETS_BY_ID = new HashMap<>();
    /** Excel questId (e.g. 6000001) → target, for claim req lookup. */
    private static final Map<Integer, TargetDef> TARGETS_BY_QUEST_ID = new HashMap<>();
    private static final Map<Integer, ChapterDef> CHAPTERS_BY_ID = new HashMap<>();
    private static final Map<WatcherTriggerType, List<TargetDef>> TARGETS_BY_TRIGGER = new HashMap<>();

    private InvestigationHandbookHelper() {}

    private static boolean isHandbookChapterId(int id) {
        return (id >= 10000 && id < 20000) || (id >= 30000 && id < 40000);
    }

    public static void onPlayerLogin(Player player) {
        if (player == null || player.getSession() == null) {
            return;
        }
        try {
            ensureLoaded();
            forceOpenState(player, OPEN_STATE_MANUAL);
            forceOpenState(player, OPEN_STATE_MENGDE_MANUAL);
            if (player.getLevel() >= 16) {
                forceOpenState(player, OPEN_STATE_EDUCATION);
            }

            // Seed counters from current inventory before reconciling targets.
            seedReliquaryHistory(player);
            reconcileFromPlayerState(player);

            int level = player.getLevel();
            List<InvestigationInfo> investigations = new ArrayList<>();
            List<TargetInfo> targets = new ArrayList<>();

            for (ChapterDef chapter : CHAPTERS) {
                if (!isChapterUnlocked(player, chapter, level)) {
                    continue;
                }
                List<TargetDef> chapterTargets =
                        TARGETS_BY_CHAPTER.getOrDefault(chapter.id, List.of());
                int completed = 0;
                for (TargetDef target : chapterTargets) {
                    int progress = getProgress(player, target.id);
                    int total = Math.max(1, target.progress);
                    int state = getTargetState(player, target.id, progress, total);
                    if (state >= STATE_COMPLETE) {
                        completed++;
                    }
                    targets.add(
                            new TargetInfo(
                                    chapter.id,
                                    clientQuestId(target),
                                    Math.min(progress, total),
                                    total,
                                    state));
                }
                int chapterTotal = Math.max(1, chapterTargets.size());
                int rewardedCount = 0;
                for (TargetDef target : chapterTargets) {
                    int st =
                            getTargetState(
                                    player,
                                    target.id,
                                    getProgress(player, target.id),
                                    Math.max(1, target.progress));
                    if (st == STATE_REWARD_TAKEN) {
                        rewardedCount++;
                    }
                }
                int chapterStored = player.getPlayerProgress().getInvestigationChapterState(chapter.id);
                int chapterState;
                if (chapterStored == STATE_REWARD_TAKEN) {
                    chapterState = STATE_REWARD_TAKEN;
                } else if (completed >= chapterTotal) {
                    chapterState = STATE_COMPLETE;
                } else {
                    chapterState = STATE_IN_PROGRESS;
                }
                // progress for chapter = claimed targets preferred, else completed
                int chapterProgress = Math.max(completed, rewardedCount);
                investigations.add(
                        new InvestigationInfo(chapter.id, chapterProgress, chapterTotal, chapterState));
            }

            player.getSession()
                    .send(new PacketPlayerInvestigationAllInfoNotify(investigations, targets));
            player.getSession()
                    .send(PacketPlayerInvestigationAllInfoNotify.asChapterNotify(investigations));
            player.getSession()
                    .send(PacketPlayerInvestigationAllInfoNotify.asTargetNotify(targets));

            Grasscutter.getLogger()
                    .debug(
                            "InvestigationHandbook notify uid={} level={} chapters={} targets={}",
                            player.getUid(),
                            level,
                            investigations.size(),
                            targets.size());
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("InvestigationHandbook notify failed: {}", t.toString());
        }
    }

    /** Live progress bump from gameplay events (mirrors BattlePass triggerMission). */
    public static void trigger(Player player, WatcherTriggerType type, int param, int amount) {
        if (player == null || type == null || type == WatcherTriggerType.TRIGGER_NONE || amount == 0) {
            return;
        }
        try {
            ensureLoaded();
            List<TargetDef> list = TARGETS_BY_TRIGGER.get(type);
            if (list == null || list.isEmpty()) {
                return;
            }
            List<TargetInfo> changed = new ArrayList<>();
            Set<Integer> changedChapters = new HashSet<>();
            for (TargetDef target : list) {
                if (!matchesTrigger(target, type, param, player)) {
                    continue;
                }
                if (!isChapterUnlocked(player, findChapter(target.investigationId), player.getLevel())) {
                    continue;
                }
                int prev = getProgress(player, target.id);
                int total = Math.max(1, target.progress);
                if (prev >= total) {
                    continue;
                }
                int next;
                if (isSnapshotTrigger(type)) {
                    next = Math.min(total, Math.max(prev, amount));
                } else {
                    next = Math.min(total, prev + amount);
                }
                if (next <= prev) {
                    continue;
                }
                setProgress(player, target.id, next);
                int state = getTargetState(player, target.id, next, total);
                changed.add(new TargetInfo(target.investigationId, clientQuestId(target), next, total, state));
                changedChapters.add(target.investigationId);
            }
            if (changed.isEmpty() || player.getSession() == null) {
                return;
            }
            player.getSession()
                    .send(PacketPlayerInvestigationAllInfoNotify.asTargetNotify(changed));
            List<InvestigationInfo> chapterUpdates = new ArrayList<>();
            for (int chapterId : changedChapters) {
                InvestigationInfo info = buildChapterInfo(player, chapterId);
                if (info != null) {
                    chapterUpdates.add(info);
                }
            }
            if (!chapterUpdates.isEmpty()) {
                player.getSession()
                        .send(PacketPlayerInvestigationAllInfoNotify.asChapterNotify(chapterUpdates));
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("InvestigationHandbook trigger failed: {}", t.toString());
        }
    }

    public static void trigger(Player player, WatcherTriggerType type) {
        trigger(player, type, 0, 1);
    }

    /**
     * Claim a single handbook target reward ({@code TakeInvestigationTargetRewardReq}).
     *
     * @return retcode (0 = success)
     */
    public static int claimTargetReward(Player player, int questId) {
        if (player == null || questId <= 0) {
            return 1;
        }
        try {
            ensureLoaded();
            // Handbook progress for area/waypoint targets is computed from saved state, and a save
            // can lag behind the last unlock. Refresh it before deciding whether the reward is due,
            // otherwise a completed row can still be refused here.
            reconcileFromPlayerState(player);
            TargetDef target = resolveTarget(questId);
            if (target == null) {
                Grasscutter.getLogger().warn("Investigation claim unknown questId={}", questId);
                return 1;
            }
            int progress = getProgress(player, target.id);
            int total = Math.max(1, target.progress);
            int state = getTargetState(player, target.id, progress, total);
            if (state == STATE_REWARD_TAKEN) {
                return 0; // already claimed — idempotent ok
            }
            if (progress < total) {
                Grasscutter.getLogger()
                        .info(
                                "Investigation claim refused (incomplete) uid={} questId={} targetId={} progress={}/{}",
                                player.getUid(),
                                questId,
                                target.id,
                                progress,
                                total);
                return 1;
            }
            grantReward(player, target.rewardId, emu.grasscutter.game.props.ActionReason.InvestigationTargetReward);
            player.getPlayerProgress().setInvestigationTargetState(target.id, STATE_REWARD_TAKEN);
            player.save();

            if (player.getSession() != null) {
                TargetInfo info =
                        new TargetInfo(
                                target.investigationId,
                                clientQuestId(target),
                                total,
                                total,
                                STATE_REWARD_TAKEN);
                player.getSession()
                        .send(PacketPlayerInvestigationAllInfoNotify.asTargetNotify(List.of(info)));
                InvestigationInfo chapterInfo = buildChapterInfo(player, target.investigationId);
                if (chapterInfo != null) {
                    player.getSession()
                            .send(
                                    PacketPlayerInvestigationAllInfoNotify.asChapterNotify(
                                            List.of(chapterInfo)));
                }
            }
            Grasscutter.getLogger()
                    .debug(
                            "Investigation target claimed uid={} questId={} targetId={} rewardId={} x{}",
                            player.getUid(),
                            questId,
                            target.id,
                            target.rewardId,
                            REWARD_MULTIPLIER);
            return 0;
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("Investigation claimTarget failed: {}", t.toString());
            return 1;
        }
    }

    /**
     * Claim a handbook chapter reward ({@code TakeInvestigationRewardReq}).
     *
     * @return retcode (0 = success)
     */
    public static int claimChapterReward(Player player, int chapterId) {
        if (player == null || chapterId <= 0) {
            return 1;
        }
        try {
            ensureLoaded();
            ChapterDef chapter = CHAPTERS_BY_ID.get(chapterId);
            if (chapter == null) {
                return 1;
            }
            if (player.getPlayerProgress().getInvestigationChapterState(chapterId)
                    == STATE_REWARD_TAKEN) {
                return 0;
            }
            List<TargetDef> targets = TARGETS_BY_CHAPTER.getOrDefault(chapterId, List.of());
            if (targets.isEmpty()) {
                return 1;
            }
            for (TargetDef target : targets) {
                int progress = getProgress(player, target.id);
                int total = Math.max(1, target.progress);
                if (progress < total) {
                    return 1;
                }
            }
            if (chapter.rewardId > 0) {
                grantReward(
                        player,
                        chapter.rewardId,
                        emu.grasscutter.game.props.ActionReason.InvestigationReward);
            }
            player.getPlayerProgress().setInvestigationChapterState(chapterId, STATE_REWARD_TAKEN);
            player.save();

            if (player.getSession() != null) {
                InvestigationInfo chapterInfo =
                        new InvestigationInfo(
                                chapterId, targets.size(), Math.max(1, targets.size()), STATE_REWARD_TAKEN);
                player.getSession()
                        .send(
                                PacketPlayerInvestigationAllInfoNotify.asChapterNotify(
                                        List.of(chapterInfo)));
            }
            Grasscutter.getLogger()
                    .debug(
                            "Investigation chapter claimed uid={} chapterId={} rewardId={} x{}",
                            player.getUid(),
                            chapterId,
                            chapter.rewardId,
                            REWARD_MULTIPLIER);
            return 0;
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("Investigation claimChapter failed: {}", t.toString());
            return 1;
        }
    }

    private static TargetDef resolveTarget(int questOrId) {
        TargetDef byId = TARGETS_BY_ID.get(questOrId);
        if (byId != null) {
            return byId;
        }
        return TARGETS_BY_QUEST_ID.get(questOrId);
    }

    private static int clientQuestId(TargetDef target) {
        // Client TakeInvestigationTargetRewardReq echoes InvestigationTarget.quest_id.
        // Live captures send excel `id` (e.g. 60001), not questId (6000001).
        return target.id;
    }

    private static void grantReward(
            Player player, int rewardId, emu.grasscutter.game.props.ActionReason reason) {
        if (rewardId <= 0) {
            return;
        }
        var rewardData = emu.grasscutter.data.GameData.getRewardDataMap().get(rewardId);
        if (rewardData == null || rewardData.getRewardItemList() == null) {
            Grasscutter.getLogger().warn("Investigation reward missing id={}", rewardId);
            return;
        }
        java.util.List<emu.grasscutter.data.common.ItemParamData> scaled = new ArrayList<>();
        for (var item : rewardData.getRewardItemList()) {
            if (item == null || item.getId() <= 0) {
                continue;
            }
            scaled.add(
                    new emu.grasscutter.data.common.ItemParamData(
                            item.getId(), Math.max(0, item.getCount()) * REWARD_MULTIPLIER));
        }
        if (!scaled.isEmpty()) {
            player.getInventory().addItemParamDatas(scaled, reason);
        }
    }

    /** Recompute snapshot targets and push changed handbook packets. */
    public static void refreshAndNotify(Player player) {
        if (player == null || player.getSession() == null) {
            return;
        }
        try {
            ensureLoaded();
            seedReliquaryHistory(player);
            Map<Integer, Integer> before = new HashMap<>();
            for (Integer targetId : TARGETS_BY_ID.keySet()) {
                before.put(targetId, getProgress(player, targetId));
            }
            reconcileFromPlayerState(player);
            List<TargetInfo> changed = new ArrayList<>();
            Set<Integer> changedChapters = new HashSet<>();
            for (TargetDef target : TARGETS_BY_ID.values()) {
                if (!isChapterUnlocked(player, findChapter(target.investigationId), player.getLevel())) {
                    continue;
                }
                int now = getProgress(player, target.id);
                int prev = before.getOrDefault(target.id, 0);
                if (now == prev) {
                    continue;
                }
                int total = Math.max(1, target.progress);
                int state = getTargetState(player, target.id, now, total);
                changed.add(
                        new TargetInfo(
                                target.investigationId,
                                clientQuestId(target),
                                Math.min(now, total),
                                total,
                                state));
                changedChapters.add(target.investigationId);
            }
            if (changed.isEmpty()) {
                return;
            }
            player.getSession()
                    .send(PacketPlayerInvestigationAllInfoNotify.asTargetNotify(changed));
            List<InvestigationInfo> chapterUpdates = new ArrayList<>();
            for (int chapterId : changedChapters) {
                InvestigationInfo info = buildChapterInfo(player, chapterId);
                if (info != null) {
                    chapterUpdates.add(info);
                }
            }
            if (!chapterUpdates.isEmpty()) {
                player.getSession()
                        .send(PacketPlayerInvestigationAllInfoNotify.asChapterNotify(chapterUpdates));
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("InvestigationHandbook refresh failed: {}", t.toString());
        }
    }

    /** Recompute progress from player world / inventory state (login / catch-up). */
    public static void reconcileFromPlayerState(Player player) {
        if (player == null) {
            return;
        }
        ensureLoaded();
        for (TargetDef target : TARGETS_BY_ID.values()) {
            int computed = computeSnapshotProgress(player, target);
            if (computed <= 0) {
                continue;
            }
            int prev = getProgress(player, target.id);
            if (computed > prev) {
                setProgress(player, target.id, Math.min(target.progress, computed));
            }
        }
    }

    private static boolean isSnapshotTrigger(WatcherTriggerType type) {
        return type == WatcherTriggerType.TRIGGER_UNLOCK_AREA
                || type == WatcherTriggerType.TRIGGER_UNLOCK_TRANS_POINT
                || type == WatcherTriggerType.TRIGGER_CITY_LEVEL_UP
                || type == WatcherTriggerType.TRIGGER_WEAPON_UPGRADE
                || type == WatcherTriggerType.TRIGGER_SPECIFIED_WEAPON_UPGRADE
                || type == WatcherTriggerType.TRIGGER_RELIQUARY_UPGRADE
                || type == WatcherTriggerType.TRIGGER_RELIQUARY_UPGRADE_EQUAL_RANK_LEVEL
                || type == WatcherTriggerType.TRIGGER_OBTAIN_RELIQUARY_HISTORY_COUNT
                || type == WatcherTriggerType.TRIGGER_AVATAR_PROMOTE
                || type == WatcherTriggerType.TRIGGER_AVATAR_PROMOTE_EXCLUDING_PLAYER
                || type == WatcherTriggerType.TRIGGER_AVATAR_UPGRADE
                || type == WatcherTriggerType.TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD;
    }

    private static int computeSnapshotProgress(Player player, TargetDef target) {
        WatcherTriggerType type = target.triggerType;
        if (type == null || type == WatcherTriggerType.TRIGGER_NONE) {
            return 0;
        }
        List<String> p = target.params;
        return switch (type) {
            case TRIGGER_UNLOCK_AREA -> {
                int areaId = parseInt(p, 0);
                yield hasUnlockedArea(player, areaId) ? 1 : 0;
            }
            case TRIGGER_UNLOCK_TRANS_POINT -> countUnlockedTransPoints(player);
            case TRIGGER_CITY_LEVEL_UP -> {
                int cityId = parseInt(p, 0);
                int needLevel = parseInt(p, 1);
                int cur =
                        player.getSotsManager() != null
                                ? player.getSotsManager().getCityInfo(cityId).getLevel()
                                : 0;
                yield cur >= needLevel && needLevel > 0 ? 1 : 0;
            }
            case TRIGGER_WEAPON_UPGRADE -> {
                int needLevel = parseInt(p, 0);
                yield countWeaponsAtLeast(player, 0, needLevel) > 0 ? 1 : 0;
            }
            case TRIGGER_SPECIFIED_WEAPON_UPGRADE -> {
                int itemId = parseInt(p, 0);
                int needLevel = parseInt(p, 1);
                yield countWeaponsAtLeast(player, itemId, needLevel) > 0 ? 1 : 0;
            }
            case TRIGGER_RELIQUARY_UPGRADE, TRIGGER_RELIQUARY_UPGRADE_EQUAL_RANK_LEVEL -> {
                int needLevel = relicNeedLevel(parseInt(p, 0));
                int rankLevel = parseInt(p, 1);
                yield countRelicsAtLeast(player, rankLevel, needLevel);
            }
            case TRIGGER_OBTAIN_RELIQUARY_HISTORY_COUNT -> {
                int rankLevel = parseInt(p, 0);
                int stored = player.getPlayerProgress().getHandbookReliquaryHistoryCount(rankLevel);
                int inventory = countRelicsByRank(player, rankLevel);
                yield Math.max(stored, inventory);
            }
            case TRIGGER_AVATAR_PROMOTE, TRIGGER_AVATAR_PROMOTE_EXCLUDING_PLAYER -> {
                int needPromote = parseInt(p, 0);
                int element = parseInt(p, 1);
                boolean excludeTraveler = type == WatcherTriggerType.TRIGGER_AVATAR_PROMOTE_EXCLUDING_PLAYER;
                yield hasAvatarPromote(player, needPromote, element, excludeTraveler) ? 1 : 0;
            }
            case TRIGGER_AVATAR_UPGRADE -> {
                int needLevel = parseInt(p, 0);
                yield hasAvatarLevel(player, needLevel) ? 1 : 0;
            }
            case TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD -> {
                yield hasFinishedAnyDungeon(player, p.size() > 0 ? p.get(0) : "") ? 1 : 0;
            }
            // Chest progress is event-driven only (no reliable historical gadget ids).
            default -> 0;
        };
    }

    private static boolean matchesTrigger(
            TargetDef target, WatcherTriggerType type, int param, Player player) {
        List<String> p = target.params;
        return switch (type) {
            case TRIGGER_UNLOCK_AREA -> param == 0 || param == parseInt(p, 0);
            case TRIGGER_OPEN_CHEST_WITH_GADGET_ID ->
                    param == 0 || target.gadgetIdSet.isEmpty() || target.gadgetIdSet.contains(param);
            case TRIGGER_CITY_LEVEL_UP -> {
                int cityId = parseInt(p, 0);
                int needLevel = parseInt(p, 1);
                int cur =
                        player.getSotsManager() != null
                                ? player.getSotsManager().getCityInfo(cityId).getLevel()
                                : 0;
                // param carries cityId from hook; accept if city matches and level reached.
                yield (param == 0 || param == cityId) && cur >= needLevel;
            }
            case TRIGGER_SPECIFIED_WEAPON_UPGRADE -> param == 0 || param == parseInt(p, 0);
            case TRIGGER_WEAPON_UPGRADE -> true;
            case TRIGGER_RELIQUARY_UPGRADE, TRIGGER_RELIQUARY_UPGRADE_EQUAL_RANK_LEVEL -> {
                int rankLevel = parseInt(p, 1);
                yield param == 0 || rankLevel <= 0 || param == rankLevel;
            }
            case TRIGGER_OBTAIN_RELIQUARY_HISTORY_COUNT -> {
                int rankLevel = parseInt(p, 0);
                yield param == 0 || param == rankLevel;
            }
            case TRIGGER_AVATAR_PROMOTE, TRIGGER_AVATAR_PROMOTE_EXCLUDING_PLAYER -> {
                int element = parseInt(p, 1);
                yield param == 0 || element <= 0 || param == element;
            }
            case TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD ->
                    param == 0 || dungeonParamContains(p.size() > 0 ? p.get(0) : "", param);
            case TRIGGER_UNLOCK_TRANS_POINT -> true;
            default -> true;
        };
    }

    /** Excel stores milestone levels as 5/9/13/17/21 for UI 4/8/12/16/20. */
    private static int relicNeedLevel(int excelLevel) {
        if (excelLevel > 1 && excelLevel % 4 == 1) {
            return excelLevel - 1;
        }
        return excelLevel;
    }

    private static boolean hasUnlockedArea(Player player, int areaId) {
        if (areaId <= 0) {
            return false;
        }
        if (player.getUnlockedSceneAreas() == null) {
            return false;
        }
        for (Set<Integer> areas : player.getUnlockedSceneAreas().values()) {
            if (areas != null && areas.contains(areaId)) {
                return true;
            }
        }
        return false;
    }

    private static int countUnlockedTransPoints(Player player) {
        if (player.getUnlockedScenePoints() == null) {
            return 0;
        }
        int count = 0;
        for (Set<Integer> points : player.getUnlockedScenePoints().values()) {
            if (points != null) {
                count += points.size();
            }
        }
        return count;
    }

    private static int countWeaponsAtLeast(Player player, int itemId, int needLevel) {
        int count = 0;
        for (GameItem item : player.getInventory()) {
            if (item.getItemType() != ItemType.ITEM_WEAPON) {
                continue;
            }
            if (itemId > 0 && item.getItemId() != itemId) {
                continue;
            }
            if (item.getLevel() >= needLevel) {
                count++;
            }
        }
        return count;
    }

    private static int countRelicsAtLeast(Player player, int rankLevel, int needLevel) {
        int count = 0;
        for (GameItem item : player.getInventory()) {
            if (item.getItemType() != ItemType.ITEM_RELIQUARY || item.getItemData() == null) {
                continue;
            }
            if (rankLevel > 0 && item.getItemData().getRankLevel() != rankLevel) {
                continue;
            }
            if (item.getLevel() >= needLevel) {
                count++;
            }
        }
        return count;
    }

    private static int countRelicsByRank(Player player, int rankLevel) {
        int count = 0;
        for (GameItem item : player.getInventory()) {
            if (item.getItemType() != ItemType.ITEM_RELIQUARY || item.getItemData() == null) {
                continue;
            }
            if (rankLevel <= 0 || item.getItemData().getRankLevel() == rankLevel) {
                count++;
            }
        }
        return count;
    }

    private static void seedReliquaryHistory(Player player) {
        // Ensure 5★ history counter is at least current inventory count.
        int fiveStar = countRelicsByRank(player, 5);
        player.getPlayerProgress().raiseHandbookReliquaryHistoryCount(5, fiveStar);
        int fourStar = countRelicsByRank(player, 4);
        player.getPlayerProgress().raiseHandbookReliquaryHistoryCount(4, fourStar);
    }

    private static boolean hasAvatarPromote(
            Player player, int needPromote, int elementValue, boolean excludeTraveler) {
        for (Avatar avatar : player.getAvatars()) {
            int id = avatar.getAvatarId();
            if (excludeTraveler
                    && (id == GameConstants.MAIN_CHARACTER_MALE
                            || id == GameConstants.MAIN_CHARACTER_FEMALE)) {
                continue;
            }
            if (avatar.getPromoteLevel() < needPromote) {
                continue;
            }
            if (elementValue > 0) {
                ElementType el =
                        avatar.getSkillDepot() != null ? avatar.getSkillDepot().getElementType() : null;
                if (el == null || el.getValue() != elementValue) {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean hasAvatarLevel(Player player, int needLevel) {
        for (Avatar avatar : player.getAvatars()) {
            if (avatar.getLevel() >= needLevel) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFinishedAnyDungeon(Player player, String csv) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        var completed = player.getPlayerProgress().getCompletedDungeons();
        if (completed == null) {
            return false;
        }
        for (String part : csv.split("[,;]")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                if (completed.contains(Integer.parseInt(part))) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static boolean dungeonParamContains(String csv, int dungeonId) {
        if (csv == null || csv.isBlank() || dungeonId == 0) {
            return true;
        }
        for (String part : csv.split("[,;]")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                if (Integer.parseInt(part) == dungeonId) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static InvestigationInfo buildChapterInfo(Player player, int chapterId) {
        List<TargetDef> chapterTargets = TARGETS_BY_CHAPTER.getOrDefault(chapterId, List.of());
        if (chapterTargets.isEmpty()) {
            return null;
        }
        if (player.getPlayerProgress().getInvestigationChapterState(chapterId) == STATE_REWARD_TAKEN) {
            return new InvestigationInfo(
                    chapterId, chapterTargets.size(), Math.max(1, chapterTargets.size()), STATE_REWARD_TAKEN);
        }
        int completed = 0;
        for (TargetDef target : chapterTargets) {
            int progress = getProgress(player, target.id);
            int total = Math.max(1, target.progress);
            if (getTargetState(player, target.id, progress, total) >= STATE_COMPLETE) {
                completed++;
            }
        }
        int total = Math.max(1, chapterTargets.size());
        int state = completed >= total ? STATE_COMPLETE : STATE_IN_PROGRESS;
        return new InvestigationInfo(chapterId, completed, total, state);
    }

    private static ChapterDef findChapter(int chapterId) {
        for (ChapterDef c : CHAPTERS) {
            if (c.id == chapterId) {
                return c;
            }
        }
        ChapterDef fallback = new ChapterDef();
        fallback.id = chapterId;
        fallback.unlockLevel = 1;
        return fallback;
    }

    private static int getProgress(Player player, int targetId) {
        return player.getPlayerProgress().getInvestigationTargetProgress(targetId);
    }

    private static void setProgress(Player player, int targetId, int progress) {
        player.getPlayerProgress().setInvestigationTargetProgress(targetId, progress);
    }

    private static int getTargetState(Player player, int targetId, int progress, int total) {
        int stored = player.getPlayerProgress().getInvestigationTargetState(targetId);
        if (stored == STATE_REWARD_TAKEN) {
            return STATE_REWARD_TAKEN;
        }
        if (progress >= total) {
            return STATE_COMPLETE;
        }
        return STATE_IN_PROGRESS;
    }

    private static void forceOpenState(Player player, int openStateId) {
        try {
            Integer prev = player.getOpenStates().get(openStateId);
            if (prev != null && prev == 1) {
                return;
            }
            player.getOpenStates().put(openStateId, 1);
            if (player.getSession() != null) {
                player.getSession().send(new PacketOpenStateChangeNotify(openStateId, 1));
            }
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("InvestigationHandbook openState {} failed: {}", openStateId, t.toString());
        }
    }

    private static boolean isChapterUnlocked(Player player, ChapterDef chapter, int level) {
        if (chapter == null) {
            return false;
        }
        if (chapter.unlockLevel > 0 && level < chapter.unlockLevel) {
            return false;
        }
        if (chapter.openStateId > 0
                && player.getProgressManager().getOpenState(chapter.openStateId) <= 0) {
            // Mondstadt manual / education: allow if we forced open-state above, or AR gate passed.
            if (chapter.openStateId == OPEN_STATE_MENGDE_MANUAL && level >= 1) {
                return true;
            }
            if (chapter.openStateId == OPEN_STATE_EDUCATION && level >= 16) {
                return true;
            }
            return false;
        }
        return true;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        CHAPTERS.clear();
        CHAPTERS_BY_ID.clear();
        TARGETS_BY_CHAPTER.clear();
        TARGETS_BY_ID.clear();
        TARGETS_BY_QUEST_ID.clear();
        TARGETS_BY_TRIGGER.clear();
        try {
            if (Files.exists(CONFIG_PATH)) {
                JsonArray arr =
                        JsonParser.parseReader(Files.newBufferedReader(CONFIG_PATH)).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    int id = obj.get("id").getAsInt();
                    if (!isHandbookChapterId(id)) {
                        continue;
                    }
                    ChapterDef def = new ChapterDef();
                    def.id = id;
                    def.unlockLevel = obj.has("unlockLevel") ? obj.get("unlockLevel").getAsInt() : 1;
                    def.openStateId = resolveOpenStateId(obj);
                    def.rewardId = obj.has("rewardId") ? obj.get("rewardId").getAsInt() : 0;
                    CHAPTERS.add(def);
                    CHAPTERS_BY_ID.put(def.id, def);
                }
            }
            if (Files.exists(TARGET_PATH)) {
                JsonArray arr =
                        JsonParser.parseReader(Files.newBufferedReader(TARGET_PATH)).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    int investigationId = obj.get("investigationId").getAsInt();
                    if (!isHandbookChapterId(investigationId)) {
                        continue;
                    }
                    if (obj.has("isDisuse") && obj.get("isDisuse").getAsBoolean()) {
                        continue;
                    }
                    TargetDef def = new TargetDef();
                    def.id = obj.get("id").getAsInt();
                    def.investigationId = investigationId;
                    def.progress = obj.has("progress") ? obj.get("progress").getAsInt() : 1;
                    def.rewardId = obj.has("rewardId") ? obj.get("rewardId").getAsInt() : 0;
                    def.questId = obj.has("questId") ? obj.get("questId").getAsInt() : 0;
                    if (obj.has("triggerConfig") && obj.get("triggerConfig").isJsonObject()) {
                        JsonObject tc = obj.getAsJsonObject("triggerConfig");
                        String typeName =
                                tc.has("triggerType") ? tc.get("triggerType").getAsString() : "";
                        def.triggerType = WatcherTriggerType.getTypeByName(typeName);
                        def.params = new ArrayList<>();
                        if (tc.has("paramList") && tc.get("paramList").isJsonArray()) {
                            for (JsonElement pe : tc.getAsJsonArray("paramList")) {
                                def.params.add(pe.isJsonNull() ? "" : pe.getAsString());
                            }
                        }
                        if (def.triggerType == WatcherTriggerType.TRIGGER_OPEN_CHEST_WITH_GADGET_ID
                                && !def.params.isEmpty()) {
                            for (String part : def.params.get(0).split(";")) {
                                part = part.trim();
                                if (part.isEmpty()) {
                                    continue;
                                }
                                try {
                                    def.gadgetIdSet.add(Integer.parseInt(part));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    } else {
                        def.triggerType = WatcherTriggerType.TRIGGER_NONE;
                        def.params = List.of();
                    }
                    TARGETS_BY_CHAPTER.computeIfAbsent(investigationId, k -> new ArrayList<>()).add(def);
                    TARGETS_BY_ID.put(def.id, def);
                    if (def.questId > 0) {
                        TARGETS_BY_QUEST_ID.put(def.questId, def);
                    }
                    if (def.triggerType != null && def.triggerType != WatcherTriggerType.TRIGGER_NONE) {
                        TARGETS_BY_TRIGGER
                                .computeIfAbsent(def.triggerType, k -> new ArrayList<>())
                                .add(def);
                    }
                }
            }
            if (CHAPTERS.isEmpty()) {
                for (int i = 0; i < 9; i++) {
                    ChapterDef def = new ChapterDef();
                    def.id = 10001 + i;
                    def.unlockLevel = new int[] {1, 8, 10, 15, 20, 30, 35, 40, 45}[i];
                    def.openStateId = i == 0 ? OPEN_STATE_MENGDE_MANUAL : 0;
                    CHAPTERS.add(def);
                    CHAPTERS_BY_ID.put(def.id, def);
                }
                int[] eduLevels = {16, 25, 30, 35};
                for (int i = 0; i < eduLevels.length; i++) {
                    ChapterDef def = new ChapterDef();
                    def.id = 30001 + i;
                    def.unlockLevel = eduLevels[i];
                    def.openStateId = OPEN_STATE_EDUCATION;
                    CHAPTERS.add(def);
                    CHAPTERS_BY_ID.put(def.id, def);
                }
            }
            loaded = true;
            Grasscutter.getLogger()
                    .debug(
                            "InvestigationHandbook loaded chapters={} targets={} triggerTypes={} rewardX{}",
                            CHAPTERS.size(),
                            TARGETS_BY_ID.size(),
                            TARGETS_BY_TRIGGER.size(),
                            REWARD_MULTIPLIER);
        } catch (Exception e) {
            Grasscutter.getLogger().warn("InvestigationHandbook load failed: {}", e.toString());
            loaded = true;
        }
    }

    private static int resolveOpenStateId(JsonObject obj) {
        if (!obj.has("unlockOpenStateType")) {
            return 0;
        }
        String name = obj.get("unlockOpenStateType").getAsString();
        return switch (name) {
            case "OPEN_ADVENTURE_MANUAL_CITY_MENGDE" -> OPEN_STATE_MENGDE_MANUAL;
            case "OPEN_ADVENTURE_MANUAL" -> OPEN_STATE_MANUAL;
            case "OPEN_ADVENTURE_MANUAL_EDUCATION" -> OPEN_STATE_EDUCATION;
            case "OPEN_STATE_NONE", "" -> 0;
            default -> 0;
        };
    }

    private static int parseInt(List<String> params, int index) {
        if (params == null || index < 0 || index >= params.size()) {
            return 0;
        }
        String s = params.get(index);
        if (s == null || s.isBlank()) {
            return 0;
        }
        // First segment if comma/semicolon list.
        int cut = s.indexOf(';');
        if (cut < 0) {
            cut = s.indexOf(',');
        }
        if (cut > 0) {
            s = s.substring(0, cut);
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final class ChapterDef {
        int id;
        int unlockLevel;
        int openStateId;
        int rewardId;
    }

    private static final class TargetDef {
        int id;
        int investigationId;
        int progress;
        int rewardId;
        int questId;
        WatcherTriggerType triggerType;
        List<String> params = List.of();
        Set<Integer> gadgetIdSet = new HashSet<>();
    }
}
