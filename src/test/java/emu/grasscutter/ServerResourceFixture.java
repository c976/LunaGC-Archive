package emu.grasscutter;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Path;

/** Satisfies static startup checks without loading game resources or starting a server. */
public final class ServerResourceFixture implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Path resources = Path.of("resources");
        Path config = Path.of("config.json");
        if (Files.exists(config)) {
            try (var reader = Files.newBufferedReader(config)) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("folderStructure")) {
                    var folders = json.getAsJsonObject("folderStructure");
                    if (folders.has("resources")) {
                        resources = Path.of(folders.get("resources").getAsString());
                    }
                }
            }
        }
        Files.createDirectories(resources.resolve("BinOutput"));
        Files.createDirectories(resources.resolve("ExcelBinOutput"));
        // Configuration references Grasscutter during initialization; initialize the owner first.
        Class.forName("emu.grasscutter.Grasscutter");
    }
}
