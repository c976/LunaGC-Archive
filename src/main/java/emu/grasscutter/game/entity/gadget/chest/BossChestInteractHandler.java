package emu.grasscutter.game.entity.gadget.chest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.gadget.GadgetChest;
import emu.grasscutter.game.player.Player;

/**
 * Domain boss flowers delegate to the same resin-gated dungeon reward service.
 * Open-world bosses are handled directly by GadgetChest, never from previews.
 */
public class BossChestInteractHandler implements ChestInteractHandler {
    @Override
    public boolean isTwoStep() {
        return true;
    }

    @Override
    public boolean onInteract(GadgetChest chest, Player player) {
        return onInteract(chest, player, false);
    }

    public boolean onInteract(GadgetChest chest, Player player, boolean condensed) {
        var dungeon = player.getScene().getDungeonManager();
        if (dungeon == null) {
            Grasscutter.getLogger().error(
                    "Boss chest has no dungeon reward source group={} uid={}",
                    chest.getGadget().getGroupId(), player.getUid());
            return false;
        }
        return dungeon.getStatueDrops(player, condensed, chest.getGadget().getGroupId());
    }
}
