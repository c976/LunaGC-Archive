package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import java.util.Objects;

/** Retain completion evidence only for MQ1000's escape-success rescue cutscene. */
final class LiyueRescuePlotRecovery {
    private static final int ESCAPE = 100010;
    private static final int PLOT = 100012;
    private static final int TALK = 100013;

    private LiyueRescuePlotRecovery() {}

    static void recordCompletionReport(QuestManager manager, QuestContent type,
                                       String paramStr, int... params) {
        if (type != QuestContent.QUEST_CONTENT_FINISH_PLOT || params.length == 0
                || params[0] != PLOT) return;
        var main = manager.getMainQuestById(1000);
        if (!eligible(main)) return;
        var plot = main.getChildQuestById(PLOT);
        if (!Objects.equals(paramStr, plot.getQuestData().getFinishCond().get(0).getParamStr())) return;
        if (!main.isLiyueRescuePlotReported()) {
            main.setLiyueRescuePlotReported(true);
            main.save();
            Grasscutter.getLogger().info("Retained Liyue rescue plot completion: uid={} state={}",
                    main.getOwner().getUid(), plot.getState());
        }
    }

    static void replayReportedCompletion(GameQuest quest) {
        if (quest.getSubQuestId() != PLOT || quest.getState() != QuestState.QUEST_STATE_UNFINISHED) return;
        var main = quest.getMainQuest();
        if (!eligible(main) || !main.isLiyueRescuePlotReported()
                || main.getChildQuestById(ESCAPE).getState() != QuestState.QUEST_STATE_FINISHED) return;
        quest.getOwner().getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_FINISH_PLOT, PLOT);
    }

    static boolean resumeOnLogin(GameMainQuest main) {
        if (!eligible(main)
                || main.getChildQuestById(ESCAPE).getState() != QuestState.QUEST_STATE_FINISHED) return false;
        var plot = main.getChildQuestById(PLOT);
        if (main.isLiyueRescuePlotReported()) {
            if (plot.getState() == QuestState.QUEST_STATE_UNSTARTED) plot.start();
            replayReportedCompletion(plot);
        } else {
            // Legacy saves have no completion evidence. Reissue this node for the client to replay
            // and report; never infer a completed cutscene merely from successful escape.
            Grasscutter.getLogger().info("Restarting unconfirmed Liyue rescue plot: uid={}",
                    main.getOwner().getUid());
            plot.clearProgress(true);
            plot.start();
        }
        return true;
    }

    static void clearReportForRollback(GameQuest quest) {
        if ((quest.getSubQuestId() == ESCAPE || quest.getSubQuestId() == PLOT)
                && quest.getMainQuest().getParentQuestId() == 1000) {
            quest.getMainQuest().setLiyueRescuePlotReported(false);
        }
    }

    private static boolean eligible(GameMainQuest main) {
        if (main == null || main.getParentQuestId() != 1000 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE) return false;
        var escape = main.getChildQuestById(ESCAPE);
        var plot = main.getChildQuestById(PLOT);
        var talk = main.getChildQuestById(TALK);
        if (escape == null || plot == null || talk == null
                || (escape.getState() != QuestState.QUEST_STATE_UNFINISHED
                    && escape.getState() != QuestState.QUEST_STATE_FINISHED)
                || (plot.getState() != QuestState.QUEST_STATE_UNSTARTED
                    && plot.getState() != QuestState.QUEST_STATE_UNFINISHED)
                || talk.getState() != QuestState.QUEST_STATE_UNSTARTED) return false;
        var data = plot.getQuestData();
        if (data == null || data.getMainId() != 1000 || data.getAcceptCond() == null
                || data.getAcceptCond().size() != 1 || data.getFinishCond() == null
                || data.getFinishCond().size() != 1) return false;
        var accept = data.getAcceptCond().get(0);
        var finish = data.getFinishCond().get(0);
        if (accept.getType() != QuestCond.QUEST_COND_STATE_EQUAL || accept.getParam() == null
                || accept.getParam().length < 2 || accept.getParam()[0] != ESCAPE
                || accept.getParam()[1] != QuestState.QUEST_STATE_FINISHED.getValue()
                || finish.getType() != QuestContent.QUEST_CONTENT_FINISH_PLOT
                || finish.getParam() == null || finish.getParam().length == 0
                || finish.getParam()[0] != PLOT) return false;
        return main.getChildQuests().values().stream().noneMatch(q -> q.getQuestData() != null
                && q.getQuestData().getOrder() > data.getOrder()
                && q.getState() != QuestState.QUEST_STATE_UNSTARTED);
    }
}
