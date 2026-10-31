package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AABLEDJBAKKOuterClass.AABLEDJBAKK;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.AvatarEnterSceneInfoOuterClass.AvatarEnterSceneInfo;
import emu.grasscutter.net.proto.AvatarInfoOuterClass.AvatarInfo;
import emu.grasscutter.net.proto.BirthdayOuterClass.Birthday;
import emu.grasscutter.net.proto.GetActivityInfoReqOuterClass.GetActivityInfoReq;
import emu.grasscutter.net.proto.SceneAvatarInfoOuterClass.SceneAvatarInfo;
import emu.grasscutter.net.proto.SceneTeamAvatarOuterClass.SceneTeamAvatar;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.TpsEquipChangeNotifyOuterClass.TpsEquipChangeNotify;
import emu.grasscutter.net.proto.TpsWeapon._TpsWeapon;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins descriptor-based 7.1 Java generation against the protobuf runtime used by AstaPS. */
public final class ProtocolGenerationTest {
    @Test
    public void generatedMessagesKeepDescriptorsAndRoundTrip() throws Exception {
        var birthday = Birthday.newBuilder().setMonth(10).setDay(5).build();
        var decoded = Birthday.parseFrom(birthday.toByteArray());

        assertEquals(10, decoded.getMonth());
        assertEquals(5, decoded.getDay());
        assertEquals("Birthday.proto", Birthday.getDescriptor().getFile().getName());

        var obfuscatedDescriptor = AABLEDJBAKK.getDescriptor().getFile();
        assertEquals("AABLEDJBAKK.proto", obfuscatedDescriptor.getName());
        assertEquals(2, obfuscatedDescriptor.getDependencies().size());
        assertEquals("APIBGNMPLHD.proto", obfuscatedDescriptor.getDependencies().get(0).getName());
        assertEquals("JMBPOAGEPCL.proto", obfuscatedDescriptor.getDependencies().get(1).getName());

        var tpsNotify = TpsEquipChangeNotify.newBuilder().setAvatarGuid(123456789L).build();
        var decodedTpsNotify = TpsEquipChangeNotify.parseFrom(tpsNotify.toByteArray());
        assertEquals(123456789L, decodedTpsNotify.getAvatarGuid());
        assertEquals("TpsEquipChangeNotify.proto", TpsEquipChangeNotify.getDescriptor().getFile().getName());
        assertEquals(
                "SceneWeaponInfo.proto",
                TpsEquipChangeNotify.getDescriptor().getFile().getDependencies().getFirst().getName());

        var ammunitionDescriptor =
                AbilityMetaUpdateTpsWeaponAmmunition.getDescriptor().getFile();
        assertEquals("AbilityMetaUpdateTpsWeaponAmmunition.proto", ammunitionDescriptor.getName());
        assertEquals("TpsWeaponAccessoryInfo.proto", ammunitionDescriptor.getDependencies().get(2).getName());
        assertEquals(
                7,
                ammunitionDescriptor
                        .getDependencies()
                        .get(2)
                        .findMessageTypeByName("TpsWeaponAccessoryInfo")
                        .findFieldByName("ammunition_type")
                        .getNumber());

        assertEquals(37, AvatarInfo.getDescriptor().findFieldByName("tps_weapon_list").getNumber());
        assertEquals(
                31,
                SceneAvatarInfo.getDescriptor().findFieldByName("tps_weapon_list").getNumber());
        assertEquals(
                382,
                SceneTeamAvatar.getDescriptor().findFieldByName("tps_weapon_list").getNumber());
        assertEquals(
                13,
                AvatarEnterSceneInfo.getDescriptor().findFieldByName("tps_weapon_list").getNumber());
        assertEquals(
                12,
                SceneWeaponInfo.getDescriptor().findFieldByName("ammunition_list").getNumber());
        assertEquals(
                2, _TpsWeapon.getDescriptor().findFieldByName("accessory_id_list").getNumber());
    }

    @Test
    public void activityMessagesUseTheClientFieldNumbers() throws Exception {
        assertEquals(186, PacketOpcodes.GetActivityInfoReq);

        // What a 7.1 client sends for activities 5001 and 5003: packed field 14.
        var request = GetActivityInfoReq.parseFrom(HexFormat.of().parseHex("720489278b27"));
        assertEquals(List.of(5001, 5003), request.getActivityIdListList());

        assertEquals(
                1917, ActivityInfo.getDescriptor().findFieldByName("meet_cond_list").getNumber());
    }
}
