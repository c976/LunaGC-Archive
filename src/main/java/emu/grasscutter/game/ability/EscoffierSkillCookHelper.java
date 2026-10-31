package emu.grasscutter.game.ability;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.ActionReason;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMaps;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server logic for Escoffier's hold-E "Improvised Cooking".
 *
 * <p>The elemental charge ring is drawn by the client. The path known to work is: visible charge bar plus
 * SkillCookReq, and <strong>no</strong>
 * DataNotify. Hand-encoding DataNotify or driving a server-side Progress GV caused CannotCreateFood and
 * made the charge bar disappear.
 * This restores that path: the client owns the bar, the server only hands out dishes.
 */
public final class EscoffierSkillCookHelper {
    public static final int ESCOFFIER_AVATAR_ID = EscoffierHealUtil.ESCOFFIER_AVATAR_ID;
    /** Skill id for the hold-to-cook elemental skill. */
    public static final int HOLD_COOK_SKILL_ID = 11122;
    /** The improvised cooking pot gadget. */
    public static final int COOK_GADGET_ID = 42112005;
    /** Weekly cap on dishes handed out. */
    public static final int WEEKLY_MAX = 1000;

    /** Wait for SkillCookReq first; only fall back to granting on pot destruction after this timeout. */
    private static final int GADGET_DESTROY_GRANT_DELAY_SEC = 3;
    /** Guards against duplicate SkillCookReq; must stay short enough to allow recharging the same pot. */
    private static final long GRANT_ICD_MS = 2500L;
    private static final long UNLOCK_NOTIFY_THROTTLE_MS = 60_000L;
    private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Shanghai");

    /** Sangonomiya-style 5-star dish. */
    private static final int DISH_GOLD = 108824;
    /** The two 4-star dishes. */
    private static final int[] DISH_PURPLE = {108822, 108825};
    /** The three 3-star dishes. */
    private static final int[] DISH_BLUE = {108823, 108606, 108558};

    /** Mutually exclusive weights summing to 100; CHANCE_DOUBLE then decides whether the dish comes doubled. */
    private static final int CHANCE_GOLD = 65;
    private static final int CHANCE_EACH_PURPLE = 10;
    private static final int CHANCE_EACH_BLUE = 5;
    private static final int CHANCE_DOUBLE = 65;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE =
            TypeToken.getParameterized(Map.class, String.class, CookWeekState.class).getType();
    private static final ConcurrentHashMap<String, CookWeekState> RUNTIME =
            new ConcurrentHashMap<>();
    private static volatile Path storePath;
    private static volatile boolean storeLoaded;
    private static final Int2LongMap LAST_GRANT_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap LAST_UNLOCK_NOTIFY_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    /** One cooking session per uid, so SkillCookReq and the destruction fallback cannot both pay out. */
    private static final Int2LongMap COOK_SESSION = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap GRANTED_SESSION = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2BooleanMap COOK_PENDING = Int2BooleanMaps.synchronize(new Int2BooleanOpenHashMap());
    private static final Int2IntMap ACTIVE_COOK_GADGET = Int2IntMaps.synchronize(new Int2IntOpenHashMap());
    private static long nextCookSession = 1L;

    private EscoffierSkillCookHelper() {}

    private static long beginCookSession(int uid) {
        long session = nextCookSession++;
        COOK_SESSION.put(uid, session);
        COOK_PENDING.put(uid, true);
        return session;
    }

