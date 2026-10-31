package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AvatarInfoOuterClass.AvatarInfo;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import sun.misc.Unsafe;

@ExtendWith(ServerResourceFixture.class)
@ResourceLock("GameData")
class UnlockAllExtraTalentsTest {
    private final Map<Map<?, ?>, Map<?, ?>> originals = new IdentityHashMap<>();
    private List<?> originalOpenStates;

    @BeforeEach
    void isolateResources() throws Exception {
        originalOpenStates = new ArrayList<>(GameData.getOpenStateList());
        GameData.getOpenStateList().clear();
        isolate(GameData.getScenePointsPerScene());
        isolate(GameData.getAvatarFlycloakDataMap());
        isolate(GameData.getAvatarTraceEffectDataMap());
        var fetterDataField = GameData.class.getDeclaredField("fetterDataMap");
        fetterDataField.setAccessible(true);
        isolate((Map<?, ?>) fetterDataField.get(null));
        isolate(GameData.getFetterDataEntries());
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void restoreResources() {
        List openStates = GameData.getOpenStateList();
        openStates.clear();
        openStates.addAll(originalOpenStates);
        for (var backup : originals.entrySet()) {
            Map resourceMap = backup.getKey();
            resourceMap.clear();
            resourceMap.putAll(backup.getValue());
        }
    }

    @Test
    void refreshesAndSavesAvatarsWithoutFettersAndNotifiesOnce() throws Exception {
        var player = playerWithAvatars();

        assertEquals(0, new UnlockAllCommand().createCommandLine(null, player).execute("all"));

        assertRefreshesEveryAvatarAndNotifiesOnce(player);
        assertTrue(player.getAvatars().getAvatarById(90001).getFetterList().isEmpty());
    }

    @Test
    void preservesFetterUnlocksWhileRefreshingAllAvatarsAndNotifyingOnce() throws Exception {
        var player = playerWithAvatars();
        GameData.getFetterDataEntries().put(90001, List.of(101, 102));
        player.getAvatars().getAvatarById(90001).getFetterList().add(101);

        assertEquals(0, new UnlockAllCommand().createCommandLine(null, player).execute("all"));

        assertRefreshesEveryAvatarAndNotifiesOnce(player);
        assertEquals(List.of(101, 102), player.getAvatars().getAvatarById(90001).getFetterList());
        assertTrue(player.getAvatars().getAvatarById(90002).getFetterList().isEmpty());
    }

    private static void assertRefreshesEveryAvatarAndNotifiesOnce(RecordingPlayer player) {
        for (var avatar : player.getAvatars()) {
            var recording = (RecordingAvatar) avatar;
            assertEquals(1, recording.recalculations);
            assertTrue(recording.forcedAbilityChange);
            assertEquals(1, recording.saves);
            assertTrue(recording.savedAfterRecalculation);
        }
        assertEquals(1, player.saves);
        assertEquals(
                1, player.packets.stream().filter(PacketAvatarDataNotify.class::isInstance).count());
    }

    private static RecordingPlayer playerWithAvatars() throws Exception {
        var player = new RecordingPlayer();
        var main = new RecordingAvatar(90001);
        player.getAvatars().getAvatars().put(90001, main);
        player.getAvatars().getAvatars().put(90002, new RecordingAvatar(90002));

        // Supply the active entity without constructing a scene or an ability manager.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var entity = (EntityAvatar) ((Unsafe) unsafeField.get(null)).allocateInstance(EntityAvatar.class);
        var avatarField = EntityAvatar.class.getDeclaredField("avatar");
        avatarField.setAccessible(true);
        avatarField.set(entity, main);
        player.getTeamManager().getActiveTeam().add(entity);
        return player;
    }

    private void isolate(Map<?, ?> resourceMap) {
        originals.put(resourceMap, new HashMap<>(resourceMap));
        resourceMap.clear();
    }

    private static final class RecordingPlayer extends Player {
        private final TeamManager team = new TeamManager(this);
        private final List<BasePacket> packets = new ArrayList<>();
        private int saves;

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public String getNickname() {
            return "TalentTest";
        }

        @Override
        public void sendPacket(BasePacket packet) {
            if (packet instanceof PacketAvatarDataNotify) {
                for (var avatar : getAvatars()) {
                    assertEquals(1, ((RecordingAvatar) avatar).saves, "Refresh must follow avatar saves");
                }
            }
            packets.add(packet);
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class RecordingAvatar extends Avatar {
        private final int avatarId;
        private int recalculations;
        private boolean forcedAbilityChange;
        private int saves;
        private boolean savedAfterRecalculation;

        private RecordingAvatar(int avatarId) {
            this.avatarId = avatarId;
        }

        @Override
        public int getAvatarId() {
            return avatarId;
        }

        @Override
        public void recalcStats(boolean forceSendAbilityChange) {
            recalculations++;
            forcedAbilityChange = forceSendAbilityChange;
        }

        @Override
        public void save() {
            saves++;
            savedAfterRecalculation = recalculations == 1;
        }

        @Override
        public AvatarInfo toProto() {
            return AvatarInfo.newBuilder().setAvatarId(avatarId).build();
        }
    }
}
