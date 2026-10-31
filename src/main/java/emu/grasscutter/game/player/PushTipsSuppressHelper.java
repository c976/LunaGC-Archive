package emu.grasscutter.game.player;

import emu.grasscutter.net.proto.PushTipsAllDataNotifyOuterClass;
import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PushTipsDataOuterClass.PushTipsData;
import emu.grasscutter.server.game.GameServer;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Marks push tips as already finished so the feature-unlock toast does not cover a widget UI the
 * moment it opens.
 *
 * <p>The widget systems (Artifact Transmuter, Repertoire of Myriad Melodies) push their data notify as soon as the gadget is used, and
 * on a private server the matching tip is still unread - the client then paints the "new feature"
 * banner over the page that just opened. The official server never hits this because the tip was
 * finished long before, which is what {@code PushTipsAllDataNotify} (opcode 2160) carries: a list of
 * {@link PushTipsData}, each {@code state} 1 for unread and 3 for finished (both values read
 * straight off a 7.0 capture).
 *
 * <p>Only {@code PushTipsData} itself has a descriptor in this tree - the enclosing notify does not
 * - so the envelope is written by hand. The repeated field is assumed to be field 1; if that is
 * wrong the client drops it as an unknown field and the tip simply is not suppressed, which is the
 * behaviour without this helper at all.
 */
public final class PushTipsSuppressHelper {
    /** {@code PUSH_TIPS_STATE_FINISH} - the tip is read and its reward claimed. */
    private static final int STATE_FINISH = 3;

    /** Field number of {@code repeated PushTipsData push_tips_list} inside the notify. */
    private static final int PUSH_TIPS_LIST_FIELD =
            PushTipsAllDataNotifyOuterClass.PushTipsAllDataNotify.PUSH_TIPS_LIST_FIELD_NUMBER;

    /** Relic define / Artifact Transmuter unlock tip. */
    private static final int TIP_RELIC_DEFINE = 7013;

    /**
     * Tips that a widget page can surface. Kept deliberately narrow: blanket-finishing every tip
     * would also silence unrelated unlocks the player has genuinely not seen.
     */
    private static final List<Integer> WIDGET_TIPS = List.of(TIP_RELIC_DEFINE);

    /**
     * The login flow sends its own push-tip state after this runs, so one suppress at login time is
     * raced and lost. Re-send a few ticks later, once that state has landed.
     */
    private static final int LOGIN_SUPPRESS_DELAY_TICKS = 10;

    private PushTipsSuppressHelper() {}

    /** Finish every tip a widget page can raise. */
    public static void suppressAll(Player player) {
        suppress(player, WIDGET_TIPS);
    }

    /** Finish the relic-define tip on its own, ahead of an Offer data notify. */
    public static void suppressRelicDefine(Player player) {
        suppress(player, List.of(TIP_RELIC_DEFINE));
    }

    /** Re-run {@link #suppressAll} after the login push-tip state has been sent. */
    public static void scheduleLoginSuppress(Player player) {
        if (player == null) {
            return;
        }
        GameServer server = player.getSession() != null ? player.getSession().getServer() : null;
        if (server == null || server.getScheduler() == null) {
            return;
        }
        server
                .getScheduler()
                .scheduleDelayedTask(() -> suppressAll(player), LOGIN_SUPPRESS_DELAY_TICKS);
    }

    public static void suppress(Player player, List<Integer> tipIds) {
        if (player == null || tipIds == null || tipIds.isEmpty()) {
            return;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CodedOutputStream cos = CodedOutputStream.newInstance(out);
            for (int tipId : tipIds) {
                cos.writeMessage(
                        PUSH_TIPS_LIST_FIELD,
                        PushTipsData.newBuilder().setPushTipsId(tipId).setState(STATE_FINISH).build());
            }
            cos.flush();

            BasePacket packet = new BasePacket(PacketOpcodes.PushTipsAllDataNotify);
            packet.setData(out.toByteArray());
            player.sendPacket(packet);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("[PushTips] suppress failed: {}", t.toString());
        }
    }
}