    /**
     * Syncs the weekly remainder on scene entry and similar moments: when quota is left, a throttled minimal
     * DataNotify clears a previously poisoned CannotCreateFood.
     * Do not spam this - that is exactly what killed the charge bar before.
     */
    public static void syncToClient(Player player) {
        if (player == null || !ownsEscoffier(player)) {
            return;
        }
        ensureStoreLoaded();
        CookWeekState state = getState(player.getAccountId());
        refreshWeekIfNeeded(state);
        int remaining = Math.max(0, WEEKLY_MAX - state.used);
        if (remaining <= 0) {
            sendDataNotify(player, state);
            return;
        }
        long now = System.currentTimeMillis();
        int uid = player.getUid();
        if (LAST_UNLOCK_NOTIFY_MS.get(uid) + UNLOCK_NOTIFY_THROTTLE_MS > now) {
            return;
        }
        sendDataNotify(player, state);
        LAST_UNLOCK_NOTIFY_MS.put(uid, now);
        Grasscutter.getLogger()
                .debug("[EscoffierCook] uid={} unlock DataNotify remain={}/{}", uid, remaining, WEEKLY_MAX);
    }

    /** Hold-to-cook skill: drop server-side shell pots, open a new session, wait for the client's SkillCookReq. */
    public static void onHoldCookSkill(Player player) {
        if (player == null || !ownsEscoffier(player)) {
            return;
        }
        purgeServerCookShells(player);
        long session = beginCookSession(player.getUid());
        // No DataNotify and no server-side Progress, matching the path where the client owns the charge bar.
        Grasscutter.getLogger()
                .debug(
                        "[EscoffierCook] hold skill {} uid={} session={} waiting SkillCookReq (client bar)",
                        HOLD_COOK_SKILL_ID,
                        player.getUid(),
                        session);
    }

    /** Client EvtCreate for the pot: record the entity and open or continue the session. */
    public static void onCookGadgetCreated(Player player, int entityId, int configId) {
        if (player == null || configId != COOK_GADGET_ID || !ownsEscoffier(player)) {
            return;
        }
        // Remove duplicate server-side EntityGadget shells - visible to the caster but with no charge UI.
        purgeServerCookShells(player);
        int uid = player.getUid();
        ACTIVE_COOK_GADGET.put(uid, entityId);
        long session = COOK_SESSION.get(uid);
        // A new pot, or recharging after the previous payout, starts a new session.
        if (!COOK_PENDING.get(uid)
                || session == 0L
                || GRANTED_SESSION.get(uid) == session) {
            session = beginCookSession(uid);
        } else {
            COOK_PENDING.put(uid, true);
        }
        Grasscutter.getLogger()
                .debug(
                        "[EscoffierCook] uid={} cook gadget {} entity={} session={}",
                        uid,
                        COOK_GADGET_ID,
                        entityId,
                        session);
    }

    /** Removes shell pots on this player's side that did not come from the client. */
    public static void purgeServerCookShells(Player player) {
        if (player == null || player.getScene() == null || player.getTeamManager() == null) {
            return;
        }
        var scene = player.getScene();
        java.util.HashSet<Integer> avatarEntityIds = new java.util.HashSet<>();
        for (EntityAvatar ea : player.getTeamManager().getActiveTeam()) {
            if (ea != null) {
                avatarEntityIds.add(ea.getId());
            }
        }
        for (GameEntity entity : new ArrayList<>(scene.getEntities().values())) {
            if (!(entity instanceof EntityGadget gadget) || entity instanceof EntityClientGadget) {
                continue;
            }
            if (gadget.getGadgetId() != COOK_GADGET_ID) {
                continue;
            }
            GameEntity owner = gadget.getOwner();
            boolean ownedByPlayer =
                    owner != null && avatarEntityIds.contains(owner.getId());
            if (!ownedByPlayer && owner instanceof EntityAvatar ea && ea.getPlayer() == player) {
                ownedByPlayer = true;
            }
            if (ownedByPlayer) {
                scene.removeEntity(gadget);
                Grasscutter.getLogger()
                        .debug(
                                "[EscoffierCook] uid={} purged server cook shell entity={}",
                                player.getUid(),
                                gadget.getId());
            }
        }
    }

