package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.BeyondProfilePictureTable;
import emu.grasscutter.game.player.BeyondProfilePictureWire;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.ProfilePictureHelper;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.ProfilePictureOuterClass;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * _BeyondProfilePictureDataNotify (7.1 CmdId 6326).
 * EEIIJMDHNNE { repeated CNMPPAPJDDI = 5; OFNJDNHLGJI = 7; bool = 1; uint32 = 8; }
 */
public class PacketBeyondProfilePictureDataNotify extends BasePacket {

    public PacketBeyondProfilePictureDataNotify(Player player) {
        super(PacketGetProfilePictureDataRsp.BEYOND_PROFILE_PICTURE_DATA_NOTIFY, 0);
        byte[] data;
        int count = 0;
        try {
            List<BeyondProfilePictureTable.Entry> list = BeyondProfilePictureTable.all();
            int curPic = 0;
            try {
                curPic = ProfilePictureHelper
                        .fillFromHeadImage(ProfilePictureOuterClass.ProfilePicture.newBuilder(), player.getHeadImage())
                        .getProfilePictureId();
            } catch (Throwable ignored) {
            }
            BeyondProfilePictureTable.Entry cur = curPic > 0 ? BeyondProfilePictureTable.find(curPic) : null;

            ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
            BeyondProfilePictureWire.varintField(out, 1, 1);
            BeyondProfilePictureTable.Entry first = null;
            int __countK = 0;
            for (BeyondProfilePictureTable.Entry e : list) {
                BeyondProfilePictureWire.bytesField(out, 5,
                        BeyondProfilePictureWire.cnmppapjddi(e.id, e.iconPath, e.unlockParam));
                if (first == null) {
                    first = e;
                }
            }
            if (cur == null) {
                cur = first;
            }
            if (cur != null) {
                BeyondProfilePictureWire.bytesField(out, 7,
                        BeyondProfilePictureWire.ofnjdnhlgji(cur.id, cur.iconPath, cur.unlockParam));
                BeyondProfilePictureWire.varintField(out, 8, player.getProfileFrameId()); // [v26] f8=JPCCEHMFMAN 是头像框id，之前错填头像id
            }
            data = out.toByteArray();
            count = list.size();
            Grasscutter.getLogger().debug("BeyondProfilePictureNotify current={} (headImage={})", cur == null ? -1 : cur.id, player.getHeadImage());
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("PacketBeyondProfilePictureDataNotify build failed: {}", t.toString());
            data = new byte[] {0x08, 0x01};
        }
        this.setData(data);
        Grasscutter.getLogger().debug("BeyondProfilePictureNotify opcode=6326 entries={} bytes={}", count, data.length);
    }
}
