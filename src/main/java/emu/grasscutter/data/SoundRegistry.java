package emu.grasscutter.data;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Names of sound events the server has actually sent in this process.
 *
 * <p>The 7.1 resource pack does not supply a complete sound event catalog. Never
 * claim this is a comprehensive list; accumulate names as Lua scenes play sounds
 * and as administrators issue sound commands.
 */
public final class SoundRegistry {
    private static final ConcurrentSkipListSet<String> NAMES =
            new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

    private SoundRegistry() {}

    public static void observe(String soundName) {
        if (soundName != null && !soundName.isBlank()) NAMES.add(soundName);
    }

    public static List<String> names(String filter) {
        String query = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        return NAMES.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    /** JLine queries the live set when a user requests Tab completion. */
    public static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return names(null).iterator();
        }
    }
}
