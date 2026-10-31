/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.bootstrap;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.gacha.GachaEpitomizedCompanionHelper;
import emu.grasscutter.game.gacha.GachaEpitomizedPrefabHelper;

public final class GachaBootstrap {
    private static volatile boolean LOADED;

    private GachaBootstrap() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void ensureLoaded() {
        if (LOADED) {
            return;
        }
        Class<GachaBootstrap> clazz = GachaBootstrap.class;
        synchronized (GachaBootstrap.class) {
            if (LOADED) {
                // ** MonitorExit[var0] (shouldn't be in output)
                return;
            }
            try {
                GachaEpitomizedPrefabHelper.load();
                GachaEpitomizedCompanionHelper.load();
                LOADED = true;
                Grasscutter.getLogger().info("GachaBootstrap: loaded epitomized tables");
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().error("GachaBootstrap: failed to load gacha tables", throwable);
            }
            return;
        }
    }
}
