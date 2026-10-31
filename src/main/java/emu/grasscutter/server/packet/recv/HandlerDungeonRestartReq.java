package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DungeonRestartGuard;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DungeonRestartReq)
public class HandlerDungeonRestartReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (!DungeonRestartGuard.mayRestart(player, payload)) {
            Grasscutter.getLogger().warn(
                    "DungeonRestartReq denied uid={} scene={}: incomplete or unclaimed domain",
                    player == null ? 0 : player.getUid(),
                    player == null ? 0 : player.getSceneId());
            return;
        }
        Grasscutter.getLogger().debug("DungeonRestartReq uid={} dungeon={} scene={}",
                player.getUid(),
                player.getScene().getDungeonManager().getDungeonData().getId(),
                player.getSceneId());
        player.getServer().getDungeonSystem().restartDungeon(player);
        player.sendPacket(new BasePacket(PacketOpcodes.DungeonRestartRsp));
    }
}
