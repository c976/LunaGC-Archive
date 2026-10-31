package emu.grasscutter.game.battlepass;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.BuyBattlePassLevelReqOuterClass.BuyBattlePassLevelReq;
import emu.grasscutter.server.packet.send.PacketBuyBattlePassLevelRsp;

/**
 * Fallback when BuyBattlePassLevelReq opcode is still unmapped (-65). 7.0 client was observed
 * sending UNKNOWN(20353) with a 2-byte payload on field 9 (= buy count).
 */
public final class BattlePassBuyHelper {
    public static final int CLIENT_BUY_LEVEL_OPCODE = 20353;

    private BattlePassBuyHelper() {}

    public static boolean tryHandle(Player player, int opcode, byte[] payload) {
        if (player == null || payload == null) {
            return false;
        }
        if (opcode != CLIENT_BUY_LEVEL_OPCODE) {
            return false;
        }
        try {
            int want = parseBuyLevel(payload);
            Grasscutter.getLogger()
                    .info(
                            "BattlePassBuyHelper opcode={} uid={} want={} gems={}",
                            opcode,
                            player.getUid(),
                            want,
                            player.getPrimogems());
            int bought = 0;
            if (want > 0 && player.getBattlePassManager() != null) {
                bought = player.getBattlePassManager().buyLevels(want);
            }
            player.sendPacket(new PacketBuyBattlePassLevelRsp(bought));
            return true;
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("BattlePassBuyHelper failed opcode=" + opcode, t);
            return false;
        }
    }

    /** 7.0 client may put buy count on field 8 (jar proto) or field 9 (observed wire). */
    public static int parseBuyLevel(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return 0;
        }
        try {
            BuyBattlePassLevelReq req = BuyBattlePassLevelReq.parseFrom(payload);
            if (req.getBuyLevel() > 0) {
                return req.getBuyLevel();
            }
        } catch (Throwable ignored) {
        }
        int f8 = readFirstVarintField(payload, 8);
        if (f8 > 0) {
            return f8;
        }
        return readFirstVarintField(payload, 9);
    }

    private static int readFirstVarintField(byte[] data, int fieldNumber) {
        int i = 0;
        while (i < data.length) {
            int tag = data[i] & 0xff;
            int fn = tag >>> 3;
            int wt = tag & 7;
            i++;
            if (wt == 0) {
                long v = 0;
                int shift = 0;
                while (i < data.length) {
                    int b = data[i++] & 0xff;
                    v |= (long) (b & 0x7f) << shift;
                    if ((b & 0x80) == 0) {
                        break;
                    }
                    shift += 7;
                    if (shift > 35) {
                        return 0;
                    }
                }
                if (fn == fieldNumber) {
                    return (int) v;
                }
            } else if (wt == 2) {
                int len = 0;
                int shift = 0;
                while (i < data.length) {
                    int b = data[i++] & 0xff;
                    len |= (b & 0x7f) << shift;
                    if ((b & 0x80) == 0) {
                        break;
                    }
                    shift += 7;
                }
                i += len;
            } else if (wt == 5) {
                i += 4;
            } else if (wt == 1) {
                i += 8;
            } else {
                return 0;
            }
        }
        return 0;
    }
}
