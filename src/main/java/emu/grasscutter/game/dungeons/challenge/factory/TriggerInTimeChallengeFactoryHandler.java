package emu.grasscutter.game.dungeons.challenge.factory;

import static emu.grasscutter.game.dungeons.challenge.enums.ChallengeType.CHALLENGE_TRIGGER_IN_TIME;

import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.enums.ChallengeType;
import emu.grasscutter.game.dungeons.challenge.trigger.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.quest.LiyueCloudDungeonSupport;
import emu.grasscutter.scripts.data.SceneGroup;
import java.util.List;

public class TriggerInTimeChallengeFactoryHandler implements ChallengeFactoryHandler {
    @Override
    public boolean isThisType(ChallengeType challengeType) {
        // kill gadgets(explosive barrel) in time
        // ActiveChallenge with 56,201,20,2,201,4
        // open chest in time
        // ActiveChallenge with 666,202,30,7,202,1
        return challengeType == CHALLENGE_TRIGGER_IN_TIME;
    }

    @Override
    public WorldChallenge build(
            int challengeIndex,
            int challengeId,
            int timeLimit,
            int param4,
            int triggerTag,
            int triggerCount,
            Scene scene,
            SceneGroup group) {
        if (emu.grasscutter.game.quest.AmberFlightExamSupport.isFlightChallenge(scene, group, challengeIndex, challengeId))
            return emu.grasscutter.game.quest.AmberFlightExamSupport.createChallenge(scene, group,
                    timeLimit, triggerTag, triggerCount);
        if (LiyueCloudDungeonSupport.isFireChallenge(scene, group, challengeIndex, challengeId))
            return LiyueCloudDungeonSupport.createFireChallenge(scene, group,
                    challengeIndex, challengeId, timeLimit, triggerTag, triggerCount);
        return new WorldChallenge(
                scene,
                group,
                challengeId, // Id
                challengeIndex, // Index
                List.of(timeLimit, triggerCount),
                timeLimit, // Limit
                triggerCount, // Goal
                List.of(new InTimeTrigger(), new TriggerGroupTriggerTrigger(Integer.toString(triggerTag))));
    }
}
