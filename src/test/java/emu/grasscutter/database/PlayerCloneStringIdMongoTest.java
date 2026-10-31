package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.IndexOptions;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in mapper regression; never connects to the configured server database. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ASTAPS_CLONE_ID_MONGO_TEST", matches = "1")
class PlayerCloneStringIdMongoTest {
    @Test
    void clonedStringIdCanBeReadAndSavedWithoutDuplicatingTheOwner() {
        String databaseName = "astaps_clone_id_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var client =
                MongoClients.create("mongodb://localhost:27018/?serverSelectionTimeoutMS=3000")) {
            var database = client.getDatabase(databaseName);
            try {
                var datastore = Morphia.createDatastore(client, databaseName);
                datastore.getMapper().map(StringIdHome.class);
                var homes = database.getCollection("homes");
                homes.createIndex(new Document("ownerUid", 1), new IndexOptions().unique(true));
                var source =
                        new Document("_id", "source-home")
                                .append("ownerUid", 10001L)
                                .append("level", 10);
                homes.insertOne(source);

                // The old clone path allocates a BSON ObjectId. Reading it as a String @Id and
                // saving attempts an insert under that string, colliding with the unique owner.
                var broken = new Document(source);
                broken.remove("_id");
                broken.put("ownerUid", 900100L);
                homes.insertOne(broken);
                var brokenHome =
                        datastore
                                .find(StringIdHome.class)
                                .filter(
                                        dev.morphia.query.experimental.filters.Filters.eq(
                                                "ownerUid", 900100L))
                                .first();
                assertNotNull(brokenHome);
                assertThrows(MongoWriteException.class, () -> datastore.save(brokenHome));

                var target = PlayerCloneService.cloneOwnedDocument(source, "ownerUid", 900101);
                homes.insertOne(target);
                var home =
                        datastore
                                .find(StringIdHome.class)
                                .filter(
                                        dev.morphia.query.experimental.filters.Filters.eq(
                                                "ownerUid", 900101L))
                                .first();
                assertNotNull(home);
                assertEquals(target.get("_id"), home.id);
                home.level = 11;
                datastore.save(home);

                assertEquals(1, homes.countDocuments(eq("ownerUid", 900101)));
                var saved = homes.find(eq("_id", target.get("_id"))).first();
                assertNotNull(saved);
                assertEquals(11, saved.getInteger("level"));
                assertEquals(source, homes.find(eq("_id", "source-home")).first());
            } finally {
                database.drop();
            }
        }
    }

    // Match GameHome's String @Id and unique owner constraint without its game resource graph or
    // constructor requirements. No production model or installed constructor patch is needed.
    @Entity(value = "homes", useDiscriminator = false)
    public static class StringIdHome {
        @Id String id;
        long ownerUid;
        int level;

        public StringIdHome() {}
    }
}
