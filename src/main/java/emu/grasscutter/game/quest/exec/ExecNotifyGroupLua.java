package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;

/** Deliver quest Lua notifications to the action's target scene, including after dungeon return. */
@QuestValueExec(QuestExec.QUEST_EXEC_NOTIFY_GROUP_LUA)
public class ExecNotifyGroupLua extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (params == null || params.length < 2) return false;
        final int sceneId, groupId;
        try {
            sceneId = Integer.parseInt(params[0]);
            groupId = Integer.parseInt(params[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (sceneId <= 0 || groupId <= 0) return false;

        var player = quest.getOwner();
        var world = player.getWorld();
        if (world == null) return false;
        var scene = world.getSceneById(sceneId);
        if (scene == null) return false;
        var scriptManager = scene.getScriptManager();

        // A queued callback must not turn an originally finished action into
        // a later quest start/failure event.
        QuestState dispatchedState = quest.getState();
        Runnable dispatch = () -> {
            boolean sceneGone = world.getScenes().get(sceneId) != scene;
            if (player.getWorld() != world
                    || !canDeliver(dispatchedState, quest.getState(), sceneId,
                            player.getSceneId(), sceneGone)) {
                return;
            }
            // The destination group can be outside the visible grid. Resolve
            // and register it before dispatching the native Lua quest event.
            if (scriptManager.getGroupById(groupId) == null) {
                Grasscutter.getLogger().warn(
                        "Quest {} could not notify Lua group {} in scene {}",
                        quest.getSubQuestId(), groupId, sceneId);
                return;
            }
            scriptManager.callEvent(
                    new ScriptArgs(groupId, eventTypeFor(dispatchedState), quest.getSubQuestId(),
                            dispatchedState == QuestState.QUEST_STATE_FINISHED ? 1 : 0)
                            .setEventSource(quest.getSubQuestId()));
        };

        // Scene 3 may exist and finish its initial load before the player returns
        // from 39403's dungeon scene 1008. The load and player-presence gates
        // must both be true or the one-shot event is silently lost.
        scriptManager.whenInitialized(ready -> {
            if (ready) {
                scene.runWhenPlayerEnters(
                        player, () -> scene.runWhenFinished(dispatch));
            } else {
                Grasscutter.getLogger().warn(
                        "Quest {} Lua group {} cannot initialize scripts in scene {}",
                        quest.getSubQuestId(), groupId, sceneId);
            }
        });
        return true;
    }

    static boolean canDeliver(QuestState dispatched, QuestState actual,
            int targetSceneId, int playerSceneId, boolean sceneGone) {
        return dispatched == actual && targetSceneId == playerSceneId && !sceneGone;
    }

    static int eventTypeFor(QuestState state) {
        return state == QuestState.QUEST_STATE_FINISHED
                ? EventType.EVENT_QUEST_FINISH
                : EventType.EVENT_QUEST_START;
    }
}
