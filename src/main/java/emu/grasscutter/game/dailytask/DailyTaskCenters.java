package emu.grasscutter.game.dailytask;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.world.Position;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Authored Lua coordinates missing from the flattened JSON; evaluated once per scene on demand. */
final class DailyTaskCenters {
    private DailyTaskCenters() {}

    static Map<String, Position> load(Path path) {
        try (var reader = Files.newBufferedReader(path)) {
            return read(reader);
        } catch (IOException | RuntimeException failure) {
            Grasscutter.getLogger()
                    .warn(
                            "[DailyTask] Cannot read commission dummy points from {}.",
                            path,
                            failure);
            return Map.of();
        }
    }

    static Map<String, Position> read(Reader reader) {
        // The dummy-point resource only declares tables. No libraries, ScriptLib, I/O or require
        // are installed, and it cannot mutate the shared game-script globals.
        var globals = new Globals();
        LuaC.install(globals);
        globals.load(reader, "commission-dummy-points").call();
        var points = globals.get("dummy_points");
        if (!points.istable()) return Map.of();
        var result = new HashMap<String, Position>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            var entry = points.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TSTRING || !entry.arg(2).istable()) continue;
            var pos = entry.arg(2).get("pos");
            if (!pos.istable()) continue;
            var x = pos.get("x");
            var y = pos.get("y");
            var z = pos.get("z");
            if (x.type() != LuaValue.TNUMBER
                    || y.type() != LuaValue.TNUMBER
                    || z.type() != LuaValue.TNUMBER) continue;
            float px = x.tofloat(), py = y.tofloat(), pz = z.tofloat();
            if (Float.isFinite(px) && Float.isFinite(py) && Float.isFinite(pz))
                result.put(key.tojstring(), new Position(px, py, pz));
        }
        return Map.copyOf(result);
    }
}
