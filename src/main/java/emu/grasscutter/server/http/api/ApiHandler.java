package emu.grasscutter.server.http.api;

import emu.grasscutter.server.http.Router;
import io.javalin.Javalin;

/** Read-only status endpoints, for uptime monitors and server-list widgets. */
public final class ApiHandler implements Router {
    public static final int ERROR_RET_CODE = -1;
    public static final int SUCCESS_RET_CODE = 0;

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get("/api/help", ServerStatusHandler::listRoutes);
        javalin.get("/api/status", ServerStatusHandler::serverStatus);
        // /status/server is not registered here: GenericHandler already serves it, in the same
        // shape, and Javalin throws on a second handler for the same method and path - which
        // aborts the whole router registration, not just this route.
    }
}
