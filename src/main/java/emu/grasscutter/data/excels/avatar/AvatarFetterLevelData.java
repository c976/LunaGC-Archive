package emu.grasscutter.data.excels.avatar;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;

@ResourceType(name = "AvatarFettersLevelExcelConfigData.json")
public class AvatarFetterLevelData extends GameResource {
    // This file ships with snake_case keys. Nothing bound before, so every row came back with
    // fetterLevel 0, they all collided on that id, and nine of the ten levels were dropped.
    // The camelCase spelling stays as an alternate for packs that use it.
    @SerializedName(value = "fetter_level", alternate = "fetterLevel")
    private int fetterLevel;

    @SerializedName(value = "need_exp", alternate = "needExp")
    private int needExp;

    @Override
    public int getId() {
        return this.fetterLevel;
    }

    public int getLevel() {
        return fetterLevel;
    }

    public int getExp() {
        return needExp;
    }
}
