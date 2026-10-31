package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ChallengeFinishTypeOuterClass.ChallengeFinishType;
import emu.grasscutter.net.proto.DungeonChallengeFinishNotifyOuterClass.DungeonChallengeFinishNotify;

public class PacketDungeonChallengeFinishNotify extends BasePacket {

    public PacketDungeonChallengeFinishNotify(WorldChallenge challenge) {
        super(PacketOpcodes.DungeonChallengeFinishNotify, true);

        boolean success = challenge.isSuccess();
        DungeonChallengeFinishNotify proto =
                DungeonChallengeFinishNotify.newBuilder()
                        .setChallengeIndex(challenge.getChallengeIndex())
                        .setIsSuccess(success)
                        .setChallengeRecordType(2)
                        .setTimeCost(Math.max(0, challenge.getFinishedTime()))
                        .setCurrentValue(Math.max(0, challenge.getScore().get()))
                        .setFinishType(
                                success
                                        ? ChallengeFinishType.ChallengeFinishType_SUCC
                                        : ChallengeFinishType.ChallengeFinishType_FAIL)
                        .build();

        this.setData(proto);
    }
}
