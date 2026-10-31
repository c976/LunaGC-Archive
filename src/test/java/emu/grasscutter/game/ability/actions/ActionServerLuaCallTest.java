package emu.grasscutter.game.ability.actions;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.Bindings;
import javax.script.SimpleBindings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

@ExtendWith(ServerResourceFixture.class)
public final class ActionServerLuaCallTest {
    @Test
    void missingAndNonFunctionBindingsReturnFalseBeforeEvaluatingParameters() throws Exception {
        var bindings = new SimpleBindings();
        assertFalse(callFunction(bindings, "missing", null));

        bindings.put("nonFunction", LuaValue.NIL);
        assertFalse(callFunction(bindings, "nonFunction", null));
        bindings.put("nonFunction", "not a function");
        assertFalse(callFunction(bindings, "nonFunction", null));
    }

    @Test
    void repeatedMissingFunctionsDoNotGenerateThrowableEvents() throws Exception {
        var logger = Grasscutter.getLogger();
        var events = new ListAppender<ILoggingEvent>();
        events.setContext(logger.getLoggerContext());
        events.start();
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        logger.addAppender(events);
        try {
            var bindings = new SimpleBindings();
            for (int index = 0; index < 20_000; index++) {
                assertFalse(callFunction(bindings, "missing_" + index, null));
            }
            assertTrue(events.list.size() <= 1, "Missing-function warnings must be globally bounded");
            events.list.forEach(event -> assertNull(event.getThrowableProxy()));
        } finally {
            logger.detachAppender(events);
            logger.setLevel(previousLevel);
            events.stop();
        }
    }

    @Test
    void functionsAddedReplacedAndRemovedAfterAMissAreResolvedFromLiveBindings() throws Exception {
        var bindings = new SimpleBindings();
        var calls = new AtomicInteger();
        var action = actionWithParameters(1);
        assertFalse(callFunction(bindings, "reloadable", action));

        bindings.put("reloadable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                calls.incrementAndGet();
                return LuaValue.TRUE;
            }
        });
        assertTrue(callFunction(bindings, "reloadable", action));
        assertEquals(1, calls.get());

        bindings.put("reloadable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                calls.addAndGet(10);
                return LuaValue.TRUE;
            }
        });
        assertTrue(callFunction(bindings, "reloadable", action));
        assertEquals(11, calls.get());

        bindings.remove("reloadable");
        assertFalse(callFunction(bindings, "reloadable", action));
        assertEquals(11, calls.get());
    }

    @Test
    void callableFunctionsKeepTheirArgumentCountsAndValues() throws Exception {
        var bindings = new SimpleBindings();
        var received = new AtomicReference<List<Integer>>();
        bindings.put("callable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                var values = new ArrayList<Integer>();
                for (int index = 1; index <= args.narg(); index++) {
                    values.add(args.arg(index).toint());
                }
                received.set(values);
                return LuaValue.TRUE;
            }
        });

        for (int parameterCount = 1; parameterCount <= 3; parameterCount++) {
            assertTrue(callFunction(bindings, "callable", actionWithParameters(parameterCount)));
            assertEquals(List.of(11, 22, 33).subList(0, parameterCount), received.get());
        }
    }

    @Test
    void realInvocationErrorsRetainTheirThrowableAndAreNotRateLimited() throws Exception {
        var bindings = new SimpleBindings();
        var failure = new LuaError("real script failure");
        bindings.put("throwing", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                throw failure;
            }
        });

        var logger = Grasscutter.getLogger();
        var events = new ListAppender<ILoggingEvent>();
        events.setContext(logger.getLoggerContext());
        events.start();
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        logger.addAppender(events);
        try {
            for (int index = 0; index < 3; index++) {
                assertFalse(callFunction(bindings, "throwing", actionWithParameters(1)));
            }
            assertEquals(3, events.list.size());
            for (var event : events.list) {
                assertEquals("Unable to invoke {}.", event.getMessage());
                assertNotNull(event.getThrowableProxy());
                assertEquals(LuaError.class.getName(), event.getThrowableProxy().getClassName());
                assertEquals(failure.getMessage(), event.getThrowableProxy().getMessage());
                assertTrue(event.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
            }
        } finally {
            logger.detachAppender(events);
            logger.setLevel(previousLevel);
            events.stop();
        }
    }

    @Test
    void warningLimiterAllowsTheFirstWarningAndReportsSuppressionAtTheBoundary() {
        var limiter = new ActionServerLuaCall.MissingFunctionWarningLimiter(100);
        assertEquals(0, limiter.acquireWarning(1_000));
        assertEquals(-1, limiter.acquireWarning(1_000));
        assertEquals(-1, limiter.acquireWarning(1_099));
        assertEquals(2, limiter.acquireWarning(1_100));
        assertEquals(0, limiter.acquireWarning(1_200));
    }

    @Test
    void warningLimiterSurvivesMonotonicClockWraparound() {
        var limiter = new ActionServerLuaCall.MissingFunctionWarningLimiter(100);
        long first = Long.MAX_VALUE - 50;
        assertEquals(0, limiter.acquireWarning(first));
        assertEquals(-1, limiter.acquireWarning(first + 99));
        assertEquals(1, limiter.acquireWarning(first + 100));
    }

    @Test
    void fiftyConcurrentCallersShareOneWarningBudgetWithoutRetainingFunctionNames() throws Exception {
        var limiter = new ActionServerLuaCall.MissingFunctionWarningLimiter(100);
        var executor = Executors.newFixedThreadPool(50);
        var ready = new CountDownLatch(50);
        var start = new CountDownLatch(1);
        var warnings = new AtomicInteger();
        var reportedSuppressions = new AtomicLong();
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int caller = 0; caller < 50; caller++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    for (int call = 0; call < 2_000; call++) {
                        long suppressed = limiter.acquireWarning(1_000);
                        if (suppressed >= 0) {
                            warnings.incrementAndGet();
                            reportedSuppressions.addAndGet(suppressed);
                        }
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(1, warnings.get());
            // The first sample can already include misses racing with its counter reset.
            assertEquals(99_999, reportedSuppressions.get() + limiter.acquireWarning(1_100));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static boolean callFunction(
            Bindings bindings, String functionName, AbilityModifierAction action) throws Exception {
        Method method = ActionServerLuaCall.class.getDeclaredMethod(
                "callFunction", Bindings.class, String.class, Ability.class, AbilityModifierAction.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, bindings, functionName, null, action);
    }

    private static AbilityModifierAction actionWithParameters(int count) {
        var action = new AbilityModifierAction();
        action.paramNum = count;
        action.param1 = constantParameter(11);
        action.param2 = constantParameter(22);
        action.param3 = constantParameter(33);
        return action;
    }

    private static DynamicFloat constantParameter(int value) {
        return new DynamicFloat(value) {
            @Override
            public int getInt(Ability ability) {
                return value;
            }
        };
    }
}
