package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.BeyondProfilePictureTable;
import emu.grasscutter.game.player.BeyondProfilePictureWire;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;

/**
 * 7.1 个人资料页（Beyond 玩家详情）CmdId 4162。
 * NJEIKGEBJHJ { repeated uint32 HNOGAMLELDD = 5; repeated CKLADNFCBGB ADNAADAEMNO = 13; int32 retcode = 10; uint32 JCNANNABFLE = 14; }
 * CKLADNFCBGB { LNFKGCNALBO EDJJPNIFJOA = 1; ... }
 * LNFKGCNALBO { uid=1; 昵称=4; 签名=12; 名片=18; 头像=23(MFDLDKCDGCF); 头像数据=3(OFNJDNHLGJI); 解锁=14 }
 */
public class PacketBeyondPlayerDetailRsp extends BasePacket {
    public static final int OPCODE = 4162;

    public PacketBeyondPlayerDetailRsp(Player player) {
        this(player, 0);
    }

    public PacketBeyondPlayerDetailRsp(Player player, int clientSequence) {
        super(OPCODE, clientSequence);
        byte[] data;
        try {
            byte[] detail = buildDetail(player);
            ByteArrayOutputStream ck = new ByteArrayOutputStream(256);
            BeyondProfilePictureWire.bytesField(ck, 1, detail);
            ByteArrayOutputStream out = new ByteArrayOutputStream(320);
            BeyondProfilePictureWire.bytesField(out, 13, ck.toByteArray());
            BeyondProfilePictureWire.varintField(out, 10, 0);
            data = out.toByteArray();
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("PacketBeyondPlayerDetailRsp build failed: {}", t.toString());
            data = new byte[0];
        }
        this.setData(data);
        Grasscutter.getLogger().debug("BeyondPlayerDetailRsp opcode=4162 seq={} bytes={}", clientSequence, data.length);
    }


    /** 探针版：把 marker / tag 填进“语义不明”的字段，便于在客户端内存里搜。 */
    public static byte[] buildDetail(Player player, int marker, String tag) {
        ByteArrayOutputStream d = new ByteArrayOutputStream(256);
        int uid = player.getUid();
        int headImage = player.getHeadImage() != 0 ? player.getHeadImage() : 10000007;
        int nameCardId = player.getNameCardId() != 0 ? player.getNameCardId() : 210001;
        BeyondProfilePictureWire.varintField(d, 1, uid);
        BeyondProfilePictureWire.varintField(d, 2, marker);
        BeyondProfilePictureWire.stringField(d, 4, player.getNickname() == null ? "" : player.getNickname());
        BeyondProfilePictureWire.varintField(d, 8, marker);
        BeyondProfilePictureWire.varintField(d, 9, marker);
        BeyondProfilePictureWire.varintField(d, 10, marker);
        BeyondProfilePictureWire.stringField(d, 12, player.getSignature() == null ? "" : player.getSignature());
        BeyondProfilePictureWire.stringField(d, 13, tag);
        BeyondProfilePictureWire.varintField(d, 16, marker);
        BeyondProfilePictureWire.varintField(d, 18, nameCardId);
        BeyondProfilePictureWire.varintField(d, 20, marker);
        BeyondProfilePictureWire.varintField(d, 22, marker);
        BeyondProfilePictureWire.varintField(d, 24, marker);
        ByteArrayOutputStream pic = new ByteArrayOutputStream(24);
        BeyondProfilePictureWire.varintField(pic, 1, headImage);
        BeyondProfilePictureWire.varintField(pic, 3, 2);
        BeyondProfilePictureWire.varintField(pic, 4, 100000);
        BeyondProfilePictureWire.bytesField(d, 23, pic.toByteArray());
        BeyondProfilePictureWire.bytesField(d, 3, BeyondProfilePictureWire.ofnjdnhlgji(2, "UI_AvatarIcon_PlayerGirl_Circle", headImage));
        return d.toByteArray();
    }

    public static byte[] buildDetail(Player player) {
        ByteArrayOutputStream d = new ByteArrayOutputStream(256);
        int uid = player.getUid();
        int headImage = player.getHeadImage() != 0 ? player.getHeadImage() : 10000007;
        int pictureId = player.getHeadImage() != 0 ? player.getHeadImage() : 2;
        int frameId = player.getProfileFrameId();
        int nameCardId = player.getNameCardId() != 0 ? player.getNameCardId() : 210001;

        BeyondProfilePictureWire.varintField(d, 1, uid);
        BeyondProfilePictureWire.stringField(d, 4, player.getNickname() == null ? "" : player.getNickname());
        BeyondProfilePictureWire.stringField(d, 5, player.getSignature() == null ? "" : player.getSignature()); // 7.1: signature=field5 (was wrongly 12)
        BeyondProfilePictureWire.varintField(d, 18, nameCardId);

        // field23 = MFDLDKCDGCF { avatar_id=1, costume_id=2, profile_picture_id=3, profile_frame_id=4 }
        ByteArrayOutputStream pic = new ByteArrayOutputStream(24);
        BeyondProfilePictureWire.varintField(pic, 1, headImage);
        BeyondProfilePictureWire.varintField(pic, 3, pictureId);
        BeyondProfilePictureWire.varintField(pic, 4, frameId);
        BeyondProfilePictureWire.bytesField(d, 23, pic.toByteArray());

        // field3 = OFNJDNHLGJI { id=1, bool=2, icon=3, icon=4, unlockParam=5 }
        String icon = null;
        BeyondProfilePictureTable.Entry cur = BeyondProfilePictureTable.find(pictureId);
        if (cur != null) icon = cur.iconPath;
        if (icon == null) icon = "UI_AvatarIcon_PlayerGirl_Circle";
        BeyondProfilePictureWire.bytesField(d, 3, BeyondProfilePictureWire.ofnjdnhlgji(pictureId, icon, headImage));

        return d.toByteArray();
    }
}
