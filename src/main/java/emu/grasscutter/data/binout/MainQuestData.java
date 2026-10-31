package emu.grasscutter.data.binout;

import dev.morphia.annotations.Entity;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.quest.enums.LogicType;
import emu.grasscutter.game.quest.enums.QuestType;
import java.util.*;
import lombok.Data;

public class MainQuestData {
    private int id;
    private int ICLLDPJFIMA;
    private int series;
    private QuestType type;

    private long titleTextMapHash;
    private int[] suggestTrackMainQuestList;
    private int[] rewardIdList;

    private SubQuestData[] subQuests;
    private List<TalkData> talks;
    /**
     * Nothing reads this: it exists so Gson has somewhere to put the field. It is typed as strings
     * because the ids outgrew {@code long} in the 4.5-era resources, and a value past
     * {@link Long#MAX_VALUE} makes Gson throw while parsing QuestData.json, which fails the whole
     * quest load rather than just this field.
     */
    private List<String> preloadLuaList;

    public int getId() {
        return id;
    }

    public int getSeries() {
        return series;
    }

    public QuestType getType() {
        return type;
    }

    public long getTitleTextMapHash() {
        return titleTextMapHash;
    }

    public int[] getSuggestTrackMainQuestList() {
        return suggestTrackMainQuestList;
    }

    public int[] getRewardIdList() {
        return rewardIdList;
    }

    public SubQuestData[] getSubQuests() {
        return subQuests;
    }

    public List<TalkData> getTalks() {
        return talks;
    }

    public void onLoad() {
        if (this.talks == null) this.talks = new ArrayList<>();
        if (this.subQuests == null) this.subQuests = new SubQuestData[0];

        this.talks = this.talks.stream().filter(Objects::nonNull).toList();
        // Apply talk data to the quest talk map.
        this.talks.forEach(talkData -> GameData.getQuestTalkMap().put(talkData.getId(), this.getId()));
        // Apply additional sub-quest data to sub-quests.
        Arrays.stream(this.subQuests)
                .forEach(
                        quest -> {
                            var questData = GameData.getQuestDataMap().get(quest.getSubId());
                            if (questData != null) questData.applyFrom(quest);
                        });
    }

    @Data
    public static class SubQuestData {
        private int subId;
        private int order;
        private String showType;
        private boolean isMpBlock;
        private boolean isRewind, finishParent;

        // Materialized BinOutput preserves reviewed prerequisites, including 36301.
        private List<QuestData.QuestAcceptCondition> acceptCond;
        private LogicType acceptCondComb;
        // The flattened QuestExcel row may lose this multi-objective rule.
        private LogicType finishCondComb;
        private LogicType failCondComb;

        // Per-subquest BinOutput actions can be absent from the flattened Excel export.
        private List<QuestData.QuestExecParam> beginExec;
        private List<QuestData.QuestExecParam> finishExec;
        private List<QuestData.QuestExecParam> failExec;

        // Materialized BinOutput can retain rewards missing from QuestExcel.
        private List<ItemParamData> gainItems;
    }

    @Data
    @Entity
    public static class TalkData {
        private int id;
        private String heroTalk;

        public TalkData() {}

        public TalkData(int id, String heroTalk) {
            this.id = id;
            this.heroTalk = heroTalk;
        }
    }
}
