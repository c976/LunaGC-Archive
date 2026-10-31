package emu.grasscutter.data.excels;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.utils.JsonUtils;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RewardPreviewBossIdentityTest {
    @Test
    void keepsAdvertisedBossMaterialIdentityEvenWhenCountIsOmitted() {
        var preview = JsonUtils.decode(
                "{\"id\":9876,\"previewItems\":["
                        + "{\"id\":102,\"count\":\"300\"},"
                        + "{\"id\":113013},"
                        + "{\"id\":113014,\"count\":null}]}",
                RewardPreviewData.class);
        assertEquals(Set.of(102, 113013, 113014), preview.getAdvertisedItemIds());
    }
}
