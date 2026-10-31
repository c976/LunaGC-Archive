package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EntityForceSyncRspOuterClass.EntityForceSyncRsp;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;

public class PacketEntityForceSyncRsp extends BasePacket {

    /** Acknowledges a force-sync the server accepted. */
    public PacketEntityForceSyncRsp(int entityId, int sceneTime) {
        super(PacketOpcodes.EntityForceSyncRsp);

        this.setData(
                EntityForceSyncRsp.newBuilder()
                        .setRetcode(0)
                        .setEntityId(entityId)
                        .setSceneTime(sceneTime));
    }

    /**
     * Rejects a force-sync and hands the client the authoritative motion to snap back to.
     *
     * @param entity the entity the client tried to move, or {@code null} if it is already gone
     */
    public PacketEntityForceSyncRsp(int retcode, int entityId, int sceneTime, GameEntity entity) {
        super(PacketOpcodes.EntityForceSyncRsp);

        var proto =
                EntityForceSyncRsp.newBuilder()
                        .setRetcode(retcode)
                        .setEntityId(entityId)
                        .setSceneTime(sceneTime);

        if (entity != null) {
            proto.setFailMotion(
                    MotionInfo.newBuilder()
                            .setPos(entity.getPosition().toProto())
                            .setRot(entity.getRotation().toProto())
                            .setSpeed(Vector.newBuilder())
                            .setState(entity.getMotionState()));
        }

        this.setData(proto);
    }
}
