package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Natlan nightsoul exploration spends Nyx / phlogiston instead of stamina. Official configs attach
 * modifiers with {@code Actor_CostStaminaRatio: -2} (Stamina_Free) or {@code state: NyxState} while
 * {@code IsNyxState} / {@code IsSpecialMove} GVs are set. This helper detects that window so the
 * server does not drain stamina in parallel with nightsoul.
 */
public final class NightsoulStaminaExempt {
    private static final String NYX_INSTANT_KEY = "_ABILITY_NyxInstant_Active";
    /** Keep skip active briefly after a Nyx cost tick (covers Kinich-style drains). */
    private static final long NYX_COST_TTL_MS = 600L;

    private static final ConcurrentHashMap<Integer, Long> recentNyxCostMs = new ConcurrentHashMap<>();
    private static final Field ACTOR_COST_STAMINA_RATIO = findField(
            "emu.grasscutter.data.binout.AbilityModifier$AbilityModifierProperty",
            "Actor_CostStaminaRatio");

    private NightsoulStaminaExempt() {}

    public static void markNyxCostActive(GameEntity entity) {
        if (entity == null) {
            return;
        }
        recentNyxCostMs.put(entity.getId(), System.currentTimeMillis());
    }

    public static boolean shouldSkipCharacterStamina(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return false;
        }
        return shouldSkipCharacterStamina(player.getTeamManager().getCurrentAvatarEntity());
    }

    public static boolean shouldSkipCharacterStamina(EntityAvatar avatar) {
        if (avatar == null) {
            return false;
        }

        Long costAt = recentNyxCostMs.get(avatar.getId());
        if (costAt != null) {
            if (System.currentTimeMillis() - costAt <= NYX_COST_TTL_MS) {
                return true;
            }
            recentNyxCostMs.remove(avatar.getId(), costAt);
        }

        Map<String, Float> gv = avatar.getGlobalAbilityValues();
        if (gv != null) {
            for (Map.Entry<String, Float> e : gv.entrySet()) {
                String key = e.getKey();
                Float value = e.getValue();
                if (key == null || value == null || value < 0.5f) {
                    continue;
                }
                if (key.endsWith("IsNyxState") || key.endsWith("IsSpecialMove")) {
                    return true;
                }
            }
            // NyxInstant alone can stick after ChangePlayMode; require active nightsoul gauge too.
            Float nyxInstant = gv.get(NYX_INSTANT_KEY);
            if (nyxInstant != null && nyxInstant >= 0.5f) {
                Float nyx = gv.get("NyxValue");
                if (nyx != null && nyx > 0.5f) {
                    return true;
                }
            }
        }

        return hasNightsoulStaminaFreeModifier(avatar);
    }

    private static boolean hasNightsoulStaminaFreeModifier(GameEntity entity) {
        if (entity.getInstancedAbilities() == null) {
            return false;
        }
        for (Ability ability : entity.getInstancedAbilities()) {
            if (ability == null || ability.getModifiers() == null) {
                continue;
            }
            for (AbilityModifierController controller : ability.getModifiers().values()) {
                if (controller == null || controller.getModifierData() == null) {
                    continue;
                }
                AbilityModifier data = controller.getModifierData();
                if (data.state == AbilityModifier.State.NyxState) {
                    return true;
                }
                if (readActorCostStaminaRatio(data.properties, ability) <= -1f) {
                    // Official Stamina_Free / *_Stamina_Reduction use -2.
                    return true;
                }
            }
        }
        return false;
    }

    private static float readActorCostStaminaRatio(Object properties, Ability ability) {
        if (properties == null || ACTOR_COST_STAMINA_RATIO == null) {
            return 0f;
        }
        try {
            Object df = ACTOR_COST_STAMINA_RATIO.get(properties);
            if (df == null) {
                return 0f;
            }
            // DynamicFloat#get(Ability, float)
            var method = df.getClass().getMethod("get", Ability.class, float.class);
            Object v = method.invoke(df, ability, 0f);
            return v instanceof Float ? (Float) v : 0f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private static Field findField(String className, String fieldName) {
        try {
            Field f = Class.forName(className).getDeclaredField(fieldName);
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
