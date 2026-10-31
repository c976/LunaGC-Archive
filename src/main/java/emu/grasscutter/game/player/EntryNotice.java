package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_INFO;

import emu.grasscutter.BuildConfig;
import emu.grasscutter.server.packet.send.PacketAntiAddictNotify;
import java.util.Objects;

/**
 * The message box shown once the player is in the world: a welcome the first time, and afterwards
 * a notice when the server has been rebuilt since the player was last in. The live announcements
 * follow once, 30 seconds later.
 *
 * <p>The client only renders an AntiAddictNotify once it is fully in the world; sent during the
 * login handshake it is swallowed. It used to hang off TowerAllDataReq, whose 7.1 CmdId is not
 * known, so it never went out; PostEnterSceneReq arrives after every scene entry, and this sends
 * at most once per login whichever request gets here first.
 */
public final class EntryNotice {
    static final long BANNER_DELAY_MS = 30_000;

    private EntryNotice() {}

    public static void sendOnce(Player player) {
        if (player == null || player.isEntryNoticeChecked()) return;
        player.setEntryNoticeChecked(true);

        if (player.isPendingWelcomeNotice()) {
            // The first world entry includes the 7.1 birth cinematic. A modal
            // AntiAddictNotify here interrupts the client-controlled intro.
            // Keep update notices and non-modal announcements on later logins.
            player.setPendingWelcomeNotice(false);
        } else if (!Objects.equals(player.getLastSeenBuildHash(), BuildConfig.GIT_HASH)) {
            player.sendPacket(
                    new PacketAntiAddictNotify(
                            1, "The server has been updated and restarted since you were last online."));
        }

        player.setLastSeenBuildHash(BuildConfig.GIT_HASH);

        // The welcome banner goes out once per login, half a minute after the player is in.
        var server = player.getServer();
        if (server != null && server.getAnnouncementSystem() != null) {
            int ticks = (int) Math.ceil(BANNER_DELAY_MS / (double) Math.max(1, GAME_INFO.tickRateMs));
            server.getScheduler()
                    .scheduleDelayedTask(
                            () -> {
                                if (player.isOnline()) server.getAnnouncementSystem().sendActive(player);
                            },
                            Math.max(1, ticks));
        }
    }
}
