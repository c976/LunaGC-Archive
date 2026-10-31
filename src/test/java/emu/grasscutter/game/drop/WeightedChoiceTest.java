package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedChoiceTest {
    @Test
    void parameterCommasDoNotTurnOneCandidateIntoAWeightedDistribution() {
        var angled = WeightedChoice.compile(
                "chest_artifact<3,1,berserker,instructor>",
                "template", Object::toString);
        assertEquals(List.of("chest_artifact<3,1,berserker,instructor>"), angled.candidates());
        var modern = WeightedChoice.compile(
                "chest_artifact<3,1,berserker,instructor>",
                "modern", value -> value.toString());
        assertEquals(List.of("chest_artifact<3,1,berserker,instructor>"),
                modern.candidates());
    }

    @Test
    void weightedExpressionsCanSelectParameterizedPoolCalls() {
        String first = "chest_artifact<3,1,berserker,instructor>";
        String second = "chest_artifact<4,2,berserker,instructor>";
        var weighted = WeightedChoice.compile(
                first + ":3, " + second + ":1", "parameterized", Object::toString);
        assertEquals(List.of(first, second), weighted.candidates());
        assertEquals("3", weighted.weights().get(0).toPlainString());
        assertEquals("1", weighted.weights().get(1).toPlainString());
        for (int seed = 0; seed < 20; seed++) {
            assertTrue(List.of(first, second).contains(weighted.sample(new Random(seed))));
        }
    }

    @Test
    void malformedTopLevelWeightExpressionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedChoice.compile("choice<A,B>:2, other",
                        "bad", Object::toString));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedChoice.compile("choice(A,B", "bad", Object::toString));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedChoice.compile("A:1, B:-1", "bad", Object::toString));
    }
}
