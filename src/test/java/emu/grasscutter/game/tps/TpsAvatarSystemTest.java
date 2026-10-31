package emu.grasscutter.game.tps;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.enums.DungeonType;
import emu.grasscutter.utils.*;
import org.junit.jupiter.api.Test;

/** Which scenes swap the team for the TPS traveler. */
public final class TpsAvatarSystemTest {
    @Test
    public void tpsScenesAreThoseThatOnlyAllowTheTpsTraveler() {
        var shootingRange =
                JsonUtils.decode(
                        "{\"id\": 51336, \"type\": \"SCENE_DUNGEON\", \"specifiedAvatarList\": [10000134, 10000135]}",
                        SceneData.class);
        var travelerOnly =
                JsonUtils.decode(
                        "{\"id\": 1005, \"type\": \"SCENE_DUNGEON\", \"specifiedAvatarList\": [10000005, 10000007]}",
                        SceneData.class);
        var open = JsonUtils.decode("{\"id\": 3, \"type\": \"SCENE_WORLD\"}", SceneData.class);

        assertTrue(TpsAvatarSystem.isTpsScene(shootingRange));
        assertFalse(TpsAvatarSystem.isTpsScene(travelerOnly));
        assertFalse(TpsAvatarSystem.isTpsScene(open));
        assertFalse(TpsAvatarSystem.isTpsScene(null));
    }

    @Test
    public void tpsDungeonTypesLoad() {
        // An unknown type loads as null, and applyTrialTeam switched on it.
        var dungeon =
                JsonUtils.decode(
                        "{\"id\": 10955, \"sceneId\": 51336, \"type\": \"DUNGEON_TPS_SHOOTING_RANGE\"}",
                        DungeonData.class);
        assertEquals(DungeonType.DUNGEON_TPS_SHOOTING_RANGE, dungeon.getType());
    }

    @Test
    public void tpsTravelerHasTheTpsAbilities() {
        // The TPS traveler's icon is the traveler's, so the icon name alone picks PlayerGirl's set.
        var data =
                JsonUtils.decode(
                        "{\"id\": 10000135, \"iconName\": \"UI_AvatarIcon_PlayerGirl\"}", AvatarData.class);
        data.buildEmbryo();

        assertEquals("LumineShadow", data.getName());
        assertTrue(data.getAbilities().contains(Utils.abilityHash("Avatar_TPS_Innate_Ability")));
        assertTrue(data.getAbilities().contains(Utils.abilityHash("Avatar_TPS_PlayerGirl_Ability")));
    }

    @Test
    public void attackModeSkillIsASkill() {
        // Depot 50001: every regular skill is 0, the aim/fire input is the attack mode skill.
        var depot =
                JsonUtils.decode(
                        "{\"id\": 50001, \"skills\": [0, 0, 0, 0], \"attackModeSkill\": 20000}",
                        AvatarSkillDepotData.class);
        assertArrayEquals(new int[] {20000}, depot.getSkillsAndEnergySkill().toArray());
    }
}
