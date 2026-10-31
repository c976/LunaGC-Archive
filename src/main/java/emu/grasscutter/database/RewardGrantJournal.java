package emu.grasscutter.database;

import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bson.Document;

/**
 * Durable, compare-and-set claim receipts for later reconciliation.
 *
 * <p>PREPARED means only the intent was saved; CONFIRMED means an irreversible
 * payment or claim confirmation was acknowledged; COMPLETED means the caller
 * reported delivery. A CONFIRMED receipt is not proof that every reward was
 * persisted. Never blindly replay unresolved receipts: virtual rewards and
 * merged material stacks are not inherently idempotent.
 *
 * <p>This class provides a journal API for reward sources; integration with
 * individual claim flows is separate. CONFIRMED receipts require inspection:
 * confirmation alone does not guarantee complete reward delivery.
 */
public final class RewardGrantJournal {
    public enum Source {
        MAIL, SHOP, BATTLE_PASS, DOMAIN, WORLD_BOSS, WORLD_CHEST, BLOSSOM, TRANSPOINT, GACHA
    }

    public enum Stage {
        PREPARED, CONFIRMED, COMPLETED, ABORTED
    }

    public record Key(Source source, int uid, String operationId) {
        public Key {
            Objects.requireNonNull(source, "source");
            if (uid <= 0 || operationId == null || !operationId.matches("[A-Za-z0-9._:-]{1,128}")) {
                throw new IllegalArgumentException("Invalid reward claim identity");
            }
        }

        public String storageId() {
            return source.name().toLowerCase(Locale.ROOT) + ":" + uid + ":" + operationId;
        }
    }

    /** Immutable requested rewards. This is audit data, not a replay command. */
    public record Reward(int itemId, int count, int level) {
        public Reward {
            if (itemId <= 0 || count <= 0 || level < 0) {
                throw new IllegalArgumentException("Invalid reward descriptor");
            }
        }
    }

    /** Price debits are recorded for inspection, never automatically replayed. */
    public record Debit(int itemId, int count) {
        public Debit {
            if (itemId <= 0 || count <= 0) {
                throw new IllegalArgumentException("Invalid payment descriptor");
            }
        }
    }

    public record Receipt(Key key, Stage stage, List<Reward> rewards, List<Debit> debits) {
        public Receipt(Key key, Stage stage, List<Reward> rewards) {
            this(key, stage, rewards, List.of());
        }

        public Receipt {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(stage, "stage");
            // Only first-activation discovery rewards allow an explicit zero payout.
            // Other paid/claimable sources must not silently settle an empty batch.
            if (rewards == null || rewards.size() > 10_000
                    || (rewards.isEmpty() && key.source() != Source.TRANSPOINT)) {
                throw new IllegalArgumentException("Invalid reward batch");
            }
            if (debits == null || debits.size() > 10_000) {
                throw new IllegalArgumentException("Invalid payment batch");
            }
            rewards = List.copyOf(rewards);
            debits = List.copyOf(debits);
        }
    }

    interface Store {
        boolean insert(Receipt receipt);

        boolean transition(Key key, Stage from, Stage to);

        List<Receipt> unresolved();

        boolean completed(Key key);
    }

    private final Store store;

    public RewardGrantJournal() {
        this(new MongoStore());
    }

