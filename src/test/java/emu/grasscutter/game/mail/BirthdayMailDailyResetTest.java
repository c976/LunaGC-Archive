package emu.grasscutter.game.mail;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.config.ConfigContainer.GameOptions.BirthdayMailOptions;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerBirthday;
import emu.grasscutter.game.props.WatcherTriggerType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sun.misc.Unsafe;

@ExtendWith(ServerResourceFixture.class)
final class BirthdayMailDailyResetTest {
    private BirthdayMailOptions originalOptions;
    private LocalDate today;

    @BeforeEach
    void configureMail() {
        originalOptions = GAME_OPTIONS.birthdayMail;
        GAME_OPTIONS.birthdayMail = new BirthdayMailOptions();
        today = LocalDate.now(ZoneId.systemDefault());
    }

    @AfterEach
    void restoreMailOptions() {
        GAME_OPTIONS.birthdayMail = originalOptions;
    }

    @Test
    void dailyResetSendsBirthdayGiftBeforeRecordingTheReset() throws Exception {
        var player = playerWithBirthday(today);
        int previousReset = player.getLastDailyReset();

        doDailyReset(player);

        assertEquals(1, player.mail.messages.size());
        var mail = player.mail.messages.get(0);
        assertEquals("Server", mail.mailContent.sender);
        assertFalse(mail.mailContent.title.isBlank());
        assertTrue(mail.mailContent.content.contains(player.getNickname()));
        assertEquals(GAME_OPTIONS.birthdayMail.gifts.length, mail.itemList.size());
        for (int index = 0; index < mail.itemList.size(); index++) {
            var gift = GAME_OPTIONS.birthdayMail.gifts[index];
            assertEquals(gift.itemId, mail.itemList.get(index).itemId);
            assertEquals(gift.count, mail.itemList.get(index).itemCount);
        }
        assertEquals(GAME_OPTIONS.birthdayMail.expireDays * 86400L,
                mail.expireTime - mail.sendTime, 1L);
        assertEquals(today.getYear(), player.getLastBirthdayMailYear());
        assertEquals(1, player.saves);
        assertEquals(previousReset, player.mail.resetAtSend);
        assertEquals(today.getYear(), player.yearAtSave);
        assertEquals(previousReset, player.resetAtSave);
        assertEquals(today, resetDate(player));
        assertEquals(1, player.battlePass.dailyResets);
        assertEquals(1, player.battlePass.loginMissions);
        assertEquals(0, player.getResinBuyCount());
    }

    @Test
    void repeatedDailyResetOnTheSameDayDoesNotSendAgain() throws Exception {
        var player = playerWithBirthday(today);

        doDailyReset(player);
        doDailyReset(player);

        assertEquals(1, player.mail.messages.size());
        assertEquals(1, player.saves);
        assertEquals(1, player.battlePass.dailyResets);
    }

