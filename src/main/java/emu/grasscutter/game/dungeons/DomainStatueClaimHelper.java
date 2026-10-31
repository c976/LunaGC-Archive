package emu.grasscutter.game.dungeons;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Resolves petrified-tree claim options.
 *
 * <p>Remote GadgetInteractReq schema is older (no field 3 / FRAGILE enum constants). Official 7.0
 * clients still send those wire fields — they land in {@link UnknownFieldSet}. We read both
 * known getters (via reflection) and unknown varints.
 */
public final class DomainStatueClaimHelper {
    public static final int FRAGILE_RESIN_ITEM_ID = 107009;
    public static final int CONDENSED_RESIN_ITEM_ID = 220007;

    private static final int COST_NONE = 0;
    private static final int COST_NORMAL = 1;
    private static final int COST_CONDENSE = 2;
    private static final int COST_MATERIAL = 5;
    private static final int COST_FRAGILE = 6;
    private static final int COST_HCOIN = 8;

    /**
     * GadgetInteractReq fields the claim reads by number (7.0: 3, 1, 4, 11, 15). The 7.1 class
     * declares them, under their 7.0 tokens, so they are read from the declared fields first.
     */
    private static final int WIRE_TIMES_OR_COST = GadgetInteractReq.EBEELPIEKEL_FIELD_NUMBER;

    private static final int WIRE_LEGACY_BOOL_A = GadgetInteractReq.AJDBEGMCGKD_FIELD_NUMBER;
    private static final int WIRE_LEGACY_BOOL_B = GadgetInteractReq.HDJABIODGCI_FIELD_NUMBER;
    private static final int WIRE_ALT_TIMES_A = GadgetInteractReq.CJIPKAIPCIB_FIELD_NUMBER;
    private static final int WIRE_ALT_TIMES_B = GadgetInteractReq.ALLPOOBNNPH_FIELD_NUMBER;

    public enum ClaimMode {
        /** 20 original resin → 1× */
        NORMAL_1X(1, 20),
        /** 40 original resin → 2× */
        NORMAL_2X(2, 40),
        /** 1 condensed gives 3x, matching the ley line blossom UI. */
        CONDENSE(3, 0),
        /** 1 fragile → 3× */
        FRAGILE(3, 0),
        /** Primogems → 3×; same cost ladder + daily count as BuyResin */
        HCOIN(3, 0);

        public final int rollTimes;
        public final int originalResinCost;

        ClaimMode(int rollTimes, int originalResinCost) {
            this.rollTimes = rollTimes;
            this.originalResinCost = originalResinCost;
        }
    }

    private DomainStatueClaimHelper() {}

