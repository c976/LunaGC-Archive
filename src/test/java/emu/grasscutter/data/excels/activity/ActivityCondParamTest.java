package emu.grasscutter.data.excels.activity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.activity.ActivityCondExcelConfigData.ActivityConfigCondition;
import org.junit.jupiter.api.Test;

class ActivityCondParamTest {
    private static ActivityConfigCondition parse(String json) {
        return new Gson().fromJson(json, ActivityConfigCondition.class);
    }

    @Test
    void conditionWithoutParamHasNoParams() {
        // 5072011 in NewActivityCondExcelConfigData: no param key at all.
        var cond = parse("{\"type\": \"NEW_ACTIVITY_COND_FINISH_MUSIC_GAME_ALL_LEVEL\"}");
        assertArrayEquals(new int[0], cond.paramArray());
    }

    @Test
    void numericParamsAreKept() {
        var cond = parse("{\"type\": \"NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL\", \"param\": [\"7\", \"\"]}");
        assertArrayEquals(new int[] {7}, cond.paramArray());
    }
}
