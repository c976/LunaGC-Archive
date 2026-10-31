package emu.grasscutter.game.dps;

import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;

/**
 * Pins the {@link AttackResult} currently being processed to the thread so {@link DPSEntity} can read the
 * reaction type and similar fields in its {@code damage} callback, without touching the very large
 * {@code Scene#handleAttack}.
 */
public final class DPSAttackContext {
    private static final ThreadLocal<AttackResult> CURRENT = new ThreadLocal<>();

    private DPSAttackContext() {}

    public static void set(AttackResult result) {
        CURRENT.set(result);
    }

    public static AttackResult get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
