package emu.grasscutter.database;

import emu.grasscutter.game.inventory.GameItem;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Orders item writes by their durable ID, including the first save and terminal deletion. */
final class ItemPersistenceService {
    private final DatabaseWriterManager writers;
    private final ExecutorService executor;
    private final Consumer<GameItem> save;
    private final Consumer<GameItem> delete;

    ItemPersistenceService(
            DatabaseWriterManager writers,
            ExecutorService executor,
            Consumer<GameItem> save,
            Consumer<GameItem> delete) {
        this.writers = writers;
        this.executor = executor;
        this.save = save;
        this.delete = delete;
    }

    void save(GameItem item) {
        var id = item.ensurePersistenceId();
        writers.submitOrdered(
                executor,
                id,
                () -> {
                    // A pending save observes mutable item state. Never upsert a consumed item.
                    if (item.getCount() > 0 && item.getOwnerId() > 0) save.accept(item);
                    else delete.accept(item);
                });
    }

    void delete(GameItem item) {
        var id = item.ensurePersistenceId();
        writers.submitOrdered(executor, id, () -> delete.accept(item));
    }
}
