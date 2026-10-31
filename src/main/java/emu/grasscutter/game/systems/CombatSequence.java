package emu.grasscutter.game.systems;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 7.1 combat invocation sequence.
 *
 * The client orders incoming CombatInvocationsNotify packets by client_sequence_id and silently
 * drops the entity-move payload of any packet whose sequence does not advance. On the
 * server -> client direction the proxy owns that counter, so it has to be handed out here,
 * strictly increasing. Only used when the target protocol actually has the field.
 */
public final class CombatSequence {
    private CombatSequence() {}

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    public static int next() {
        return SEQ.incrementAndGet();
    }
}
