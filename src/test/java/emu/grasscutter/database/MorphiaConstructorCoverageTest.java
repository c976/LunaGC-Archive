package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.home.suite.HomeSuiteItem;
import emu.grasscutter.game.managers.cooking.ActiveCookCompoundData;
import emu.grasscutter.game.world.Location;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

final class MorphiaConstructorCoverageTest {
    @Test
    void persistedEntitiesHaveNoArgConstructors() {
        for (var type : new Class<?>[] {HomeSuiteItem.class, ActiveCookCompoundData.class, Location.class}) {
            assertDoesNotThrow(() -> type.getDeclaredConstructor(), type.getName());
        }
    }

    @Test
    void compoundFieldsCanBeLoadedFromMongo() throws Exception {
        assertFalse(Modifier.isFinal(ActiveCookCompoundData.class.getDeclaredField("costTime").getModifiers()));
        assertFalse(Modifier.isFinal(ActiveCookCompoundData.class.getDeclaredField("compoundId").getModifiers()));
    }
}
