package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.ability.NyxHelper;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilityInvocationsNotifyOuterClass.AbilityInvocationsNotify;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.AbilityInvocationsNotify)
public class HandlerAbilityInvocationsNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        AbilityInvocationsNotify notif = AbilityInvocationsNotify.parseFrom(payload);

        Player player = session.getPlayer();
        for (AbilityInvokeEntry entry : notif.getInvokesList()) {
            player.getAbilityManager().onAbilityInvoke(entry);
            player.getAbilityInvokeHandler().addEntry(entry.getForwardType(), entry);
        }
        player.getAbilityManager().flushPendingBoL();

        // The patched server drains Skirk's temporary mode resource on every
        // ability-invocation tick. Keep this best-effort so malformed or
        // partially initialized sessions do not break packet handling.
        try {
            if (player != null && player.getTeamManager() != null) {
                var avatar = player.getTeamManager().getCurrentAvatarEntity();
                if (avatar != null && NyxHelper.isSkirkEntity(avatar)) {
                    SkirkCunningBridge.tickModeDrain(player, avatar);
                }
            }
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger().warn("Skirk drain tick hook failed: {}", t.toString());
        }
    }
}
