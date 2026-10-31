package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.common.DropItemData;
import java.util.List;
import lombok.Getter;

@ResourceType(
        name = {"DropTableExcelConfigData.json", "DropSubTableExcelConfigData.json"},
        loadPriority = LoadPriority.HIGH)
@Getter
public class DropTableData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int randomType;
    private int dropLevel;
    private List<DropItemData> dropVec;
    private int nodeType;
    private boolean fallToGround;
    private int sourceType;
    private int everydayLimit;
    private int historyLimit;
    private int activityLimit;

    @Override
    public void onLoad() {
        // Some 7.1 rows (e.g. investigation point 1010100) are a header with no dropVec at all.
        if (this.dropVec == null) this.dropVec = List.of();
    }
}
