/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.game.avatar.AvatarGuidCodec
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.Opcodes
 *  emu.grasscutter.net.packet.PacketHandler
 *  emu.grasscutter.net.proto.AvatarExtraLevelUpgradeReqParser
 *  emu.grasscutter.net.proto.ParsedExtraLevelUpgradeReq
 *  emu.grasscutter.server.game.GameSession
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.avatar.AvatarExtraLevelHelper;
import emu.grasscutter.game.avatar.AvatarGuidCodec;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.AvatarExtraLevelUpgradeReqParser;
import emu.grasscutter.net.proto.ParsedExtraLevelUpgradeReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(value=27533)
public class HandlerAvatarExtraLevelUpgradeReq70
extends PacketHandler {
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) throws Exception {
        ParsedExtraLevelUpgradeReq parsedExtraLevelUpgradeReq = AvatarExtraLevelUpgradeReqParser.parseAnyStrict((byte[])byArray2);
        long l = AvatarGuidCodec.resolve((Player)gameSession.getPlayer(), (long)parsedExtraLevelUpgradeReq.getAvatarGuid());
        if (l != parsedExtraLevelUpgradeReq.getAvatarGuid()) {
            parsedExtraLevelUpgradeReq = new ParsedExtraLevelUpgradeReq(l, parsedExtraLevelUpgradeReq.getTargetLevel(), parsedExtraLevelUpgradeReq.getProtoKind());
        }
        AvatarExtraLevelHelper.handleExtraLevelRequest(gameSession.getPlayer(), 27533, parsedExtraLevelUpgradeReq, byArray2.length);
    }
}

