package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;

/**
 * Columbina PermanentSkill_2, Mountain Moon Dew.
 *
 * <p>Talent text: separate cap/duration from ordinary Verdant Dew ({@code MoonOvergrowPoint_All}),
 * consumed only after ordinary dew is exhausted. Ability JSON gates generation on
 * {@code RGV_TempMoonOvergrowPoint} but never writes it - the client dew-point UI reads that
 * regional value.
 */
public final class ColumbinaMountainDew {
    public static final String MOUNTAIN_DEW = "RGV_TempMoonOvergrowPoint";
    public static final String ORDINARY_DEW = "MoonOvergrowPoint_All";
    public static final float MAX = 3f;

    private ColumbinaMountainDew() {}

    public static boolean isPermanentSkill2(Ability ability) {
        if (ability == null || ability.getData() == null || ability.getData().abilityName == null) {
            return false;
        }
        return ability.getData().abilityName.contains("Columbina_PermanentSkill_2");
    }

    public static boolean isAddMoonOverGrowModifier(String modifierName) {
        return modifierName != null
                && modifierName.contains("PermanentSkill_2_AddMoonOverGrowCount");
    }

    /** Direct grant when AddMoonOverGrowCount modifier is applied — UI counter only. */
    public static void grantOne(Ability ability, GameEntity any) {
        if (ability == null) return;
        GameEntity team = resolveTeam(ability, any);
        if (team == null) return;

        float cur = team.getGlobalAbilityValues().getOrDefault(MOUNTAIN_DEW, 0f);
        if (cur >= MAX - 0.001f) {
            Grasscutter.getLogger().info("[Columbina][MountainDew] UI already at cap {}", cur);
            return;
        }

        float next = Math.min(MAX, cur + 1f);
        team.getGlobalAbilityValues().put(MOUNTAIN_DEW, next);
        team.onAbilityValueUpdate();

        if (!AbilityManager.isServerOwnedChain()) {
            Player host = ability.getPlayerOwner();
            if (host == null && team.getScene() != null) host = team.getScene().getHost();
            if (host != null) {
                host.sendPacket(new PacketServerGlobalValueChangeNotify(team, MOUNTAIN_DEW, next));
            }
        }

        Grasscutter.getLogger().info("[Columbina][MountainDew] UI {} -> {}", cur, next);
        try {
            if (ability.getPlayerOwner() != null) {
                emu.grasscutter.game.ability.LaumaC1HealHelper.onMoonBloom(ability.getPlayerOwner());
            }
        } catch (Throwable ignored) {
        }
    }

    public static void mirrorFromOrdinaryDewDelta(Ability ability, GameEntity teamOrSelf, float delta) {
        if (ability == null || teamOrSelf == null || Math.abs(delta) < 0.001f) return;
        if (!isPermanentSkill2(ability)) return;

        GameEntity team = resolveTeam(ability, teamOrSelf);
        if (team == null) return;

        float cur = team.getGlobalAbilityValues().getOrDefault(MOUNTAIN_DEW, 0f);
        float next = Math.max(0f, Math.min(MAX, cur + delta));
        if (Math.abs(next - cur) < 0.001f) return;

        team.getGlobalAbilityValues().put(MOUNTAIN_DEW, next);
        team.onAbilityValueUpdate();

        if (!AbilityManager.isServerOwnedChain()) {
            Player host = ability.getPlayerOwner();
            if (host == null && team.getScene() != null) host = team.getScene().getHost();
            if (host != null) {
                host.sendPacket(new PacketServerGlobalValueChangeNotify(team, MOUNTAIN_DEW, next));
            }
        }

        Grasscutter.getLogger()
                .info("[Columbina][MountainDew] mirror {} -> {} (delta={})", cur, next, delta);
    }

    private static GameEntity resolveTeam(Ability ability, GameEntity fallback) {
        if (ability != null && ability.getPlayerOwner() != null) {
            var team = ability.getPlayerOwner().getTeamManager().getEntity();
            if (team != null) return team;
        }
        return fallback;
    }
}
