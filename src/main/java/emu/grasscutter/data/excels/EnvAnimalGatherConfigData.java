package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import java.util.List;

@ResourceType(
        name = "EnvAnimalGatherExcelConfigData.json",
        loadPriority = ResourceType.LoadPriority.LOW)
public class EnvAnimalGatherConfigData extends GameResource {
    private int animalId;
    private String entityType;
    private List<ItemParamData> gatherItemList;
    private String excludeWeathers;
    private int aliveTime;
    private int escapeTime;
    private int escapeRadius;

    @Override
    public int getId() {
        return animalId;
    }

    public int getAnimalId() {
        return animalId;
    }

    public String getEntityType() {
        return entityType;
    }

    public ItemParamData getGatherItem() {
        return gatherItemList != null && gatherItemList.size() > 0 ? gatherItemList.get(0) : null;
    }

    /**
     * Every item the animal drops when gathered.
     *
     * <p>{@link #getGatherItem()} only ever answers the first entry, so animals whose excel row
     * lists more than one drop (most fowl drop meat plus a feather) handed out a single item. The
     * caller falls back to {@code getGatherItem()} when this is null, so a row with no list at all
     * still behaves as before.
     *
     * @return the raw drop list, or {@code null} when the row declares none
     */
    public List<ItemParamData> getGatherItemList() {
        return gatherItemList;
    }
}
