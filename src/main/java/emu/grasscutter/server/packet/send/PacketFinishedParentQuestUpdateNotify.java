package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FinishedParentQuestUpdateNotifyOuterClass.FinishedParentQuestUpdateNotify;
import java.util.List;

public class PacketFinishedParentQuestUpdateNotify extends BasePacket {

    public PacketFinishedParentQuestUpdateNotify(GameMainQuest quest) {
        super(PacketOpcodes.FinishedParentQuestUpdateNotify);

        var proto = FinishedParentQuestUpdateNotify.newBuilder();
        if (shouldShow(quest)) {
            proto.addParentQuestList(quest.toProto(true));
        }

        this.setData(proto.build());
    }

    /**
     * Tells the client a main quest is finished without the server holding any data for it.
     *
     * <p>Region gates are quest-driven, and a region released after this server's resource set was
     * cut has no quest data here at all - so {@code /quest finish} cannot reach it. `ParentQuest`
     * only needs an id and the finished flag for the client to believe it, which is enough to test
     * whether a barrier is quest-gated. Nothing is persisted: this lasts until the player relogs.
     */
    public PacketFinishedParentQuestUpdateNotify(int... mainQuestIds) {
        super(PacketOpcodes.FinishedParentQuestUpdateNotify);

        var proto = FinishedParentQuestUpdateNotify.newBuilder();
        for (int id : mainQuestIds) {
            proto.addParentQuestList(
                    emu.grasscutter.net.proto.ParentQuestOuterClass.ParentQuest.newBuilder()
                            .setParentQuestId(id)
                            .setIsFinished(true)
                            .setParentQuestState(
                                    emu.grasscutter.game.quest.enums.ParentQuestState
                                            .PARENT_QUEST_STATE_FINISHED
                                            .getValue())
                            .build());
        }
        this.setData(proto);
    }

    public PacketFinishedParentQuestUpdateNotify(List<GameMainQuest> quests) {
        super(PacketOpcodes.FinishedParentQuestUpdateNotify);

        var proto = FinishedParentQuestUpdateNotify.newBuilder();

        for (GameMainQuest mainQuest : quests) {
            if (shouldShow(mainQuest)) {
                proto.addParentQuestList(mainQuest.toProto(true));
            }
        }
        proto.build();
        this.setData(proto);
    }

    /**
     * With questing off, quest events still start main quests. An unfinished parent sent then is
     * tracked by the client (the prologue's "talk to Paimon", with a return-to-quest button) though
     * the quest log is empty, since its sub-quests are held back too.
     */
    private static boolean shouldShow(GameMainQuest quest) {
        return QuestManager.isQuestingActive()
                || quest.isAddedByCommand()
                || quest.getState() == ParentQuestState.PARENT_QUEST_STATE_FINISHED;
    }
}
