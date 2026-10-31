package emu.grasscutter.game.dps;

import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Target dummy used by the DPS test.
 *
 * <p>Damage numbers are computed by the client and reported through {@code CombatInvocationsNotify}; the
 * server only records the result on the entity,
 * so all that is needed here is accounting in {@link #damage} plus a logarithmic HP curve that keeps the
 * dummy alive for the whole run.
 */
public class DPSEntity extends EntityMonster {

    /** Orders of magnitude of damage (log10) spanned from full to empty HP; larger drains the bar slower. */
    private static final double HP_BAR_DECADES = 8d;

    private float totalDamage;
    private float maxHit;
    private int hitCount;

    private final Map<ElementType, Float> damageByElement = new EnumMap<>(ElementType.class);
    private final Map<String, Float> damageByReaction = new HashMap<>();

    public DPSEntity(Scene scene, MonsterData monsterData, Position pos, Position rot, int level) {
        super(scene, monsterData, pos, rot, level);
        // Filed under the pseudo group, which the challenge kill check and end-of-run cleanup both rely on.
        this.setGroupId(DPSChallenge.GROUP_ID);
        this.setConfigId(DPSChallenge.CONFIG_ID);
        this.setAiId(DPSChallenge.AI_CONFIG_ID);
    }

    public float getTotalDamage() {
        return this.totalDamage;
    }

    public float getMaxHit() {
        return this.maxHit;
    }

    public int getHitCount() {
        return this.hitCount;
    }

    /** Direct damage broken down by element, excluding recognised reaction damage. Read-only. */
    public Map<ElementType, Float> getDamageByElement() {
        return Collections.unmodifiableMap(this.damageByElement);
    }

    /** Damage broken down by reaction. Read-only. */
    public Map<String, Float> getDamageByReaction() {
        return Collections.unmodifiableMap(this.damageByReaction);
    }

    /**
     * Sends no weapon to the dummy. Without its axe the hilichurl brute never initialises its AI, so it
     * stands still and can be hit continuously.
     */
    @Override
    public int getMonsterWeaponId() {
        return 0;
    }

    @Override
    public void damage(float amount, int killerId, ElementType attackType) {
        if (amount <= 0f || !Float.isFinite(amount)) {
            super.damage(amount, killerId, attackType);
            return;
        }

        var element = attackType == null ? ElementType.None : attackType;
        this.totalDamage += amount;
        this.hitCount++;
        if (amount > this.maxHit) this.maxHit = amount;

        AttackResult attack = DPSAttackContext.get();
        GameEntity attacker = this.getScene() != null ? this.getScene().getEntityById(killerId) : null;
        String reaction = DPSReactionHelper.detect(attack, attacker, element);
        if (reaction != null) {
            this.damageByReaction.merge(reaction, amount, Float::sum);
        } else {
            this.damageByElement.merge(element, amount, Float::sum);
        }

        // HP decays logarithmically with cumulative damage: the dummy looks progressively beaten up but
        // always keeps 1 HP, so it survives the full 60 seconds while elemental particles still drop on the
        // usual HP thresholds.
        float maxHp = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float curHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        if (!Float.isFinite(maxHp) || maxHp <= 1f || !Float.isFinite(curHp)) {
            return;
        }

        double remaining =
                Math.max(0d, 1d - Math.log10(this.totalDamage / 100d + 1d) / HP_BAR_DECADES);
        float targetHp = (float) (remaining * (maxHp - 1f)) + 1f;
        if (targetHp < curHp) {
            super.damage(curHp - targetHp, killerId, attackType);
        }
    }
}