    /** Client destroyed the pot: schedule the fallback payout, still preferring SkillCookReq. */
    public static void onCookGadgetDestroyed(Player player, int entityId) {
        if (player == null) {
            return;
        }
        int uid = player.getUid();
        // Only tracked client pots count; an unrelated EvtDestroy must not interrupt this round.
        if (ACTIVE_COOK_GADGET.get(uid) != entityId) {
            return;
        }
        long session = COOK_SESSION.get(uid);
        if (!COOK_PENDING.get(uid)
                || session == 0L
                || GRANTED_SESSION.get(uid) == session) {
            ACTIVE_COOK_GADGET.remove(uid);
            return;
        }
        // Keep COOK_PENDING and keep waiting for SkillCookReq; only the entity id is cleared here.
        ACTIVE_COOK_GADGET.remove(uid);
        Grasscutter.getLogger()
                .debug(
                        "[EscoffierCook] cook gadget {} destroyed uid={} session={} fallback in {}s (pending kept)",
                        COOK_GADGET_ID,
                        uid,
                        session,
                        GADGET_DESTROY_GRANT_DELAY_SEC);
        Grasscutter.getGameServer()
                .getScheduler()
                .scheduleDelayedTask(
                        () -> {
                            Player p = Grasscutter.getGameServer().getPlayerByUid(uid);
                            if (p != null) {
                                handleCookRequest(p, "gadget-destroy", session);
                            }
                        },
                        GADGET_DESTROY_GRANT_DELAY_SEC);
    }

    /** SkillCookReq entry point: the same pot can recharge without holding E again. */
    public static void handleCookRequest(Player player) {
        if (player == null) {
            return;
        }
        int uid = player.getUid();
        long session = COOK_SESSION.get(uid);
        // The same pot can recharge and send SkillCookReq again without another hold-E.
        // Only open a new session once the previous round paid out, or if none ever started.
        if (session == 0L || GRANTED_SESSION.get(uid) == session) {
            // Ignore stray requests when no pot was ever opened in this process.
            if (!COOK_PENDING.get(uid) && COOK_SESSION.get(uid) == 0L && LAST_GRANT_MS.get(uid) == 0L) {
                Grasscutter.getLogger()
                        .debug("[EscoffierCook] uid={} SkillCookReq ignored (no cook started)", uid);
                return;
            }
            session = beginCookSession(uid);
            Grasscutter.getLogger()
                    .debug("[EscoffierCook] uid={} SkillCookReq new session={} (reuse pot)", uid, session);
        }
        handleCookRequest(player, "SkillCookReq", session);
    }