    RewardGrantJournal(Store store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Persist intent before an irreversible operation. A duplicate key must
     * never replace the original receipt or permit a second attempt.
     */
    public boolean prepare(Key key, List<Reward> rewards) {
        return prepare(key, rewards, List.of());
    }

    public boolean prepare(Key key, List<Reward> rewards, List<Debit> debits) {
        return store.insert(new Receipt(key, Stage.PREPARED, rewards, debits));
    }

    /** Confirm only after the caller has accepted its irreversible action. */
    public void confirm(Key key) {
        advance(key, Stage.PREPARED, Stage.CONFIRMED);
    }

    /**
     * End a PREPARED attempt only if the caller has positively established that
     * no payment, claim flag or inventory write took place. Never call this from
     * an exception handler or for an ambiguous remote/database outcome.
     * ABORTED receipts remain durable and reject reuse of the same operation ID.
     */
    public void abortUnpaid(Key key) {
        advance(key, Stage.PREPARED, Stage.ABORTED);
    }

    /** Call only after all rewards have been delivered; this is not a DB transaction. */
    public void complete(Key key) {
        advance(key, Stage.CONFIRMED, Stage.COMPLETED);
    }

    private void advance(Key key, Stage from, Stage to) {
        Objects.requireNonNull(key, "key");
        if (!store.transition(key, from, to)) {
            throw new IllegalStateException(
                    "Reward receipt transition refused: " + key.storageId() + " " + from + " -> " + to);
        }
    }

    /** List work needing source-specific inspection, never automatic replay. */
    public List<Receipt> unresolved() {
        return List.copyOf(store.unresolved());
    }

    public boolean completed(Key key) {
        return store.completed(Objects.requireNonNull(key));
    }

    private static final class MongoStore implements Store {
        private com.mongodb.client.MongoCollection<Document> collection() {
            return DatabaseManager.getGameDatabase().getCollection("reward_grant_receipts");
        }

        @Override
        public boolean completed(Key key) {
            return collection().find(Filters.and(Filters.eq("_id", key.storageId()),
                    Filters.eq("stage", Stage.COMPLETED.name()))).first() != null;
        }

        @Override
        public boolean insert(Receipt receipt) {
            return DatabaseHelper.callSynchronousDatabaseWrite(() -> {
                List<Document> lines = receipt.rewards().stream()
                        .map(line -> new Document("itemId", line.itemId())
                                .append("count", line.count())
                                .append("level", line.level()))
                        .toList();
                List<Document> debits = receipt.debits().stream()
                        .map(line -> new Document("itemId", line.itemId())
                                .append("count", line.count()))
                        .toList();
                long now = System.currentTimeMillis();
                var doc = new Document("_id", receipt.key().storageId())
                        .append("uid", receipt.key().uid())
                        .append("source", receipt.key().source().name())
                        .append("operationId", receipt.key().operationId())
                        .append("stage", receipt.stage().name())
                        .append("rewards", lines)
                        .append("debits", debits)
                        .append("createdAt", now)
                        .append("updatedAt", now);
                try {
                    collection().insertOne(doc);
                    return true;
                } catch (MongoWriteException conflict) {
                    if (conflict.getError() != null && conflict.getError().getCode() == 11000) {
                        return false;
                    }
                    throw conflict;
                }
            });
        }

        @Override
        public boolean transition(Key key, Stage from, Stage to) {
            return DatabaseHelper.callSynchronousDatabaseWrite(() ->
                    collection().updateOne(
                            Filters.and(
                                    Filters.eq("_id", key.storageId()),
                                    Filters.eq("stage", from.name())),
                            Updates.combine(
                                    Updates.set("stage", to.name()),
                                    Updates.set("updatedAt", System.currentTimeMillis())))
                            .getModifiedCount() == 1);
        }

        @Override
        public List<Receipt> unresolved() {
            var result = new ArrayList<Receipt>();
            for (Document doc : collection().find(Filters.in(
                    "stage", Stage.PREPARED.name(), Stage.CONFIRMED.name()))) {
                Source source = Source.valueOf(doc.getString("source"));
                Key key = new Key(source, doc.getInteger("uid"), doc.getString("operationId"));
                var rewards = new ArrayList<Reward>();
                for (Document line : doc.getList("rewards", Document.class)) {
                    rewards.add(new Reward(
                            line.getInteger("itemId"),
                            line.getInteger("count"),
                            line.getInteger("level")));
                }
                var debits = new ArrayList<Debit>();
                var storedDebits = doc.getList("debits", Document.class);
                if (storedDebits != null) {
                    for (Document line : storedDebits) {
                        debits.add(new Debit(line.getInteger("itemId"), line.getInteger("count")));
                    }
                }
                result.add(new Receipt(key, Stage.valueOf(doc.getString("stage")), rewards, debits));
            }
            return result;
        }
    }
}
