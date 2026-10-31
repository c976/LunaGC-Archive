package emu.grasscutter.game.tower;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.gadget.GadgetContent;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.ChangeEnergyReasonOuterClass.ChangeEnergyReason;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketDungeonChallengeFinishNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketWorktopOptionNotify;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime guards for the tower handoff, which is more stateful than a normal dungeon. */
public final class TowerAbyssFix {
    public static final Position MUSK_REEF_DOOR = new Position(1638.7793f, 195.0458f, -2659.1758f);
    public static final Position MUSK_REEF_ROT = new Position(0.0f, 181.4546f, 0.0f);

    private static final int START_WORKTOP_GADGET_ID = 70360010;
    private static final ConcurrentHashMap<Integer, EntryAnchor> ENTRY_BY_UID = new ConcurrentHashMap<>();

    private TowerAbyssFix() {}

    public static void rememberEntry(Player player) {
        if (player == null || player.getPosition() == null) return;

        int sceneId = player.getSceneId() > 0 ? player.getSceneId() : 3;
        ENTRY_BY_UID.put(
                player.getUid(),
                new EntryAnchor(sceneId, player.getPosition(), player.getRotation(), 0));
        try {
            if (player.getTowerManager() != null && player.getTowerManager().getTowerData() != null) {
                player.getTowerManager().getTowerData().entryScene = sceneId;
            }
        } catch (Throwable ignored) {
            // Entry memory is best effort; it must never block team selection.
        }
    }

    public static void rememberEnterPoint(Player player, int pointId) {
        if (player == null || pointId <= 0) return;
        var anchor = ENTRY_BY_UID.get(player.getUid());
        if (anchor != null) anchor.pointId = pointId;
    }

    public static void clearEntry(Player player) {
        if (player != null) ENTRY_BY_UID.remove(player.getUid());
    }

    public static boolean isFirstChamber(Player player) {
        try {
            return player.getTowerManager().getTowerData().currentLevel <= 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void ensureStartKey(Player player) {
        // Modern tower floors set option 175/176 from Lua. Injecting 177 here created a second,
        // non-functional "Start Challenge" row and reappeared after the real option was taken.
        // Keep this as a no-op so delayed enter-level tasks stay harmless.
    }

    /** Clear start-key options after the challenge has begun (or after a successful select). */
    public static void clearStartKeyOptions(Player player) {
        if (player == null || player.getScene() == null) return;
        Scene scene = player.getScene();
        for (GameEntity entity : scene.getEntities().values()) {
            if (!(entity instanceof EntityGadget gadget)
                    || gadget.getGadgetId() != START_WORKTOP_GADGET_ID
                    || !(gadget.getContent() instanceof GadgetWorktop worktop)) {
                continue;
            }
            if (worktop.getWorktopOptions().isEmpty()) continue;
            worktop.getWorktopOptions().clear();
            scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));
        }
    }

    /** Heal and refill, then make slot-1 the active avatar (server + scene). */
    public static void prepareFirstChamber(Player player) {
        if (player == null || player.getTeamManager() == null) return;
        TeamManager teamManager = player.getTeamManager();
        for (EntityAvatar entity : teamManager.getActiveTeam()) {
            if (entity == null || entity.getAvatar() == null) continue;
            try {
                resetHealth(player, entity);
                resetEnergy(player, entity);
            } catch (Exception exception) {
                Grasscutter.getLogger().warn(
                        "Tower first chamber state reset failed uid={} avatar={}: {}",
                        player.getUid(),
                        entity.getAvatar().getAvatarId(),
                        exception.toString());
            }
        }
        forceLeadAvatar(player);
    }

