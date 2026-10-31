/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.server.packet.send.PacketAvatarDieAnimationEndRsp;
import emu.grasscutter.server.packet.send.PacketWorldPlayerDieNotify;

public final class DelayedAvatarSwap
implements Runnable {
    private final Scene scene;
    private final EntityAvatar dead;

    public DelayedAvatarSwap(Scene scene, EntityAvatar entityAvatar) {
        this.scene = scene;
        this.dead = entityAvatar;
    }

    @Override
    public void run() {
        try {
            if (this.dead == null || this.scene == null) {
                return;
            }
            Player player = this.dead.getPlayer();
            if (player == null) {
                return;
            }
            TeamManager teamManager = player.getTeamManager();
            EntityAvatar entityAvatar = teamManager.getCurrentAvatarEntity();
            if (entityAvatar != null && entityAvatar.getId() != this.dead.getId()) {
                return;
            }
            if (this.dead.isAlive()) {
                return;
            }
            int n = teamManager.getDeadAvatarReplacement();
            if (n >= 0 && n < teamManager.getActiveTeam().size()) {
                teamManager.setCurrentCharacterIndex(n);
                EntityAvatar entityAvatar2 = teamManager.getActiveTeam().get(n);
                this.scene.replaceEntity(this.dead, entityAvatar2);
            } else {
                this.scene.removeEntity(this.dead);
                player.sendPacket(new PacketWorldPlayerDieNotify(this.dead.getKilledType(), this.dead.getKilledBy()));
            }
            try {
                player.sendPacket(new PacketAvatarDieAnimationEndRsp((long)this.dead.getId(), 0));
            }
            catch (Throwable throwable) {}
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("DelayedAvatarSwap failed", throwable);
        }
    }
}
