/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.entity.EntityGadget
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.world.Position
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.game.world.data.TeleportProperties
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.scripts.SceneScriptManager
 *  emu.grasscutter.scripts.ScriptLoader
 *  emu.grasscutter.scripts.data.SceneBlock
 *  emu.grasscutter.scripts.data.SceneConfig
 *  emu.grasscutter.scripts.data.SceneGadget
 *  emu.grasscutter.scripts.data.SceneGroup
 *  emu.grasscutter.scripts.data.SceneMeta
 *  emu.grasscutter.server.packet.send.PacketScenePlayerLocationNotify
 *  emu.grasscutter.server.packet.send.PacketWorldPlayerLocationNotify
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DomainChallengeKeyHelper;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneConfig;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMeta;
import emu.grasscutter.server.packet.send.PacketScenePlayerLocationNotify;
import emu.grasscutter.server.packet.send.PacketWorldPlayerLocationNotify;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.script.Bindings;

public final class DomainContinueSpawnHelper {
    private static final long CONTINUE_WINDOW_MS = 8000L;
    private static final float KEY_STANDOFF = 2.2f;
    private static final ConcurrentHashMap<Integer, Long> CONTINUE_UNTIL_MS = new ConcurrentHashMap();

    private DomainContinueSpawnHelper() {
    }

    public static void markContinueChallenge(Player player) {
        if (player == null) {
            return;
        }
        CONTINUE_UNTIL_MS.put(player.getUid(), System.currentTimeMillis() + 8000L);
    }

    public static boolean tryApplyNearChallengeKey(Player player) {
        return DomainContinueSpawnHelper.applyNearChallengeKey(player);
    }

    public static void adjustTeleportToNearKey(TeleportProperties teleportProperties) {
        Position[] positionArray;
        if (teleportProperties == null) {
            return;
        }
        int n = teleportProperties.getSceneId();
        if (n <= 0) {
            return;
        }
        if (n < 40100 || n > 40999) {
            return;
        }
        if (teleportProperties.getDungeonId() > 0 || teleportProperties.getTeleportType() != null) {
            // empty if block
        }
        if ((positionArray = DomainContinueSpawnHelper.findKeyAndBornFromMeta(n)) == null || positionArray[0] == null) {
            return;
        }
        Position position = positionArray[0];
        Position position2 = positionArray[1];
        Position position3 = DomainContinueSpawnHelper.offsetTowardEntrance(position, position2);
        Position position4 = DomainContinueSpawnHelper.faceToward(position, position3);
        teleportProperties.setTeleportTo(position3);
        if (position4 != null) {
            teleportProperties.setTeleportRot(position4);
        }
        Grasscutter.getLogger().info("Domain near-key teleport scene={} -> ({}, {}, {})", new Object[]{n, Float.valueOf(position3.getX()), Float.valueOf(position3.getY()), Float.valueOf(position3.getZ())});
    }

