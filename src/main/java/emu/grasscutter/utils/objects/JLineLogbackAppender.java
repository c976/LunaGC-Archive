package emu.grasscutter.utils.objects;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import emu.grasscutter.Grasscutter;
import java.nio.charset.StandardCharsets;

public final class JLineLogbackAppender extends ConsoleAppender<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!this.started) return;

        Grasscutter.getConsole().printAbove(new String(encoder.encode(eventObject), StandardCharsets.UTF_8));
    }
}
