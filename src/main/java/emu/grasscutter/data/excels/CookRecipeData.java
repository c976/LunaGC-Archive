package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.common.ItemParamData;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@ResourceType(
        name = {"CookRecipeExcelConfigData.json"},
        loadPriority = LoadPriority.LOW)
@Getter
public class CookRecipeData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int rankLevel;
    private boolean isDefaultUnlocked;
    private int maxProficiency;

    private List<ItemParamData> qualityOutputVec;
    private List<ItemParamData> inputVec;

    @Override
    public void onLoad() {
        // Some 7.0 Excel dumps swap inputVec (ingredients) and qualityOutputVec (dish qualities).
        // Detect by id ranges: cooked dishes are 108xxx+, raw ingredients are typically < 108000.
        List<ItemParamData> inputs = nonEmpty(inputVec);
        List<ItemParamData> outputs = nonEmpty(qualityOutputVec);
        if (!inputs.isEmpty()
                && !outputs.isEmpty()
                && looksLikeFoodIds(inputs)
                && looksLikeIngredientIds(outputs)) {
            this.inputVec = outputs;
            this.qualityOutputVec = inputs;
        } else {
            this.inputVec = inputs;
            this.qualityOutputVec = outputs;
        }
    }

    private static List<ItemParamData> nonEmpty(List<ItemParamData> list) {
        List<ItemParamData> out = new ArrayList<>();
        if (list == null) return out;
        for (ItemParamData p : list) {
            if (p != null && p.getId() > 0 && p.getCount() > 0) out.add(p);
        }
        return out;
    }

    private static boolean looksLikeFoodIds(List<ItemParamData> list) {
        return list.stream().allMatch(p -> p.getId() >= 108000 && p.getId() < 110000);
    }

    private static boolean looksLikeIngredientIds(List<ItemParamData> list) {
        // Ingredients/materials: not cooked-food item ids (108xxx).
        return list.stream().noneMatch(p -> p.getId() >= 108000 && p.getId() < 110000);
    }
}
