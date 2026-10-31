package emu.grasscutter.server.threading;

/** What a managed pool is for, so a status readout can group pools that serve the same purpose. */
public enum ThreadPoolType {
    MAIN,
    DATABASE,
    SCRIPT,
    GAME,
    QUEST,
    WORLD,
    SCHEDULER,
    OTHER
}
