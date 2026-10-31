package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.InTimeTrigger;
import emu.grasscutter.game.dungeons.challenge.trigger.TriggerGroupTriggerTrigger;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneTrigger;
import emu.grasscutter.server.packet.send.PacketChallengeDataNotify;
import emu.grasscutter.server.packet.send.PacketDungeonChallengeFinishNotify;
import java.util.List;
import java.util.function.Consumer;

/** Scene adapters for Amber's three timed flight exams; never complete them at login. */
public final class AmberFlightExamSupport {
    private record Exam(int mainId, int questId, int readyId, int groupId, int localId, int suites) {}
    private static final List<Exam> EXAMS = List.of(
            new Exam(480, 48002, 48001, 133001244, 244, 8),
            new Exam(427, 42702, 42701, 133004148, 148, 15),
            new Exam(427, 42705, 42704, 133004149, 149, 23));
    private AmberFlightExamSupport() {}

    private static Exam examFor(GameQuest quest) {
        if (quest == null) return null;
        return EXAMS.stream().filter(exam -> exam.mainId == quest.getMainQuestId()
                && exam.questId == quest.getSubQuestId()).findFirst().orElse(null);
    }

    public static boolean ownsGroup(GameQuest quest, int sceneId, int groupId) {
        var exam = examFor(quest);
        return sceneId == 3 && exam != null && exam.groupId == groupId;
    }

    static boolean isActive(GameQuest quest) {
        var exam = examFor(quest);
        if (!QuestManager.isQuestingActive() || exam == null
                || quest.getState() != QuestState.QUEST_STATE_UNFINISHED) return false;
        var main = quest.getMainQuest();
        if (main == null || main.isFinished()
                || main.getState() == ParentQuestState.PARENT_QUEST_STATE_FINISHED) return false;
        var ready = main.getChildQuestById(exam.readyId);
        return ready != null && ready.getState() == QuestState.QUEST_STATE_FINISHED;
    }

    static boolean preserveOnLogin(GameMainQuest main) {
        return main != null && EXAMS.stream().anyMatch(exam -> exam.mainId == main.getParentQuestId()
                && isActive(main.getChildQuestById(exam.questId)));
    }

    private static void whenSceneReady(Player player, Consumer<Scene> action) {
        if (player.getSceneLoadState() != Player.SceneLoadState.LOADED) return;
        var world = player.getWorld();
        var scene = world != null ? world.getSceneById(3) : null;
        if (scene == null) return;
        var scripts = scene.getScriptManager();
        scripts.whenInitialized(ready -> {
            if (!ready) return;
            scene.runWhenPlayerEnters(player, () -> scene.runWhenFinished(() -> scene.getScheduler().scheduleDelayedTask(() -> {
                if (player.getWorld() == world && player.getSceneId() == 3
                        && world.getScenes().get(3) == scene && !scripts.isDestroyed()) action.accept(scene);
            }, 1)));
        });
    }

    public static void ensure(GameQuest quest) {
        if (quest == null) return;
        ensureTransition(quest.getMainQuest());
        if (!isActive(quest)) return;
        var exam = examFor(quest);
        whenSceneReady(quest.getOwner(), scene -> {
            if (!isActive(quest)) return;
            var scripts = scene.getScriptManager();
            var fresh = scripts.getGroupInstanceById(exam.groupId) == null;
            var group = scripts.getGroupById(exam.groupId);
            if (group == null || group.getScript() == null) {
                Grasscutter.getLogger().warn("Amber flight exam group {} is unavailable", exam.groupId);
                return;
            }
            group.setDontUnload(true);
            var running = scene.getChallenge();
            boolean activeAttempt = running != null && running.inProgress()
                    && running.getGroup() != null && running.getGroup().id == exam.groupId
                    && isFlightChallenge(scene, running.getGroup(), running.getChallengeIndex(), running.getChallengeId());
            if (fresh || !activeAttempt) {
                // Reset every consumed coin and cached batch when there is no live timed attempt.
                for (int suite = 2; suite <= exam.suites; suite++) {
                    var data = group.getSuiteByIndex(suite);
                    if (data != null) scripts.removeGroupSuite(group, data);
                }
                scripts.refreshGroupSuite(exam.groupId, 1);
                if (group.variables != null) group.variables.forEach(variable ->
                        scripts.getVariables(exam.groupId).put(variable.name, variable.value));
            }
            rememberGroup(quest.getMainQuest(), exam.groupId);
        });
    }

    private static boolean needsTransition(GameMainQuest main) {
        if (!QuestManager.isQuestingActive() || main == null || main.getParentQuestId() != 427
                || main.isFinished() || main.getState() == ParentQuestState.PARENT_QUEST_STATE_FINISHED) return false;
        var second = main.getChildQuestById(42702);
        var third = main.getChildQuestById(42705);
        return second != null && second.getState() == QuestState.QUEST_STATE_FINISHED
                && (third == null || third.getState() != QuestState.QUEST_STATE_FINISHED);
    }

