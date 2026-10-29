package emu.grasscutter.data.excels.scene;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "LimitRegionExcelConfigData.json")
@Getter
public final class LimitRegionData extends GameResource {
    private static final String TYPE_BIGWORLD = "LIMIT_REGION_TYPE_BIGWORLD";

    @Getter(onMethod_ = @Override)
    private int id;

    private int sceneId;
    private String type;
    private String name;
    private String openstate;
    private boolean hidePaimon;
    private int order;

    public boolean isBigWorld() {
        return TYPE_BIGWORLD.equals(this.type);
    }

    public boolean isProgressionGated() {
        return !this.hidePaimon && this.openstate != null && !this.openstate.isBlank();
    }
}
