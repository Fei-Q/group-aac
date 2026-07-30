package com.example.groupaac.data.realtime.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealtimeEventRouterTest {

    @Test
    fun routesDisplayControlChannel() {
        val route =
            RealtimeEventRouter.route(
                "display.pi-1.control"
            )

        assertEquals(
            RealtimeRouteKind.DISPLAY_CONTROL,
            route.kind
        )
        assertEquals(
            "pi-1",
            route.displayId
        )
        assertNull(route.sessionId)
    }

    @Test
    fun routesDisplayDeviceEventsChannel() {
        val route =
            RealtimeEventRouter.route(
                "display.pi-1.events"
            )

        assertEquals(
            RealtimeRouteKind.DISPLAY_DEVICE_EVENTS,
            route.kind
        )
        assertEquals(
            "pi-1",
            route.displayId
        )
        assertNull(route.sessionId)
    }

    @Test
    fun routesSessionDisplayEventsSeparately() {
        val route =
            RealtimeEventRouter.route(
                "session.session-1.display.events"
            )

        assertEquals(
            RealtimeRouteKind.DISPLAY_EVENTS,
            route.kind
        )
        assertEquals(
            "session-1",
            route.sessionId
        )
        assertNull(route.displayId)
    }
}