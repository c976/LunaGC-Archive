package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PredicateEvaluator {

    private PredicateEvaluator() {}

    public static boolean all(List<Map<String, Object>> predicates, Ability ability,
                               GameEntity owner, GameEntity target, AbilityModifierAction action) {
        if (predicates == null || predicates.isEmpty()) return true;
        for (var pred : predicates) {
            if (pred == null) continue;
            if (!evaluate(pred, ability, owner, target, action)) return false;
        }
        return true;
    }

    public static boolean evaluate(Map<String, Object> pred, Ability ability,
                                    GameEntity owner, GameEntity target, AbilityModifierAction action) {
        Object typeObj = pred.get("$type");
        if (!(typeObj instanceof String type)) return true;
        GameEntity resolved = resolveTarget(pred, ability, owner, target);
        if ("BJJDEAIEIGP".equals(type)) {
            GameEntity caster = ability != null ? ability.getCasterEntity() : null;
            return hasHexenzirkelTag(caster != null ? caster : (owner != null ? owner : resolved));
        }
        if ("ByUnlockTalentParam".equals(type)) {
            // Prefer the predicate's target (CasterOriginOwner / Target / …); fall back to caster.
            GameEntity talentTarget = resolveTalentParamTarget(pred, ability, owner, resolved);
            return byUnlockTalentParam(pred, talentTarget);
        }
        return switch (type) {
            case "ByHasModifier"      -> byHasModifier(pred, ability, resolved);
            case "ByTargetGlobalValue" -> byTargetGlobalValue(pred, ability, resolved);
            case "ByTargetHPRatio"    -> byTargetHPRatio(pred, ability, resolved);
            default -> true;
        };
    }

    private static GameEntity resolveTalentParamTarget(Map<String, Object> pred, Ability ability,
                                                        GameEntity owner, GameEntity resolved) {
        Object t = pred.get("target");
        if (t instanceof String s) {
            if ("CasterOriginOwner".equals(s) || "OriginOwner".equals(s) || "Caster".equals(s)) {
                GameEntity caster = ability != null ? ability.getCasterEntity() : null;
                if (caster instanceof EntityAvatar) return caster;
                if (ability != null && ability.getPlayerOwner() != null) {
                    return ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
                }
            }
            if ("Team".equals(s) && ability != null && ability.getPlayerOwner() != null) {
                return ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
            }
        }
        if (resolved instanceof EntityAvatar) return resolved;
        if (owner instanceof EntityAvatar) return owner;
        GameEntity caster = ability != null ? ability.getCasterEntity() : null;
        if (caster instanceof EntityAvatar) return caster;
        if (ability != null && ability.getPlayerOwner() != null) {
            return ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        }
        return resolved;
    }

    private static GameEntity resolveTarget(Map<String, Object> pred, Ability ability,
                                              GameEntity owner, GameEntity defaultTarget) {
        Object t = pred.get("target");
        if (!(t instanceof String s)) return defaultTarget;
        return switch (s) {
            case "Self", "Target", "Applier" -> defaultTarget;
            case "Owner" -> owner != null ? owner : defaultTarget;
            case "Caster", "CasterOriginOwner" -> {
                GameEntity caster = ability != null ? ability.getCasterEntity() : null;
                yield caster != null ? caster : defaultTarget;
            }
            case "OriginOwner", "CurLocalAvatar" ->
                ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity()
                    : defaultTarget;
            case "Team" ->
                ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getEntity()
                    : defaultTarget;
            default -> defaultTarget;
        };
    }

    private static boolean hasHexenzirkelTag(GameEntity target) {
        if (!(target instanceof EntityAvatar ea)) return false;
        Avatar avatar = ea.getAvatar();
        if (avatar == null || avatar.getAvatarData() == null) return false;
        var tags = avatar.getAvatarData().getTags();
        return tags != null && tags.contains("AVATAR_TAG_HEXENZIRKEL");
    }

    private static boolean byUnlockTalentParam(Map<String, Object> pred, GameEntity target) {
        Object tp = pred.get("talentParam");
        if (!(tp instanceof String talentParam) || talentParam.isEmpty()) return false;
        if (!(target instanceof EntityAvatar ea)) return false;
        Avatar avatar = ea.getAvatar();
        if (avatar == null) return false;

        // UnlockTalentParam stores a named param (e.g.
        // Player_Ice_StarSuperconducted_Aura_Permanent_Skill_1) under openConfig
        // (e.g. Player_Ice_PermanentSkill_1). Comparing talentParam to openConfig always fails.
        if (avatar.getProudSkillList() != null) {
            for (int proudSkillId : avatar.getProudSkillList()) {
                if (proudSkillUnlocksTalentParam(proudSkillId, talentParam)) {
                    return true;
                }
            }
        }
        if (avatar.getTalentIdList() != null) {
            for (int talentId : avatar.getTalentIdList()) {
                var td = GameData.getAvatarTalentDataMap().get(talentId);
                if (td == null || td.getOpenConfig() == null) continue;
                if (openConfigUnlocksTalentParam(td.getOpenConfig(), talentParam)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean proudSkillUnlocksTalentParam(int proudSkillId, String talentParam) {
        ProudSkillData ps = GameData.getProudSkillDataMap().get(proudSkillId);
        if (ps == null || ps.getOpenConfig() == null) return false;
        if (talentParam.equals(ps.getOpenConfig())) return true;
        return openConfigUnlocksTalentParam(ps.getOpenConfig(), talentParam);
    }

    private static boolean openConfigUnlocksTalentParam(String openConfigName, String talentParam) {
        var entry = GameData.getOpenConfigEntries().get(openConfigName);
        if (entry == null || entry.getTalentParamEntries() == null) return false;
        for (var param : entry.getTalentParamEntries()) {
            if (param != null && talentParam.equals(param.getParamName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean byHasModifier(Map<String, Object> pred, Ability ability, GameEntity target) {
        Object mn = pred.get("modifierName");
        if (!(mn instanceof String modifierName) || ability == null || target == null) return false;
        for (Ability ab : target.getInstancedAbilities()) {
            if (ab == null) continue;
            if (ab.getModifiers().containsKey(modifierName)) return true;
        }
        return false;
    }

    private static boolean byTargetGlobalValue(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null) return false;
        Object key = pred.get("key");
        if (!(key instanceof String k)) return false;
        // Team-scoped keys used by Columbina PermanentSkill_2 live on the team entity.
        GameEntity valueEntity = target;
        if (("_ABILITY_Columbina_IsTeamInField".equals(k)
                || "MoonOvergrowPoint_All".equals(k)
                || "RGV_TempMoonOvergrowPoint".equals(k))
                && ability != null
                && ability.getPlayerOwner() != null) {
            var team = ability.getPlayerOwner().getTeamManager().getEntity();
            if (team != null) valueEntity = team;
        }
        float current = valueEntity.getGlobalAbilityValues().getOrDefault(k, 0f);
        // Also accept avatar-local copies for AddCount / temp keys.
        if (current == 0f && valueEntity != target) {
            current = target.getGlobalAbilityValues().getOrDefault(k, 0f);
        }
        float bound = readFloat(pred.get("value"), ability);
        Object cmpObj = pred.get("compareType");
        String cmp = cmpObj instanceof String s ? s : "Equal";
        return switch (cmp) {
            case "MoreThan", "Greater"    -> current > bound;
            case "MoreThanAndEqual", "MoreOrEqual", "GreaterOrEqual" -> current >= bound;
            case "LessThan", "Lesser"     -> current < bound;
            case "LessThanAndEqual", "LessOrEqual", "LesserOrEqual" -> current <= bound;
            case "NotEqual"               -> current != bound;
            default                       -> current == bound;
        };
    }

    private static boolean byTargetHPRatio(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null || ability == null) return true;
        Object hpRatio = pred.get("HPRatio");
        float threshold;
        if (hpRatio instanceof String key) {
            threshold = ability.getAbilitySpecials().getOrDefault(key, 0f);
            if (threshold <= 0f) {
                threshold = readFloat(hpRatio, ability);
            }
        } else {
            threshold = readFloat(hpRatio, ability);
        }
        if (threshold <= 0f) return true;
        float maxHp = target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_HP);
        float curHp = target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP);
        if (maxHp <= 0f) return true;
        float ratio = curHp / maxHp;
        Object logicObj = pred.get("logic");
        String logic = logicObj instanceof String s ? s : "Greater";
        return switch (logic) {
            case "Lesser", "LessThan" -> ratio < threshold;
            case "LesserOrEqual", "LessThanAndEqual", "LessOrEqual" -> ratio <= threshold;
            case "GreaterOrEqual", "MoreThanAndEqual", "MoreOrEqual" -> ratio >= threshold;
            case "Equal" -> Math.abs(ratio - threshold) < 1e-5f;
            case "NotEqual" -> Math.abs(ratio - threshold) >= 1e-5f;
            default -> ratio > threshold; // Greater / MoreThan
        };
    }

    private static float readFloat(Object v) {
        return readFloat(v, null);
    }

    /**
     * Resolves predicate bounds, including DynamicFloat op lists such as
     * {@code ["MoonOverGrow_CountMax", 1.0, "SUB"]} used by Columbina PermanentSkill_2.
     */
    private static float readFloat(Object v, Ability ability) {
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof String s) {
            if (ability != null) {
                return ability.getAbilitySpecials().getOrDefault(s, 0f);
            }
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
                return 0f;
            }
        }
        if (v instanceof Map<?, ?> m) {
            Object inner = m.get("value");
            if (inner instanceof Number n) return n.floatValue();
            Object exp = m.get("__exp_FixedValue");
            if (exp instanceof Number n) return n.floatValue();
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            var ops = new ArrayList<DynamicFloat.StackOp>(list.size());
            for (Object item : list) {
                if (item instanceof Number n) {
                    ops.add(new DynamicFloat.StackOp(n.floatValue()));
                } else if (item instanceof String s) {
                    ops.add(new DynamicFloat.StackOp(s));
                } else if (item instanceof Boolean b) {
                    ops.add(new DynamicFloat.StackOp(b));
                }
            }
            if (!ops.isEmpty()) {
                var df = new DynamicFloat(ops);
                if (ability != null) {
                    return df.get(ability.getAbilitySpecials(), 0f);
                }
                return df.get(0f);
            }
        }
        return 0f;
    }
}
