package emu.grasscutter.server.threading;

/**
 * How close a pool is to trouble. Declared worst-last: the ordinal ordering is what picks the worst
 * pool in a status readout, so new values belong in severity order.
 */
public enum ThreadPoolHealth {
    NORMAL,
    BUSY,
    WARNING,
    DANGER
}
