package com.helboy.vpnclub.corex

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun stableVersionOrdering() {
        assertTrue(ReleaseVersion.isNewer("v4.10.0", "4.2.0"))
        assertFalse(ReleaseVersion.isNewer("v3.5.1", "4.1.0"))
        assertFalse(ReleaseVersion.isNewer("4.2", "4.2.0"))
        assertFalse(ReleaseVersion.isNewer("v4.3.0-beta.1", "4.2.0"))
        assertFalse(ReleaseVersion.isNewer("garbage", "4.2.0"))
    }
}
