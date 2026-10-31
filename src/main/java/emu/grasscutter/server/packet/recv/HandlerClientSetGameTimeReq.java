package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientSetGameTimeReqOuterClass.ClientSetGameTimeReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketClientSetGameTimeRsp;
import emu.grasscutter.server.packet.send.PacketPlayerGameTimeNotify;

/** Handles the absolute world-minute request sent by the 7.1 Paimon clock. */
@Opcodes(PacketOpcodes.ClientSetGameTimeReq)
public final class HandlerClientSetGameTimeReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = ClientSetGameTimeReq.parseFrom(payload);
        var player = session.getPlayer();
        var world = player.getWorld();
        int sequence =
                header == null || header.length == 0
                        ? 0
                        : PacketHead.parseFrom(header).getClientSequenceId();

        if (world.isTimeLocked()) {
            session.send(
                    new PacketClientSetGameTimeRsp(
                            player,
                            req.getClientGameTime(),
                            sequence,
                            Retcode.RET_PLAYER_TIME_LOCKED_VALUE));
            return;
        }

        long previousMinutes = world.getTotalGameTimeMinutes();
        long requestedMinutes = Integer.toUnsignedLong(req.getGameTime());
        long newTime = requestedMinutes * 1000L;
        world.changeTime(newTime);
        world.getHost().updatePlayerGameTime(newTime);
        world.getPlayers()
                .forEach(
                        member -> {
                            member.sendPacket(new PacketPlayerGameTimeNotify(member));
                            member.getQuestManager()
                                    .queueEvent(QuestContent.QUEST_CONTENT_GAME_TIME_TICK);
                        });

        session.send(new PacketClientSetGameTimeRsp(player, req.getClientGameTime(), sequence, 0));
        Grasscutter.getLogger()
                .info(
                        "Client game time updated for uid {}: {} -> {} total minutes.",
                        player.getUid(),
                        previousMinutes,
                        requestedMinutes);
    }
}
