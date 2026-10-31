package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceRegistryTest {
    @Test
    void onlyExplicitCompleteNamesResolve() {
        var registry = new ResourceRegistry(Map.of(
                "genshin.avatar_ascension.hoarfrost_core", 113010,
                "genshin.currency.mora", 202,
                "genshin.artifact.instructor.flower.3.1", 60311));
        assertEquals(113010, registry.resolve("genshin.avatar_ascension.hoarfrost_core"));
        assertEquals(202, registry.resolve("genshin.currency.mora"));
        assertEquals(60311, registry.resolve("genshin.artifact.instructor.flower.3.1"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.avatar_ascension"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.currency.Mora"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("113010"));
    }

    @Test
    void constructionRejectsIncompleteKeysAndInvalidIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRegistry(Map.of("genshin.artifact", 10005)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRegistry(Map.of("genshin.currency.mora", -1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRegistry(Map.of("genshin.currency.mora", 0)));
    }
    @Test
    void authoredDictionaryAndArtifactCandidatesShareOneKeyspace() {
        var registry = ResourceRegistry.compile("""
                # Project-owned names
                genshin.currency.mora\t202
                genshin.experience.adventure\t102
                """, Set.of(202, 60311), Map.of(
                "genshin.artifact.instructor.flower.3.1", 60311));
        assertEquals(Map.of(
                "genshin.currency.mora", 202,
                "genshin.experience.adventure", 102,
                "genshin.artifact.instructor.flower.3.1", 60311), registry.entries());
        assertThrows(UnsupportedOperationException.class,
                () -> registry.entries().put("genshin.currency.primogem", 201));
    }

    @Test
    void authoredDictionaryRejectsDuplicateAndConflictingKeys() {
        for (String duplicate : new String[]{
                "genshin.currency.mora\t202\\ngenshin.currency.mora\t202",
                "genshin.currency.mora\t202\\ngenshin.currency.mora\t201"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ResourceRegistry.compile(duplicate, Set.of(201, 202), Map.of()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ResourceRegistry.compile("genshin.currency.mora\t202",
                        Set.of(202), Map.of("genshin.currency.mora", 202)));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceRegistry.compile("genshin.currency.mora\t202",
                        Set.of(202, 60311),
                        Map.of("genshin.currency.mora", 60311)));
    }

    @Test
    void authoredDictionaryRejectsMalformedAndUnknownNativeItems() {
        for (String invalid : new String[]{
                "genshin.currency.mora", "genshin.currency.mora\t0",
                "genshin.currency.mora\t-1", "genshin.currency.mora\tfoo",
                "genshin.currency.mora\t2147483648", "genshin..mora\t202",
                "genshin.currency\t202", "genshin.currency.mora\t999"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ResourceRegistry.compile(invalid, Set.of(202), Map.of()),
                    invalid);
        }
        assertThrows(IllegalArgumentException.class,
                () -> ResourceRegistry.compile("", Set.of(202),
                        Map.of("genshin.artifact.instructor.flower.3.1", 999)));
    }

}
