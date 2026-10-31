package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

/**
 * City / SotS level-up table used from Fontaine onward (Nod-Krai = city 7).
 *
 * <p>{@link StatuePromoteData} only covers cities 1–4; newer regions live here.
 */
@ResourceType(name = "CityLevelupConfigData.json")
@Getter
public class CityLevelupData extends GameResource {
    @SerializedName(
            value = "cityId",
            alternate = {"city_id"})
    private int cityId;

    private int level;

    @SerializedName(
            value = "sceneId",
            alternate = {"scene_id"})
    private int sceneId;

    @SerializedName(
            value = "rewardId",
            alternate = {"rewardID", "reward_id"})
    private int rewardId;

    @SerializedName(
            value = "consumeItem",
            alternate = {"consume_item"})
    private ConsumeItem consumeItem;

    /** Stamina / area actions. Obfuscated field name in 7.0 dump. */
    @SerializedName(
            value = "actionVec",
            alternate = {"HJDKMDJFECH"})
    private List<LevelupAction> actionVec;

    @Override
    public int getId() {
        return (this.cityId << 8) + this.level;
    }

    public int getCostItemId() {
        return this.consumeItem != null ? this.consumeItem.getItemId() : 0;
    }

    public int getCostItemCount() {
        return this.consumeItem != null ? this.consumeItem.getItemNum() : 0;
    }

    public int getStaminaGain() {
        if (this.actionVec == null) return 0;
        for (var action : this.actionVec) {
            if (action == null || action.getType() == null) continue;
            if (action.getType().contains("STAMINA") && action.getParam1Vec() != null) {
                for (int p : action.getParam1Vec()) {
                    if (p > 0) return p;
                }
            }
        }
        return 0;
    }

    @Getter
    public static final class ConsumeItem {
        @SerializedName(
                value = "itemId",
                alternate = {"item_id", "id"})
        private int itemId;

        @SerializedName(
                value = "itemNum",
                alternate = {"item_num", "count"})
        private int itemNum;
    }

    @Getter
    public static final class LevelupAction {
        private String type;

        @SerializedName(
                value = "param1Vec",
                alternate = {"param1_vec"})
        private int[] param1Vec;
    }
}
