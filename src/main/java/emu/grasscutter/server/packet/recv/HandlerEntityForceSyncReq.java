package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EntityForceSyncReqOuterClass.EntityForceSyncReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEntityForceSyncRsp;

/**
 * The client sends this when it believes an entity has drifted out of sync and wants the server to
 * accept a corrected motion. Left unanswered, the client simply keeps re-sending and the entity
 * stays desynced. Corrections are accepted for entities still in the scene and rejected otherwise,
 * with the authoritative motion returned so the client can snap back.
 */
@Opcodes(PacketOpcodes.EntityForceSyncReq)
public class HandlerEntityForceSyncReq extends PacketHandler {
    /** The entity the client named is no longer in the scene. */
    private static final int RET_ENTITY_NOT_EXIST = 1;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        EntityForceSyncReq req = EntityForceSyncReq.parseFrom(payload);

        Player player = session.getPlayer();
        Scene scene = player == null ? null : player.getScene();
        GameEntity entity = scene == null ? null : scene.getEntityById(req.getEntityId());

        if (entity == null) {
            session.send(
                    new PacketEntityForceSyncRsp(
                            RET_ENTITY_NOT_EXIST, req.getEntityId(), req.getSceneTime(), null));
            return;
        }

        // A sync that lands mid-transition must not move anything: the scene is still streaming in
        // and the client's idea of where things are is not authoritative yet.
        if (player.getSceneLoadState() == Player.SceneLoadState.LOADING) {
            session.send(
                    new PacketEntityForceSyncRsp(
                            RET_ENTITY_NOT_EXIST, req.getEntityId(), req.getSceneTime(), entity));
            return;
        }

        if (req.hasMotionInfo()) {
            var motion = req.getMotionInfo();
            entity.move(new Position(motion.getPos()), new Position(motion.getRot()));
            entity.setMotionState(motion.getState());
        }
        entity.setLastMoveSceneTimeMs(req.getSceneTime());

        session.send(new PacketEntityForceSyncRsp(req.getEntityId(), req.getSceneTime()));
    }
}
