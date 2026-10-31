package emu.grasscutter.data.excels.monster;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.net.proto.SceneMonsterInfoOuterClass.SceneMonsterInfo;
import emu.grasscutter.utils.JsonUtils;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards {@link MonsterData}'s affix list against being null.
 *
 * <p>1527 of the 3408 rows in MonsterExcelConfigData.json omit "affix" entirely, so Gson leaves the
 * field null. EntityMonster.toProto hands it to addAllAffixList, and protobuf rejects a null: the
 * throw escapes into the world tick, which then fails every tick a monster from one of those rows
 * is spawning. The player sees terrain and no entities.
 */
public final class MonsterDataAffixTest {
    private static MonsterData load(String json) throws Exception {
        var data = JsonUtils.decode(json, MonsterData.class);
        assertNotNull(data);
        // onLoad is what ResourceLoader calls after deserializing each row.
        Method onLoad = MonsterData.class.getDeclaredMethod("onLoad");
        onLoad.setAccessible(true);
        onLoad.invoke(data);
        return data;
    }

    @Test
    @DisplayName("a row with no affix field still yields a usable list")
    public void missingAffixBecomesEmpty() throws Exception {
        var data = load("{\"id\":20010101,\"equips\":[],\"describeId\":0}");

        assertNotNull(data.getAffix(), "a null affix is what protobuf rejects");
        assertTrue(data.getAffix().isEmpty());
    }

    @Test
    @DisplayName("a row that does carry affixes keeps them")
    public void presentAffixIsKept() throws Exception {
        var data = load("{\"id\":20010101,\"equips\":[],\"describeId\":0,\"affix\":[1101,1102]}");

        assertEquals(2, data.getAffix().size());
        assertTrue(data.getAffix().contains(1101));
    }

    @Test
    @DisplayName("the proto builder accepts the result, which is the call that used to throw")
    public void protoBuilderAcceptsIt() throws Exception {
        var data = load("{\"id\":20010101,\"equips\":[],\"describeId\":0}");

        assertDoesNotThrow(
                () ->
                        SceneMonsterInfo.newBuilder()
                                .setMonsterId(data.getId())
                                .addAllAffixList(data.getAffix())
                                .build());
    }
}
