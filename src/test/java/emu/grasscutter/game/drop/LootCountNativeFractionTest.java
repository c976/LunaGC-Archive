package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LootCountNativeFractionTest {
    @Test
    void explicitOutcomesHaveExactBoundaries() {
        var random = new Random(1);
        assertEquals(2, LootCount.parse("2:1,3:0").sample(random));
        assertEquals(3, LootCount.parse("2:0,3:1").sample(random));
        assertEquals(4, LootCount.parse("2:0,4:1").sample(random));
        assertEquals(0, LootCount.parse("0").sample(random));
        assertEquals(0, LootCount.parse("0:1,1:0").sample(random));
        assertEquals(1, LootCount.parse("0:0,1:1").sample(random));
    }

    @Test
    void decimalWeightsRemainSupportedButCountsAreIntegers() {
        var roll = LootCount.parse("1:1, 2:7");
        var random = new Random(12);
        for (int i = 0; i < 500; i++) {
            int value = roll.sample(random);
            assertTrue(value == 1 || value == 2);
        }
        assertEquals(2, LootCount.parse("2:7").sample(random));
        assertEquals(2, LootCount.parse("2:1, 2:2").sample(random));
        for (String invalid : new String[] {
                "2, 3:41.4", "2:58.6, 3", "2:0, 3:0"
        }) {
            assertThrows(IllegalArgumentException.class, () -> LootCount.parse(invalid), invalid);
        }
        for (String value : new String[]{"-1.25", "1.2.3",
                "2+1:100.01", "2+1:-1", "2+1:abc", "2+1:55.56:5",
                "2+0:50", "100000+1:1", "1+1:10.5.5"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LootCount.parse(value), value);
        }
        for (String invalid : new String[] {"3.0", "1.122", "2147483647.0001"}) {
            assertThrows(IllegalArgumentException.class, () -> LootCount.parse(invalid), invalid);
        }
        int upper = 0;
        var fractional = LootCount.parse("1:878, 2:122");
        for (int i = 0; i < 10000; i++) {
            int result = fractional.sample(random);
            assertTrue(result == 1 || result == 2);
            if (result == 2) upper++;
        }
        assertEquals(0.122, upper / 10000.0, 0.02);
        assertEquals(100001, LootCount.parse("100001").sample(random));
        assertEquals(Integer.MAX_VALUE, LootCount.parse("2147483647").sample(random));
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("2147483648"));
    }
}
