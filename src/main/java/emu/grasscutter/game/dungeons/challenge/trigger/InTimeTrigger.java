package emu.grasscutter.game.dungeons.challenge.trigger;

import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.server.packet.send.PacketChallengeDataNotify;

public class InTimeTrigger extends ChallengeTrigger {
    @Override
    public void onBegin(WorldChallenge challenge) {
        // Show time remaining UI. Value is an absolute scene-time deadline:
        // remaining = value - currentSceneTime. Use startedAt (just set in start()) so mid-half /
        // long-lived scenes cannot display "9:27 left -> 0:27" from a bare duration.
        var scene = challenge.getScene();
        int deadline = challenge.getStartedAt() + challenge.getTimeLimit();
        scene.broadcastPacket(new PacketChallengeDataNotify(challenge, 2, deadline));
    }

    @Override
    public void onCheckTimeout(WorldChallenge challenge) {
        var current = challenge.getScene().getSceneTimeSeconds();
        if (current - challenge.getStartedAt() > challenge.getTimeLimit()) {
            challenge.fail();
        }
    }
}
