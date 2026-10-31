package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

public final class NameIndexPaginationTest {
    @Test
    void pagesDoNotOverlapOrDropEntries() {
        var values = List.of("a", "b", "c", "d", "e");
        assertEquals(List.of("a", "b"), NameIndex.selectPage(values, 1, 2));
        assertEquals(List.of("c", "d"), NameIndex.selectPage(values, 2, 2));
        assertEquals(List.of("e"), NameIndex.selectPage(values, 3, 2));
        assertTrue(NameIndex.selectPage(values, 4, 2).isEmpty());
        assertTrue(NameIndex.selectPage(values, Integer.MAX_VALUE, 2).isEmpty());
    }

    @Test
    void invalidPageInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> NameIndex.selectPage(List.of(1), 0, 2));
        assertThrows(IllegalArgumentException.class, () -> NameIndex.selectPage(List.of(1), 1, 0));
    }
}
