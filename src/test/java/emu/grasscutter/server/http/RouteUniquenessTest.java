package emu.grasscutter.server.http;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails when two routers register the same method and path.
 *
 * <p>Javalin throws on the second registration, and that throw escapes addRouter and aborts the
 * whole router setup - so one duplicate route does not disable itself, it takes every route
 * registered after it down with it. The server still starts, and the only sign is one warning
 * buried in the log.
 *
 * <p>This reads the sources rather than starting Javalin, so it needs no server and no database.
 */
public final class RouteUniquenessTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/emu/grasscutter");

    /** Matches javalin.get("/path", ...) and the other verbs. */
    private static final Pattern ROUTE =
            Pattern.compile("javalin\\s*\\.\\s*(get|post|put|patch|delete)\\s*\\(\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("no method and path is registered twice")
    public void routesAreUnique() throws IOException {
        Map<String, List<String>> registrations = new LinkedHashMap<>();

        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var source = Files.readString(file);
                var matcher = ROUTE.matcher(source);
                while (matcher.find()) {
                    var key = matcher.group(1).toUpperCase() + " " + matcher.group(2);
                    registrations
                            .computeIfAbsent(key, k -> new ArrayList<>())
                            .add(file.getFileName().toString());
                }
            }
        }

        var duplicates = new ArrayList<String>();
        registrations.forEach(
                (route, files) -> {
                    if (files.size() > 1) duplicates.add(route + " <- " + files);
                });

        assertTrue(
                duplicates.isEmpty(),
                "These routes are registered more than once, which aborts router setup:\n  "
                        + String.join("\n  ", duplicates));
    }

    @Test
    @DisplayName("the scan actually finds routes, so an empty result cannot pass by accident")
    public void scanFindsRoutes() throws IOException {
        var found = 0;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = ROUTE.matcher(Files.readString(file));
                while (matcher.find()) found++;
            }
        }

        assertTrue(found > 20, "only found " + found + " routes; the pattern has probably rotted");
    }
}
