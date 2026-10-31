package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.assertThrows;

import emu.grasscutter.net.proto.ClientScriptEventNotifyOuterClass.ClientScriptEventNotify;
import emu.grasscutter.scripts.constants.EventType;

import org.junit.jupiter.api.Test;

class ClientScriptEventNotifyTest {
    @Test
    void emptyNearPlatformParametersReachTheConfigIdWorkaround() {
        byte[] payload =
                ClientScriptEventNotify.newBuilder()
                        .setEventType(EventType.EVENT_AVATAR_NEAR_PLATFORM)
                        .build()
                        .toByteArray();

        // The empty list is no longer dropped: it goes on to the scene (null here) to look up the
        // source entity's config id, instead of failing on the absent first parameter.
        assertThrows(
                NullPointerException.class,
                () -> new HandlerClientScriptEventNotify().handle(null, new byte[0], payload));
    }

    @Test
    void emptyParametersForOtherEventsStillReachSceneAccess() {
        byte[] payload =
                ClientScriptEventNotify.newBuilder()
                        .setEventType(EventType.EVENT_PLATFORM_REACH_POINT)
                        .build()
                        .toByteArray();

        assertThrows(
                NullPointerException.class,
                () -> new HandlerClientScriptEventNotify().handle(null, new byte[0], payload));
    }
}
