package emu.grasscutter.data.excels.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins where a rotation's floors 9-12 are read from.
 *
 * <p>All 124 rows of the 7.1 TowerScheduleExcelConfigData.json name the list {@code LHOGNLPBILP}.
 * Read under {@code schedules} only, every rotation had no floors 9-12 and the Abyss stopped at 8.
 */
public final class TowerScheduleDataTest {
    private static List<Integer> floorsOf(String key) {
        var data =
                JsonUtils.decode(
                        "{\"scheduleId\":124,\"entranceFloorId\":[1001],\""
                                + key
                                + "\":[{\"floorList\":[1134,1135,1140,1141]},{},{},{}]}",
                        TowerScheduleData.class);
        data.onLoad();
        assertEquals(1, data.getSchedules().size(), "empty schedule entries are dropped");
        return data.getSchedules().get(0).getFloorList();
    }

    @Test
    @DisplayName("floors 9-12 load from the 7.1 obfuscated key")
    public void obfuscatedKey() {
        assertEquals(List.of(1134, 1135, 1140, 1141), floorsOf("LHOGNLPBILP"));
    }

    @Test
    @DisplayName("floors 9-12 still load from the readable key")
    public void readableKey() {
        assertEquals(List.of(1134, 1135, 1140, 1141), floorsOf("schedules"));
    }
}
