package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.common.*;
import java.util.Arrays;
import lombok.Getter;

@ResourceType(name = "RewardPreviewExcelConfigData.json", loadPriority = LoadPriority.HIGH)
public class RewardPreviewData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private ItemParamStringData[] previewItems;
    private ItemParamData[] previewItemsArray;

    public ItemParamData[] getPreviewItems() {
        return previewItemsArray;
    }

    /**
     * Previews advertise possible items even when the 7.1 resource omits a count.
     * This is identity evidence only, never a payable quantity or probability.
     */
    public java.util.Set<Integer> getAdvertisedItemIds() {
        if (previewItems == null) return java.util.Set.of();
        var ids = new java.util.HashSet<Integer>();
        for (ItemParamStringData item : previewItems) {
            if (item != null && item.getId() > 0) ids.add(item.getId());
        }
        return java.util.Set.copyOf(ids);
    }

    @Override
    public void onLoad() {
        if (this.previewItems != null && this.previewItems.length > 0) {
            this.previewItemsArray =
                    Arrays.stream(this.previewItems)
                            .filter(d -> d.getId() > 0 && d.getCount() != null && !d.getCount().isEmpty())
                            .map(ItemParamStringData::toItemParamData)
                            .toArray(size -> new ItemParamData[size]);
        } else {
            this.previewItemsArray = new ItemParamData[0];
        }
    }
}
