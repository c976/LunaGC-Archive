package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.achievement.Achievement;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AchievementAllDataNotifyOuterClass;
import emu.grasscutter.net.proto.SetReliquaryFavouriteRspOuterClass;

public class PacketSetReliquaryFavouriteRsp extends BasePacket {
    public PacketSetReliquaryFavouriteRsp(long itemId,boolean favourite) {
        super(PacketOpcodes.SetReliquaryFavouriteRsp);


        var notify = SetReliquaryFavouriteRspOuterClass.SetReliquaryFavouriteRsp.newBuilder()
                .setTargetReliquaryGuid(itemId).setIsFavourite(favourite);

        this.setData(notify);
    }
}