    public static boolean applyNearChallengeKey(Player player) {
        EntityAvatar entityAvatar;
        Position position;
        Position position2;
        if (player == null) {
            return false;
        }
        Scene scene = player.getScene();
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return false;
        }
        Position position3 = DomainContinueSpawnHelper.findChallengeKeyPosition(scene);
        Position[] keyAndBorn;
        if (position3 == null && (keyAndBorn = DomainContinueSpawnHelper.findKeyAndBornFromMeta(scene.getId())) != null) {
            position3 = keyAndBorn[0];
        }
        if (position3 == null) {
            return false;
        }
        position2 = DomainContinueSpawnHelper.resolveBornPos(scene);
        if (position2 == null && (keyAndBorn = DomainContinueSpawnHelper.findKeyAndBornFromMeta(scene.getId())) != null) {
            position2 = keyAndBorn[1];
        }
        position = DomainContinueSpawnHelper.offsetTowardEntrance(position3, position2);
        Position position4 = DomainContinueSpawnHelper.faceToward(position3, position);
        player.getPosition().set(position);
        if (position4 != null) {
            player.getRotation().set(position4);
        }
        if (player.getTeamManager() != null && (entityAvatar = player.getTeamManager().getCurrentAvatarEntity()) != null) {
            entityAvatar.getPosition().set(position);
            if (position4 != null) {
                entityAvatar.getRotation().set(position4);
            }
            try {
                entityAvatar.move(position, position4 != null ? position4 : player.getRotation());
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        try {
            if (player.getWorld() != null) {
                player.sendPacket((BasePacket)new PacketWorldPlayerLocationNotify(player.getWorld()));
            }
            if (player.getScene() != null) {
                player.sendPacket((BasePacket)new PacketScenePlayerLocationNotify(player.getScene()));
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        CONTINUE_UNTIL_MS.remove(player.getUid());
        return true;
    }

    private static Position resolveBornPos(Scene scene) {
        SceneScriptManager sceneScriptManager = scene.getScriptManager();
        if (sceneScriptManager == null) {
            return null;
        }
        SceneConfig sceneConfig = sceneScriptManager.getConfig();
        return sceneConfig != null ? sceneConfig.born_pos : null;
    }

    private static Position findChallengeKeyPosition(Scene scene) {
        Position position = null;
        Position position2 = null;
        for (Object object : scene.getEntities().values()) {
            EntityGadget entityGadget;
            if (!(object instanceof EntityGadget) || !DomainChallengeKeyHelper.isChallengeKeyGadgetId((entityGadget = (EntityGadget)object).getGadgetId())) continue;
            if (entityGadget.getGadgetId() == 70360010) {
                position = DomainContinueSpawnHelper.copyPosition(entityGadget.getPosition());
                continue;
            }
            if (position2 != null) continue;
            position2 = DomainContinueSpawnHelper.copyPosition(entityGadget.getPosition());
        }
        if (position != null || position2 != null) {
            return position != null ? position : position2;
        }
        SceneScriptManager sceneScriptManager = scene.getScriptManager();
        if (sceneScriptManager == null) {
            return null;
        }
        for (SceneBlock sceneBlock : sceneScriptManager.getBlocks().values()) {
            Position position3 = DomainContinueSpawnHelper.findKeyInBlock(sceneBlock, scene.getId());
            if (position3 == null) continue;
            return position3;
        }
        return null;
    }

    private static Position[] findKeyAndBornFromMeta(int n) {
        try {
            SceneMeta sceneMeta = SceneMeta.of((int)n);
            if (sceneMeta == null) {
                return null;
            }
            Position position = sceneMeta.config != null ? sceneMeta.config.born_pos : null;
            Position position2 = null;
            Map<Integer, SceneBlock> map = sceneMeta.blocks;
            if (map != null) {
                for (SceneBlock sceneBlock : map.values()) {
                    DomainContinueSpawnHelper.ensureBlockGroupsLoaded(sceneBlock, n, sceneMeta);
                    position2 = DomainContinueSpawnHelper.findKeyInBlock(sceneBlock, n);
                    if (position2 == null) continue;
                    break;
                }
            }
            if (position2 == null) {
                Grasscutter.getLogger().info("Domain near-key: no challenge key found in SceneMeta scene={}", (Object)n);
                return null;
            }
            Grasscutter.getLogger().info("Domain near-key scene={} key=({}, {}, {})", new Object[]{n, Float.valueOf(position2.getX()), Float.valueOf(position2.getY()), Float.valueOf(position2.getZ())});
            return new Position[]{position2, position};
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().debug("findKeyAndBornFromMeta scene={} failed: {}", (Object)n, (Object)throwable.toString());
            return null;
        }
    }

    private static void ensureBlockGroupsLoaded(SceneBlock sceneBlock, int n, SceneMeta sceneMeta) {
        if (sceneBlock == null || sceneBlock.groups != null) {
            return;
        }
        try {
            Bindings bindings = sceneMeta != null && sceneMeta.context != null ? sceneMeta.context : ScriptLoader.getEngine().createBindings();
            sceneBlock.load(n, bindings);
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().debug("Domain near-key: block.load failed scene={} block={}: {}", new Object[]{n, sceneBlock.id, throwable.toString()});
        }
    }

    private static Position findKeyInBlock(SceneBlock sceneBlock, int n) {
        if (sceneBlock == null || sceneBlock.groups == null) {
            return null;
        }
        Position position = null;
        Position position2 = null;
        for (SceneGroup sceneGroup : sceneBlock.groups.values()) {
            if (sceneGroup == null) continue;
            try {
                if (!sceneGroup.isLoaded()) {
                    sceneGroup.load(n);
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            if (sceneGroup.gadgets == null) continue;
            for (SceneGadget sceneGadget : sceneGroup.gadgets.values()) {
                if (sceneGadget == null || !DomainChallengeKeyHelper.isChallengeKeyGadgetId(sceneGadget.gadget_id) || sceneGadget.pos == null) continue;
                if (sceneGadget.gadget_id == 70360010) {
                    position = DomainContinueSpawnHelper.copyPosition(sceneGadget.pos);
                    continue;
                }
                if (position2 != null) continue;
                position2 = DomainContinueSpawnHelper.copyPosition(sceneGadget.pos);
            }
        }
        return position != null ? position : position2;
    }

    private static Position offsetTowardEntrance(Position position, Position position2) {
        float f;
        float f2;
        if (position2 != null) {
            f2 = position2.getX() - position.getX();
            f = position2.getZ() - position.getZ();
        } else {
            f2 = 0.0f;
            f = 1.0f;
        }
        float f3 = (float)Math.sqrt(f2 * f2 + f * f);
        if (f3 < 0.5f) {
            return new Position(position.getX(), position.getY(), position.getZ() + 2.2f);
        }
        float f4 = 2.2f / f3;
        return new Position(position.getX() + f2 * f4, position.getY(), position.getZ() + f * f4);
    }

    private static Position faceToward(Position position, Position position2) {
        float f = position.getX() - position2.getX();
        float f2 = position.getZ() - position2.getZ();
        if (Math.abs(f) < 0.01f && Math.abs(f2) < 0.01f) {
            return new Position(0.0f, 180.0f, 0.0f);
        }
        float f3 = (float)Math.toDegrees(Math.atan2(f, f2));
        return new Position(0.0f, f3, 0.0f);
    }

    private static Position copyPosition(Position position) {
        return position == null ? null : new Position(position.getX(), position.getY(), position.getZ());
    }

    public static void clearPlayerState(int uid) {
        CONTINUE_UNTIL_MS.remove(uid);
    }
}

