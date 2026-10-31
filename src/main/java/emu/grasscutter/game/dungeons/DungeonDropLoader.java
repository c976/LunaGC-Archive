/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.dungeons.DungeonDrop;
import emu.grasscutter.game.dungeons.DungeonDropEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

public final class DungeonDropLoader {
    private static volatile boolean loaded;
    private static volatile long loadedFileTime;

    private DungeonDropLoader() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void ensureLoaded() {
        Path path = DungeonDropLoader.resolveDropPath();
        long l = DungeonDropLoader.readFileTime(path);
        if (!GameData.getDungeonDropDataMap().isEmpty() && loaded && l == loadedFileTime) {
            return;
        }
        Class<DungeonDropLoader> clazz = DungeonDropLoader.class;
        synchronized (DungeonDropLoader.class) {
            l = DungeonDropLoader.readFileTime(path);
            if (!GameData.getDungeonDropDataMap().isEmpty() && loaded && l == loadedFileTime) {
                // ** MonitorExit[var3_2] (shouldn't be in output)
                return;
            }
            DungeonDropLoader.forceReloadLocked(path, l);
            // ** MonitorExit[var3_2] (shouldn't be in output)
            return;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void forceReload() {
        Class<DungeonDropLoader> clazz = DungeonDropLoader.class;
        synchronized (DungeonDropLoader.class) {
            Path path = DungeonDropLoader.resolveDropPath();
            DungeonDropLoader.forceReloadLocked(path, DungeonDropLoader.readFileTime(path));
            // ** MonitorExit[var0] (shouldn't be in output)
            return;
        }
    }

    private static void forceReloadLocked(Path path, long l) {
        try {
            List<DungeonDrop> list = DataLoader.loadList("DungeonDrop.json", DungeonDrop.class);
            GameData.getDungeonDropDataMap().clear();
            int n = 0;
            for (DungeonDrop dungeonDrop : list) {
                if (dungeonDrop == null || dungeonDrop.getDrops() == null || dungeonDrop.getDrops().isEmpty()) continue;
                GameData.getDungeonDropDataMap().put(dungeonDrop.getDungeonId(), dungeonDrop.getDrops());
                ++n;
            }
            loaded = true;
            loadedFileTime = l;
            int n2 = DungeonDropLoader.firstArtifactId(4513);
            Grasscutter.getLogger().info("Loaded {} dungeon drop table(s) from DungeonDrop.json (4513 first artifact={}).", (Object)n, (Object)n2);
        }
        catch (Exception exception) {
            loaded = false;
            loadedFileTime = Long.MIN_VALUE;
            Grasscutter.getLogger().error("Failed to load DungeonDrop.json; domain artifacts will not drop.", exception);
        }
    }

    private static int firstArtifactId(int n) {
        List<DungeonDropEntry> list = GameData.getDungeonDropDataMap().get(n);
        if (list == null) {
            return -1;
        }
        for (DungeonDropEntry dungeonDropEntry : list) {
            int n2;
            if (dungeonDropEntry == null || dungeonDropEntry.getItems() == null || dungeonDropEntry.getItems().isEmpty() || (n2 = dungeonDropEntry.getItems().get(0).intValue()) < 20000) continue;
            return n2;
        }
        return -1;
    }

    private static Path resolveDropPath() {
        Path path = Path.of("", new String[0]).toAbsolutePath();
        Path path2 = path.resolve("data").resolve("DungeonDrop.json");
        if (Files.isRegularFile(path2, new LinkOption[0])) {
            return path2;
        }
        Path path3 = Path.of("G:/LunaGC_from_189/LunaGC/data/DungeonDrop.json", new String[0]);
        if (Files.isRegularFile(path3, new LinkOption[0])) {
            return path3;
        }
        return Path.of("D:/Dev/data/DungeonDrop.json", new String[0]);
    }

    private static long readFileTime(Path path) {
        try {
            if (path != null && Files.isRegularFile(path, new LinkOption[0])) {
                FileTime fileTime = Files.getLastModifiedTime(path, new LinkOption[0]);
                return fileTime != null ? fileTime.toMillis() : Long.MIN_VALUE;
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
        return Long.MIN_VALUE;
    }

    public static boolean isLoaded() {
        return loaded && !GameData.getDungeonDropDataMap().isEmpty();
    }

    static {
        loadedFileTime = Long.MIN_VALUE;
    }
}
