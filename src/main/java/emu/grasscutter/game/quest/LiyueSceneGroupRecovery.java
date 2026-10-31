package emu.grasscutter.game.quest;

import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.server.packet.send.PacketGroupSuiteNotify;
import java.util.concurrent.ThreadLocalRandom;

/** Scene-load overrides only for MQ1008's task amber/NPC and MQ1009's three offerings. */
public final class LiyueSceneGroupRecovery {
    private LiyueSceneGroupRecovery() {}

    private static GameMainQuest activeMain(Scene scene, int id) {
        if (!QuestManager.isQuestingActive() || scene.getId() != 3 || scene.getWorld() == null
                || scene.getWorld().getHost() == null) return null;
        var main = scene.getWorld().getHost().getQuestManager().getMainQuestById(id);
        return main != null && main.getParentQuestId() == id && !main.isFinished()
                && main.getState() == ParentQuestState.PARENT_QUEST_STATE_NONE ? main : null;
    }

    private static boolean state(GameMainQuest main, int id, QuestState state) {
        var quest = main.getChildQuestById(id);
        return quest != null && quest.getState() == state;
    }

    private static boolean amberSuite(int suite) { return suite >= 2 && suite <= 6; }

    /** Zero means retain the normal loader's behavior for unrelated groups/phases. */
    public static int selectSuite(Scene scene, SceneGroupInstance instance) {
        int group = instance.getGroupId();
        if (group >= 133101914 && group <= 133101916) {
            var main = activeMain(scene, 1009);
            if (main == null || !state(main, 100906, QuestState.QUEST_STATE_FINISHED)) return 0;
            int quest = 100907 + group - 133101914;
            boolean pending = state(main, quest, QuestState.QUEST_STATE_UNFINISHED)
                    && main.getOwner().getPlayerProgress().getCurrentProgress(String.valueOf(group)) < 1;
            return pending ? 2 : 1;
        }
        if (group != 133103908 && group != 133103907) return 0;
        var main = activeMain(scene, 1008);
        if (main == null || !state(main, 100808, QuestState.QUEST_STATE_FINISHED)) return 0;
        boolean searching = state(main, 100803, QuestState.QUEST_STATE_UNFINISHED);
        boolean talkPending = state(main, 100803, QuestState.QUEST_STATE_FINISHED)
                && (state(main, 100805, QuestState.QUEST_STATE_UNSTARTED)
                    || state(main, 100805, QuestState.QUEST_STATE_UNFINISHED));
        if (group == 133103907 && !talkPending) return 1;
        if (group == 133103908 && !searching) return 0;
        synchronized (main) {
            int selected = main.getLiyueAmberSuite();
            if (!amberSuite(selected)) {
                selected = instance.getActiveSuiteId();
                if (!amberSuite(selected)) {
                    selected = ThreadLocalRandom.current().nextInt(2, 7);
                    // A lost choice starts a fresh search. Old monster-amber breaks are not rescue evidence.
                    if (group == 133103908) for (int config = 908006; config <= 908010; config++) {
                        instance.getCachedGadgetStates().remove(config);
                        instance.getDeadEntities().remove(config);
                    }
                }
                main.setLiyueAmberSuite(selected);
                main.save();
            }
            return selected;
        }
    }

    /** Remember native Lua's random choice and notify the client of the actual Li Dang suite. */
    public static void onSuiteChanged(Scene scene, SceneGroupInstance instance) {
        int group = instance.getGroupId();
        if (group != 133103908 && group != 133103907) return;
        var main = activeMain(scene, 1008);
        if (main == null || !state(main, 100808, QuestState.QUEST_STATE_FINISHED)) return;
        int suite = instance.getActiveSuiteId();
        if (group == 133103908 && amberSuite(suite)
                && main.getLiyueAmberSuite() != suite) {
            main.setLiyueAmberSuite(suite);
            main.save();
        }
        if (group == 133103907 && suite >= 1 && suite <= 6)
            scene.broadcastPacket(new PacketGroupSuiteNotify(group, suite));
    }

    static void restoreForQuest(GameMainQuest main) {
        var player = main.getOwner();
        var scene = player.getScene();
        if (scene == null || scene.getId() != 3 || scene.getScriptManager() == null) return;
        var scripts = scene.getScriptManager();
        scripts.whenInitialized(ready -> {
            if (!ready) return;
            scene.runWhenFinished(() -> {
                if (player.getScene() != scene || activeMain(scene, main.getParentQuestId()) != main) return;
                int[] groups = main.getParentQuestId() == 1008 ? new int[] {133103908, 133103907}
                        : new int[] {133101914, 133101915, 133101916};
                for (int group : groups) {
                    if (scripts.getGroupById(group) == null) continue;
                    var instance = scripts.getGroupInstanceById(group);
                    if (instance == null) continue;
                    int suite = selectSuite(scene, instance);
                    if (suite > 0 && instance.getActiveSuiteId() != suite)
                        scripts.refreshGroup(instance, suite, false);
                }
            });
        });
    }
}
