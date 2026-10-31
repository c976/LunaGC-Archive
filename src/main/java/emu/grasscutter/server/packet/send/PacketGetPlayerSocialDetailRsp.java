package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPlayerSocialDetailRspOuterClass.GetPlayerSocialDetailRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.net.proto.SocialDetailOuterClass.SocialDetail;

public class PacketGetPlayerSocialDetailRsp extends BasePacket {

    public PacketGetPlayerSocialDetailRsp(SocialDetail.Builder detail) {
        this(detail, 0);
    }

    /** 把客户端请求的回执号（clientSequenceId）原样带回，否则客户端会丢弃整个回答包。 */
    public PacketGetPlayerSocialDetailRsp(SocialDetail.Builder detail, int clientSequence) {
        super(PacketOpcodes.GetPlayerSocialDetailRsp, clientSequence);
        GetPlayerSocialDetailRsp.Builder proto = GetPlayerSocialDetailRsp.newBuilder();
        if (detail != null) {
            proto.setDetailData(detail);
        } else {
            proto.setRetcode(RetcodeOuterClass.Retcode.RET_SVR_ERROR_VALUE);
        }
        this.setData(proto);
        Grasscutter.getLogger()
                .debug(
                        "SocialDetailRsp seq={} hasDetail={} bytes={}",
                        clientSequence,
                        detail != null,
                        this.getData().length);
    }
}
