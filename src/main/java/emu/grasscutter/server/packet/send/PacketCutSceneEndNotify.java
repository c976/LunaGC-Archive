package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CutSceneEndNotifyOuterClass.CutSceneEndNotify;

public class PacketCutSceneEndNotify extends BasePacket {

    public PacketCutSceneEndNotify(int cutsceneId) {
        this(cutsceneId, 0);
    }

    public PacketCutSceneEndNotify(int cutsceneId, int retcode) {
        super(PacketOpcodes.CutSceneEndNotify);

        setData(
                CutSceneEndNotify.newBuilder()
                        .setCutsceneId(cutsceneId)
                        .setRetcode(retcode)
                        .build());
    }
}
