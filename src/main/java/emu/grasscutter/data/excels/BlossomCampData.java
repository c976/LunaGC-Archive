package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import java.util.List;
import lombok.Getter;

/** Original 7.1 BlossomGroups: authored encounter groups and their next-camp graph. */
@Getter
@ResourceType(name = "BlossomGroupsExcelConfigData.json")
public final class BlossomCampData extends GameResource {
    private int id;
    private int cityId;
    private int sectionId;
    private int limit_level;
    private List<Integer> refreshTypeVec;
    private List<Integer> newGroupVec;
    private List<Integer> nextCampIdVec;

    @Override
    public int getId() {
        return id;
    }

    public int getLimitLevel() {
        return limit_level;
    }
}
