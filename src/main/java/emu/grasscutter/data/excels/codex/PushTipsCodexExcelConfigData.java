package emu.grasscutter.data.excels.codex;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import lombok.Getter;

/** Maps a push tip to the id it uses in the CODEX_PUSHTIPS (type 6) codex list. */
@Getter
@ResourceType(name = "PushTipsCodexExcelConfigData.json")
public class PushTipsCodexExcelConfigData extends GameResource {
    @SerializedName("PushTipId")
    private int pushTipId;

    @SerializedName("Id")
    private int codexId;

    private int sortOrder;

    @Override
    public int getId() {
        return this.pushTipId;
    }
}
