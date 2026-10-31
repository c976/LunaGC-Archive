package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SnezhnayaTrainCallNotifyOuterClass.SnezhnayaTrainCallNotify;
import emu.grasscutter.net.proto.SnezhnayaTrainCallReqOuterClass.SnezhnayaTrainCallReq;
import emu.grasscutter.net.proto.SnezhnayaTrainSetRouteReqOuterClass.SnezhnayaTrainSetRouteReq;
import emu.grasscutter.net.proto.SnezhnayaTrainStateNotifyOuterClass.SnezhnayaTrainState;
import emu.grasscutter.net.proto.SnezhnayaTrainStateNotifyOuterClass.SnezhnayaTrainStateNotify;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bodies captured from an official 7.1 session calling the Snezhnaya train. */
final class SnezhnayaTrainProtocolTest {
    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s);
    }

    @Test
    void opcodesMatchTheCapture() {
        assertEquals(3893, PacketOpcodes.SnezhnayaTrainCallReq);
        assertEquals(26580, PacketOpcodes.SnezhnayaTrainCallNotify);
        assertEquals(23234, PacketOpcodes.SnezhnayaTrainStateNotify);
        assertEquals(28390, PacketOpcodes.SnezhnayaTrainSetRouteReq);
    }

    @Test
    void callReqCarriesTheCurveTheClientStandsBy() throws Exception {
        var req = SnezhnayaTrainCallReq.parseFrom(hex(
                "722010a7a080b10118042574aca63e2d1f9697403a0c0a0aa6a080b101a7a080b101"));
        var info = req.getCurveInfo();
        assertEquals(371200039, info.getEEFABNDBKCO());
        assertEquals(4, info.getHMHCGLCNBMK());
        assertEquals(0.3255f, info.getDEEKKONIOPE(), 1e-4f);
        assertEquals(4.737f, info.getSpeed(), 1e-3f);
        assertEquals(List.of(371200038, 371200039), info.getBCOFNPNHJCI(0).getNDABJFDJBENList());
    }

    @Test
    void callNotifyIsTheStation() throws Exception {
        assertEquals(2, SnezhnayaTrainCallNotify.parseFrom(hex("4002")).getStationId());
        assertEquals("4002", HexFormat.of().formatHex(
                SnezhnayaTrainCallNotify.newBuilder().setStationId(2).build().toByteArray()));
    }

    @Test
    void stateNotifyBeforeAndAfterDeparture() throws Exception {
        var waiting = SnezhnayaTrainStateNotify.parseFrom(hex(
                "0a0f0d97840546153d1152431d20d8f44558a7a080b1016004"));
        assertEquals(371200039, waiting.getCurveId());
        assertEquals(4, waiting.getSegment());
        assertEquals(8545.1f, waiting.getPos().getX(), 0.1f);
        assertEquals(SnezhnayaTrainState.SNEZHNAYA_TRAIN_STATE_NONE, waiting.getState());

        var running = SnezhnayaTrainStateNotify.parseFrom(hex(
                "0a0f0d0ca1054615921152431d499af34510033a02010258c5a080b1016004680172030102037801"));
        assertEquals(371200069, running.getCurveId());
        assertEquals(3, running.getDestStationId());
        assertEquals(1, running.getSourceId());
        assertEquals(List.of(1, 2), running.getReachedStationListList());
        assertEquals(List.of(1, 2, 3), running.getStationListList());
        assertEquals(SnezhnayaTrainState.SNEZHNAYA_TRAIN_STATE_RUNNING, running.getState());
    }

    @Test
    void setRouteReqNamesTheDestination() throws Exception {
        var req = SnezhnayaTrainSetRouteReq.parseFrom(hex(
                "1001220301020330033a250a2387a080b10188a080b101a6a080b101c5a080b101c6a080b101a9a080b101aaa080b101"));
        assertEquals(1, req.getSourceId());
        assertEquals(3, req.getDestStationId());
        assertEquals(List.of(1, 2, 3), req.getStationListList());
        assertEquals(
                List.of(371200007, 371200008, 371200038, 371200069, 371200070, 371200041, 371200042),
                req.getRouteList(0).getPointListList());
    }
}
