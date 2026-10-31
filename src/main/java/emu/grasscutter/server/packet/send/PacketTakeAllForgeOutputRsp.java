package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto._TakeAllForgeOutputRspOuterClass._TakeAllForgeOutputRsp;
import java.util.List;

public class PacketTakeAllForgeOutputRsp extends BasePacket {
    public PacketTakeAllForgeOutputRsp(
            int retcode, List<GameItem> output, List<GameItem> extra, List<GameItem> refund) {
        super(PacketOpcodes._TakeAllForgeOutputRsp);

        _TakeAllForgeOutputRsp.Builder builder =
                _TakeAllForgeOutputRsp.newBuilder()
                        .setRetcode(retcode)
                        .addAllOutputItemList(output.stream().map(GameItem::toItemHintProto).toList())
                        .addAllExtraOutputItemList(extra.stream().map(GameItem::toItemHintProto).toList())
                        .addAllReturnItemList(refund.stream().map(GameItem::toItemHintProto).toList());

        setData(builder.build());
    }
}
