package emu.grasscutter;

import static emu.grasscutter.config.Configuration.SERVER;
import static emu.grasscutter.utils.lang.Language.translate;

import ch.qos.logback.classic.*;
import emu.grasscutter.auth.*;
import emu.grasscutter.command.*;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.database.*;
import emu.grasscutter.game.managers.cooking.CookingCompoundManager;
import emu.grasscutter.game.managers.cooking.CookingManager;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.plugin.api.ServerHelper;
import emu.grasscutter.server.dispatch.DispatchServer;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.http.HttpServer;
import emu.grasscutter.server.http.dispatch.*;
import emu.grasscutter.server.http.documentation.*;
import emu.grasscutter.server.http.handlers.*;
import emu.grasscutter.tools.Tools;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.lang.Language;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.io.*;
import java.time.Duration;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Locale;
import emu.grasscutter.server.threading.ManagedScheduledThreadPoolExecutor;
import emu.grasscutter.server.threading.ServerHealthSnapshot;
import emu.grasscutter.server.threading.ServerRuntimeSnapshot;
import emu.grasscutter.server.threading.ThreadPoolConfigResolver;
import emu.grasscutter.server.threading.ThreadPoolManager;
import emu.grasscutter.server.threading.ThreadPoolSnapshot;
import emu.grasscutter.server.threading.ThreadPoolType;
import java.util.concurrent.*;
import javax.annotation.Nullable;
import lombok.*;
import org.jline.reader.*;
import org.jline.terminal.*;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.LoggerFactory;

public final class Grasscutter {
    public static final File configFile = new File("./config.json");
    // The generated protocol classes are thousands of classes nothing looks up by reflection, so
    // they are left out of the startup scan.
    public static final Reflections reflector =
            new Reflections(
                    new ConfigurationBuilder()
                            .forPackage("emu.grasscutter")
                            .filterInputsBy(
                                    new FilterBuilder()
                                            .includePackage("emu.grasscutter")
                                            .excludePackage("emu.grasscutter.net.proto")));
    @Getter private static final Logger logger = (Logger) LoggerFactory.getLogger(Grasscutter.class);

    @Getter public static ConfigContainer config;

    @Getter @Setter private static Language language;
    @Getter @Setter private static String preferredLanguage;

    @Getter private static int currentDayOfWeek;
    @Setter private static ServerRunMode runModeOverride = null; // Config override for run mode
    @Setter private static boolean noConsole = false;

    @Getter private static HttpServer httpServer;
    @Getter private static GameServer gameServer;
    @Getter private static DispatchServer dispatchServer;
    @Getter private static PluginManager pluginManager;
    @Getter private static CommandMap commandMap;

    @Getter @Setter private static AuthenticationSystem authenticationSystem;
    @Getter @Setter private static PermissionHandler permissionHandler;

    private static LineReader consoleLineReader = null;

    @Getter
    private static final ExecutorService threadPool =
            new ThreadPoolExecutor(
                    6,
                    6,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(),
                    FastThreadLocalThread::new,
                    new ThreadPoolExecutor.AbortPolicy());

    static {
        // Declare logback configuration.
        System.setProperty("logback.configurationFile", "src/main/resources/logback.xml");

        // Disable the MongoDB logger.
        var mongoLogger = (Logger) LoggerFactory.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.OFF);

        // Load server configuration.
        Grasscutter.loadConfig();
        // Attempt to update configuration.
        ConfigContainer.updateConfig();

        Grasscutter.getLogger().info("Loading Grasscutter...");

        // Load translation files.
        Grasscutter.loadLanguage();