    private static void ensureTransition(GameMainQuest main) {
        if (!needsTransition(main)) return;
        whenSceneReady(main.getOwner(), scene -> {
            if (!needsTransition(main)) return;
            var scripts = scene.getScriptManager();
            var fresh = scripts.getGroupInstanceById(133004148) == null;
            var group = scripts.getGroupById(133004148);
            if (group == null || group.getScript() == null) return;
            group.setDontUnload(true);
            if (fresh || scripts.getVariables(group.id).getOrDefault("transition", 0) == 0) {
                for (int suite = 2; suite < 15; suite++)
                    scripts.removeGroupSuite(group, group.getSuiteByIndex(suite));
                scripts.addGroupSuite(scripts.getGroupInstanceById(group.id), group.getSuiteByIndex(15));
                scripts.getVariables(group.id).put("transition", 1);
            }
            rememberGroup(main, group.id);
        });
    }

    private static void rememberGroup(GameMainQuest main, int groupId) {
        if (main.getQuestGroupSuites().stream().noneMatch(gs -> gs.getScene() == 3 && gs.getGroup() == groupId)) {
            main.getQuestGroupSuites().add(QuestGroupSuite.of().scene(3).group(groupId).suite(1).build());
            main.save();
        }
    }

    public static void onSceneLoaded(Player player) {
        if (player.getSceneId() != 3) return;
        for (var exam : EXAMS) ensure(player.getQuestManager().getQuestById(exam.questId));
        ensureTransition(player.getQuestManager().getMainQuestById(427));
    }

    public static boolean isFlightGroup(Scene scene, int groupId) {
        return scene != null && scene.getId() == 3
                && EXAMS.stream().anyMatch(exam -> exam.groupId == groupId);
    }

    public static boolean isFlightChallenge(Scene scene, SceneGroup group, int localId, int dataId) {
        return scene != null && scene.getId() == 3 && group != null && dataId == 295
                && EXAMS.stream().anyMatch(exam -> exam.groupId == group.id && exam.localId == localId);
    }

    public static WorldChallenge createChallenge(Scene scene, SceneGroup group, int seconds, int tag, int count) {
        var exam = EXAMS.stream().filter(candidate -> candidate.groupId == group.id).findFirst().orElseThrow();
        var timer = new InTimeTrigger() {
            @Override public void onBegin(WorldChallenge challenge) {
                // Challenge 295's time and count must occupy different protocol parameters.
                scene.broadcastPacket(new PacketChallengeDataNotify(challenge, 1,
                        challenge.getStartedAt() + challenge.getTimeLimit()));
            }
        };
        var markers = new TriggerGroupTriggerTrigger(String.valueOf(tag)) {
            @Override public void onGroupTrigger(WorldChallenge challenge, SceneTrigger trigger) {
                if (trigger.currentGroup != null && trigger.currentGroup.id == exam.groupId)
                    super.onGroupTrigger(challenge, trigger);
            }
        };
        return new FlightChallenge(scene, group, exam.localId, seconds, count, timer, markers);
    }

    static void stopWithQuest(GameQuest quest) {
        var exam = examFor(quest);
        if (exam == null || (quest.getState() != QuestState.QUEST_STATE_FINISHED
                && quest.getState() != QuestState.QUEST_STATE_FAILED)) return;
        var world = quest.getOwner().getWorld();
        var scene = world != null ? world.getSceneById(3) : null;
        if (scene == null) return;
        var instance = scene.getScriptManager().getGroupInstanceById(exam.groupId);
        if (instance != null) instance.getLuaGroup().setDontUnload(
                exam.questId == 42702 && quest.getState() == QuestState.QUEST_STATE_FINISHED);
        if (exam.questId == 42705 && quest.getState() == QuestState.QUEST_STATE_FINISHED) {
            var previous = scene.getScriptManager().getGroupInstanceById(133004148);
            if (previous != null) previous.getLuaGroup().setDontUnload(false);
        }
        var challenge = scene.getChallenge();
        if (challenge instanceof FlightChallenge flight && challenge.getGroup().id == exam.groupId)
            flight.stopQuietly(quest.getState() == QuestState.QUEST_STATE_FINISHED);
    }

    private static final class FlightChallenge extends WorldChallenge {
        private FlightChallenge(Scene scene, SceneGroup group, int localId, int seconds, int count,
                InTimeTrigger timer, TriggerGroupTriggerTrigger markers) {
            super(scene, group, 295, localId, List.of(seconds, count), seconds, count, List.of(timer, markers));
        }
        @Override public synchronized void done() {
            if (getScene().getSceneTimeSeconds() - getStartedAt() > getTimeLimit()) super.fail();
            else super.done();
        }
        @Override public synchronized void fail() { super.fail(); }
        private synchronized void stopQuietly(boolean success) {
            if (!inProgress()) return;
            setProgress(false);
            setSuccess(success);
            setFinishedTime(Math.max(0, getScene().getSceneTimeSeconds() - getStartedAt()));
            getScene().broadcastPacket(new PacketDungeonChallengeFinishNotify(this));
        }
    }
}
