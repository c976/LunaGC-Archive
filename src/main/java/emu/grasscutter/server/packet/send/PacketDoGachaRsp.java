package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.gacha.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DoGachaRspOuterClass.DoGachaRsp;
import emu.grasscutter.net.proto.GachaItemOuterClass.GachaItem;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.util.List;

public class PacketDoGachaRsp extends BasePacket {

    public PacketDoGachaRsp(
            GachaBanner banner, List<GachaItem> list, PlayerGachaBannerInfo gachaInfo, boolean radiance) {
        super(PacketOpcodes.DoGachaRsp);

        ItemParamData costItem = banner.getCost(1);
        ItemParamData costItem10 = banner.getCost(10);
        int gachaTimesLimit = banner.getGachaTimesLimit();
        int leftGachaTimes =
                switch (gachaTimesLimit) {
                    case Integer.MAX_VALUE -> Integer.MAX_VALUE;
                    default -> Math.max(gachaTimesLimit - gachaInfo.getTotalPulls(), 0);
                };
        DoGachaRsp.Builder rsp =
                DoGachaRsp.newBuilder()
                        .setGachaType(banner.getGachaType())
                        .setGachaScheduleId(banner.getScheduleId())
                        .setGachaTimes(list.size())
                        .setNewGachaRandom(12345)
                        .setLeftGachaTimes(leftGachaTimes)
                        .setGachaTimesLimit(gachaTimesLimit)
                        .setCostItemId(costItem.getId())
                        .setCostItemNum(costItem.getCount())
                        .setTenCostItemId(costItem10.getId())
                        .setTenCostItemNum(costItem10.getCount())
                        .addAllGachaItemList(list);

        if (banner.hasEpitomized()) {
            rsp.setIsEpitomized(true)
                    .setWishItemId(gachaInfo.getWishItemId())
                    .setWishProgress(gachaInfo.getFailedChosenItemPulls())
                    .setWishMaxProgress(banner.getWishMaxProgress());
        }

        this.setData(withRadiance(rsp.build(), radiance));
    }

    /**
     * Capturing Radiance: 7.1 DoGachaRsp carries a bool at field 286 (not present in the generated
     * class, so it is written straight to the wire). Set when the response contains a 5-star, and the
     * client plays the radiance animation.
     */
    private static byte[] withRadiance(DoGachaRsp rsp, boolean radiance) {
        byte[] base = rsp.toByteArray();
        if (!radiance) return base;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(base.length + 16);
        out.writeBytes(base);
        emu.grasscutter.game.player.BeyondProfilePictureWire.varintField(out, 286, 1);
        return out.toByteArray();
    }

    public PacketDoGachaRsp() {
        super(PacketOpcodes.DoGachaRsp);

        DoGachaRsp p =
                DoGachaRsp.newBuilder().setRetcode(RetcodeOuterClass.Retcode.RET_SVR_ERROR_VALUE).build();

        this.setData(p);
    }

    public PacketDoGachaRsp(Retcode retcode) {
        super(PacketOpcodes.DoGachaRsp);

        DoGachaRsp p = DoGachaRsp.newBuilder().setRetcode(retcode.getNumber()).build();

        this.setData(p);
    }
}
