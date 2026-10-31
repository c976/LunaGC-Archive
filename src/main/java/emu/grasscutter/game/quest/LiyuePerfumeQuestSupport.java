package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketCombineFormulaDataNotify;

/** MQ1012's task recipes need explicit unlock packets when the login recipe list is unavailable. */
public final class LiyuePerfumeQuestSupport {
    private LiyuePerfumeQuestSupport() {}

    public static void sendFormulas(Player player) {
        if (!QuestManager.isQuestingActive()) return;
        var main = player.getQuestManager().getMainQuestById(1012);
        if (main == null || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE) return;
        var entry = main.getChildQuestById(101214);
        var grinding = main.getChildQuestById(101210);
        if (entry == null || entry.getState() != QuestState.QUEST_STATE_FINISHED
                || grinding == null || grinding.getState() != QuestState.QUEST_STATE_UNFINISHED) return;
        // Validate the task recipes before exposing them; unrelated combine types are unchanged.
        for (int offset = 0; offset < 3; offset++) {
            var recipe = GameData.getCombineDataMap().get(16003 + offset);
            if (recipe == null || recipe.getCombineType() != 99
                    || recipe.getResultItemId() != 100490 + offset
                    || recipe.getResultItemCount() != 1
                    || recipe.getMaterialItems() == null || recipe.getMaterialItems().size() != 1
                    || recipe.getMaterialItems().get(0).getId() != 100487 + offset
                    || recipe.getMaterialItems().get(0).getCount() != 1) return;
        }
        // Send even when the server already considers these unlocked: the client may have
        // never received CombineDataNotify, whose 7.1 CmdId is currently unknown.
        for (int id = 16003; id <= 16005; id++) {
            player.getUnlockedCombines().add(id);
            player.sendPacket(new PacketCombineFormulaDataNotify(id));
        }
        Grasscutter.getLogger().info(
                "Sent Liyue perfume recipe unlocks: uid={} recipes=[16003,16004,16005]",
                player.getUid());
    }
}
