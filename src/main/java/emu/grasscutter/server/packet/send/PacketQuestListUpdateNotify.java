package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestListUpdateNotifyOuterClass.QuestListUpdateNotify;

public class PacketQuestListUpdateNotify extends BasePacket {
    public PacketQuestListUpdateNotify(GameQuest quest) {
        super(PacketOpcodes.QuestListUpdateNotify);

        var builder = QuestListUpdateNotify.newBuilder();
        // Statue activation quest 303 is processed even when general questing is disabled.
        if (QuestManager.isQuestVisible(quest)
                || quest.getState() == QuestState.QUEST_STATE_FINISHED
                || quest.getMainQuestId() == 303) {
            builder.addQuestList(quest.toProto());
        }
        this.setData(builder.build());
    }
}