        // Check server structure.
        Utils.startupCheck();
    }

    public static void main(String[] args) throws Exception {
        Crypto.loadKeys(); // Load keys from buffers.

        // Parse start-up arguments.
        if (StartupArguments.parse(args)) {
            System.exit(0); // Exit early.
        }

        // Get the server run mode.
        var runMode = Grasscutter.getRunMode();

        // Create command map.
        commandMap = new CommandMap(true);

        // Initialize server.
        logger.info(translate("messages.status.starting"));
        logger.info(translate("messages.status.game_version", GameConstants.VERSION));
        logger.info(translate("messages.status.version", GameConstants.VERSION, "Asta"));
        logger.info("Commit: {} | Built: {}", BuildConfig.GIT_HASH, BuildConfig.BUILD_DATETIME);

        // Initialize database.
        DatabaseManager.initialize();

        // Initialize the default systems.
        authenticationSystem = new DefaultAuthentication();
        permissionHandler = new DefaultPermissionHandler();

        // Create server instances.
        if (runMode == ServerRunMode.HYBRID || runMode == ServerRunMode.GAME_ONLY)
            Grasscutter.gameServer = new GameServer();
        if (runMode == ServerRunMode.HYBRID || runMode == ServerRunMode.DISPATCH_ONLY)
            Grasscutter.httpServer = new HttpServer();

        // Create a server hook instance with both servers.
        new ServerHelper(gameServer, httpServer);

        // Create plugin manager instance.
        pluginManager = new PluginManager();

        if (runMode != ServerRunMode.GAME_ONLY) {
            // Add HTTP routes after loading plugins.
            httpServer.addRouter(HttpServer.UnhandledRequestRouter.class);
            httpServer.addRouter(HttpServer.DefaultRequestRouter.class);
            httpServer.addRouter(RegionHandler.class);
            httpServer.addRouter(LogHandler.class);
            httpServer.addRouter(GenericHandler.class);
            httpServer.addRouter(AnnouncementsHandler.class);
            httpServer.addRouter(AuthenticationHandler.class);
            httpServer.addRouter(GachaHandler.class);
            httpServer.addRouter(DocumentationServerHandler.class);
            httpServer.addRouter(HandbookHandler.class);
            httpServer.addRouter(emu.grasscutter.server.http.api.ApiHandler.class);
            httpServer.addRouter(emu.grasscutter.server.http.console.WebConsoleHandler.class);
        }

        // Check if the HTTP server should start.
        var started = config.server.http.startImmediately;
        if (started) {
            Grasscutter.getLogger().info("HTTP server is starting...");
            Grasscutter.startDispatch();

            Grasscutter.getLogger().info("Game server is starting...");
        }

        // Load resources.
        if (runMode != ServerRunMode.DISPATCH_ONLY) {
            // Load all resources.
            Grasscutter.updateDayOfWeek();
            ResourceLoader.loadAll();

            // Item identities are available only after ExcelBinOutput has been loaded.
            // CSS and YAML own ordinary chest and scene-point activation rewards.
            emu.grasscutter.game.reward.RewardConfiguration.load();


            // GameServer is constructed before resources load, so cooking/compound default-unlock
            // tables were empty at that point. Rebuild them now that ExcelBinOutput is available.
            CookingManager.initialize();
            CookingCompoundManager.initialize();

            // The game server, and with it the shop system, is built before the resources are, so
            // the shops that come out of the game data are listed now.
            if (gameServer != null) gameServer.getShopSystem().loadArtifactShop();

            // Generate handbooks.
            Tools.createGmHandbooks(false);
            // Generate gacha mappings.
            Tools.generateGachaMappings();
        }

        // Start servers.
        if (runMode == ServerRunMode.HYBRID) {
            if (!started) Grasscutter.startDispatch();
            gameServer.start();
        } else if (runMode == ServerRunMode.DISPATCH_ONLY) {
            if (!started) Grasscutter.startDispatch();
        } else if (runMode == ServerRunMode.GAME_ONLY) {
            gameServer.start();
        } else {
            logger.error(translate("messages.status.run_mode_error", runMode));
            logger.error(translate("messages.status.run_mode_help"));
            logger.error(translate("messages.status.shutdown"));
            System.exit(1);
        }

        // Stamp the game version into the built-in login Lua shell.
        emu.grasscutter.utils.LuaShell.addLoginLuaShell();

        // Start the periodic status readout.
        startRuntimeMonitor();

        // Start the database monitor and, if configured, the timed restart.
        emu.grasscutter.server.ServerWatchdog.start();

        // Enable all plugins.
        pluginManager.enablePlugins();

        // Hook into shutdown event.
        Runtime.getRuntime().addShutdownHook(new Thread(Grasscutter::onShutdown));

        // Open console.
        Grasscutter.startConsole();
    }

    /** Server shutdown event. */
    private static void onShutdown() {
        // Disable all plugins.
        if (pluginManager != null) Utils.runAll("Plugin shutdown", pluginManager::disablePlugins);
        // Shutdown the game server.
        if (gameServer != null) Utils.runAll("Game server shutdown", gameServer::onServerShutdown);

        // Held until the database writers have drained, so its waits are not cut short.
        boolean interrupted = Thread.interrupted();
        try {
            // Wait for Grasscutter's thread pool to finish.
            var executor = Grasscutter.getThreadPool();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            // Still drain accepted database writes before restoring the shutdown hook's interrupt.
            interrupted = true;
        }

        // Stop admission first, then drain every database writer against one shared deadline.
        var databaseShutdown = DatabaseHelper.shutdownWriters(Duration.ofSeconds(5));
        if (!databaseShutdown.completed()) {
            logger.error(
                    "Database shutdown incomplete: {} active at timeout, {} accepted tasks never started, interrupted={}; pools={}",
                    databaseShutdown.activeAtTimeoutTaskCount(),
                    databaseShutdown.notStartedTaskCount(),
                    databaseShutdown.interrupted(),
                    databaseShutdown.pools());
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    /** Utility method for starting the: - SDK server - Dispatch server */
    public static void startDispatch() throws Exception {
        httpServer.start(); // Start the SDK/HTTP server.

        if (Grasscutter.getRunMode() == ServerRunMode.DISPATCH_ONLY) {
            dispatchServer = new DispatchServer("0.0.0.0", 1111); // Create the dispatch server.
            dispatchServer.start(); // Start the dispatch server.
        }
    }

    /*
     * Methods for the language system component.
     */

    public static void loadLanguage() {
        var locale = config.language.language;
        language = Language.getLanguage(Utils.getLanguageCode(locale));
    }

    /*
     * Methods for the configuration system component.
     */

    /** Attempts to load the configuration from a file. */
    public static void loadConfig() {
        // Check if config.json exists. If not, we generate a new config.
        if (!configFile.exists()) {
            getLogger().info("config.json could not be found. Generating a default configuration ...");
            config = new ConfigContainer();
            Grasscutter.saveConfig(config);
            return;
        }

        // If the file already exists, we attempt to load it.
        try {
            config = JsonUtils.loadToClass(configFile.toPath(), ConfigContainer.class);
        } catch (Exception exception) {
            getLogger()
                    .error(
                            "There was an error while trying to load the configuration from config.json. Please make sure that there are no syntax errors. If you want to start with a default configuration, delete your existing config.json.");
            System.exit(1);
        }
    }

    /**
     * Saves the provided server configuration.
     *
     * @param config The configuration to save, or null for a new one.
     */
    public static void saveConfig(@Nullable ConfigContainer config) {
        if (config == null) config = new ConfigContainer();

        try (FileWriter file = new FileWriter(configFile)) {
            file.write(JsonUtils.encode(config));
        } catch (IOException ignored) {
            logger.error("Unable to write to config file.");
        } catch (Exception e) {
            logger.error("Unable to save config file.", e);
        }
    }

    /*
     * Getters for the various server components.
     */

    public static Language getLanguage(String langCode) {
        return Language.getLanguage(langCode);
    }

    public static ServerRunMode getRunMode() {
        return Grasscutter.runModeOverride != null ? Grasscutter.runModeOverride : SERVER.runMode;
    }

    public static synchronized LineReader getConsole() {
        if (consoleLineReader == null) {
            Terminal terminal = null;
            try {
                terminal = TerminalBuilder.builder().jna(true).build();
            } catch (Exception e) {
                try {
                    // Fallback to a dumb jline terminal.
                    terminal = TerminalBuilder.builder().dumb(true).build();
                } catch (Exception ignored) {
                    // When dumb is true, build() never throws.
                }
            }

            consoleLineReader = LineReaderBuilder.builder().terminal(terminal).build();
        }

        return consoleLineReader;
    }

    /*
     * Utility methods.
     */

    public static void updateDayOfWeek() {
        Calendar calendar = Calendar.getInstance();
        Grasscutter.currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        logger.debug("Set day of week to " + currentDayOfWeek);
    }

    public static void startConsole() {
        // Console should not start in dispatch only mode.
        if (Grasscutter.getRunMode() == ServerRunMode.DISPATCH_ONLY && Grasscutter.noConsole) {
            logger.info(translate("messages.dispatch.no_commands_error"));
            return;
        } else if (!config.server.game.enableConsole) {
            // The loop below never runs, so saying "type help" would be an invitation to type at a
            // prompt that is not there.
            logger.info("Done! The console is disabled; set server.game.enableConsole to use it.");
            return;
        } else {
            logger.info(translate("messages.status.done"));
        }

        String input = null;
        var isLastInterrupted = false;
        while (config.server.game.enableConsole) {
            try {
                input = consoleLineReader.readLine(commandMap.getConsolePrompt());
            } catch (UserInterruptException e) {
                if (!isLastInterrupted) {
                    isLastInterrupted = true;
                    logger.info("Press Ctrl-C again to shutdown.");
                    continue;
                } else {
                    Runtime.getRuntime().exit(0);
                }
            } catch (EndOfFileException e) {
                // Nothing is attached to stdin - a redirect, a service, a background launch. Asking
                // again just returns EOF again, so the loop span the CPU and filled the log with its
                // own complaints. Stop reading; the server's own threads keep it running.
                logger.info("No console attached, running without commands.");
                return;
            } catch (IOError e) {
                logger.error("An IO error occurred while trying to read from console.", e);
                return;
            } catch (IllegalStateException e) {
                // `stop` closes the terminal from the shutdown hook while this thread is back in
                // readLine; JLine then throws "Terminal has been closed". Nothing is left to read.
                return;
            }

            isLastInterrupted = false;

            try {
                commandMap.invoke(null, null, input);
            } catch (Exception e) {
                logger.error(translate("messages.game.command_error"), e);
            }
        }
    }

    /*
     * Enums for the configuration.
     */

    public enum ServerRunMode {
        HYBRID,
        DISPATCH_ONLY,
        GAME_ONLY
    }

    public enum ServerDebugMode {
        ALL,
        MISSING,
        WHITELIST,
        BLACKLIST,
        NONE
    }

    /** How often the status readout is logged. Short enough to catch a backlog, long enough to ignore. */
    private static final long MONITOR_INTERVAL_MINUTES = 3;

    /** The last status verdict printed, so an unchanged one is not repeated at INFO. */
    private static String lastReportedVerdict = null;

    private static ScheduledExecutorService runtimeMonitor;

    /**
     * Logs CPU, memory and every managed thread pool on a fixed interval.
     *
     * <p>The numbers exist whether or not anyone looks at them; printing them periodically is what
     * makes a queue backing up visible before it turns into players being refused at login.
     */
    private static void startRuntimeMonitor() {
        if (runtimeMonitor != null) return;

        var config = ThreadPoolConfigResolver.resolve(
                "RUNTIME_MONITOR", ThreadPoolType.SCHEDULER, 1, 1, 0, 60);
        runtimeMonitor = new ManagedScheduledThreadPoolExecutor(config, runnable -> {
            var thread = new Thread(runnable, "runtime-monitor");
            thread.setDaemon(true);
            return thread;
        });
        runtimeMonitor.scheduleAtFixedRate(
                Grasscutter::logRuntimeStatus,
                MONITOR_INTERVAL_MINUTES,
                MONITOR_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(runtimeMonitor::shutdownNow));
    }

    /** One pass of the status readout. */
    public static void logRuntimeStatus() {
        try {
            var runtime = ServerRuntimeSnapshot.collect();
            var pools = ThreadPoolManager.getInstance().getAll().stream()
                    .map(ThreadPoolManager.getInstance()::snapshot)
                    .sorted(Comparator.comparing(ThreadPoolSnapshot::name))
                    .toList();
            var health = ServerHealthSnapshot.from(runtime, pools);

            // Twenty-two lines every three minutes buries everything else in the console, and
            // a healthy server repeats the same twenty-two lines forever. Say it out loud only
            // when the verdict actually changes; otherwise it is still there at debug.
            var verdict =
                    health.health()
                            + "/"
                            + health.bottleneck()
                            + "/"
                            + health.diagnosisText()
                            + "/"
                            + pools.stream().map(pool -> pool.name() + ":" + pool.health()).toList();
            var changed = !verdict.equals(lastReportedVerdict);
            lastReportedVerdict = verdict;
            if (!changed && !logger.isDebugEnabled()) return;

            log(changed,
                    """
                    ---------------------------- server status ----------------------------
                    health:      {}
                    CPU:         process {} / system {}
                    JVM memory:  {}MB / {}MB ({})
                    host memory: {}MB / {}MB ({})
                    started:     {}
                    sampled:     {}
                    uptime:      {}
                    GC:          {} collections / {} ms
                    bottleneck:  {}
                    diagnosis:   {}
                    suggestion:  {}""",
                    health.health(),
                    formatPercent(runtime.processCpuLoad()),
                    formatPercent(runtime.systemCpuLoad()),
                    runtime.usedJvmMemory() / 1024 / 1024,
                    runtime.maxJvmMemory() / 1024 / 1024,
                    formatPercent(health.jvmMemoryUsage()),
                    usedSystemMemoryMegabytes(runtime),
                    runtime.totalSystemMemory() / 1024 / 1024,
                    formatPercent(health.systemMemoryUsage()),
                    runtime.startedAtText(),
                    runtime.sampledAtText(),
                    runtime.uptimeText(),
                    runtime.gcCount(),
                    runtime.gcTimeMillis(),
                    health.bottleneck(),
                    health.diagnosisText(),
                    health.suggestion());

            pools.forEach(pool -> logThreadPool(pool, changed));
            log(changed, "-----------------------------------------------------------------------");
        } catch (Throwable t) {
            // A monitor that kills its own schedule by throwing is worse than no monitor:
            // scheduleAtFixedRate cancels the task on the first exception and never says so.
            logger.warn("Failed to log the server status.", t);
        }
    }

    private static long usedSystemMemoryMegabytes(ServerRuntimeSnapshot runtime) {
        if (runtime.totalSystemMemory() < 0 || runtime.freeSystemMemory() < 0) return -1L;
        return (runtime.totalSystemMemory() - runtime.freeSystemMemory()) / 1024 / 1024;
    }

    /** Negative means the figure was not available, not that it was zero. */
    private static String formatPercent(double value) {
        return value < 0 ? "sampling" : String.format(Locale.ROOT, "%.2f%%", value * 100D);
    }

    private static String formatCapacity(int capacity) {
        return capacity < 0 ? "unbounded" : Integer.toString(capacity);
    }

    /** Logs at INFO when this readout says something new, and at DEBUG when it repeats. */
    private static void log(boolean newsworthy, String format, Object... args) {
        if (newsworthy) {
            logger.info(format, args);
        } else {
            logger.debug(format, args);
        }
    }

    private static void logThreadPool(ThreadPoolSnapshot snapshot, boolean newsworthy) {
        log(newsworthy,
                """
                pool {}
                  type {} / health {} / {}
                  threads: active {} / live {} / core {} / max {}
                  queue:   {} / {}
                  tasks:   submitted {} / completed {} / failed {} / rejected {}
                  timing:  average {}ms / longest {}ms
                  {}""",
                snapshot.name(),
                snapshot.type(),
                snapshot.health(),
                snapshot.lifecycleState(),
                snapshot.activeCount(),
                snapshot.currentPoolSize(),
                snapshot.corePoolSize(),
                snapshot.maximumPoolSize(),
                snapshot.queueSize(),
                formatCapacity(snapshot.queueCapacity()),
                snapshot.submittedTaskCount(),
                snapshot.completedTaskCount(),
                snapshot.failedTaskCount(),
                snapshot.rejectedTaskCount(),
                snapshot.averageExecutionMillis(),
                snapshot.maxExecutionMillis(),
                snapshot.diagnosisText());
    }
}