    private static void handleCookRequest(Player player, String source, long session) {
        if (player == null) {
            return;
        }
        int uid = player.getUid();

        if (session != 0L && GRANTED_SESSION.get(uid) == session) {
            Grasscutter.getLogger()
                    .debug(
                            "[EscoffierCook] uid={} via={} skipped (session {} already granted)",
                            uid,
                            source,
                            session);
            return;
        }

        long now = System.currentTimeMillis();
        if (LAST_GRANT_MS.get(uid) + GRANT_ICD_MS > now) {
            Grasscutter.getLogger()
                    .debug("[EscoffierCook] uid={} via={} skipped (icd)", uid, source);
            return;
        }
        ensureStoreLoaded();
        if (!ownsEscoffier(player)) {
            Grasscutter.getLogger()
                    .warn("[EscoffierCook] uid={} has no Escoffier", player.getUid());
            player.sendPacket(EscoffierSkillCookProto.buildCookRspError(1));
            return;
        }

        CookWeekState state = getState(player.getAccountId());
        refreshWeekIfNeeded(state);
        if (state.used >= WEEKLY_MAX) {
            Grasscutter.getLogger()
                    .debug(
                            "[EscoffierCook] uid={} weekly limit reached ({}/{})",
                            player.getUid(),
                            state.used,
                            WEEKLY_MAX);
            sendDataNotify(player, state);
            player.sendPacket(EscoffierSkillCookProto.buildCookRspError(1));
            return;
        }

        int previousGadgetEntity = ACTIVE_COOK_GADGET.get(uid);
        COOK_PENDING.remove(uid);

        // Claim the session before granting, so the destruction fallback cannot race and pay twice.
        if (session != 0L) {
            GRANTED_SESSION.put(uid, session);
        }

        List<Integer> rewards = rollCookRewards();
        int itemId = rewards.get(0);
        int count = rewards.size(); // one dish, or two when the double roll hits

        GameItem granted = new GameItem(itemId, count);
        if (!player.getInventory().addItem(granted, ActionReason.SubfieldDrop, true)) {
            Grasscutter.getLogger()
                    .warn("[EscoffierCook] uid={} failed to add item {} x{}", uid, itemId, count);
            if (session != 0L && GRANTED_SESSION.get(uid) == session) {
                GRANTED_SESSION.remove(uid);
            }
            // Roll the pending state back so a real completed charge can still pay out.
            COOK_PENDING.put(uid, true);
            if (previousGadgetEntity != 0) {
                ACTIVE_COOK_GADGET.put(uid, previousGadgetEntity);
            }
            player.sendPacket(EscoffierSkillCookProto.buildCookRspError(1));
            return;
        }

        player.sendPacket(EscoffierSkillCookProto.buildCookRsp(itemId, count));
        state.used++;
        persistState(player.getAccountId(), state);
        if (state.used >= WEEKLY_MAX) {
            sendDataNotify(player, state);
        }
        LAST_GRANT_MS.put(uid, System.currentTimeMillis());
        purgeServerCookShells(player);

        // Prefer keeping the client pot entity id for the next charge; if destruction wins the race, still
        // open a session so SkillCookReq can reuse it.
        if (previousGadgetEntity != 0) {
            armReusePotCycle(player, previousGadgetEntity);
        } else {
            long next = beginCookSession(uid);
            Grasscutter.getLogger()
                    .debug(
                            "[EscoffierCook] uid={} armed reuse cycle session={} (no tracked entity)",
                            uid,
                            next);
        }

        Grasscutter.getLogger()
                .debug(
                        "[EscoffierCook] uid={} via={} session={} granted item={} x{}{} remaining={}/{}",
                        player.getUid(),
                        source,
                        session,
                        itemId,
                        count,
                        count > 1 ? " (double)" : "",
                        WEEKLY_MAX - state.used,
                        WEEKLY_MAX);
    }

    private static void armReusePotCycle(Player player, int gadgetEntityId) {
        if (player == null) {
            return;
        }
        int uid = player.getUid();
        if (gadgetEntityId != 0 && player.getScene() != null) {
            GameEntity entity = player.getScene().getEntityById(gadgetEntityId);
            if (entity instanceof EntityClientGadget gadget
                    && gadget.getGadgetId() == COOK_GADGET_ID) {
                ACTIVE_COOK_GADGET.put(uid, gadgetEntityId);
            } else {
                ACTIVE_COOK_GADGET.remove(uid);
            }
        } else {
            ACTIVE_COOK_GADGET.remove(uid);
        }
        long next = beginCookSession(uid);
        Grasscutter.getLogger()
                .debug(
                        "[EscoffierCook] uid={} armed reuse cycle session={} entity={}",
                        uid,
                        next,
                        ACTIVE_COOK_GADGET.get(uid));
    }

