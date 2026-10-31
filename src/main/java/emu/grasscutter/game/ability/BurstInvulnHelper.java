/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.player.Player;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class BurstInvulnHelper {
    private static final long DURATION_MS = 4500L;
    private static final ConcurrentHashMap<Integer, Long> UNTIL_MS = new ConcurrentHashMap();
    private static final Field INVULN_FIELD;
    private static final Method GET_PLAYER;
    private static final Field PLAYER_FIELD;

    private BurstInvulnHelper() {
    }

    public static void clearPlayerState(int uid) {
        UNTIL_MS.remove(uid);
    }

    private static Player playerOf(AbilityManager abilityManager) {
        Object object;
        if (abilityManager == null) {
            return null;
        }
        try {
            if (GET_PLAYER != null && (object = GET_PLAYER.invoke(abilityManager, new Object[0])) instanceof Player) {
                Player player = (Player)object;
                return player;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            if (PLAYER_FIELD != null && (object = PLAYER_FIELD.get(abilityManager)) instanceof Player) {
                Player player = (Player)object;
                return player;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    public static void arm(AbilityManager abilityManager) {
        if (abilityManager == null) {
            return;
        }
        try {
            Player player = BurstInvulnHelper.playerOf(abilityManager);
            if (player == null) {
                return;
            }
            long l = System.currentTimeMillis() + 4500L;
            UNTIL_MS.merge(player.getUid(), l, Math::max);
            if (INVULN_FIELD != null) {
                INVULN_FIELD.setBoolean(abilityManager, true);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static boolean isArmed(AbilityManager abilityManager) {
        if (abilityManager == null) {
            return false;
        }
        try {
            Player player = BurstInvulnHelper.playerOf(abilityManager);
            if (player == null) {
                return false;
            }
            Long l = UNTIL_MS.get(player.getUid());
            if (l == null) {
                return false;
            }
            long l2 = System.currentTimeMillis();
            if (l2 < l) {
                return true;
            }
            UNTIL_MS.remove(player.getUid(), l);
            if (INVULN_FIELD != null) {
                try {
                    INVULN_FIELD.setBoolean(abilityManager, false);
                }
                catch (Throwable throwable) {}
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    public static void clear(AbilityManager abilityManager) {
        if (abilityManager == null) {
            return;
        }
        try {
            Player player = BurstInvulnHelper.playerOf(abilityManager);
            if (player != null) {
                UNTIL_MS.remove(player.getUid());
            }
            if (INVULN_FIELD != null) {
                INVULN_FIELD.setBoolean(abilityManager, false);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    static {
        Field field = null;
        Method method = null;
        try {
            field = AbilityManager.class.getDeclaredField("abilityInvulnerable");
            field.setAccessible(true);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            method = AbilityManager.class.getDeclaredMethod("getPlayer", new Class[0]);
            method.setAccessible(true);
        }
        catch (Throwable throwable) {
            try {
                Field field2 = AbilityManager.class.getDeclaredField("player");
                field2.setAccessible(true);
            }
            catch (Throwable throwable2) {
                // empty catch block
            }
        }
        INVULN_FIELD = field;
        GET_PLAYER = method;
        field = null;
        try {
            field = AbilityManager.class.getDeclaredField("player");
            field.setAccessible(true);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        PLAYER_FIELD = field;
    }
}
