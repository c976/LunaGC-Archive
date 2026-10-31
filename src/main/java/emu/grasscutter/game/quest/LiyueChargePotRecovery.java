package emu.grasscutter.game.quest;

import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;

/** Preserve MQ1011's parallel heating/protection stages; native group-load Lua restores the pot. */
public final class LiyueChargePotRecovery {
    private LiyueChargePotRecovery() {}

    static boolean resumeOnLogin(GameMainQuest main) {
        if (main.getParentQuestId() != 1011 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE) return false;
        var ready = main.getChildQuestById(101104);
        var end = main.getChildQuestById(101127);
        if (ready == null || ready.getState() != QuestState.QUEST_STATE_FINISHED
                || end == null || end.getState() != QuestState.QUEST_STATE_UNFINISHED) return false;
        // Retry native acceptance of missing parallel stage quests without repeating finish actions.
        ready.triggerStateEvents();
        return true;
    }

    public static boolean isProgressKey(String key) {
        return switch (key) {
            case "13300137701", "13300137702", "13300137703", "133001377204",
                    "133001377901", "133001377201", "133001377902", "133001377202",
                    "133001377203", "1330013779011", "1330013772011", "1330013779021",
                    "1330013772021", "1330013772031", "1330013772041",
                    "13300137801", "13300137802" -> true;
            default -> false;
        };
    }
}
