package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 风神瞳等带战斗锁定的机关被标成战斗中时，客户端会一直锁镜头。 */
final class GadgetAiInfoTest {
    @Test
    void gadgetsAreNotMarkedInCombat() {
        var info = EntityBaseGadget.gadgetAiInfo().build();
        assertFalse(info.getIsEnteredCombat());
    }
}
