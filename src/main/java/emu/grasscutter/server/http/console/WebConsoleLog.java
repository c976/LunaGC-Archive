package emu.grasscutter.server.http.console;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * The last few thousand log lines, for the web console's live log.
 *
 * <p>Attached to the root logger in code rather than in logback.xml, so it exists only while the
 * web console is enabled.
 */
public final class WebConsoleLog {
    private static final int CAPACITY = 2000;

    public record Line(long seq, String level, String text) {}

    private static final ArrayDeque<Line> lines = new ArrayDeque<>(CAPACITY);
    private static long nextSeq = 1;
    private static boolean installed = false;

    private WebConsoleLog() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern("%d{HH:mm:ss} <%level:%logger{0}> %msg");
        layout.start();

        var appender =
                new AppenderBase<ILoggingEvent>() {
                    @Override
                    protected void append(ILoggingEvent event) {
                        var text = layout.doLayout(event);
                        if (event.getThrowableProxy() != null) {
                            text += "\n" + ThrowableProxyUtil.asString(event.getThrowableProxy());
                        }
                        add(event.getLevel().toString(), text);
                    }
                };
        appender.setContext(context);
        appender.setName("WEBCONSOLE");
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    static synchronized void add(String level, String text) {
        if (lines.size() == CAPACITY) lines.removeFirst();
        lines.addLast(new Line(nextSeq++, level, text));
    }

    /** Lines newer than {@code afterSeq}, oldest first; 0 returns everything kept. */
    public static synchronized List<Line> since(long afterSeq) {
        var result = new ArrayList<Line>();
        for (var line : lines) {
            if (line.seq() > afterSeq) result.add(line);
        }
        return result;
    }
}
