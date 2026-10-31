package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.world.WorldLevelData;
import emu.grasscutter.game.entity.gadget.chest.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaError;

public class WorldDataSystem extends BaseGameSystem {
    private final Map<String, ChestInteractHandler> chestInteractHandlerMap; // chestType-Handler
    private final Map<String, SceneGroup> sceneInvestigationGroupMap; // <sceneId_groupId, Group>

    public WorldDataSystem(GameServer server) {
        super(server);
        this.chestInteractHandlerMap = new HashMap<>();
        this.sceneInvestigationGroupMap = new ConcurrentHashMap<>();

        loadChestConfig();
    }

    public synchronized void loadChestConfig() {
        // set the special chest first
        chestInteractHandlerMap.put("SceneObj_Chest_Flora", new BossChestInteractHandler());

        try {
            DataLoader.loadList("ChestReward.json", ChestReward.class)
                    .forEach(
                            reward ->
                                    reward
                                            .getObjNames()
                                            .forEach(
                                                    name ->
                                                            chestInteractHandlerMap.computeIfAbsent(
                                                                    name, x -> new NormalChestInteractHandler(reward))));
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load chest reward config.", e);
        }
    }

    public Map<String, ChestInteractHandler> getChestInteractHandlerMap() {
        return chestInteractHandlerMap;
    }

    private SceneGroup getInvestigationGroup(int sceneId, int groupId) {
        var key = sceneId + "_" + groupId;
        if (!sceneInvestigationGroupMap.containsKey(key)) {
            try {
                var group = SceneGroup.of(groupId).load(sceneId);
                sceneInvestigationGroupMap.putIfAbsent(key, group);
                return group;
            } catch (LuaError luaError) {
                Grasscutter.getLogger()
                        .error("failed to get investigationGroup {} in scene{}:", groupId, sceneId, luaError);
            }
        }
        return sceneInvestigationGroupMap.get(key);
    }

    public int getMonsterLevel(SceneMonster monster, World world) {
        // Calculate level
        int level = monster.level;
        WorldLevelData worldLevelData = GameData.getWorldLevelDataMap().get(world.getWorldLevel());

        if (worldLevelData != null) {
            level = Math.max(level, worldLevelData.getMonsterLevel());
        }
        return level;
    }

    private InvestigationMonsterOuterClass.InvestigationMonster getInvestigationMonster(
            Player player, InvestigationMonsterData imd) {
        try {
            return InvestigationTrackHelper.buildInvestigationMonster(player, imd);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("InvestigationTrackHelper failed id={}: {}", imd.getId(), t.toString());
            return null;
        }
    }

    public List<InvestigationMonsterOuterClass.InvestigationMonster> getInvestigationMonstersByCityId(
            Player player, int cityId) {
        var cityData = GameData.getCityDataMap().get(cityId);
        if (cityData == null) {
            Grasscutter.getLogger().warn("City not exist {}", cityId);
            return List.of();
        }

        // Sequential: InvestigationTrackHelper loads Lua groups via shared ScriptLoader bindings.
        return GameData.getInvestigationMonsterDataMap().values().stream()
                .filter(imd -> imd.getCityId() == cityId)
                .map(imd -> this.getInvestigationMonster(player, imd))
                .filter(Objects::nonNull)
                .toList();
    }
}
