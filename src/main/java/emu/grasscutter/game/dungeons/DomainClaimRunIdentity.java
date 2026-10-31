package emu.grasscutter.game.dungeons;

import java.util.UUID;

/**
 * Identifies one challenge run. Each DungeonManager owns an instance;
 * restartDungeon starts a fresh claim identity even when the same scene is reused.
 */
final class DomainClaimRunIdentity {
    private String token = UUID.randomUUID().toString();

    String operationId(int dungeonId) {
        if (dungeonId <= 0) {
            throw new IllegalArgumentException("Invalid dungeon id");
        }
        return dungeonId + "." + token;
    }

    void restart() {
        token = UUID.randomUUID().toString();
    }
}
