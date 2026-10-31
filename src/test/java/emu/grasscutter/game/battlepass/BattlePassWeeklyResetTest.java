package emu.grasscutter.game.battlepass;

import static emu.grasscutter.game.props.BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE;
import static emu.grasscutter.game.props.BattlePassMissionStatus.MISSION_STATUS_POINT_TAKEN;
import static emu.grasscutter.game.props.BattlePassMissionStatus.MISSION_STATUS_UNFINISHED;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.excels.BattlePassMissionData;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class BattlePassWeeklyResetTest {
    @Test
    void newWeekClearsMissionAndPointCapWithoutChangingAccumulatedLevel() throws Exception {
        var pass = new BattlePassManager();
        var mission = new BattlePassMission(1);
        var data = new BattlePassMissionData();
        set(data, "refreshType", BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE);
        set(mission, "data", data);
        mission.setStatus(MISSION_STATUS_POINT_TAKEN);
        mission.setProgress(3);
        pass.getMissions().put(mission.getId(), mission);

        pass.addPointsDirectly(GameConstants.BATTLE_PASS_POINT_PER_WEEK, true);
        assertEquals(GameConstants.BATTLE_PASS_POINT_PER_WEEK, pass.getCyclePoints());
        int level = pass.getLevel();
        int point = pass.getPoint();

        assertEquals(1, pass.resetWeeklyProgress().size());
        assertEquals(0, pass.getCyclePoints());
        assertEquals(MISSION_STATUS_UNFINISHED, mission.getStatus());
        assertEquals(0, mission.getProgress());
        assertEquals(level, pass.getLevel());
        assertEquals(point, pass.getPoint());

        pass.addPointsDirectly(100, true);
        assertEquals(100, pass.getCyclePoints());
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
