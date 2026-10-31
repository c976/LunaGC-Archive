package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.shop.ShopGoodsBuilder;
import emu.grasscutter.game.shop.ShopInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public class PacketBuyGoodsRsp extends BasePacket {
    public PacketBuyGoodsRsp(int shopType, int boughtNum, ShopInfo shopInfo, int nextRefreshTime) {
        super(PacketOpcodes.BuyGoodsRsp);

        BuyGoodsRspOuterClass.BuyGoodsRsp buyGoodsRsp =
                BuyGoodsRspOuterClass.BuyGoodsRsp.newBuilder()
                        .setShopType(shopType)
                        .setBuyCount(boughtNum)
                        .addGoodsList(
                                ShopGoodsBuilder.fromShopInfo(shopInfo, boughtNum, nextRefreshTime))
                        .build();

        this.setData(buyGoodsRsp);
    }

    public PacketBuyGoodsRsp(Retcode retcode) {
        super(PacketOpcodes.BuyGoodsRsp);
        this.setData(
                BuyGoodsRspOuterClass.BuyGoodsRsp.newBuilder()
                        .setRetcode(retcode.getNumber())
                        .build());
    }
}
