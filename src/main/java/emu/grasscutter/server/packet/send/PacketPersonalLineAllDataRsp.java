package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.LockedPersonallineDataOuterClass.LockedPersonallineData;
import emu.grasscutter.net.proto.PersonalLineAllDataRspOuterClass;
import java.util.*;
import java.util.stream.Collectors;

public class PacketPersonalLineAllDataRsp extends BasePacket {

    public PacketPersonalLineAllDataRsp(
            Collection<GameMainQuest> gameMainQuestList, Set<Integer> unlockedLines) {
        super(PacketOpcodes.PersonalLineAllDataRsp);

        var proto = PersonalLineAllDataRspOuterClass.PersonalLineAllDataRsp.newBuilder();

        var questList =
                gameMainQuestList.stream()
                        .map(GameMainQuest::getChildQuests)
                        .map(Map::values)
                        .flatMap(Collection::stream)
                        .map(GameQuest::getSubQuestId)
                        .collect(Collectors.toSet());

        var miaoUnlocked = new ArrayList<Integer>();
        var miaoLocked = new ArrayList<Integer>();

        // 关键：不管玩家解不解锁，只要没在进行中的传说任务，统统填 locked
        // （客户端靠 locked 列表画卡片 + 快速体验入口）
        GameData.getPersonalLineDataMap().values().stream()
                .filter(i -> !questList.contains(i.getStartQuestId()))
                .forEach(
                        i -> {
                            proto.addLockedPersonalLineList(
                                    LockedPersonallineData.newBuilder()
                                            .setPersonalLineId(i.getId())
                                            .setLockReason(
                                                    LockedPersonallineData.LockReason.LockReason_QUEST)
                                            .build());
                            if (unlockedLines != null && unlockedLines.contains(i.getId())) {
                                miaoUnlocked.add(i.getId());
                            } else {
                                miaoLocked.add(i.getId());
                            }
                        });

        // [锚点] 把发出去的列表打印出来
        var miaoAll = new ArrayList<Integer>();
        GameData.getPersonalLineDataMap().keySet().forEach(miaoAll::add);
        Collections.sort(miaoAll);
        Collections.sort(miaoUnlocked);
        Collections.sort(miaoLocked);

        this.setData(proto);
    }
}
