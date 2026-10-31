package emu.grasscutter.game.quest;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.world.WeatherData;
import emu.grasscutter.game.quest.enums.QuestExec;
import java.util.Collection;
import java.util.function.IntFunction;

/**
 * Reconstructs unambiguous quest-owned weather from active quest data.
 *
 * <p>An activation is recoverable only if the same subquest deactivates the
 * area on finish. This avoids replaying one-shot weather changes that may
 * have been superseded by later quest actions.
 */
final class QuestWeatherRestore {
    private QuestWeatherRestore() {}

    static int selectArea(
            Collection<QuestData> activeSubquests, int currentSceneId,
            IntFunction<WeatherData> weatherByArea) {
        if (activeSubquests == null || currentSceneId <= 0 || weatherByArea == null) return 0;

        int selectedArea = 0;
        for (var quest : activeSubquests) {
            if (quest == null || quest.getBeginExec() == null || quest.getFinishExec() == null) {
                continue;
            }
            for (var action : quest.getBeginExec()) {
                int areaId = areaWithFlag(action, 1);
                if (areaId <= 0) continue;
                var weather = weatherByArea.apply(areaId);
                if (weather == null || weather.getGadgetID() <= 0
                        || weather.getSceneID() != currentSceneId) continue;

                boolean hasFinishReset = quest.getFinishExec().stream()
                        .anyMatch(exit -> areaWithFlag(exit, 0) == areaId);
                if (!hasFinishReset) continue;
                if (selectedArea != 0 && selectedArea != areaId) {
                    return 0; // Active competing quest weather: do not invent precedence.
                }
                selectedArea = areaId;
            }
        }
        return selectedArea;
    }

    private static int areaWithFlag(QuestData.QuestExecParam action, int flag) {
        if (action == null || action.getType() != QuestExec.QUEST_EXEC_SET_WEATHER_GADGET) {
            return 0;
        }
        String[] params = action.getParam();
        if (params == null || params.length < 2) return 0;
        try {
            int area = Integer.parseInt(params[0]);
            return area > 0 && Integer.parseInt(params[1]) == flag ? area : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
