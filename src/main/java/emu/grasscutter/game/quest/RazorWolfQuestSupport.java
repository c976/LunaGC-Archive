package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import java.util.ArrayList;

/** Resume MQ454's native story wolf and trial Razor without replacing the weekly trial. */
public final class RazorWolfQuestSupport {
    private RazorWolfQuestSupport() {}

    public static boolean isStoryWolf(int sceneId, int groupId, int configId, int monsterId) {
        return sceneId == 3 && groupId == 133002259 && configId == 913 && monsterId == 29020101;
    }

    public static boolean isActiveBattle(GameMainQuest main) {
        if (main == null || main.getParentQuestId() != 454 || main.isFinished()
                || main.getState() == ParentQuestState.PARENT_QUEST_STATE_FINISHED) return false;
        var fight = main.getChildQuestById(45405);
        var intro = main.getChildQuestById(45416);
        var closing = main.getChildQuestById(45417);
        if (closing != null && closing.getState() == QuestState.QUEST_STATE_FINISHED) return false;
        return fight != null && fight.getState() == QuestState.QUEST_STATE_UNFINISHED
                && intro != null && intro.getState() == QuestState.QUEST_STATE_FINISHED;
    }

    static boolean inStoryArena(int sceneId, Position position) {
        return sceneId == 3 && position != null
                && position.computeDistance(new Position(1994f, 250.08f, -260.5f)) <= 48;
    }

    public static void ensure(GameQuest quest) {
        if (!QuestManager.isQuestingActive() || quest == null || quest.getMainQuestId() != 454
                || quest.getSubQuestId() != 45405 || !isActiveBattle(quest.getMainQuest())) return;
        var player = quest.getOwner();
        var scene = player.getScene();
        if (scene == null || player.getSceneLoadState() != Player.SceneLoadState.LOADED
                || !player.hasSentLoginPackets() || !inStoryArena(scene.getId(), player.getPosition())) return;
        scene.runWhenFinished(() -> scene.getScheduler().scheduleDelayedTask(() -> {
            if (player.getScene() != scene || !isActiveBattle(quest.getMainQuest())
                    || !inStoryArena(scene.getId(), player.getPosition())) return;
            if (player.getTeamManager().getTrialAvatarGuid(9) == 0)
                player.getTeamManager().addTrialAvatar(9, 454);
            // A reload loses the transient boss/timer, while the completed intro remains saved.
            if (scene.getEntityByConfigId(913, 133002259) == null)
                scene.getScriptManager().refreshGroupSuite(133002259, 1, quest);
        }, 1));
    }

    private static boolean hasEndedBattle(GameMainQuest main) {
        if (main == null || main.getParentQuestId() != 454) return false;
        var fight = main.getChildQuestById(45405);
        var closing = main.getChildQuestById(45417);
        return main.isFinished() || main.getState() == ParentQuestState.PARENT_QUEST_STATE_FINISHED
                || (fight != null && fight.getState() == QuestState.QUEST_STATE_FINISHED)
                || (closing != null && closing.getState() == QuestState.QUEST_STATE_FINISHED);
    }

    public static void onQuestFinished(GameQuest quest) {
        if (quest == null || quest.getMainQuestId() != 454
                || (quest.getSubQuestId() != 45405 && quest.getSubQuestId() != 45417
                    && quest.getSubQuestId() != 45406)
                || !hasEndedBattle(quest.getMainQuest())) return;
        var world = quest.getOwner().getWorld();
        if (world != null) removeStoryWolf(world.getScenes().get(3));
    }

    private static void removeStoryWolf(Scene scene) {
        if (scene == null || scene.getId() != 3) return;
        var wolf = scene.getEntityByConfigId(913, 133002259);
        boolean storyWolf = wolf instanceof EntityMonster monster
                && isStoryWolf(scene.getId(), monster.getGroupId(), monster.getConfigId(),
                        monster.getEntityTypeId());
        var scripts = scene.getScriptManager();
        var instance = scripts.getGroupInstanceById(133002259);
        boolean storySuite = instance != null && instance.getActiveSuiteId() == 1;
        if (!storyWolf && !storySuite) return;

        // Dynamic CreateMonster/CreateGadget entities are absent from suite 1's static lists.
        // Remove them without kill rewards, weekly blossoms or a synthetic quest completion.
        scripts.cancelGroupTimerEvent(133002259, "Prepare");
        var remove = new ArrayList<GameEntity>();
        if (storyWolf) remove.add(wolf);
        if (scene.getEntityByConfigId(954, 133002259) == null) {
            for (int configId : new int[] {2222, 2223, 2238}) {
                var gadget = scene.getEntityByConfigId(configId, 133002259);
                if (gadget instanceof EntityGadget) remove.add(gadget);
            }
        }
        scene.removeEntities(remove, VisionType.VisionType_VISION_REMOVE);
        if (!remove.isEmpty()) Grasscutter.getLogger().info(
                "Razor story battle cleanup removed {} entities from group 133002259", remove.size());
    }

    public static void onSceneLoaded(Player player) {
        if (player == null || player.getSceneLoadState() != Player.SceneLoadState.LOADED
                || !player.hasSentLoginPackets()) return;
        var scene = player.getScene();
        if (scene == null || scene.getId() != 3) return;
        if (hasEndedBattle(player.getQuestManager().getMainQuestById(454))) {
            scene.runWhenFinished(() -> scene.getScheduler().scheduleDelayedTask(() -> {
                if (player.getScene() == scene
                        && hasEndedBattle(player.getQuestManager().getMainQuestById(454)))
                    removeStoryWolf(scene);
            }, 1));
            return;
        }
        ensure(player.getQuestManager().getQuestById(45405));
    }

    public static boolean scheduleStoryWolf(Scene scene, SceneGroup group, int configId, int delaySeconds) {
        if (scene == null || scene.getId() != 3 || group == null || group.id != 133002259 || configId != 913)
            return false;
        var player = scene.getWorld().getHost();
        Runnable spawn = () -> {
            var instance = scene.getScriptManager().getGroupInstanceById(group.id);
            if (instance == null || instance.getActiveSuiteId() != 1) return;
            if (player.getScene() != scene || !isActiveBattle(player.getQuestManager().getMainQuestById(454))
                    || !inStoryArena(scene.getId(), player.getPosition())) return;
            scene.getScriptManager().spawnMonstersByConfigId(group, configId, 0);
            // Quest completion can race with a delayed spawn already executing on the scene.
            if (hasEndedBattle(player.getQuestManager().getMainQuestById(454))) removeStoryWolf(scene);
        };
        if (delaySeconds > 0)
            scene.getScheduler().scheduleDelayedTask(spawn, LiyueCloudDungeonSupport.ticksForSeconds(delaySeconds));
        else spawn.run();
        return true;
    }
}
