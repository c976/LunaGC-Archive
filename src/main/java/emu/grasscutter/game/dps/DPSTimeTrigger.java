package emu.grasscutter.game.dps;

import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger;

/**
 * DPS timer. On timeout it goes through {@link WorldChallenge#done()}.
 *
 * <p>{@code ChallengeDataNotify} is deliberately not sent: refreshing every second against an absolute
 * scene deadline makes the client flash "-1s"
 * and skip seconds after a time lock or resync. The run duration is already in
 * {@code DungeonChallengeBeginNotify.paramList},
 * so the client counts down locally; {@link DPSChallenge} decides on the wall clock whether time is up.
 */
public class DPSTimeTrigger extends ChallengeTrigger {

    @Override
    public void onBegin(WorldChallenge challenge) {
        // no-op: do not touch ChallengeDataNotify
    }

    @Override
    public void onCheckTimeout(WorldChallenge challenge) {
        if (challenge instanceof DPSChallenge dps) {
            if (dps.elapsedSeconds() >= dps.getTimeLimit()) {
                challenge.done();
            }
            return;
        }
        var elapsed = challenge.getScene().getSceneTimeSeconds() - challenge.getStartedAt();
        if (elapsed >= challenge.getTimeLimit()) {
            challenge.done();
        }
    }
}
