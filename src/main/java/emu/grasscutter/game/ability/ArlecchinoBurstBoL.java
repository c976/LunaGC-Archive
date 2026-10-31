package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangeHpDebtsReason._ChangeHpDebtsReason;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arlecchino Q Bond-of-Life settle.
 *
 * <ol>
 *   <li>On cast: lock BoL for {@link #LOCK_MS} — consume blocked, AddHPDebts still allowed.
 *   <li>After the lock: arm clear from the burst damage frame, then clear {@link
 *       #CLEAR_AFTER_HIT_MS} later.
 *   <li>If a slash arrived during the lock, clear at {@code lockEnd + CLEAR_AFTER_HIT_MS}.
 *   <li>Miss / no slash: {@link #MISS_TIMEOUT_MS} from cast.
 * </ol>
 */
public final class ArlecchinoBurstBoL {
    private static final int ARLECCHINO_AVATAR_ID = 10000096;

    public static final int ARLECCHINO_BURST_SKILL_ID = 10965;

    private static final String BURST_ABILITY_MARKER = "Arlecchino_ElementalBurst";

    /** Consume lock from cast. BoL can still increase. */
    private static final long LOCK_MS = 1000L;

    /** After damage frame (or lock end if slash was during lock). */
    private static final long CLEAR_AFTER_HIT_MS = 100L;

    /** Absolute ceiling from cast if no slash is recognized. */
    private static final long MISS_TIMEOUT_MS = 4000L;

    /** Late skill-success notifications can arrive after the pending burst has settled. */
    private static final long COMPLETED_CAST_GUARD_MS = 2000L;

    private static final ConcurrentHashMap<Integer, PendingBurst> PENDING = new ConcurrentHashMap<>();
    /** Burst healing already applied for the pending cast, so a repeated heal adds no more. */
    private static final ConcurrentHashMap<Integer, Float> BURST_HEALED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, CompletedBurst> COMPLETED_BURSTS =
            new ConcurrentHashMap<>();

    private ArlecchinoBurstBoL() {}

    /**
     * @param clearAtMs 0 until a slash is armed; then wall-clock clear time
     * @param bolSnapshot BoL at first recognized slash, else -1
     * @param castSnapshot BoL at cast (miss ceiling)
     * @param slashAtMs first recognized slash wall time, else 0
     */
    private record PendingBurst(
            EntityAvatar avatar,
            long castAtMs,
            long clearAtMs,
            float bolSnapshot,
            float castSnapshot,
            long slashAtMs) {}

    private record CompletedBurst(EntityAvatar avatar, long completedAtMs) {}

    public static void onBurstCast(EntityAvatar caster) {
        if (caster == null || caster.getAvatar() == null) {
            return;
        }
        if (caster.getAvatar().getAvatarId() != ARLECCHINO_AVATAR_ID) {
            return;
        }

        synchronized (caster) {
            PendingBurst existing = PENDING.get(caster.getId());
            if (existing != null && existing.avatar() == caster) {
                repinClientBoL(caster);
                return;
            }

            long now = System.currentTimeMillis();
            if (hasRecentlyCompletedCast(caster, now)) {
                return;
            }
            float curDebt = caster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            BURST_HEALED.remove(caster.getId());
            PENDING.put(caster.getId(), new PendingBurst(caster, now, 0L, -1f, curDebt, 0L));
            Grasscutter.getLogger()
                    .info(
                            "[BoL] Arlecchino burst: lock {} BoL for {}ms; then slash→+{}ms / miss→{}ms",
                            curDebt,
                            LOCK_MS,
                            CLEAR_AFTER_HIT_MS,
                            MISS_TIMEOUT_MS);
            if (curDebt > 0f) {
                // Non-zero change forces EntityFightPropChangeReasonNotify — client bar follows it.
                ArlecchinoBoLSync.pushBoL(
                        caster,
                        curDebt,
                        Math.max(0.01f, curDebt * 0.0001f),
                        _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
            }
        }
    }

    /**
     * Arm the Q lock from ability actions that often fire <em>before</em> {@code EvtDoSkillSucc}.
     * Without this, ReduceHPDebts / SetGlobalValue zero BoL on the client at cast start.
     */
    public static void tryPreArmFromAbility(Ability ability, EntityAvatar avatar) {
        if (avatar == null || avatar.getAvatar() == null) {
            return;
        }
        if (avatar.getAvatar().getAvatarId() != ARLECCHINO_AVATAR_ID) {
            return;
        }
        if (ability == null || ability.getData() == null || ability.getCasterEntity() != avatar) {
            return;
        }
        PendingBurst existing = PENDING.get(avatar.getId());
        if (existing != null && existing.avatar() == avatar) {
            return;
        }
        String name = "";
        if (ability != null && ability.getData() != null && ability.getData().abilityName != null) {
            name = ability.getData().abilityName;
        }
        if (isBurstAbilityName(name)) {
            Grasscutter.getLogger()
                    .info("[BoL] Arlecchino pre-arm lock from ability={}", name);
            onBurstCast(avatar);
        }
    }

    /** True only for a debt reduction from Arlecchino's own burst ability. */
    public static boolean looksLikeBurstWipe(
            Ability ability, EntityAvatar avatar, float curDebt, float newDebt) {
        if (curDebt <= 0.5f
                || ability == null
                || ability.getData() == null
                || ability.getCasterEntity() != avatar) {
            return false;
        }
        String name = "";
        if (ability != null && ability.getData() != null && ability.getData().abilityName != null) {
            name = ability.getData().abilityName;
        }
        return isBurstAbilityName(name);
    }

    /**
     * Highest-priority consume lock: while Q is pending, BoL must not decrease via any path
     * ({@link ArlecchinoBoLSync#pushBoL}, ReduceHPDebts, NA Masque, SetGlobalValue, …). Adds still
     * allowed. Clear only through {@link #applyClear} after the 1s lock + slash/+100ms rules.
     */
    public static boolean isConsumeBlocked(int entityId) {
        return PENDING.containsKey(entityId);
    }

    public static boolean isConsumeBlocked(EntityAvatar avatar) {
        return avatar != null && isConsumeBlocked(avatar.getId());
    }

    /** {@link ArlecchinoBoLSync#pushBoL} uses this to allow the post-slash settle write. */
    public static boolean allowAuthoritativeClear(int entityId) {
        return CLEAR_BYPASS.contains(entityId);
    }

    private static final java.util.Set<Integer> CLEAR_BYPASS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void repinClientBoL(EntityAvatar avatar) {
        if (avatar == null || !isPending(avatar.getId())) {
            return;
        }
        float debt = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (debt <= 0f) {
            PendingBurst pending = PENDING.get(avatar.getId());
            if (pending != null && pending.castSnapshot() > 0f) {
                debt = pending.castSnapshot();
                avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, debt);
            } else {
                return;
            }
        }
        ArlecchinoBoLSync.pushBoL(
                avatar,
                debt,
                0f,
                _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
    }

    public static boolean isPending(int entityId) {
        return PENDING.containsKey(entityId);
    }

    /**
     * Record / refresh slash. Clear is never before {@code cast + LOCK_MS + CLEAR_AFTER_HIT_MS}.
     *
     * <p>Only {@link #isBurstHit} counts — NA / E / teammate / DoT must not arm clear.
     */
    public static void onAttack(EntityAvatar attacker, AttackResult result) {
        if (attacker == null || result == null) {
            return;
        }
        if (attacker.getAvatar() == null || attacker.getAvatar().getAvatarId() != ARLECCHINO_AVATAR_ID) {
            return;
        }
        PendingBurst cur = PENDING.get(attacker.getId());
        if (cur == null) {
            return;
        }
        if (result.getDamage() <= 0f) {
            return;
        }
        // Resolve the packet attacker (often ElementalBurst_Gadget), not only the avatar.
        GameEntity source = null;
        if (attacker.getScene() != null) {
            source = attacker.getScene().getEntityById(result.getAttackerId());
        }
        String abilityName = resolveBurstAbilityName(attacker, source, result);
        if (!isBurstHit(attacker, source, result, abilityName)) {
            return;
        }

        long now = System.currentTimeMillis();
        float debtNow = attacker.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        PENDING.computeIfPresent(
                attacker.getId(),
                (id, pending) -> {
                    long slashAt = pending.slashAtMs() > 0L ? pending.slashAtMs() : now;
                    // Keep earliest slash; refresh clear from that slash vs lock end.
                    if (pending.slashAtMs() > 0L && now > pending.slashAtMs()) {
                        // Later multi-hits: push clear to last hit + 100ms, still not before lock end.
                        slashAt = now;
                    }
                    long lockEnd = pending.castAtMs() + LOCK_MS;
                    long clearAt = Math.max(lockEnd, slashAt) + CLEAR_AFTER_HIT_MS;
                    float snap = pending.bolSnapshot() >= 0f ? pending.bolSnapshot() : debtNow;
                    long firstSlash = pending.slashAtMs() > 0L ? pending.slashAtMs() : now;
                    Grasscutter.getLogger()
                            .info(
                                    "[BoL] Arlecchino burst: slash dmg={} anim={} ability={} snap={} lockLeft={}ms → clear in {}ms",
                                    result.getDamage(),
                                    result.getAnimEventId(),
                                    abilityName,
                                    snap,
                                    Math.max(0L, lockEnd - System.currentTimeMillis()),
                                    Math.max(0L, clearAt - System.currentTimeMillis()));
                    return new PendingBurst(
                            pending.avatar(),
                            pending.castAtMs(),
                            clearAt,
                            snap,
                            pending.castSnapshot(),
                            firstSlash);
                });
    }

    public static void onTick(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        COMPLETED_BURSTS.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue().avatar().getPlayer() == player
                                        && now - entry.getValue().completedAtMs()
                                                >= COMPLETED_CAST_GUARD_MS);
        if (PENDING.isEmpty()) {
            return;
        }
        for (var entry : List.copyOf(PENDING.entrySet())) {
            EntityAvatar avatar = entry.getValue().avatar();
            if (avatar == null || avatar.getPlayer() != player) continue;
            synchronized (avatar) {
                PendingBurst pending = PENDING.get(entry.getKey());
                if (pending == null || pending.avatar() != avatar) continue;
                long lockEnd = pending.castAtMs() + LOCK_MS;

                // Keep client bar pinned while we still hold BoL (client Q often zeros locally).
                if (now < (pending.clearAtMs() > 0L ? pending.clearAtMs() : lockEnd + MISS_TIMEOUT_MS)) {
                    repinClientBoL(avatar);
                }

                // Never clear during the consume lock.
                if (now < lockEnd) {
                    continue;
                }

                boolean due =
                        (pending.clearAtMs() > 0L && now >= pending.clearAtMs())
                                || (pending.clearAtMs() == 0L
                                        && now - pending.castAtMs() >= MISS_TIMEOUT_MS);
                if (!due) {
                    continue;
                }
                String reason =
                        pending.clearAtMs() > 0L && now >= pending.clearAtMs()
                                ? "post-slash"
                                : "miss-timeout";
                // Keep pending discoverable until completion so cleanup can acquire this monitor.
                applyClear(pending.avatar(), pending.bolSnapshot(), pending.castSnapshot(), reason);
                COMPLETED_BURSTS.put(
                        entry.getKey(), new CompletedBurst(avatar, System.currentTimeMillis()));
                PENDING.remove(entry.getKey(), pending);
                BURST_HEALED.remove(entry.getKey());
            }
        }
    }

    /**
     * @return {@code true} if a pending burst took the heal (caller must skip its own heal)
     */
    public static boolean prepareBurstHeal(EntityAvatar avatar, float amount, boolean mute) {
        if (avatar == null) {
            return false;
        }
        synchronized (avatar) {
            PendingBurst pending = PENDING.get(avatar.getId());
            if (pending == null || pending.avatar() != avatar) {
                return false;
            }
            // Apply the heal right away. The Bond itself stays held (PENDING) so the client can
            // still scale the burst by it - only the CLEAR waits for the slash / lock end. Deferring
            // the heal too made Q heal land ~0.5-1s late. One cast heals once: a repeated heal
            // only adds what exceeds the amount already applied.
            if (amount > 0f) {
                float applied = BURST_HEALED.getOrDefault(avatar.getId(), 0f);
                if (amount > applied) {
                    ArlecchinoBoLUtil.applyBurstHeal(avatar, amount - applied, mute);
                    BURST_HEALED.put(avatar.getId(), amount);
                }
            }
            long now = System.currentTimeMillis();
            long lockEnd = pending.castAtMs() + LOCK_MS;
            boolean hold =
                    now < lockEnd
                            || (pending.clearAtMs() > 0L && now < pending.clearAtMs())
                            || (pending.clearAtMs() == 0L
                                    && now - pending.castAtMs() < MISS_TIMEOUT_MS);
            if (!hold) {
                // Past hold - settle the clear now.
                applyClear(pending.avatar(), pending.bolSnapshot(), pending.castSnapshot(), "burst-heal");
                COMPLETED_BURSTS.put(
                        avatar.getId(), new CompletedBurst(avatar, System.currentTimeMillis()));
                PENDING.remove(avatar.getId(), pending);
                BURST_HEALED.remove(avatar.getId());
            }
            return true;
        }
    }

    public static void onBurstHeal(EntityAvatar avatar) {
        prepareBurstHeal(avatar, 0f, true);
    }

    public static void clearEntityState(int entityId) {
        while (true) {
            EntityAvatar avatar = stateAvatar(entityId);
            if (avatar == null) {
                BURST_HEALED.remove(entityId);
                return;
            }
            synchronized (avatar) {
                if (stateAvatar(entityId) != avatar) continue;
                clearAvatarState(avatar);
                return;
            }
        }
    }

    public static void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        var avatars = new HashSet<EntityAvatar>();
        if (player.getTeamManager() != null) {
            avatars.addAll(player.getTeamManager().getActiveTeam());
        }
        for (PendingBurst pending : PENDING.values()) {
            if (pending.avatar().getPlayer() == player) avatars.add(pending.avatar());
        }
        for (CompletedBurst completed : COMPLETED_BURSTS.values()) {
            if (completed.avatar().getPlayer() == player) avatars.add(completed.avatar());
        }
        for (EntityAvatar avatar : avatars) {
            if (avatar != null) clearAvatarState(avatar);
        }
    }

    private static EntityAvatar stateAvatar(int entityId) {
        PendingBurst pending = PENDING.get(entityId);
        if (pending != null) return pending.avatar();
        CompletedBurst completed = COMPLETED_BURSTS.get(entityId);
        return completed == null ? null : completed.avatar();
    }

    private static void clearAvatarState(EntityAvatar avatar) {
        synchronized (avatar) {
            PendingBurst pending = PENDING.get(avatar.getId());
            if (pending != null && pending.avatar() == avatar) {
                PENDING.remove(avatar.getId(), pending);
                BURST_HEALED.remove(avatar.getId());
            }
            CompletedBurst completed = COMPLETED_BURSTS.get(avatar.getId());
            if (completed != null && completed.avatar() == avatar) {
                COMPLETED_BURSTS.remove(avatar.getId(), completed);
                if (pending == null) BURST_HEALED.remove(avatar.getId());
            }
        }
    }

    private static boolean hasRecentlyCompletedCast(EntityAvatar avatar, long now) {
        CompletedBurst completed = COMPLETED_BURSTS.get(avatar.getId());
        if (completed == null) {
            return false;
        }
        if (completed.avatar() == avatar
                && now - completed.completedAtMs() < COMPLETED_CAST_GUARD_MS) {
            return true;
        }
        COMPLETED_BURSTS.remove(avatar.getId(), completed);
        return false;
    }

    public static boolean hasRecentlyCompletedCast(EntityAvatar avatar) {
        return avatar != null && hasRecentlyCompletedCast(avatar, System.currentTimeMillis());
    }

    private static boolean isBurstHit(
            EntityAvatar caster, GameEntity source, AttackResult result, String abilityName) {
        if (isBurstAbilityName(abilityName)) {
            return true;
        }
        // Q damage is often owned by the burst gadget — any of its abilities named ElementalBurst.
        if (source != null && source != caster && sourceHasBurstAbility(source)) {
            return true;
        }
        String animEvent = result != null ? result.getAnimEventId() : null;
        if (animEvent == null || animEvent.isEmpty()) {
            return false;
        }
        String s = animEvent.toLowerCase(Locale.ROOT);
        return s.contains("arlecchino_elementalburst")
                || s.contains("elementalburst_attack")
                || (s.contains("elementalburst") && s.contains("arlecchino"));
    }

    private static boolean isBurstAbilityName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (ArlecchinoBoLUtil.isBurstAttackAbility(name)) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        // Bind to Arlecchino Q only — never generic teammate ElementalBurst.
        return lower.contains("arlecchino_elementalburst")
                || name.contains(BURST_ABILITY_MARKER)
                || (lower.contains("elementalburst") && lower.contains("arlecchino"));
    }

    private static String resolveBurstAbilityName(
            EntityAvatar caster, GameEntity source, AttackResult result) {
        String fromCaster = resolveAbilityName(caster, result);
        if (isBurstAbilityName(fromCaster)) {
            return fromCaster;
        }
        if (source != null && source != caster) {
            String fromSource = resolveAbilityName(source, result);
            if (fromSource != null && !fromSource.isEmpty()) {
                return fromSource;
            }
        }
        return fromCaster;
    }

    private static String resolveAbilityName(GameEntity entity, AttackResult result) {
        if (entity == null || result == null || !result.hasAbilityIdentifier()) {
            return null;
        }
        try {
            int instanced = result.getAbilityIdentifier().getInstancedAbilityId();
            if (instanced <= 0) {
                return null;
            }
            List<Ability> abilities = entity.getInstancedAbilities();
            if (abilities == null || instanced > abilities.size()) {
                return null;
            }
            Ability ability = abilities.get(instanced - 1);
            if (ability == null || ability.getData() == null) {
                return null;
            }
            return ability.getData().abilityName;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean sourceHasBurstAbility(GameEntity source) {
        try {
            List<Ability> abilities = source.getInstancedAbilities();
            if (abilities == null) {
                return false;
            }
            for (Ability ability : abilities) {
                if (ability == null || ability.getData() == null || ability.getData().abilityName == null) {
                    continue;
                }
                if (isBurstAbilityName(ability.getData().abilityName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void applyClear(
            EntityAvatar avatar, float bolSnapshot, float castSnapshot, String reason) {
        if (avatar == null || avatar.getAvatar() == null) {
            return;
        }

        float curDebt = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        ArlecchinoBoLSync.markPostBurstClear(avatar.getId());

        float allowance = bolSnapshot >= 0f ? bolSnapshot : Math.max(0f, castSnapshot);
        float toClear = Math.min(curDebt, allowance);
        CLEAR_BYPASS.add(avatar.getId());
        try {
            if (toClear <= 0f) {
                Grasscutter.getLogger()
                        .info(
                                "[BoL] Arlecchino burst: clear skip (cur={} snap={} reason={})",
                                curDebt,
                                bolSnapshot,
                                reason);
                if (curDebt > 0f) {
                    ArlecchinoBoLSync.pushBoL(
                            avatar,
                            curDebt,
                            0f,
                            _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
                } else {
                    ArlecchinoBoLSync.pushBoL(
                            avatar,
                            0f,
                            0f,
                            _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH);
                }
                return;
            }

            float newDebt = Math.max(0f, curDebt - toClear);
            ArlecchinoBoLSync.pushBoL(
                    avatar,
                    newDebt,
                    -toClear,
                    newDebt <= 0f
                            ? _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH
                            : _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY);
            Grasscutter.getLogger()
                    .info(
                            "[BoL] Arlecchino burst: cleared {} BoL → remain {} (snap={} reason={})",
                            toClear,
                            newDebt,
                            bolSnapshot,
                            reason);
        } finally {
            CLEAR_BYPASS.remove(avatar.getId());
        }
    }
}
