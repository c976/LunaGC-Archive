package emu.grasscutter.game.ability;

import emu.grasscutter.net.packet.PacketOpcodes;

/** Protocol ids for Escoffier's hold-E improvised cooking, from the 7.1 opcode table. */
public final class EscoffierSkillCookOpcodes {
    /** Client asks for the dish once charging completes; empty payload. 7.1 CmdId unknown (0). */
    public static final int COOK_REQ = PacketOpcodes._AvatarEscoffierSkillCookReq;
    /** Dish result response. */
    public static final int COOK_RSP = PacketOpcodes._AvatarEscoffierSkillCookRsp;
    /** Weekly count sync; sending too many fields poisons the charge bar. */
    public static final int COOK_DATA_NOTIFY = PacketOpcodes._AvatarEscoffierSkillCookDataNotify;

    private EscoffierSkillCookOpcodes() {}
}
