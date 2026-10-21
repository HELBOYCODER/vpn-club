package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.data.assets.GeoAsset
import dev.valid7996.narcicgeteway.domain.model.GeoFilesSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AssetsLogicTest {
    private val local = GeoAsset("geoip.dat", present = true, sizeBytes = 1000, updatedAt = 1_000_000)

    @Test fun missingFileIsMissingWhateverTheServerSays() {
        assertEquals(UpdateState.Missing, updateState(local.copy(present = false), RemoteInfo(1000, null)))
    }

    @Test fun unknownWithoutRemoteInfo() {
        assertEquals(UpdateState.Unknown, updateState(local, null))
        assertEquals(UpdateState.Unknown, updateState(local, RemoteInfo(null, null)))
    }

    @Test fun differentSizeOrNewerDateMeansUpdate() {
        assertEquals(UpdateState.UpdateAvailable, updateState(local, RemoteInfo(2000, null)))
        assertEquals(UpdateState.UpdateAvailable, updateState(local, RemoteInfo(null, 1_000_000 + 120_000)))
    }

    @Test fun sameSizeAndNotNewerIsUpToDate() {
        assertEquals(UpdateState.UpToDate, updateState(local, RemoteInfo(1000, 1_000_000 + 30_000)))
        assertEquals(UpdateState.UpToDate, updateState(local, RemoteInfo(1000, null)))
    }

    @Test fun urlsFollowTheChosenSource() {
        assertEquals(
            "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geosite.dat",
            geoUrl(GeoFilesSource.Chocolate4U, "geosite.dat"),
        )
    }

    @Test fun tinyFilesAreRejected() {
        assertFalse(looksLikeGeoFile(512))
        assertTrue(looksLikeGeoFile(5_000_000))
    }

    @Test fun progressFraction() {
        assertNull(progressFraction(10, null))
        assertNull(progressFraction(10, 0))
        assertEquals(0.5f, progressFraction(50, 100)!!, 0.0001f)
        assertEquals(1f, progressFraction(500, 100)!!, 0.0001f)
    }

    @Test fun errorMessagesAreHumanAndNeverLeakTheClassName() {
        listOf(UnknownHostException("x"), SocketTimeoutException("x"), GeoHttpException(404), GeoHttpException(503), GeoBadFileException("x"), RuntimeException("boom"))
            .forEach { e ->
                val m = fetchErrorMessage(e)
                assertTrue(m.isNotBlank())
                assertFalse(m.contains("Exception"))
                assertFalse(m.contains("boom"))
            }
        assertTrue(fetchErrorMessage(GeoHttpException(404)).contains("پیدا نشد"))
    }
}
