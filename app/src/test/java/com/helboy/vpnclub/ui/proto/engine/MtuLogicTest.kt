package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.data.net.MtuOverhead
import dev.valid7996.narcicgeteway.data.net.MtuOverheads
import dev.valid7996.narcicgeteway.data.net.MtuResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MtuLogicTest {
    private fun measured(recommended: Int = 1420, toServer: Boolean = true, confirmed: Boolean = true) = MtuResult.Measured(
        pathMtu = 1500, recommended = recommended, linkMtu = 1500, host = "example.org",
        overhead = MtuOverheads.worstCase(), confirmed = confirmed, toServer = toServer,
    )

    @Test fun manualEntryIsCheckedAgainstTheAllowedRange() {
        assertEquals(Checked.Ok(1400), MtuLogic.check("1400"))
        assertEquals(Checked.Ok(MtuLogic.MIN_ALLOWED), MtuLogic.check(MtuLogic.MIN_ALLOWED.toString()))
        assertEquals(Checked.Ok(MtuLogic.MAX_ALLOWED), MtuLogic.check(MtuLogic.MAX_ALLOWED.toString()))
        assertTrue(MtuLogic.check("999") is Checked.Bad)
        assertTrue(MtuLogic.check("9001") is Checked.Bad)
        assertTrue(MtuLogic.check("14oo") is Checked.Bad)
        assertTrue(MtuLogic.check("") is Checked.Bad)
    }

    @Test fun unusualButLegalValuesGetAWarning() {
        assertNull(MtuLogic.softWarning(1500))
        assertNull(MtuLogic.softWarning(1280))
        assertNotNull(MtuLogic.softWarning(1100))
        assertNotNull(MtuLogic.softWarning(9000))
    }

    @Test fun presetsAreAllValid() {
        MtuLogic.PRESETS.forEach { assertTrue(MtuLogic.check(it.toString()) is Checked.Ok) }
    }

    @Test fun onlyAMeasurementCanBeApplied() {
        assertEquals(1420, MtuLogic.applicable(measured(1420)))
        assertNull(MtuLogic.applicable(MtuResult.VpnActive))
        assertNull(MtuLogic.applicable(MtuResult.NotMeasurable(1500)))
        assertNull(MtuLogic.applicable(null))
    }

    @Test fun describeFlagsVpnAndUnmeasurableAsProblems() {
        assertTrue(MtuLogic.describe(MtuResult.VpnActive).all { it.bad })
        val lines = MtuLogic.describe(MtuResult.NotMeasurable(1492))
        assertTrue(lines.first().bad)
        assertEquals(2, lines.size)
        assertEquals(1, MtuLogic.describe(MtuResult.NotMeasurable(null)).size)
    }

    @Test fun describeMeasuredSaysWhereItProbedAndWhetherItWasConfirmed() {
        val toServer = MtuLogic.describe(measured(toServer = true))
        assertFalse(toServer.any { it.bad })
        val toPublic = MtuLogic.describe(measured(toServer = false))
        assertTrue(toPublic.any { it.bad })
        val unconfirmed = MtuLogic.describe(measured(confirmed = false))
        assertTrue(unconfirmed.any { it.bad })
    }

    @Test fun noProfileMeansNoServerHint() {
        assertNull(MtuLogic.serverHint(null))
    }

    @Test fun overheadExplanationIsIncludedInTheText() {
        val overhead = MtuOverhead(40, listOf(MtuOverhead.Part("IPv4", 20), MtuOverhead.Part("TCP", 20)))
        val r = MtuResult.Measured(1500, 1460, null, "h", overhead, confirmed = true, toServer = true)
        assertTrue(MtuLogic.describe(r).any { it.text.contains("IPv4") })
    }
}
