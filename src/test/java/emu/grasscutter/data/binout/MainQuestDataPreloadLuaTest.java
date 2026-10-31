package emu.grasscutter.data.binout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the type of {@link MainQuestData}'s preloadLuaList.
 *
 * <p>Nothing reads the field, so a wrong type is invisible until a resource pack contains an id
 * past {@link Long#MAX_VALUE}. Gson then throws while parsing that one quest file, and because
 * ResourceLoader reads them in a loop, the failure takes the whole quest load with it rather than
 * skipping a field nobody wanted.
 */
public final class MainQuestDataPreloadLuaTest {
    /**
     * The value from the startup failure this test exists for: Gson reported "Expected a long but
     * was 13231653233445166494 at path $.preloadLuaList[0]". It is larger than Long.MAX_VALUE
     * (9223372036854775807).
     */
    private static final String OVERSIZED_ID = "13231653233445166494";

    private static String questJson(String... luaIds) {
        return "{\"id\":303,\"series\":99,\"titleTextMapHash\":123456789,"
                + "\"preloadLuaList\":["
                + String.join(",", luaIds)
                + "]}";
    }

    @Test
    @DisplayName("an id past Long.MAX_VALUE parses instead of failing the quest load")
    public void oversizedIdParses() {
        var data =
                assertDoesNotThrow(
                        () -> JsonUtils.decode(questJson(OVERSIZED_ID), MainQuestData.class));

        assertNotNull(data);
        assertEquals(303, data.getId());
    }

    @Test
    @DisplayName("ordinary ids still parse, and the rest of the quest survives")
    public void ordinaryIdsParse() {
        var data = JsonUtils.decode(questJson("1", "2", OVERSIZED_ID), MainQuestData.class);

        assertNotNull(data);
        assertEquals(303, data.getId());
        assertEquals(99, data.getSeries());
        assertEquals(123456789L, data.getTitleTextMapHash());
    }
}
