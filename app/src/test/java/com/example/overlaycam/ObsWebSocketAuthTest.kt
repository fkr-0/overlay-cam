package com.example.overlaycam

import kotlin.test.Test
import kotlin.test.assertEquals

class ObsWebSocketAuthTest {
    @Test
    fun computesObsWebSocketV5AuthenticationDigest() {
        assertEquals(
            "V8pVriFPEtnaK7wzQPlqOgkXegTAwSevsIeJLiFx/Nw=",
            ObsWebSocketAuth.computeAuthentication(
                password = "supersecret",
                salt = "salt123",
                challenge = "challenge456",
            ),
        )
    }
}
