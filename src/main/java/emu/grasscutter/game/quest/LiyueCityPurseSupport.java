package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.enums.QuestState;

/** Recover the initial MQ1013 purse only when it was never granted or spent. */
final class LiyueCityPurseSupport {
    private LiyueCityPurseSupport() {}

    static void restoreMissing(GameMainQuest main) {
        if (main.getParentQuestId() != 1013 || main.isFinished()) return;
        var entry = main.getChildQuestById(101301);
        if (entry == null || entry.getState() != QuestState.QUEST_STATE_FINISHED
                || main.getTalks() == null || !main.getTalks().containsKey(101301)
                || entry.getQuestData() == null || entry.getQuestData().getGainItems() == null
                || entry.getQuestData().getGainItems().stream().noneMatch(i ->
                    i.getId() == 100496 && i.getCount() == 50000)) return;
        var player = main.getOwner();
        if (player.getPlayerProgress().hasPlayerObtainedItemHistorically(100496)) return;
        for (int id = 1; id <= 3; id++) {
            var quest = main.getChildQuestById(101303 + (id - 1) * 4);
            var record = player.getPlayerProgress().getBargains().get(id);
            if (quest == null || (quest.getState() != QuestState.QUEST_STATE_UNSTARTED
                    && quest.getState() != QuestState.QUEST_STATE_UNFINISHED)
                    || record != null && (record.isFinished() || record.getResult() != null)) return;
        }
        var item = GameData.getItemDataMap().get(100496);
        if (item == null || item.getMaterialType() != MaterialType.MATERIAL_QUEST) return;
        var inventory = player.getInventory();
        synchronized (inventory) {
            if (inventory.getItemCountById(100496) != 0
                    || player.getPlayerProgress().hasPlayerObtainedItemHistorically(100496)) return;
            if (inventory.addItem(100496, 50000, ActionReason.QuestItem)) {
                player.save();
                Grasscutter.getLogger().info("Restored initial Liyue worker purse: uid={} amount=50000",
                        player.getUid());
            }
        }
    }
}
