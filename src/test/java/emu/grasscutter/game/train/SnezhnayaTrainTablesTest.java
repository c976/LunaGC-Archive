package emu.grasscutter.game.train;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SnezhnayaTrainTablesTest {
    private static SnezhnayaTrainSystem.Tables tables() {
        var t = new SnezhnayaTrainSystem.Tables();
        // From SnezhnayaTrainStationMapConfigData / SnezhnayaTrackToCurveConfigData 7.1.
        t.stationByCurve.put(371200039, 2);
        t.stationByCurve.put(371200069, 2);
        for (int c : new int[] {371200008, 371200038, 371200039}) t.forwardByCurve.put(c, 371200069);
        return t;
    }

    @Test
    void stationComesFromTheCurve() {
        assertEquals(2, tables().stationOf(371200039));
        assertEquals(0, tables().stationOf(1));
    }

    @Test
    void departureMovesOntoTheForwardCurve() {
        var route = List.of(371200007, 371200008, 371200038, 371200069, 371200070, 371200041, 371200042);
        // The captured train stood on 371200039 and departed onto 371200069.
        assertEquals(371200069, tables().nextCurve(371200039, route));
        assertEquals(371200070, tables().nextCurve(371200069, route));
    }
}