    /**
     * Always start a chamber / half in slot 1. {@link TeamManager#useTemporaryTeam} can preserve the
     * previous half's slot index; setting the index alone does not {@code replaceEntity}.
     */
    public static void forceLeadAvatar(Player player) {
        if (player == null || player.getTeamManager() == null) return;
        TeamManager teamManager = player.getTeamManager();
        var team = teamManager.getActiveTeam();
        if (team == null || team.isEmpty()) return;
        EntityAvatar lead = team.get(0);
        if (lead == null || lead.getAvatar() == null) return;

        EntityAvatar current = teamManager.getCurrentAvatarEntity();
        teamManager.setCurrentCharacterIndex(0);
        if (current == null || current.getAvatar() == null) {
            return;
        }
        if (current.getAvatar().getGuid() == lead.getAvatar().getGuid()) {
            return;
        }
        try {
            teamManager.changeAvatar(lead.getAvatar().getGuid());
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower forceLeadAvatar failed uid={}: {}",
                            player.getUid(),
                            t.toString());
            try {
                if (player.getScene() != null) {
                    player.getScene().replaceEntity(current, lead);
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
    }

    /**
     * Ends any live challenge and tells the client to drop the timer / kill-count UI. Must run
     * before nulling {@code scene.challenge} or leaving the dungeon, otherwise the HUD sticks in
     * the overworld.
     */
    public static void endChallengeUi(Player player) {
        endChallengeUi(player, false);
    }

    /**
     * @param quiet if true, skip {@code EVENT_CHALLENGE_FAIL} so reconfigure can open team UI
     *     in-dungeon instead of fail-settle.
     */
    public static void endChallengeUi(Player player, boolean quiet) {
        if (player == null) return;
        Scene scene = player.getScene();
        if (scene == null) return;
        var challenge = scene.getChallenge();
        if (challenge == null) return;
        try {
            if (quiet) {
                challenge.abortQuiet();
            } else if (challenge.inProgress()) {
                challenge.fail();
            } else {
                // Already finished server-side but client may still show the HUD.
                player.sendPacket(new PacketDungeonChallengeFinishNotify(challenge));
            }
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("Tower endChallengeUi failed uid={}: {}", player.getUid(), t.toString());
            try {
                player.sendPacket(new PacketDungeonChallengeFinishNotify(challenge));
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
        try {
            scene.setChallenge(null);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private static void resetHealth(Player player, EntityAvatar entity) {
        Avatar avatar = entity.getAvatar();
        float maxHp = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 0) maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 0) return;

        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, maxHp);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, maxHp);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0);
        avatar.setCurrentHp(maxHp);
        player.sendPacket(new PacketAvatarFightPropUpdateNotify(avatar, FightProperty.FIGHT_PROP_CUR_HP));
        player.sendPacket(new PacketAvatarFightPropUpdateNotify(avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        player.sendPacket(new PacketAvatarLifeStateChangeNotify(avatar));
    }

    private static void resetEnergy(Player player, EntityAvatar entity) {
        Avatar avatar = entity.getAvatar();
        FightProperty current;
        try {
            current = entity.GetEnergyProp(avatar);
        } catch (Throwable ignored) {
            var depot = avatar.getSkillDepot();
            ElementType element = depot == null ? null : depot.getElementType();
            if (element == null) return;
            current = element.getCurEnergyProp();
        }

        FightProperty maximum;
        if (current == FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY) {
            maximum = FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY;
        } else {
            var depot = avatar.getSkillDepot();
            ElementType element = depot == null ? null : depot.getElementType();
            if (element == null) return;
            maximum = element.getMaxEnergyProp();
        }
        float value = entity.getFightProperty(maximum);
        if (value <= 0) value = 100;
        avatar.setFightProperty(current, value);
        entity.setFightProperty(current, value);
        avatar.setCurrentEnergy(current, value);
        player.sendPacket(new PacketAvatarFightPropUpdateNotify(avatar, current));
        player.sendPacket(new PacketAvatarFightPropNotify(avatar));
        if (entity.getScene() != null) {
            entity.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, current));
        }
    }

    public static int resolveExitScene(Player player, int fallback) {
        // Spiral Abyss always returns to the overworld Musk Reef island.
        return 3;
    }

    public static Position resolveExitPosition(Player player, int fallbackScene, int fallbackPoint) {
        // Always use the dungeon-entry tranPos (point 45), never the remembered feet position —
        // that often lands on the approach beach below the portal.
        int sceneId = 3;
        int pointId = fallbackPoint > 0 ? fallbackPoint : 45;
        for (int candidate : pointId == 45 ? new int[] {45} : new int[] {pointId, 45}) {
            try {
                ScenePointEntry entry = GameData.getScenePointEntryById(sceneId, candidate);
                if (entry == null || entry.getPointData() == null) continue;
                Position position = entry.getPointData().getTranPos();
                if (position == null) position = entry.getPointData().getPos();
                if (position != null) return new Position(position);
            } catch (Throwable ignored) {
                // Fall through to the fixed Musk Reef door.
            }
        }
        return new Position(MUSK_REEF_DOOR);
    }

    public static Position resolveExitRotation(Player player) {
        try {
            ScenePointEntry entry = GameData.getScenePointEntryById(3, 45);
            if (entry != null && entry.getPointData() != null && entry.getPointData().getRot() != null) {
                return new Position(entry.getPointData().getRot());
            }
        } catch (Throwable ignored) {
            // Use the compiled-in door facing.
        }
        return new Position(MUSK_REEF_ROT);
    }

    /** Sync door placement before save so a full quit does not resume inside the dungeon. */
    public static void applyDoorExitForLogout(Player player) {
        if (player == null) return;
        try {
            if (player.getTeamManager() != null) {
                player.getTeamManager().cleanTemporaryTeam();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
        player.setSceneId(3);
        if (player.getPosition() != null) {
            player.getPosition().set(MUSK_REEF_DOOR);
        }
        if (player.getRotation() != null) {
            player.getRotation().set(MUSK_REEF_ROT);
        }
        clearEntry(player);
        try {
            if (player.getTowerManager() != null) {
                player.getTowerManager().clearEntry();
                player.getTowerManager().clearAbyssResume();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    public static boolean isTowerSceneId(int sceneId) {
        return sceneId >= 33100 && sceneId <= 34999;
    }

    public static boolean isInTowerDungeon(Player player) {
        if (player == null) return false;
        if (player.getScene() != null) {
            Scene scene = player.getScene();
            DungeonManager manager = scene.getDungeonManager();
            if (manager != null && manager.isTowerDungeon()) return true;
            if (scene.getSceneType() == SceneType.SCENE_DUNGEON && isTowerSceneId(scene.getId())) {
                return true;
            }
        }
        return isTowerSceneId(player.getSceneId());
    }

    public static final class EntryAnchor {
        public final int sceneId;
        public final Position position;
        public final Position rotation;
        public int pointId;

        private EntryAnchor(int sceneId, Position position, Position rotation, int pointId) {
            this.sceneId = sceneId > 0 ? sceneId : 3;
            this.position = position == null ? new Position(MUSK_REEF_DOOR) : new Position(position);
            this.rotation = rotation == null ? new Position(MUSK_REEF_ROT) : new Position(rotation);
            this.pointId = pointId;
        }
    }
}
