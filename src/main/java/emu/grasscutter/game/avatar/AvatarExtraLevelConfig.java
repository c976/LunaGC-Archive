/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.avatar;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.common.ItemParamData;
import java.util.List;

public final class AvatarExtraLevelConfig {
    @SerializedName(value="DEJBOJMHHJB", alternate={"GGCNFKENPPB", "LFKJOBPEIKM"})
    private int fromLevel;
    @SerializedName(value="BBPEGJLEEEC", alternate={"LCBMNGNFOCE", "DKLMGPLCDGG"})
    private int toLevel;
    private List<ItemParamData> costItems;

    public int getFromLevel() {
        return this.fromLevel;
    }

    public int getToLevel() {
        return this.toLevel;
    }

    public List<ItemParamData> getCostItems() {
        return this.costItems;
    }
}
