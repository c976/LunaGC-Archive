package emu.grasscutter.game.player;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.utils.Utils;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fontaine dive combat must NOT live in {@link GameConstants#DEFAULT_ABILITY_STRINGS}.
 *
 * <p>Those embryos are rebuilt for every avatar on login / team swap / death / revive — anyone on
 * land then plays the blue water circle. Official ConfigGlobalCombat also keeps them out of
 * defaults; they belong on dive levels only.
 *
 * <p>Strategy: attach Dive_Team + Absorb embryos only while diving; detach only on clearly-land
 * motions (STANDBY/RUN/SWIM/…). Do NOT detach on FIGHT/NOTIFY/unknown — DiveCast aim briefly leaves
 * MOTION_DIVE_* and must keep the embryos.
 *
 * <p>Avatar embryo push does not rely on EntityAvatar source hooks (remote jar may lack them):
 * {@link #attach} builds AbilityControlBlock and sends AbilityChangeNotify itself.
 */
public final class DiveAbilityHelper {
    private static final ConcurrentHashMap<Integer, Boolean> ATTACHED = new ConcurrentHashMap<>();

    private DiveAbilityHelper() {}

    public static boolean isDiveMotion(MotionState state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case MotionState_MOTION_DIVE_IDLE,
                    MotionState_MOTION_DIVE_MOVE,
                    MotionState_MOTION_DIVE_DASH,
                    MotionState_MOTION_DIVE_DOLPHINE,
                    MotionState_MOTION_DIVE_SWIM_MOVE,
                    MotionState_MOTION_DIVE_SWIM_IDLE,
                    MotionState_MOTION_DIVE_SWIM_DASH -> true;
            default -> false;
        };
    }

    /** Surface / land locomotion — safe to scrub dive embryos. */
    public static boolean isClearlyLandMotion(MotionState state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case MotionState_MOTION_STANDBY,
                    MotionState_MOTION_STANDBY_MOVE,
                    MotionState_MOTION_DANGER_STANDBY,
                    MotionState_MOTION_DANGER_STANDBY_MOVE,
                    MotionState_MOTION_RUN,
                    MotionState_MOTION_DANGER_RUN,
                    MotionState_MOTION_WALK,
                    MotionState_MOTION_DANGER_WALK,
                    MotionState_MOTION_DASH,
                    MotionState_MOTION_DANGER_DASH,
                    MotionState_MOTION_DASH_BEFORE_SHAKE,
                    MotionState_MOTION_JUMP,
                    MotionState_MOTION_FALL_ON_GROUND,
                    MotionState_MOTION_LAND_SPEED,
                    MotionState_MOTION_CLIMB,
                    MotionState_MOTION_CLIMB_JUMP,
                    MotionState_MOTION_STANDBY_TO_CLIMB,
                    MotionState_MOTION_JUMP_UP_WALL_FOR_STANDBY,
                    MotionState_MOTION_LADDER_IDLE,
                    MotionState_MOTION_LADDER_MOVE,
                    MotionState_MOTION_LADDER_SLIP,
                    MotionState_MOTION_LADDER_TO_STANDBY,
                    MotionState_MOTION_FLY,
                    MotionState_MOTION_FLY_FAST,
                    MotionState_MOTION_FLY_SLOW,
                    MotionState_MOTION_POWERED_FLY,
                    MotionState_MOTION_SWIM_IDLE,
                    MotionState_MOTION_SWIM_MOVE,
                    MotionState_MOTION_SWIM_DASH,
                    MotionState_MOTION_SWIM_JUMP,
                    MotionState_MOTION_SKIFF_BOARDING,
                    MotionState_MOTION_SKIFF_NORMAL,
                    MotionState_MOTION_SKIFF_DASH,
                    MotionState_MOTION_SKIFF_POWERED_DASH,
                    MotionState_MOTION_SIT_IDLE,
                    MotionState_MOTION_SLIP -> true;
            default -> false;
        };
    }

    public static boolean isAttached(Player player) {
        return player != null && Boolean.TRUE.equals(ATTACHED.get(player.getUid()));
    }

    public static boolean isCurrentlyDiving(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return false;
        }
        EntityAvatar cur = player.getTeamManager().getCurrentAvatarEntity();
        if (cur == null) {
            return false;
        }
        MotionState state = cur.getMotionState();
        if (isDiveMotion(state)) {
            return true;
        }
        // Aim / cast / fight under water: keep embryos while still attached.
        return isAttached(player) && !isClearlyLandMotion(state);
    }

    public static void onMotionChange(Player player, GameEntity entity, MotionState state) {
        if (player == null || !(entity instanceof EntityAvatar)) {
            return;
        }
        EntityAvatar current =
                player.getTeamManager() != null
                        ? player.getTeamManager().getCurrentAvatarEntity()
                        : null;
        if (current == null || current.getId() != entity.getId()) {
            return;
        }
        if (isDiveMotion(state)) {
            if (!isAttached(player)) {
                attach(player);
            }
        } else if (isClearlyLandMotion(state)) {
            if (isAttached(player)) {
                forceCleanLand(player);
            }
        }
    }

    /** Team rebuild / scene enter: scrub on land, re-attach if still underwater. */
    public static void onTeamOrSceneRebuild(Player player) {
        if (player == null) {
            return;
        }
        if (isCurrentlyDiving(player)) {
            attach(player);
        } else {
            forceCleanLand(player);
        }
    }

    public static void attach(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        boolean was = isAttached(player);
        ATTACHED.put(player.getUid(), true);
        ensureTeamDiveAbility(player);
        ensureAvatarDiveAbilities(player);
        if (!was) {
            Grasscutter.getLogger().debug("[DiveAbility] attach uid={}", player.getUid());
        }
    }

    public static void forceCleanLand(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        boolean was = isAttached(player);
        ATTACHED.put(player.getUid(), false);
        removeTeamDiveAbility(player, was);
        removeAvatarDiveAbilities(player, was);
        if (was) {
            Grasscutter.getLogger().debug("[DiveAbility] cleanLand uid={}", player.getUid());
        }
    }

    public static void clearPlayer(int uid) {
        ATTACHED.remove(uid);
    }

    /** Append dive combat embryos when attached (EntityAvatar hook or manual notify). */
    public static int appendDiveEmbryos(
            EntityAvatar avatar, AbilityControlBlock.Builder abilityControlBlock, int embryoId) {
        if (avatar == null || abilityControlBlock == null || !isAttached(avatar.getPlayer())) {
            return embryoId;
        }
        Set<Integer> existing = new HashSet<>();
        for (AbilityEmbryo emb : abilityControlBlock.getAbilityEmbryoListList()) {
            existing.add(emb.getAbilityNameHash());
        }
        for (String name : GameConstants.DIVE_AVATAR_ABILITIES) {
            int hash = Utils.abilityHash(name);
            if (existing.contains(hash)) {
                continue;
            }
            abilityControlBlock.addAbilityEmbryoList(
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++embryoId)
                            .setAbilityNameHash(hash)
                            .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                            .build());
            existing.add(hash);
        }
        return embryoId;
    }

    private static void ensureAvatarDiveAbilities(Player player) {
        for (EntityAvatar entity : player.getTeamManager().getActiveTeam()) {
            if (entity == null) {
                continue;
            }
            addDiveInstancedAbilities(player, entity);
            try {
                AbilityControlBlock base = entity.getAbilityControlBlock();
                AbilityControlBlock.Builder builder = base.toBuilder();
                int embryoId = base.getAbilityEmbryoListCount();
                appendDiveEmbryos(entity, builder, embryoId);
                player.sendPacket(new PacketAbilityChangeNotify(entity.getId(), builder.build()));
            } catch (Throwable t) {
                player.sendPacket(new PacketAbilityChangeNotify(entity));
            }
        }
    }

    private static void removeAvatarDiveAbilities(Player player, boolean notify) {
        Set<Integer> diveHashes = diveAvatarHashes();
        for (EntityAvatar entity : player.getTeamManager().getActiveTeam()) {
            if (entity == null) {
                continue;
            }
            boolean removedInst =
                    entity.getInstancedAbilities()
                            .removeIf(
                                    a ->
                                            a != null
                                                    && a.getData() != null
                                                    && isDiveAvatarAbility(a.getData().abilityName));
            if (!(notify || removedInst)) {
                continue;
            }
            try {
                AbilityControlBlock base = entity.getAbilityControlBlock();
                AbilityControlBlock.Builder builder = AbilityControlBlock.newBuilder();
                int id = 0;
                for (AbilityEmbryo emb : base.getAbilityEmbryoListList()) {
                    if (diveHashes.contains(emb.getAbilityNameHash())) {
                        continue;
                    }
                    builder.addAbilityEmbryoList(
                            AbilityEmbryo.newBuilder()
                                    .setAbilityId(++id)
                                    .setAbilityNameHash(emb.getAbilityNameHash())
                                    .setAbilityOverrideNameHash(emb.getAbilityOverrideNameHash())
                                    .build());
                }
                player.sendPacket(new PacketAbilityChangeNotify(entity.getId(), builder.build()));
            } catch (Throwable t) {
                player.sendPacket(new PacketAbilityChangeNotify(entity));
            }
        }
    }

    private static void addDiveInstancedAbilities(Player player, EntityAvatar entity) {
        for (String name : GameConstants.DIVE_AVATAR_ABILITIES) {
            AbilityData data = GameData.getAbilityData(name);
            if (data == null) {
                continue;
            }
            boolean already =
                    entity.getInstancedAbilities().stream()
                            .anyMatch(
                                    a ->
                                            a != null
                                                    && a.getData() != null
                                                    && name.equals(a.getData().abilityName));
            if (!already) {
                player.getAbilityManager().addAbilityToEntity(entity, data);
            }
        }
    }

    private static boolean isDiveAvatarAbility(String name) {
        if (name == null) {
            return false;
        }
        for (String dive : GameConstants.DIVE_AVATAR_ABILITIES) {
            if (dive.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Integer> diveAvatarHashes() {
        Set<Integer> hashes = new HashSet<>();
        for (String name : GameConstants.DIVE_AVATAR_ABILITIES) {
            hashes.add(Utils.abilityHash(name));
        }
        return hashes;
    }

    private static void ensureTeamDiveAbility(Player player) {
        String ability = GameConstants.DIVE_TEAM_ABILITY;
        Set<String> embryos = player.getTeamManager().getTeamAbilityEmbryos();
        EntityTeam team = player.getTeamManager().getEntity();
        boolean changed = embryos.add(ability);
        if (team != null) {
            AbilityData data = GameData.getAbilityData(ability);
            if (data != null) {
                boolean already =
                        team.getInstancedAbilities().stream()
                                .anyMatch(
                                        a ->
                                                a != null
                                                        && a.getData() != null
                                                        && ability.equals(a.getData().abilityName));
                if (!already) {
                    player.getAbilityManager().addAbilityToEntity(team, data);
                    changed = true;
                }
            }
            if (changed) {
                player.sendPacket(
                        new PacketAbilityChangeNotify(team.getId(), buildTeamBlock(player)));
            }
        }
    }

    private static void removeTeamDiveAbility(Player player, boolean notify) {
        String ability = GameConstants.DIVE_TEAM_ABILITY;
        Set<String> embryos = player.getTeamManager().getTeamAbilityEmbryos();
        boolean removed = embryos.remove(ability);
        EntityTeam team = player.getTeamManager().getEntity();
        if (team != null) {
            boolean removedInst =
                    team.getInstancedAbilities()
                            .removeIf(
                                    a ->
                                            a != null
                                                    && a.getData() != null
                                                    && ability.equals(a.getData().abilityName));
            if (notify || removed || removedInst) {
                player.sendPacket(
                        new PacketAbilityChangeNotify(team.getId(), buildTeamBlock(player)));
            }
        }
    }

    private static AbilityControlBlock buildTeamBlock(Player player) {
        AbilityControlBlock base = player.getTeamManager().getAbilityControlBlock();
        AbilityControlBlock.Builder builder = AbilityControlBlock.newBuilder();
        int id = 0;
        int diveHash = Utils.abilityHash(GameConstants.DIVE_TEAM_ABILITY);
        for (AbilityEmbryo emb : base.getAbilityEmbryoListList()) {
            if (emb.getAbilityNameHash() == diveHash && !isAttached(player)) {
                continue;
            }
            builder.addAbilityEmbryoList(
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++id)
                            .setAbilityNameHash(emb.getAbilityNameHash())
                            .setAbilityOverrideNameHash(emb.getAbilityOverrideNameHash())
                            .build());
        }
        if (isAttached(player)) {
            boolean exists =
                    builder.getAbilityEmbryoListList().stream()
                            .anyMatch(e -> e.getAbilityNameHash() == diveHash);
            if (!exists) {
                builder.addAbilityEmbryoList(
                        AbilityEmbryo.newBuilder()
                                .setAbilityId(++id)
                                .setAbilityNameHash(diveHash)
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build());
            }
        }
        return builder.build();
    }
}
