package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.server.packet.send.PacketMapAreaChangeNotify;

/** [mapAreaId, state]: switches a MapAreaConfigData area, such as a mist or sandstorm. */
@QuestValueExec(QuestExec.QUEST_EXEC_CHANGE_MAP_AREA_STATE)
public final class ExecChangeMapAreaState extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr.length < 2) return false;
        int mapAreaId = Integer.parseInt(paramStr[0].trim());
        boolean open = Integer.parseInt(paramStr[1].trim()) != 0;
        var player = quest.getOwner();
        player.getMapAreaStates().put(mapAreaId, open);
        player.save();
        player.sendPacket(new PacketMapAreaChangeNotify(mapAreaId, open));
        Grasscutter.getLogger().debug("[MapArea] quest {} set area {} open={}", quest.getSubQuestId(), mapAreaId, open);
        return true;
    }
}
