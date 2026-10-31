package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import java.util.concurrent.TimeUnit;

/** Performs a complete, synchronous hard delete of an account and its player-owned data. */
public final class AccountDeletionService {
    private static final long LOGOUT_TIMEOUT_SECONDS = 10;

    private AccountDeletionService() {}

    public static synchronized void delete(Account account) {
        disconnectAndWait(account.getId());

        // Resolve the player after logout so the final onLogout saves have already been submitted.
        Player player = DatabaseHelper.getPlayerByAccount(account, Player.class);

        // Hold every database writer at a common fence. This drains writes that were queued before
        // deletion and prevents those writes from recreating documents while the hard delete runs.
        try (DatabaseWriteBarrier ignored = DatabaseWriteBarrier.acquire()) {
            if (player != null) {
                deletePlayerData(player.getUid());
            }
            deleteAccountDocument(account.getId());
        }
    }

    private static void disconnectAndWait(String accountId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LOGOUT_TIMEOUT_SECONDS);
        var gameServer = Grasscutter.getGameServer();
        Player online = gameServer.getPlayerByAccountId(accountId);

        while (online != null) {
            if (online.getSession() != null) {
                online.getSession().close();
            }

            while (gameServer.getPlayerByAccountId(accountId) == online) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException(
                            "Timed out waiting for account " + accountId + " to log out before deletion");
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while waiting for account " + accountId + " to log out", e);
                }
            }

            // A reconnect can race the first disconnect. Keep the account offline for the delete.
            online = gameServer.getPlayerByAccountId(accountId);
        }
    }

    private static void deletePlayerData(int uid) {
        var database = DatabaseManager.getGameDatabase();

        database.getCollection("achievements").deleteMany(eq("uid", uid));
        database.getCollection("activities").deleteMany(eq("uid", uid));
        database.getCollection("homes").deleteMany(eq("ownerUid", uid));
        database.getCollection("mail").deleteMany(eq("ownerUid", uid));
        database.getCollection("avatars").deleteMany(eq("ownerId", uid));
        database.getCollection("gachas").deleteMany(eq("ownerId", uid));
        database.getCollection("items").deleteMany(eq("ownerId", uid));
        database.getCollection("quests").deleteMany(eq("ownerUid", uid));
        database.getCollection("battlepass").deleteMany(eq("ownerUid", uid));
        database.getCollection("dailytasks").deleteMany(eq("ownerUid", uid));
        database.getCollection("group_instances").deleteMany(eq("ownerUid", uid));
        database.getCollection("open_world_spawns").deleteMany(eq("ownerUid", uid));
        database.getCollection("music_game_beatmaps").deleteMany(eq("authorUid", uid));

        database.getCollection("friendships").deleteMany(eq("ownerId", uid));
        database.getCollection("friendships").deleteMany(eq("friendId", uid));

        DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(dev.morphia.query.experimental.filters.Filters.eq("_id", uid))
                .delete();
    }

    private static void deleteAccountDocument(String accountId) {
        DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(dev.morphia.query.experimental.filters.Filters.eq("_id", accountId))
                .delete();
    }
}
