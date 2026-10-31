package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.gacha.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GachaWishReqOuterClass.GachaWishReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGachaWishRsp;
import java.util.Arrays;

@Opcodes(PacketOpcodes.GachaWishReq)
public class HandlerGachaWishReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GachaWishReq req = GachaWishReq.parseFrom(payload);

        GachaBanner banner =
                session.getServer().getGachaSystem().getGachaBanners().get(req.getGachaScheduleId());
        Grasscutter.getLogger()
                .info(
                        "GachaWishReq uid={} schedule={} type={} item={} banner={} epitomized={}",
                        session.getPlayer().getUid(),
                        req.getGachaScheduleId(),
                        req.getGachaType(),
                        req.getItemId(),
                        banner != null,
                        banner != null && banner.hasEpitomized());
        if (banner == null || !banner.hasEpitomized()) {
            session.send(new PacketGachaWishRsp(Retcode.RET_GACHA_SCHEDULE_NOT_MATCH));
            return;
        }

        // Item 0 cancels the Epitomized Path; otherwise only a featured 5-star of this banner can
        // be chosen.
        if (req.getItemId() != 0
                && Arrays.stream(banner.getRateUpItems5()).noneMatch(id -> id == req.getItemId())) {
            Grasscutter.getLogger()
                    .info(
                            "GachaWishReq rejected: item {} is not a featured 5-star of schedule {} {}",
                            req.getItemId(),
                            req.getGachaScheduleId(),
                            Arrays.toString(banner.getRateUpItems5()));
            session.send(new PacketGachaWishRsp(Retcode.RET_GACHA_WISH_INVALID_ITEM));
            return;
        }

        PlayerGachaBannerInfo gachaInfo = session.getPlayer().getGachaInfo().getBannerInfo(banner);

        // Fate Points are only lost when the chosen item actually changes; re-picking the same one
        // keeps them.
        if (gachaInfo.getWishItemId() != req.getItemId()) {
            gachaInfo.setFailedChosenItemPulls(0);
            gachaInfo.setWishItemId(req.getItemId());
        }

        session.send(
                new PacketGachaWishRsp(
                        req.getGachaType(),
                        req.getGachaScheduleId(),
                        gachaInfo.getWishItemId(),
                        gachaInfo.getFailedChosenItemPulls(),
                        banner.getWishMaxProgress()));
    }
}
