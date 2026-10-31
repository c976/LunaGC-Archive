package emu.grasscutter.game.systems;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

/**
 * Repertoire of Myriad Melodies (item 220110).
 *
 * <p>The widget is a pure UI opener: unlike a bomb or a Kamera it is never consumed, so both
 * {@code UseItemReq} and {@code QuickUseWidgetReq} have to answer without touching the stack and
 * then push the repertoire contents. The client opens MusicGameMainPage off the back of
 * {@code MusicGameBookAllDataNotify} (opcode 5005); until that notify lands the page shows
 * TxtItemName placeholders instead of song entries.
 *
 * <p>No {@code MusicGameBookAllDataNotify} descriptor ships with this tree, so the notify is sent
 * with an empty body. That is enough to open the page with an empty repertoire - custom beatmaps
 * and clear records are not persisted yet, and filling them in needs the message shape from a
 * capture first.
 */
public final class MusicGameBookSystem {
    /** Repertoire of Myriad Melodies, adjacent to the Artifact Transmuter (220109) in the widget block. */
    public static final int GADGET_ITEM_ID = 220110;

    private MusicGameBookSystem() {}

    /** Make sure the widget is actually in the bag before answering a use of it. */
    public static void ensureGadget(Player player) {
        if (player == null) {
            return;
        }
        Inventory inv = player.getInventory();
        if (inv == null) {
            return;
        }
        if (inv.getItemCountById(GADGET_ITEM_ID) <= 0) {
            inv.addItem(new GameItem(GADGET_ITEM_ID, 1), ActionReason.PlayerUpgradeReward);
        }
    }

    /** Push the repertoire so MusicGameMainPage has something to render. */
    public static void sendDataNotify(Player player) {
        if (player == null) {
            return;
        }
        try {
            BasePacket packet = new BasePacket(PacketOpcodes.MusicGameBookAllDataNotify);
            packet.setData(new byte[0]);
            player.sendPacket(packet);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("[MusicGameBook] data notify failed: {}", t.toString());
        }
    }
}
