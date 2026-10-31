package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import emu.grasscutter.game.achievement.Achievement;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.home.FurnitureMakeSlotItem;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.home.HomeAnimalItem;
import emu.grasscutter.game.home.HomeBlockItem;
import emu.grasscutter.game.home.HomeFurnitureItem;
import emu.grasscutter.game.home.HomeNPCItem;
import emu.grasscutter.game.home.HomeSceneItem;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

class MorphiaEntityConstructorTest {
    @Test
    void loginEntitiesProvideNoArgConstructors() {
        assertNoArgConstructor(Achievements.class);
        assertNoArgConstructor(Achievement.class);
        assertNoArgConstructor(GameHome.class);
        assertNoArgConstructor(FurnitureMakeSlotItem.class);
        assertNoArgConstructor(HomeAnimalItem.class);
        assertNoArgConstructor(HomeBlockItem.class);
        assertNoArgConstructor(HomeFurnitureItem.class);
        assertNoArgConstructor(HomeNPCItem.class);
        assertNoArgConstructor(HomeSceneItem.class);
    }

    private static void assertNoArgConstructor(Class<?> type) {
        assertDoesNotThrow(
                () -> {
                    Constructor<?> constructor = type.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    constructor.newInstance();
                },
                () -> type.getName() + " must remain constructible by Morphia");
    }
}
