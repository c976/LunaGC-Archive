package emu.grasscutter.data.excels.monster;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.data.ResourceType.LoadPriority;
import lombok.Getter;

@ResourceType(name = "MonsterTitleExcelConfigData.json", loadPriority = LoadPriority.HIGH)
@Getter
public class MonsterTitleData extends GameResource {
    @SerializedName(value = "titleId", alternate = {"titleID"})
    private int titleId;

    private long titleNameTextMapHash;

    @Override
    public int getId() {
        return titleId;
    }
}
