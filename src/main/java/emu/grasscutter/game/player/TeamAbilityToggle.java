package emu.grasscutter.game.player;

import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import java.util.Collection;

/**
 * Adds or removes team abilities by hand, for support buttons and other team-level abilities that a
 * quest or level config would normally grant.
 *
 * <p>The names go into {@link TeamManager#getTeamAbilityEmbryos()}, which the team's control block
 * already lists, so they survive scene changes until logout. Resending that block is what makes the
 * client pick the change up without a scene change.
 */
public final class TeamAbilityToggle {
    private TeamAbilityToggle() {}

    public static boolean isOn(Player player, String ability) {
        return player.getTeamManager().getTeamAbilityEmbryos().contains(ability);
    }

    public static void set(Player player, Collection<String> abilities, boolean enable) {
        set(player, abilities, enable, true);
    }

    /**
     * @param notify whether to resend the team control block; pass false while the player is entering a
     *     scene, whose enter packets carry the updated block anyway
     */
    public static void set(Player player, Collection<String> abilities, boolean enable, boolean notify) {
        var teamManager = player.getTeamManager();
        var embryos = teamManager.getTeamAbilityEmbryos();
        var team = teamManager.getEntity();

        for (var ability : abilities) {
            if (enable) {
                embryos.add(ability);
                if (team != null
                        && team.getInstancedAbilities().stream()
                                .noneMatch(
                                        a ->
                                                a != null
                                                        && a.getData() != null
                                                        && ability.equals(a.getData().abilityName))) {
                    player.getAbilityManager().addAbilityToEntity(team, ability);
                }
            } else {
                embryos.remove(ability);
                if (team != null) {
                    team.getInstancedAbilities()
                            .removeIf(
                                    a ->
                                            a != null
                                                    && a.getData() != null
                                                    && ability.equals(a.getData().abilityName));
                }
            }
        }

        if (notify && team != null) {
            player.sendPacket(new PacketAbilityChangeNotify(team.getId(), teamManager.getAbilityControlBlock()));
        }
    }
}
