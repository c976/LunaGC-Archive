package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetAllUnlockNameCardRspOuterClass.GetAllUnlockNameCardRsp;

public class PacketGetAllUnlockNameCardRsp extends BasePacket {

    public PacketGetAllUnlockNameCardRsp(Player player) {
        super(PacketOpcodes.GetAllUnlockNameCardRsp);

        // [喵喵 v10] 升序发（HashSet 迭代顺序随机，客户端按列表顺序/选中项作锚点会错乱）+ 显式带 retcode=0
        java.util.List<Integer> sorted = new java.util.ArrayList<>(player.getNameCardList());
        java.util.Collections.sort(sorted);
        GetAllUnlockNameCardRsp proto =
                GetAllUnlockNameCardRsp.newBuilder().addAllNameCardList(sorted).setRetcode(0).build();

        this.setData(proto);
    }
}
