package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LimboAndShieldTest {
    /** Just enough of an entity to exercise the limbo and shield bookkeeping. */
    private static final class StubEntity extends GameEntity {
        private final Int2FloatMap props = new Int2FloatOpenHashMap();

        StubEntity() {
            super(null);
        }

        @Override public void initAbilities() {}
        @Override public int getEntityTypeId() { return 0; }
        @Override public Int2FloatMap getFightProperties() { return props; }
        @Override public Position getPosition() { return new Position(); }
        @Override public Position getRotation() { return new Position(); }
        @Override public SceneEntityInfo toProto() { return SceneEntityInfo.getDefaultInstance(); }
    }

    private static AbilityModifier limboModifier() {
        var modifier = new AbilityModifier();
        modifier.state = AbilityModifier.State.Limbo;
        return modifier;
    }

    @Test
    void unrelatedRemovalKeepsAnUntrackedLimbo() {
        var entity = new StubEntity();
        // The AttachTo* mixins apply limbo without an ability or modifier name.
        entity.onAddAbilityModifier(limboModifier());
        assertTrue(entity.isLimbo());

        entity.onLimboModifierRemoved("SomeAbility", "SomeOtherModifier");
        assertTrue(entity.isLimbo());
    }

    @Test
    void removingTheTrackedModifierReleasesTheGate() {
        var entity = new StubEntity();
        entity.trackLimboModifier("Boss", "Split", 0.3f);
        assertTrue(entity.isLimbo());

        entity.onLimboModifierRemoved("Boss", "Unrelated");
        assertTrue(entity.isLimbo());

        entity.onLimboModifierRemoved("Boss", "Split");
        assertFalse(entity.isLimbo());
    }

    @Test
    void theHighestRemainingThresholdWins() {
        var entity = new StubEntity();
        entity.trackLimboModifier("Boss", "High", 0.8f);
        entity.trackLimboModifier("Boss", "Low", 0.3f);

        entity.onLimboModifierRemoved("Boss", "High");
        assertTrue(entity.isLimbo());
        assertEquals(0.3f, entity.getLimboHpThreshold(), 1e-6f);
    }

    private static AbilityModifierAction setGlobalValue(String key) {
        var action = new AbilityModifierAction();
        action.type = AbilityModifierAction.Type.SetGlobalValueV2;
        action.key = key;
        return action;
    }

    @Test
    void breakingTheStackClearsOnlyTheStackModifiersOwnMarkers() {
        var stack = new AbilityModifier();
        stack.onAdded = new AbilityModifierAction[] {setGlobalValue("_MONSTER_Boss_HasMultiShield")};
        stack.onRemoved = new AbilityModifierAction[] {setGlobalValue("_MONSTER_Boss_HasMultiShield")};
        // Raised but never reset by the stack modifier itself: not a phase marker of the stack.
        var other = new AbilityModifier();
        other.onAdded = new AbilityModifierAction[] {setGlobalValue("_MONSTER_Boss_Phase")};
        other.onRemoved = new AbilityModifierAction[] {setGlobalValue("_MONSTER_Boss_Phase")};

        Map<String, AbilityModifier> modifiers = new LinkedHashMap<>();
        modifiers.put("UNIQUE_Monster_Boss_PhaseChange_MultiShield", stack);
        modifiers.put("PhaseChange_ConvertHP", other);
        var ability = new AbilityData();
        ability.abilityName = "Monster_Boss_PhaseChange";
        ability.modifiers = modifiers;

        var entity = new StubEntity();
        entity.getGlobalAbilityValues().put("_MONSTER_Boss_HasMultiShield", 1f);
        entity.getGlobalAbilityValues().put("_MONSTER_Boss_Phase", 1f);

        assertTrue(MultiShieldHelper.clearPhaseMarkers(entity, new MultiShieldHelper.Setup(ability, 0.02f)));
        assertEquals(0f, entity.getGlobalAbilityValues().get("_MONSTER_Boss_HasMultiShield"));
        assertEquals(1f, entity.getGlobalAbilityValues().get("_MONSTER_Boss_Phase"));

        // Already cleared: nothing changes the second time.
        assertFalse(MultiShieldHelper.clearPhaseMarkers(entity, new MultiShieldHelper.Setup(ability, 0.02f)));
    }
}
