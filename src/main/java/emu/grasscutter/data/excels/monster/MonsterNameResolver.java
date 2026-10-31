package emu.grasscutter.data.excels.monster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.scripts.data.SceneMonster;

/** Resolves client display IDs against the loaded resource tables for every monster spawn path. */
public final class MonsterNameResolver {
    private MonsterNameResolver() {}

    public record Names(int titleId, int specialNameId) {}

    public static Names resolve(MonsterData data, SceneMonster metadata) {
        var describe = data.getDescribeData();
        int titleId = metadata != null ? metadata.title_id : 0;
        if (!validTitle(titleId)) {
            titleId = describe != null && validTitle(describe.getTitleId()) ? describe.getTitleId() : 0;
        }

        int specialNameId = metadata != null ? metadata.special_name_id : 0;
        if (!validSpecialName(specialNameId)) {
            // Script variants remain authoritative when valid. Legacy IDs and spawns without
            // script metadata use a real name from this monster's current description family.
            specialNameId = data.getSpecialNameId();
            if (!validSpecialName(specialNameId)) {
                specialNameId = defaultSpecialNameId(describe);
            }
        }
        return new Names(titleId, specialNameId);
    }

    public static int defaultSpecialNameId(MonsterDescribeData describe) {
        if (describe == null || describe.getSpecialNameLabId() <= 0) {
            return 0;
        }
        int selected = 0;
        for (var name : GameData.getMonsterSpecialNameDataMap().values()) {
            if (name.getSpecialNameLabId() == describe.getSpecialNameLabId()
                    && name.getId() > 0
                    && name.getSpecialNameTextMapHash() != 0
                    && (selected == 0 || name.getId() < selected)) {
                selected = name.getId();
            }
        }
        return selected;
    }

    private static boolean validTitle(int id) {
        var title = GameData.getMonsterTitleDataMap().get(id);
        return id > 0 && title != null && title.getTitleNameTextMapHash() != 0;
    }

    private static boolean validSpecialName(int id) {
        var name = GameData.getMonsterSpecialNameDataMap().get(id);
        return id > 0 && name != null && name.getSpecialNameTextMapHash() != 0;
    }
}