    @Test
    void theSameLoadedPlayerReceivesMailWhenItsDailyResetBecomesStale() throws Exception {
        var player = playerWithBirthday(today);
        player.setLastDailyReset((int) today.atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
        doDailyReset(player);
        assertTrue(player.mail.messages.isEmpty());
        assertEquals(0, player.battlePass.dailyResets);

        // Simulate the previous reset belonging to the preceding date without reconstructing Player.
        player.setLastDailyReset((int) today.minusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
        doDailyReset(player);

        assertEquals(1, player.mail.messages.size());
        assertEquals(1, player.battlePass.dailyResets);
        assertEquals(today.getYear(), player.getLastBirthdayMailYear());
        assertEquals(today, resetDate(player));
    }

    @Test
    void anotherDailyResetOnTheSamePlayerStillHonorsItsMailedYear() throws Exception {
        var player = playerWithBirthday(today);
        doDailyReset(player);

        player.setLastDailyReset((int) today.minusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
        doDailyReset(player);

        assertEquals(1, player.mail.messages.size());
        assertEquals(1, player.saves);
        assertEquals(2, player.battlePass.dailyResets);
        assertEquals(today.getYear(), player.getLastBirthdayMailYear());
        assertEquals(today, resetDate(player));
    }

    @Test
    void birthdayAlreadyMailedThisYearIsNotSentWhenResetRuns() throws Exception {
        var player = playerWithBirthday(today);
        player.setLastBirthdayMailYear(today.getYear());

        doDailyReset(player);

        assertTrue(player.mail.messages.isEmpty());
        assertEquals(0, player.saves);
        assertEquals(1, player.battlePass.dailyResets);
        assertEquals(today, resetDate(player));
    }

    @Test
    void previousYearsMailDoesNotPreventThisYearsBirthdayGift() throws Exception {
        var player = playerWithBirthday(today);
        player.setLastBirthdayMailYear(today.getYear() - 1);

        doDailyReset(player);

        assertEquals(1, player.mail.messages.size());
        assertEquals(today.getYear(), player.getLastBirthdayMailYear());
    }

    @Test
    void nonBirthdayDailyResetDoesNotSendMailOrMarkTheYear() throws Exception {
        var player = playerWithBirthday(today.plusDays(1));

        doDailyReset(player);

        assertTrue(player.mail.messages.isEmpty());
        assertEquals(0, player.getLastBirthdayMailYear());
        assertEquals(0, player.saves);
        assertEquals(1, player.battlePass.dailyResets);
        assertEquals(today, resetDate(player));
    }

    @Test
    void disabledBirthdayMailDoesNotSendWhileDailyResetStillCompletes() throws Exception {
        var player = playerWithBirthday(today);
        GAME_OPTIONS.birthdayMail.enabled = false;

        doDailyReset(player);

        assertTrue(player.mail.messages.isEmpty());
        assertEquals(0, player.getLastBirthdayMailYear());
        assertEquals(0, player.saves);
        assertEquals(today, resetDate(player));
    }

    private RecordingPlayer playerWithBirthday(LocalDate birthday) throws Exception {
        // Keep the real reset/mail code, but bypass resource-backed Player construction and DB writes.
        var player = allocate(RecordingPlayer.class);
        player.birthday = new PlayerBirthday(birthday.getDayOfMonth(), birthday.getMonthValue());
        player.mail = new RecordingMailHandler(player);
        player.battlePass = new RecordingBattlePassManager();
        var account = new Account();
        account.setLocale(Locale.US);
        player.setAccount(account);
        player.setResinBuyCount(3);
        player.setLastDailyReset((int) today.minusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
        return player;
    }

    private static LocalDate resetDate(Player player) {
        return java.time.Instant.ofEpochSecond(player.getLastDailyReset())
                .atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static void doDailyReset(Player player) throws Exception {
        Method method = Player.class.getDeclaredMethod("doDailyReset");
        method.setAccessible(true);
        method.invoke(player);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingPlayer extends Player {
        private PlayerBirthday birthday;
        private RecordingMailHandler mail;
        private RecordingBattlePassManager battlePass;
        private int saves;
        private int yearAtSave;
        private int resetAtSave;

        @Override
        public PlayerBirthday getBirthday() {
            return birthday;
        }

        @Override
        public String getNickname() {
            return "Birthday Traveler";
        }

        @Override
        public MailHandler getMailHandler() {
            return mail;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return battlePass;
        }

        @Override
        public boolean setForgePoints(int value) {
            return true;
        }

        @Override
        public void save() {
            saves++;
            yearAtSave = getLastBirthdayMailYear();
            resetAtSave = getLastDailyReset();
        }
    }

    private static final class RecordingMailHandler extends MailHandler {
        private final List<Mail> messages = new ArrayList<>();
        private int resetAtSend;

        private RecordingMailHandler(Player player) {
            super(player);
        }

        @Override
        public void sendMail(Mail mail) {
            resetAtSend = getPlayer().getLastDailyReset();
            messages.add(mail);
        }
    }

    private static final class RecordingBattlePassManager extends BattlePassManager {
        private int dailyResets;
        private int loginMissions;

        @Override
        public void resetDailyMissions() {
            dailyResets++;
        }

        @Override
        public void triggerMission(WatcherTriggerType type) {
            assertEquals(WatcherTriggerType.TRIGGER_LOGIN, type);
            loginMissions++;
        }

        @Override
        public void resetWeeklyMissions() {}
    }
}
