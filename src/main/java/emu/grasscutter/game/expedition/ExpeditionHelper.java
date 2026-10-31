package emu.grasscutter.game.expedition;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;
import java.util.*;

/** Shared expedition helpers (state refresh + reward roll). */
public final class ExpeditionHelper {
    private ExpeditionHelper() {}

    /** Mark timed-out DOING expeditions as FINISH_WAIT_REWARD. */
    public static boolean refreshFinishedStates(Player player) {
        if (player == null || player.getExpeditionInfo() == null) {
            return false;
        }
        int now = Utils.getCurrentSeconds();
        boolean changed = false;
        for (ExpeditionInfo info : player.getExpeditionInfo().values()) {
            if (info.getState() == 1
                    && now - info.getStartTime() >= info.getHourTime() * 60 * 60) {
                info.setState(2);
                changed = true;
            }
        }
        return changed;
    }

    public static List<GameItem> rollRewards(Player player, ExpeditionInfo expInfo) {
        List<GameItem> items = new ArrayList<>();
        if (player == null || expInfo == null) {
            return items;
        }
        List<ExpeditionRewardDataList> lists =
                player
                        .getServer()
                        .getExpeditionSystem()
                        .getExpeditionRewardDataList()
                        .get(expInfo.getExpId());
        if (lists == null) {
            return items;
        }
        lists.stream()
                .filter(r -> r.getHourTime() == expInfo.getHourTime())
                .map(ExpeditionRewardDataList::getRewards)
                .forEach(items::addAll);
        return items;
    }

    /** All expedition spot IDs from ExpeditionDataExcel (cities 1–8). */
    public static List<Integer> allOpenExpeditionIds() {
        return List.of(
                101, 102, 103, 104, 105, 106, 201, 202, 203, 204, 205, 206, 301, 302, 303, 304, 305,
                306, 401, 402, 403, 404, 405, 406, 501, 502, 503, 504, 505, 506, 601, 602, 603, 604,
                605, 606, 701, 702, 703, 704, 705, 706, 801, 802, 803, 804, 805, 806);
    }
}
