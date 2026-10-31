package emu.grasscutter.game.inventory;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.ItemData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GameItemGrantCopyTest {
    private static final Gson GSON = new Gson();

    @Test
    void grantCopiesHaveIndependentIdentityAndAffixLists() {
        var definition = GSON.fromJson(
                "{\"id\":11501,\"itemType\":\"ITEM_WEAPON\",\"skillAffix\":[1001],\"stackLimit\":1}",
                ItemData.class);
        var source = new GameItem(definition);
        source.setLevel(42);
        source.setRefinement(3);
        source.setLocked(true);
        source.getAffixes().add(1002);
        source.setPurchasedAppendPropIdList(new ArrayList<>(List.of(3001)));
        source.setDefiniteAppendPropIdList(new ArrayList<>(List.of(3002)));
        source.setTpsAccessoryIds(new ArrayList<>(List.of(4001)));
        source.ensurePersistenceId();

        var first = source.copyForGrant();
        var second = source.copyForGrant();

        assertNotSame(source, first);
        assertNotSame(first, second);
        assertEquals(source.getItemId(), first.getItemId());
        assertEquals(42, first.getLevel());
        assertEquals(3, first.getRefinement());
        assertTrue(first.isLocked());
        assertNull(first.getObjectId());
        assertNull(second.getObjectId());
        assertEquals(0, first.getOwnerId());
        assertEquals(0, first.getGuid());

        first.getAffixes().add(1003);
        first.getPurchasedAppendPropIdList().add(3003);
        first.getDefiniteAppendPropIdList().add(3004);
        first.getTpsAccessoryIds().add(4002);

        assertEquals(List.of(1001, 1002), source.getAffixes());
        assertEquals(List.of(3001), source.getPurchasedAppendPropIdList());
        assertEquals(List.of(3002), source.getDefiniteAppendPropIdList());
        assertEquals(List.of(4001), source.getTpsAccessoryIds());
        assertEquals(source.getAffixes(), second.getAffixes());
    }
}
