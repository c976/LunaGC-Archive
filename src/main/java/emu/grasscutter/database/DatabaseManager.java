package emu.grasscutter.database;

import static emu.grasscutter.config.Configuration.DATABASE;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.max;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import com.mongodb.client.*;
import dev.morphia.*;
import dev.morphia.annotations.Entity;
import dev.morphia.mapping.*;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.Account;

public final class DatabaseManager {
    private static final int DEFAULT_COUNTER_START = 10_000;
    private static final String PLAYER_UID_COUNTER = "PlayerUid";
    private static final String PLAYER_UID_RESERVATIONS = "player_uid_reservations";
    private static Datastore gameDatastore;
    private static Datastore dispatchDatastore;

    public static Datastore getGameDatastore() {
        return gameDatastore;
    }

    public static Datastore getAccountDatastore() {
        if (Grasscutter.getRunMode() == ServerRunMode.HYBRID) return gameDatastore;
        else return dispatchDatastore;
    }

    public static MongoDatabase getGameDatabase() {
        return getGameDatastore().getDatabase();
    }

    public static void initialize() {
        // Initialize
        MongoClient gameMongoClient = MongoClients.create(DATABASE.game.connectionUri);

        // Set mapper options.
        MapperOptions mapperOptions =
                MapperOptions.builder().storeEmpties(true).storeNulls(false).build();

        // Create data store.
        gameDatastore =
                Morphia.createDatastore(gameMongoClient, DATABASE.game.collection, mapperOptions);

        // Map classes.
        var entities =
                Grasscutter.reflector.getTypesAnnotatedWith(Entity.class).stream()
                        .filter(
                                cls -> {
                                    Entity e = cls.getAnnotation(Entity.class);
                                    return e != null && !e.value().equals(Mapper.IGNORED_FIELDNAME);
                                })
                        .toArray(Class<?>[]::new);

        gameDatastore.getMapper().map(entities);

        // Ensure indexes for the game datastore
        ensureIndexes(gameDatastore);

        if (Grasscutter.getRunMode() != ServerRunMode.HYBRID) {
            MongoClient dispatchMongoClient = MongoClients.create(DATABASE.server.connectionUri);

            dispatchDatastore =
                    Morphia.createDatastore(dispatchMongoClient, DATABASE.server.collection, mapperOptions);
            dispatchDatastore.getMapper().map(new Class<?>[] {DatabaseCounter.class, Account.class});

            // Ensure indexes for dispatch datastore
            ensureIndexes(dispatchDatastore);
        }
    }

    /**
     * Ensures the database indexes exist and rebuilds them if there is an error with them
     *
     * @param datastore The datastore to ensure indexes on
     */
    private static void ensureIndexes(Datastore datastore) {
        try {
            datastore.ensureIndexes();
        } catch (MongoCommandException e) {
            Grasscutter.getLogger().info("Mongo index error: ", e);
            // Duplicate index error
            if (e.getCode() == 85) {
                // Drop all indexes and re add them
                MongoIterable<String> collections = datastore.getDatabase().listCollectionNames();
                for (String name : collections) {
                    datastore.getDatabase().getCollection(name).dropIndexes();
                }
                // Add back indexes
                datastore.ensureIndexes();
            }
        }
    }

    /**
     * Reserves and returns the next integer from a Mongo counter.
     *
     * <p>The increment happens in Mongo before this method returns. The optional floor only moves a
     * stale counter forward; it never moves one backwards. This remains correct when several server
     * processes share the same database.
     */
    static int reserveNextId(Datastore datastore, String counterName, int floor) {
        return DatabaseHelper.callSynchronousDatabaseWrite(
                () -> reserveNextIdAdmitted(datastore, counterName, floor));
    }

    private static int reserveNextIdAdmitted(Datastore datastore, String counterName, int floor) {
        if (floor < 0 || floor == Integer.MAX_VALUE) {
            throw new IllegalStateException("No more ids are available for " + counterName);
        }

        var counters = datastore.getDatabase().getCollection("counters");
        try {
            counters.insertOne(new Document("_id", counterName).append("count", (long) floor));
        } catch (MongoWriteException e) {
            if (!isDuplicateKey(e)) throw e;
        }

        // Old installations may have a counter behind already-created documents. Raising it first
        // makes migration safe without ever decreasing a counter another process has advanced.
        counters.updateOne(eq("_id", counterName), max("count", (long) floor));
        var counter =
                counters.findOneAndUpdate(
                        eq("_id", counterName),
                        inc("count", 1L),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (counter == null || !(counter.get("count") instanceof Number number)) {
            throw new IllegalStateException("Could not reserve id from counter " + counterName);
        }

        long reserved = number.longValue();
        if (reserved <= 0 || reserved > Integer.MAX_VALUE) {
            throw new IllegalStateException("Id counter exhausted for " + counterName);
        }
        return (int) reserved;
    }

    private static boolean isDuplicateKey(MongoWriteException e) {
        return e.getError() != null && e.getError().getCode() == 11000;
    }

    public static int getNextId(Class<?> c) {
        // Keep counters in the game database, which is where older AstaPS versions stored them.
        // Moving the account counter to the dispatch database would reset upgraded installations.
        return reserveNextId(getGameDatastore(), c.getSimpleName(), DEFAULT_COUNTER_START);
    }

    public static int getNextId(Object o) {
        return getNextId(o.getClass());
    }

    static int reserveNextPlayerUid(int highestExistingUid) {
        return reserveNextId(getGameDatastore(), PLAYER_UID_COUNTER, Math.max(0, highestExistingUid));
    }

    static boolean tryReservePlayerUid(int uid) {
        return DatabaseHelper.callSynchronousDatabaseWrite(() -> tryReservePlayerUidAdmitted(uid));
    }

    private static boolean tryReservePlayerUidAdmitted(int uid) {
        try {
            getGameDatabase()
                    .getCollection(PLAYER_UID_RESERVATIONS)
                    .insertOne(new Document("_id", uid));
            return true;
        } catch (MongoWriteException e) {
            if (isDuplicateKey(e)) return false;
            throw e;
        }
    }
}