    private static boolean ownsEscoffier(Player player) {
        if (player.getAvatars() == null) {
            return false;
        }
        for (Avatar avatar : player.getAvatars()) {
            if (avatar != null && avatar.getAvatarId() == ESCOFFIER_AVATAR_ID) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks exactly one dish by mutually exclusive weights, then rolls 65% for a doubled portion.
     * Weights: 65 for the gold dish, 10 per purple, 5 per blue (100 total).
     */
    private static List<Integer> rollCookRewards() {
        List<Integer> out = new ArrayList<>(2);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int roll = rng.nextInt(100);
        int picked;
        if (roll < CHANCE_GOLD) {
            picked = DISH_GOLD;
        } else {
            roll -= CHANCE_GOLD;
            if (roll < CHANCE_EACH_PURPLE * DISH_PURPLE.length) {
                picked = DISH_PURPLE[roll / CHANCE_EACH_PURPLE];
            } else {
                roll -= CHANCE_EACH_PURPLE * DISH_PURPLE.length;
                int blueIdx = Math.min(roll / CHANCE_EACH_BLUE, DISH_BLUE.length - 1);
                picked = DISH_BLUE[blueIdx];
            }
        }
        out.add(picked);
        if (rng.nextInt(100) < CHANCE_DOUBLE) {
            out.add(picked);
        }
        return out;
    }

    private static void sendDataNotify(Player player, CookWeekState state) {
        int remaining = Math.max(0, WEEKLY_MAX - state.used);
        player.sendPacket(
                EscoffierSkillCookProto.buildCookDataNotify(
                        state.used, WEEKLY_MAX, nextResetEpochSec()));
        Grasscutter.getLogger()
                .debug(
                        "[EscoffierCook] uid={} dataNotify remain={}/{} (minimal 2-field)",
                        player.getUid(),
                        remaining,
                        WEEKLY_MAX);
    }

    private static CookWeekState getState(String accountId) {
        if (accountId == null || accountId.isEmpty()) {
            accountId = "_unknown";
        }
        return RUNTIME.computeIfAbsent(accountId, k -> new CookWeekState(currentWeekId(), 0));
    }

    private static void refreshWeekIfNeeded(CookWeekState state) {
        long week = currentWeekId();
        if (state.weekId != week) {
            state.weekId = week;
            state.used = 0;
        }
    }

    private static void persistState(String accountId, CookWeekState state) {
        if (accountId == null || accountId.isEmpty()) {
            return;
        }
        RUNTIME.put(accountId, state);
        saveStore();
    }

    static long currentWeekId() {
        ZonedDateTime now = ZonedDateTime.now(RESET_ZONE);
        ZonedDateTime anchor =
                now.with(DayOfWeek.MONDAY).withHour(4).withMinute(0).withSecond(0).withNano(0);
        if (now.isBefore(anchor)) {
            anchor = anchor.minusWeeks(1);
        }
        return anchor.toEpochSecond();
    }

    static long nextResetEpochSec() {
        ZonedDateTime now = ZonedDateTime.now(RESET_ZONE);
        ZonedDateTime next =
                now.with(DayOfWeek.MONDAY).withHour(4).withMinute(0).withSecond(0).withNano(0);
        if (!now.isBefore(next)) {
            next = next.plusWeeks(1);
        }
        return next.toEpochSecond();
    }

    private static synchronized void ensureStoreLoaded() {
        if (storeLoaded) {
            return;
        }
        storePath = Path.of("data", "escoffier_skill_cook.json");
        storeLoaded = true;
        if (!Files.isRegularFile(storePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storePath, StandardCharsets.UTF_8)) {
            Map<String, CookWeekState> loaded = GSON.fromJson(reader, STORE_TYPE);
            if (loaded != null) {
                RUNTIME.putAll(loaded);
            }
            Grasscutter.getLogger()
                    .debug("[EscoffierCook] loaded {} account cook records", RUNTIME.size());
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("[EscoffierCook] failed to load store {}: {}", storePath, e.toString());
        }
    }

    private static synchronized void saveStore() {
        if (storePath == null) {
            storePath = Path.of("data", "escoffier_skill_cook.json");
        }
        try {
            Files.createDirectories(storePath.getParent());
            Map<String, CookWeekState> copy = new HashMap<>(RUNTIME);
            try (Writer writer = Files.newBufferedWriter(storePath, StandardCharsets.UTF_8)) {
                GSON.toJson(copy, STORE_TYPE, writer);
            }
        } catch (IOException e) {
            Grasscutter.getLogger()
                    .warn("[EscoffierCook] failed to save store {}: {}", storePath, e.toString());
        }
    }

    static final class CookWeekState {
        long weekId;
        int used;

        CookWeekState() {}

        CookWeekState(long weekId, int used) {
            this.weekId = weekId;
            this.used = used;
        }
    }
}
