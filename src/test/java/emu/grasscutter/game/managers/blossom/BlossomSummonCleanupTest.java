package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BlossomSummonCleanupTest {
    private record Link(int id, int parentId) {}

    @Test
    void removesTransitiveSummonsEvenWhenChildrenPrecedeTheirOwners() {
        var grandchild = new Link(301, 201);
        var unrelated = new Link(500, 999);
        var child = new Link(201, 101);
        var encounterRoot = new Link(101, 0);
        var otherPlayersGadget = new Link(501, 500);
        var anotherEncounterRoot = new Link(102, 0);

        var owned = BlossomSummonCleanup.descendants(
                List.of(grandchild, unrelated, child, encounterRoot, otherPlayersGadget,
                        anotherEncounterRoot),
                Set.of(101, 102),
                Link::id,
                Link::parentId);

        assertEquals(Set.of(child, grandchild), Set.copyOf(owned));
        assertFalse(owned.contains(encounterRoot));
        assertFalse(owned.contains(unrelated));
        assertFalse(owned.contains(otherPlayersGadget));
    }

    @Test
    void recognizesSummonsFromAnEarlierWaveEvenWhenTheCasterHasDespawned() {
        var earlierWaveSummon = new Link(230, 110);
        var laterWaveSummon = new Link(240, 120);
        var unrelatedSkill = new Link(250, 1000);
        var owned = BlossomSummonCleanup.descendants(
                List.of(earlierWaveSummon, laterWaveSummon, unrelatedSkill),
                Set.of(110, 120),
                Link::id,
                Link::parentId);
        assertEquals(Set.of(earlierWaveSummon, laterWaveSummon), Set.copyOf(owned));
    }

    @Test
    void aSummonChainCanStillBeResolvedAfterItsIntermediateOwnerDisappears() {
        var child = new Link(201, 101);
        var grandchild = new Link(301, 201);
        var first = BlossomSummonCleanup.descendants(
                List.of(child), Set.of(101), Link::id, Link::parentId);
        assertEquals(List.of(child), first);
        // The server has removed entity 201; only its recorded ID keeps entity 301 linked.
        var second = BlossomSummonCleanup.descendants(
                List.of(grandchild), Set.of(101, 201), Link::id, Link::parentId);
        assertEquals(List.of(grandchild), second);
    }

    @Test
    void entityCreationImmediatelyRecordsEncounterChildrenAndNestedSummons() {
        assertTrue(BlossomSummonCleanup.isEncounterChild(101, Set.of(101), Set.of()));
        assertTrue(BlossomSummonCleanup.isEncounterChild(201, Set.of(101), Set.of(201)));
        assertFalse(BlossomSummonCleanup.isEncounterChild(999, Set.of(101), Set.of(201)));
        assertFalse(BlossomSummonCleanup.isEncounterChild(0, Set.of(101), Set.of(201)));
    }

    @Test
    void skipsUnlinkedEntitiesAndCyclesOutsideTheEncounter() {
        var unrelatedA = new Link(500, 501);
        var unrelatedB = new Link(501, 500);
        var unowned = new Link(502, 0);
        assertTrue(BlossomSummonCleanup.descendants(
                List.of(unrelatedA, unrelatedB, unowned),
                Set.of(101),
                Link::id,
                Link::parentId).isEmpty());
    }
}
