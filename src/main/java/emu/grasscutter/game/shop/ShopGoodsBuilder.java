package emu.grasscutter.game.shop;

import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;
import java.util.stream.Collectors;

/** Builds client ShopGoods payloads from server ShopInfo. */
public final class ShopGoodsBuilder {
    /** Official shop uses disable_type when goods cannot be bought (sold out / locked). */
    private static final int DISABLE_TYPE_SOLD_OUT = 1;

    private ShopGoodsBuilder() {}

    public static ShopGoods.Builder fromShopInfo(ShopInfo info, int boughtNum, int nextRefreshTime) {
        int buyLimit = info.getBuyLimit();
        int bought = boughtNum;
        if (buyLimit > 0) {
            bought = Math.min(Math.max(0, bought), buyLimit);
        } else {
            bought = Math.max(0, bought);
        }
        boolean soldOut = buyLimit > 0 && bought >= buyLimit;
        int disableType = info.getDisableType();
        if (soldOut && disableType == 0) {
            disableType = DISABLE_TYPE_SOLD_OUT;
        }

        // buy_limit (OCFMGIPGLDK), disable_type (ELPGDNACFOA), scoin, mcoin, the level range and
        // single_limit are unplaced in 7.1 and sit on unused numbers (LunaGC_7.1.0's ShopGoods), so
        // setting them is harmless; the price reaches the client through cost_item_list below.
        ShopGoods.Builder goods =
                ShopGoods.newBuilder()
                        .setGoodsId(info.getGoodsId())
                        .setGoodsItem(
                                ItemParam.newBuilder()
                                        .setItemId(info.getGoodsItem().getId())
                                        .setCount(info.getGoodsItem().getCount())
                                        .build())
                        .setScoin(info.getScoin())
                        .setHcoin(info.getHcoin())
                        .setMcoin(info.getMcoin())
                        .setOCFMGIPGLDK(buyLimit)
                        // Caps the purchase slider; monthly remaining uses buy_limit - bought_num.
                        .setSingleLimit(buyLimit > 0 ? Math.max(1, buyLimit - bought) : 0)
                        .setBeginTime(info.getBeginTime())
                        .setEndTime(info.getEndTime())
                        .setMinLevel(info.getMinLevel())
                        .setMaxLevel(info.getMaxLevel())
                        .setELPGDNACFOA(disableType)
                        .setBoughtNum(bought)
                        .setNextRefreshTime(nextRefreshTime);

        if (info.getCostItemList() != null) {
            goods.addAllCostItemList(
                    info.getCostItemList().stream()
                            .map(
                                    x ->
                                            ItemParam.newBuilder()
                                                    .setItemId(x.getId())
                                                    .setCount(x.getCount())
                                                    .build())
                            .collect(Collectors.toList()));
        }

        // Mora, Primogems and Genesis Crystals as cost items: the currency fields are not placed in
        // 7.1, cost_item_list is (idea from LunaGC_7.1.0).
        addCurrencyCost(goods, 202, info.getScoin());
        addCurrencyCost(goods, 201, info.getHcoin());
        addCurrencyCost(goods, 203, info.getMcoin());
        // pre_goods_id_list is unnamed (MEODDILKAAD) and unplaced in 7.1.
        if (info.getPreGoodsIdList() != null && !info.getPreGoodsIdList().isEmpty()) {
            goods.addAllMEODDILKAAD(info.getPreGoodsIdList());
        }

        return goods;
    }

    private static void addCurrencyCost(ShopGoods.Builder goods, int itemId, int count) {
        if (count <= 0) return;
        goods.addCostItemList(ItemParam.newBuilder().setItemId(itemId).setCount(count).build());
    }
}