    public static boolean shouldOpenUiOnly(GadgetInteractReq req) {
        if (req == null) {
            return true;
        }
        try {
            if (req.getOpType() == InterOpType.InterOpType_INTER_OP_START) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return safeCostValue(req) == COST_NONE && !hasClaimHints(req);
    }

    public static ClaimMode resolve(GadgetInteractReq req) {
        if (req == null) {
            return ClaimMode.NORMAL_1X;
        }

        int cost = safeCostValue(req);
        if (cost == COST_CONDENSE || reflectIsUseCondense(req)) {
            return ClaimMode.CONDENSE;
        }
        if (cost == COST_FRAGILE || cost == COST_MATERIAL) {
            return ClaimMode.FRAGILE;
        }
        if (cost == COST_HCOIN) {
            return ClaimMode.HCOIN;
        }

        // NORMAL / unknown: 20 vs 40 from wire field 3 (and alts) — often only in UnknownFieldSet.
        if (isDoubleOriginalResin(req)) {
            return ClaimMode.NORMAL_2X;
        }

        // Legacy condensed bools in unknown fields.
        if (unknownBoolTrue(req, WIRE_LEGACY_BOOL_B) || unknownBoolTrue(req, WIRE_LEGACY_BOOL_A)) {
            // Only treat as condensed when cost is not explicitly NORMAL with a double hint.
            if (cost != COST_NORMAL) {
                return ClaimMode.CONDENSE;
            }
        }

        return ClaimMode.NORMAL_1X;
    }

    private static boolean hasClaimHints(GadgetInteractReq req) {
        return isDoubleOriginalResin(req)
                || reflectIsUseCondense(req)
                || reflectUiInteractId(req) != 0
                || unknownFirstVarint(req, WIRE_TIMES_OR_COST) != 0;
    }

    private static boolean isDoubleOriginalResin(GadgetInteractReq req) {
        long a = unknownFirstVarint(req, WIRE_TIMES_OR_COST);
        long b = unknownFirstVarint(req, WIRE_ALT_TIMES_A);
        long c = unknownFirstVarint(req, WIRE_ALT_TIMES_B);
        int ui = reflectUiInteractId(req);
        return isDoubleHint(a) || isDoubleHint(b) || isDoubleHint(c) || isDoubleHint(ui);
    }

    private static boolean isDoubleHint(long v) {
        return v == 2L || v == 40L;
    }

    private static int safeCostValue(GadgetInteractReq req) {
        try {
            return req.getResinCostTypeValue();
        } catch (Throwable t) {
            return COST_NONE;
        }
    }

    private static boolean reflectIsUseCondense(GadgetInteractReq req) {
        try {
            Method m = req.getClass().getMethod("getIsUseCondenseResin");
            Object v = m.invoke(req);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int reflectUiInteractId(GadgetInteractReq req) {
        try {
            Method m = req.getClass().getMethod("getUiInteractId");
            Object v = m.invoke(req);
            return v instanceof Integer ? (Integer) v : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long unknownFirstVarint(GadgetInteractReq req, int fieldNumber) {
        try {
            var declared = req.getDescriptorForType().findFieldByNumber(fieldNumber);
            if (declared != null && !declared.isRepeated()) {
                Object v = req.getField(declared);
                if (v instanceof Boolean b) return b ? 1L : 0L;
                if (v instanceof Number n) return n.longValue();
            }
            UnknownFieldSet ufs = req.getUnknownFields();
            if (ufs == null || !ufs.hasField(fieldNumber)) {
                return 0L;
            }
            List<Long> list = ufs.getField(fieldNumber).getVarintList();
            if (list == null || list.isEmpty()) {
                return 0L;
            }
            return list.get(0);
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static boolean unknownBoolTrue(GadgetInteractReq req, int fieldNumber) {
        long v = unknownFirstVarint(req, fieldNumber);
        return v != 0L;
    }

    public static void logInteract(int uid, GadgetInteractReq req, ClaimMode mode) {
        if (req == null) {
            Grasscutter.getLogger().info("StatueClaim uid={} mode={} req=null", uid, mode);
            return;
        }
        try {
            String unknown = dumpUnknown(req);
            Grasscutter.getLogger()
                    .info(
                            "StatueClaim uid={} mode={} op={} cost={} condense={} uiInteract={} unknown={}",
                            uid,
                            mode,
                            req.getOpTypeValue(),
                            safeCostValue(req),
                            reflectIsUseCondense(req),
                            reflectUiInteractId(req),
                            unknown);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .info("StatueClaim uid={} mode={} dumpFailed={}", uid, mode, t.toString());
        }
    }

    private static String dumpUnknown(GadgetInteractReq req) {
        try {
            UnknownFieldSet ufs = req.getUnknownFields();
            if (ufs == null || ufs.asMap().isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : ufs.asMap().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(e.getKey()).append('=');
                List<Long> vars = e.getValue().getVarintList();
                if (vars != null && !vars.isEmpty()) {
                    sb.append(vars);
                } else {
                    sb.append("?");
                }
            }
            sb.append('}');
            return sb.toString();
        } catch (Throwable t) {
            return "err";
        }
    }
}
