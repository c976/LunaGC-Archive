package emu.grasscutter.server.threading;

import java.util.concurrent.TimeUnit;

/** The sizing a managed pool was built with, kept so a status readout can show it. */
public record ThreadPoolConfig(
        String name,
        ThreadPoolType type,
        int coreThreads,
        int maxThreads,
        int queueCapacity,
        long keepAliveTime,
        TimeUnit keepAliveUnit) {}
