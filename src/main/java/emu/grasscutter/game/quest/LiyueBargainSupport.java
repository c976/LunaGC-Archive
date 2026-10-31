package emu.grasscutter.game.quest;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BargainData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.proto.BargainResultTypeOuterClass.BargainResultType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketBargainOfferPriceRsp;

/** Reviewed MQ1013 workers only; keep other regions' bargaining behavior unchanged. */
public final class LiyueBargainSupport {
    private LiyueBargainSupport() {}

    private static BargainData reviewedData(int id) {
        if (id < 1 || id > 3) return null;
        var data = GameData.getBargainDataMap().get(id);
        return data != null && data.getQuestId() == 1013 && data.getItemId() == 100496
                ? data : null;
    }

    private static boolean active(Player player, int id) {
        var main = player.getQuestManager().getMainQuestById(1013);
        if (!QuestManager.isQuestingActive() || main == null || main.getParentQuestId() != 1013
                || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE) return false;
        var quest = main.getChildQuestById(101303 + (id - 1) * 4);
        return quest != null && quest.getState() == QuestState.QUEST_STATE_UNFINISHED;
    }

    /** Also serves a saved active stage whose start event was lost before login. */
    public static boolean prepare(Player player, int id) {
        if (reviewedData(id) == null || !active(player, id)) return false;
        var bargains = player.getPlayerProgress().getBargains();
        synchronized (bargains) {
            if (!bargains.containsKey(id)) player.getQuestManager().startBargain(id);
            var bargain = bargains.get(id);
            if (bargain.isFinished()) {
                var result = bargain.getResult();
                if (result == BargainResultType.BargainResultType_BARGAIN_COMPLETE_SUCC) {
                    player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_BARGAIN_SUCC, id, 0);
                } else if (result == BargainResultType.BargainResultType_BARGAIN_COMPLETE_FAIL) {
                    player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_BARGAIN_FAIL, id, 0);
                }
            }
        }
        checkPurse(player);
        return true;
    }

    /** Match the native worker talk gates and expose their material-payment fallback. */
    public static void checkPurse(Player player) {
        if (!player.getPlayerProgress().hasPlayerObtainedItemHistorically(100496)) return;
        int[] required = {20000, 25000, 15000};
        int funds = player.getInventory().getItemCountById(100496);
        for (int id = 1; id <= 3; id++) {
            if (reviewedData(id) == null || !active(player, id) || funds >= required[id - 1]) continue;
            var record = player.getPlayerProgress().getBargains().get(id);
            if (record == null) continue;
            synchronized (record) {
                if (record.isFinished()) continue;
                record.setResult(BargainResultType.BargainResultType_BARGAIN_COMPLETE_FAIL);
                record.setFinished(true);
            }
            player.save();
            player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ITEM_LESS_THAN_BARGAIN, id, 0);
        }
    }

    public static boolean handleOffer(Player player, BargainRecord bargain, int price) {
        int id = bargain.getBargainId();
        var data = reviewedData(id);
        if (data == null) return false;
        BargainResultType result;
        QuestContent event = null;
        synchronized (bargain) {
            if (!active(player, id) || bargain.isFinished() || price < 0) {
                result = bargain.getResult() != null ? bargain.getResult()
                        : BargainResultType.BargainResultType_BARGAIN_SINGLE_FAIL;
                player.sendPacket(new PacketBargainOfferPriceRsp(result, bargain,
                        bargain.isFinished() ? Retcode.RET_BARGAIN_FINISHED.getNumber()
                                : Retcode.RET_BARGAIN_NOT_ACTIVATED.getNumber()));
                return true;
            }
            var inventory = player.getInventory();
            synchronized (inventory) {
                if (inventory.getItemCountById(data.getItemId()) < price) {
                    result = BargainResultType.BargainResultType_BARGAIN_COMPLETE_FAIL;
                    event = QuestContent.QUEST_CONTENT_ITEM_LESS_THAN_BARGAIN;
                } else {
                    int previousMood = bargain.getCurrentMood();
                    result = bargain.applyOffer(price);
                    if (result == BargainResultType.BargainResultType_BARGAIN_SINGLE_FAIL) {
                        int mood = Math.max(data.getMoodLowLimit(),
                                previousMood - data.getSingleFailMoodDeduction());
                        bargain.setCurrentMood(mood);
                        if (mood <= data.getMoodLowLimit()) {
                            result = BargainResultType.BargainResultType_BARGAIN_COMPLETE_FAIL;
                            event = QuestContent.QUEST_CONTENT_BARGAIN_FAIL;
                        }
                    } else if (result == BargainResultType.BargainResultType_BARGAIN_COMPLETE_SUCC) {
                        if (data.isDeleteItem() && !inventory.removeItemById(data.getItemId(), price)) {
                            result = BargainResultType.BargainResultType_BARGAIN_COMPLETE_FAIL;
                            event = QuestContent.QUEST_CONTENT_ITEM_LESS_THAN_BARGAIN;
                        } else {
                            event = QuestContent.QUEST_CONTENT_BARGAIN_SUCC;
                        }
                    } else {
                        event = QuestContent.QUEST_CONTENT_BARGAIN_FAIL;
                    }
                }
            }
            bargain.setResult(result);
            if (result != BargainResultType.BargainResultType_BARGAIN_SINGLE_FAIL) {
                bargain.setFinished(true);
            }
            player.save();
            player.sendPacket(new PacketBargainOfferPriceRsp(result, bargain, 0));
        }
        // A single rejected offer changes mood; it is not proof that the purse is empty.
        if (event != null) player.getQuestManager().queueEvent(event, id, 0);
        return true;
    }
}
