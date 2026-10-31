package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.BeyondProfilePictureTable;
import emu.grasscutter.game.player.BeyondProfilePictureWire;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 头像列表响应。
 * 5271 = map<uint32, OFNJDNHLGJI>@1 + int32@10
 * 20816 = 7.0 真号（repeated uint32@6 + repeated uint32@11 + int32@10）
 */
public class PacketGetProfilePictureDataRsp extends BasePacket {
    public static final int PROFILE_PICTURE_DATA_RSP = 9219; // 7.1 real (generated CmdId)
    public static final int PROFILE_PICTURE_DATA_RSP_70 = 20816;
    public static final int BEYOND_PROFILE_PICTURE_DATA_NOTIFY = 6326;
    public static final int[] FRAME_IDS = {100000, 100011, 100012, 100013, 100014};

    public PacketGetProfilePictureDataRsp(Player player) {
        this(player, PROFILE_PICTURE_DATA_RSP, 0);
    }

    public PacketGetProfilePictureDataRsp(Player player, int clientSequence) {
        this(player, PROFILE_PICTURE_DATA_RSP, clientSequence);
    }

    public PacketGetProfilePictureDataRsp(Player player, int opcode, int clientSequence, int[] repFields) {
        super(opcode, clientSequence);
        ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        List<BeyondProfilePictureTable.Entry> list = BeyondProfilePictureTable.all();
        for (int rf : repFields) {
            for (BeyondProfilePictureTable.Entry e : list) {
                BeyondProfilePictureWire.varintField(out, rf, e.id);
            }
        }
        this.setData(out.toByteArray());
        Grasscutter.getLogger().debug("ProfilePicProbe opcode={} fields={} entries={} bytes={}", opcode, java.util.Arrays.toString(repFields), list.size(), out.size());
    }

    public PacketGetProfilePictureDataRsp(Player player, int opcode, int clientSequence) {
        super(opcode, clientSequence);
        byte[] data;
        int count = 0;
        try {
            if (opcode == PROFILE_PICTURE_DATA_RSP_70) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                List<BeyondProfilePictureTable.Entry> l = BeyondProfilePictureTable.all();
                for (BeyondProfilePictureTable.Entry e : l) {
                    BeyondProfilePictureWire.varintField(out, 6, e.id);
                }
                for (int f : FRAME_IDS) {
                    BeyondProfilePictureWire.varintField(out, 11, f);
                }
                BeyondProfilePictureWire.varintField(out, 10, 0);
                data = out.toByteArray();
                count = l.size();
            } else {
                List<BeyondProfilePictureTable.Entry> list = BeyondProfilePictureTable.all();
                ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
                // 7.1 real layout: repeated uint32 special_profile_picture_list = 4; int32 retcode = 11;
                for (BeyondProfilePictureTable.Entry e : list) {
                    BeyondProfilePictureWire.varintField(out, 2, e.id);
                    BeyondProfilePictureWire.varintField(out, 8, e.id);
                }
                BeyondProfilePictureWire.varintField(out, 9, 0);
                data = out.toByteArray();
                count = list.size();
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("PacketGetProfilePictureDataRsp build failed: {}", t.toString());
            data = new byte[] {0x50, 0x00};
        }
        this.setData(data);
        Grasscutter.getLogger().debug("ProfilePictureRsp opcode={} seq={} entries={} bytes={}", opcode, clientSequence, count, data.length);
    }
}
