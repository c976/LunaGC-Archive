package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CutSceneBeginNotifyOuterClass.CutSceneBeginNotify;

public class PacketCutsceneBeginNotify extends BasePacket {

    public PacketCutsceneBeginNotify(int cutsceneId) {
        this(cutsceneId, false);
    }

    public PacketCutsceneBeginNotify(int cutsceneId, boolean waitForFinish) {
        super(PacketOpcodes.CutSceneBeginNotify, true);

        setData(CutSceneBeginNotify.newBuilder().setCutsceneId(cutsceneId).setIsWaitOthers(waitForFinish));
    }
}
