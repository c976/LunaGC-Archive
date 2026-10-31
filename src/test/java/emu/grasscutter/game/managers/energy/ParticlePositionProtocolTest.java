package emu.grasscutter.game.managers.energy;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.proto.AbilityActionGenerateElemBallOuterClass.AbilityActionGenerateElemBall;
import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Real 7.1 skill payloads: old fields 1/7 silently place particles at world origin. */
final class ParticlePositionProtocolTest {
    @ParameterizedTest(name = "{0} particle position")
    @CsvSource({
        "Kazuha,5a0f0d5f3f2445152a6580431d451ad2c4720515e21b8842,2627.9607,256.79034,-1680.8209,68.05446",
        "Arlecchino,5a0f0dbf3224451588d97d431d9a1ad2c4720515ae299943,2627.1716,253.84973,-1680.8313,306.32562"
    })
    void capturedSkillPacketsDecodeTheirActualSpawnPosition(
            String character, String hex, float x, float y, float z, float yaw) throws Exception {
        var packet = AbilityActionGenerateElemBall.parseFrom(HexFormat.of().parseHex(hex));
        assertTrue(packet.hasPos(), character + " must not spawn particles at the default origin");
        assertTrue(packet.hasRot());
        assertEquals(x, packet.getPos().getX(), 0.001f);
        assertEquals(y, packet.getPos().getY(), 0.001f);
        assertEquals(z, packet.getPos().getZ(), 0.001f);
        assertEquals(yaw, packet.getRot().getY(), 0.001f);
        assertEquals(11, AbilityActionGenerateElemBall.POS_FIELD_NUMBER);
        assertEquals(14, AbilityActionGenerateElemBall.ROT_FIELD_NUMBER);
        assertEquals(packet, AbilityActionGenerateElemBall.parseFrom(packet.toByteArray()));
    }
}
