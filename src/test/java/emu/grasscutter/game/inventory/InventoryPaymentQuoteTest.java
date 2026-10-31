package emu.grasscutter.game.inventory;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class InventoryPaymentQuoteTest {
    @Test
    void combinesRepeatedCurrencyAndMultipliesPurchaseCount() {
        assertEquals(Map.of(202, 600, 201, 10),
                InventoryPaymentQuote.calculate(
                        List.of(new ItemParamData(202, 100), new ItemParamData(202, 200),
                                new ItemParamData(201, 5), new ItemParamData(203, 0)),
                        2));
    }

    @Test
    void rejectsNegativePricesAndMultiplicationOverflow() {
        assertNull(InventoryPaymentQuote.calculate(List.of(new ItemParamData(202, -1)), 1));
        assertNull(InventoryPaymentQuote.calculate(List.of(new ItemParamData(202, 1)), 0));
        assertNull(InventoryPaymentQuote.calculate(
                List.of(new ItemParamData(202, Integer.MAX_VALUE)), 2));
        assertNull(InventoryPaymentQuote.calculate(
                List.of(new ItemParamData(202, Integer.MAX_VALUE),
                        new ItemParamData(202, 1)), 1));
    }

    @Test
    void zeroCostsRemainPayable() {
        assertEquals(Map.of(), InventoryPaymentQuote.calculate(
                List.of(new ItemParamData(201, 0), new ItemParamData(202, 0)), 3));
    }

    @Test
    void ignoresEmptyExcelCostSlots() {
        assertEquals(
                Map.of(100017, 2),
                InventoryPaymentQuote.calculate(
                        List.of(
                                new ItemParamData(100017, 1),
                                new ItemParamData(0, 0),
                                new ItemParamData(0, 0)),
                        2));
    }

    @Test
    void rejectsNonzeroCostsWithInvalidItemIds() {
        assertNull(InventoryPaymentQuote.calculate(List.of(new ItemParamData(0, 1)), 1));
    }
}
