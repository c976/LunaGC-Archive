package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.game.Account;
import java.util.Locale;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Admin account commands write only the fields they change, encoded as Morphia stores them. */
class AccountFieldUpdateTest {
    private static MongoClient client;
    private static Datastore datastore;

    @BeforeAll
    static void mapper() {
        // Never contacted: encoding needs only the mapper.
        client = MongoClients.create("mongodb://127.0.0.1:1");
        datastore = Morphia.createDatastore(
                client, "test", MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
        datastore.getMapper().map(Account.class);
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @Test
    void banSetsOnlyBanFieldsAndUnsetsTheIpProvenance() {
        var account = account();
        account.setBanReason("cheating");
        account.setBanEndTime(2_000_000_000);
        account.setBanStartTime(1_700_000_000);
        account.setBanned(true);

        var update = DatabaseHelper.accountFieldUpdate(datastore, account, DatabaseHelper.BAN_FIELDS);

        var set = update.get("$set", Document.class);
        assertEquals(Set.of("banReason", "banEndTime", "banStartTime", "isBanned"), set.keySet());
        assertEquals(true, set.get("isBanned"));
        assertEquals("cheating", set.get("banReason"));
        assertEquals(Set.of("bannedByIp"), update.get("$unset", Document.class).keySet());
    }

    @Test
    void unbanClearsReasonAndIpProvenance() {
        var account = account();
        account.setBanEndTime(0);
        account.setBanStartTime(0);

        var update = DatabaseHelper.accountFieldUpdate(datastore, account, DatabaseHelper.BAN_FIELDS);

        assertEquals(false, update.get("$set", Document.class).get("isBanned"));
        assertEquals(Set.of("banReason", "bannedByIp"), update.get("$unset", Document.class).keySet());
    }

    @Test
    void localeIsWrittenAsMorphiaStoresIt() {
        var account = account();
        account.setLocale(Locale.forLanguageTag("fr-FR"));

        var update = DatabaseHelper.accountFieldUpdate(datastore, account, "locale");

        assertEquals(
                datastore.getMapper().toDocument(account).get("locale"),
                update.get("$set", Document.class).get("locale"));
        assertNull(update.get("$unset"));
    }

    private static Account account() {
        var account = new Account();
        account.setId("10001");
        account.setUsername("traveler");
        account.setToken("newer-login-token");
        return account;
    }
}
