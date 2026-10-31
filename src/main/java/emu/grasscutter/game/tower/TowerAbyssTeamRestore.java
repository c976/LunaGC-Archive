package emu.grasscutter.game.tower;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import java.util.List;

/**
 * Rebuild abyss temporary parties from persisted GUIDs before mid-half swap / chamber enter.
 * {@code TeamManager.temporaryTeam} is transient and may be null while {@link TowerData#abyssTeamGuids}
 * still holds the selected parties — without this, {@code useTemporaryTeam(1)} NPEs or keeps the upper half.
 */
public final class TowerAbyssTeamRestore {
    private TowerAbyssTeamRestore() {}

    /** @return true if a temporary team list is available afterwards */
    public static boolean ensure(Player player) {
        if (player == null || player.getTowerManager() == null || player.getTeamManager() == null) {
            return false;
        }
        try {
            var tm = player.getTeamManager();
            var guids = player.getTowerManager().getAbyssTeamGuids();
            if (guids == null || guids.isEmpty()) {
                // Live temporary parties may still be intact even if TowerData was wiped.
                if (tm.getTemporaryTeamCount() >= 2) {
                    Grasscutter.getLogger()
                            .warn(
                                    "TowerAbyssTeamRestore uid={}: abyssTeamGuids empty, keeping live temporaryTeam count={}",
                                    player.getUid(),
                                    tm.getTemporaryTeamCount());
                    return true;
                }
                Grasscutter.getLogger()
                        .warn("TowerAbyssTeamRestore uid={}: abyssTeamGuids empty", player.getUid());
                return tm.hasTemporaryTeam();
            }
            tm.setupTemporaryTeam(guids);
            boolean ok = tm.hasTemporaryTeam();
            Grasscutter.getLogger()
                    .info(
                            "TowerAbyssTeamRestore uid={} teams={} sizes={} ok={}",
                            player.getUid(),
                            guids.size(),
                            guids.stream().map(List::size).toList(),
                            ok);
            return ok;
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("TowerAbyssTeamRestore uid={} failed: {}", player.getUid(), t.toString());
            return false;
        }
    }
}
