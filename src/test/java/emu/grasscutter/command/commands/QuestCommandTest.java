package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.game.quest.enums.QuestState;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public final class QuestCommandTest {
    @Test
    void listsUnfinishedQuestsWithoutDebugLogRegistration() {
        // A fresh account has no quest debug log entries, but still has active quests.
        var main = mainQuest(
                subQuest(35202, QuestState.QUEST_STATE_UNFINISHED),
                subQuest(35203, QuestState.QUEST_STATE_UNSTARTED));

        assertEquals(List.of(35202), ids(QuestCommand.activeQuests(List.of(main), false)));
    }

    @Test
    void listsAllActiveSubquestsInOrderAndExcludesInactiveStates() {
        var first = mainQuest(
                subQuest(48002, QuestState.QUEST_STATE_UNFINISHED),
                subQuest(48001, QuestState.QUEST_STATE_FINISHED));
        var second = mainQuest(
                subQuest(35202, QuestState.QUEST_STATE_UNFINISHED),
                subQuest(35203, QuestState.QUEST_STATE_FAILED));

        assertEquals(List.of(35202, 48002), ids(QuestCommand.activeQuests(List.of(first, second), false)));
        assertEquals(List.of(), QuestCommand.activeQuests(List.of(), false));
    }

    @Test
    void hidesNativeInternalQuestsUnlessAllIsRequested() {
        var hidden = new QuestData();
        var nativeSubquest = new MainQuestData.SubQuestData();
        nativeSubquest.setShowType("QUEST_HIDDEN");
        hidden.applyFrom(nativeSubquest);

        var visible = new QuestData();
        var main = mainQuest(
                subQuest(30302, QuestState.QUEST_STATE_UNFINISHED, hidden),
                subQuest(30600, QuestState.QUEST_STATE_UNFINISHED, visible));

        assertEquals(List.of(30600), ids(QuestCommand.activeQuests(List.of(main), false)));
        assertEquals(List.of(30302, 30600), ids(QuestCommand.activeQuests(List.of(main), true)));
    }

    @Test
    void treatsMissingTextMapPlaceholdersAsUnknown() {
        assertEquals(null, QuestCommand.readableQuestText("[N/A] 1965198481"));
        assertEquals(null, QuestCommand.readableQuestText(null));
        assertEquals(null, QuestCommand.readableQuestText(" "));
        assertEquals("Meet Lisa at the temple", QuestCommand.readableQuestText("Meet Lisa at the temple"));
        assertEquals("First line Second line", QuestCommand.readableQuestText("First line\nSecond line"));
    }

    private static List<Integer> ids(List<GameQuest> quests) {
        return quests.stream().map(GameQuest::getSubQuestId).collect(Collectors.toList());
    }

    private static GameMainQuest mainQuest(GameQuest... quests) {
        var main = new GameMainQuest();
        var children = new HashMap<Integer, GameQuest>();
        for (var quest : quests) {
            children.put(quest.getSubQuestId(), quest);
        }
        setField(main, "childQuests", children);
        return main;
    }

    private static GameQuest subQuest(int id, QuestState state) {
        return subQuest(id, state, null);
    }

    private static GameQuest subQuest(int id, QuestState state, QuestData data) {
        var quest = new GameQuest();
        setField(quest, "subQuestId", id);
        setField(quest, "questData", data);
        quest.setState(state);
        return quest;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
