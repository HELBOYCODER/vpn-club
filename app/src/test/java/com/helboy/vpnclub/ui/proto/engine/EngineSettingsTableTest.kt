package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.RunMode
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSettingsTableTest {
    private val d = EngineDefaults.settings
    private val all = EngineSettingsTable.sections.flatMap { it.controls }

    private fun appSettingsFields(): Set<String> =
        AppSettings::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

    @Test fun everyAppSettingsFieldIsCoveredOrExplicitlyExcluded() {
        val fields = appSettingsFields()
        val covered = EngineSettingsTable.covered
        val excluded = EngineSettingsTable.excluded.keys
        assertEquals("fields with neither control nor reason", emptySet<String>(), fields - covered - excluded)
        assertEquals("table names a field AppSettings does not have", emptySet<String>(), (covered + excluded) - fields)
        assertEquals("fields both covered and excluded", emptySet<String>(), covered.intersect(excluded))
    }

    @Test fun controlIdsAndSectionIdsAreUnique() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        val sections = EngineSettingsTable.sections
        assertEquals(sections.size, sections.map { it.id }.toSet().size)
    }

    @Test fun noFieldIsOwnedByTwoSections() {
        val seen = mutableSetOf<String>()
        EngineSettingsTable.sections.forEach { s ->
            s.covers.forEach { assertTrue("${it} appears twice", seen.add(it)) }
        }
    }

    @Test fun everyExclusionHasAReason() {
        EngineSettingsTable.excluded.forEach { (k, v) -> assertTrue(k, v.isNotBlank()) }
    }

    @Test fun switchesWriteWhatTheyRead() {
        all.filterIsInstance<SwitchControl>().forEach { c ->
            val flipped = !c.get(d)
            assertEquals(c.id, flipped, c.get(c.set(d, flipped)))
            assertEquals(c.id, c.get(d), c.get(c.restore(c.set(d, flipped), d)))
        }
    }

    @Test fun choicesWriteWhatTheyReadForEveryOption() {
        all.filterIsInstance<ChoiceControl>().forEach { c ->
            assertTrue("${c.id} default is not an option", c.options.any { it.first == c.get(d) })
            c.options.forEach { (id, _) -> assertEquals(c.id, id, c.get(c.set(d, id))) }
        }
    }

    @Test fun numbersAcceptTheirBoundsAndDefaultsAreInside() {
        all.filterIsInstance<NumberControl>().forEach { c ->
            assertTrue("${c.id} default ${c.get(d)}", c.get(d) in c.min..c.max)
            assertEquals(c.id, c.min, c.get(c.set(d, c.min)))
            assertEquals(c.id, c.max, c.get(c.set(d, c.max)))
            assertTrue(c.id, c.min <= c.max)
        }
    }

    @Test fun textDefaultsPassTheirOwnValidationWhereTheyMustBeSet() {
        val mustBeValid = setOf(
            "delayTestUrl", "ipApiUrl", "fragmentLength", "fragmentInterval", "hevTunRwTimeout",
            "vpnDns", "remoteDns", "directDns", "singBoxNtpServer", "torVirtualAddrNetwork",
            "rstaSpoofConnectIp", "rstaSpoofFakeSni",
        )
        all.filterIsInstance<TextControl>().filter { it.id in mustBeValid }.forEach { c ->
            assertNull("${c.id} default '${c.get(d)}'", c.validate(c.get(d)))
        }
    }

    @Test fun textControlsWriteWhatTheyRead() {
        all.filterIsInstance<TextControl>().forEach { c ->
            assertEquals(c.id, "x", c.get(c.set(d, "x")))
        }
    }

    @Test fun restoringASectionPutsItsFieldsBackAndLeavesOthers() {
        val changed = d.copy(killSwitch = true, autoConnectOnBoot = true, runMode = RunMode.ProxyOnly, vpnMtu = 1300, muxEnabled = true)
        val restored = EngineSettingsTable.section("mode")!!.restore(changed, d)
        assertEquals(d.killSwitch, restored.killSwitch)
        assertEquals(d.autoConnectOnBoot, restored.autoConnectOnBoot)
        assertEquals(d.runMode, restored.runMode)
        assertEquals(1300, restored.vpnMtu)
        assertTrue(restored.muxEnabled)
    }

    @Test fun restoringTunSectionResetsBothFlags() {
        val changed = d.copy(useHevTun = true, useZepTun = false, hevTunRwTimeout = "1,1")
        val restored = EngineSettingsTable.section("tun")!!.restore(changed, d)
        assertEquals(d.useHevTun, restored.useHevTun)
        assertEquals(d.useZepTun, restored.useZepTun)
        assertEquals(d.hevTunRwTimeout, restored.hevTunRwTimeout)
    }

    @Test fun restoringEverySectionFromAnyStateEqualsDefaultsForItsFields() {
        val tweaked = d.copy(
            killSwitch = true, vpnMtu = 1234, remoteDns = "https://example.org/dns", muxEnabled = true, fragmentEnabled = true,
            singBoxMuxEnabled = true, delayTestUrl = "https://x.test", torStrictNodes = true, sshCompression = true,
            speedInNotification = false, rstaSpoofFakeSni = "x.example", autoSelectRetry = false, dnsPoolEnabled = true,
            proxySharing = true, useHevTun = true, useZepTun = false,
        )
        val restored = EngineSettingsTable.sections.fold(tweaked) { acc, s -> s.restore(acc, d) }
        assertEquals(d, restored)
    }

    @Test fun turningSharingOnCreatesCredentialsAndOffKeepsThem() {
        val sharing = all.filterIsInstance<SwitchControl>().first { it.id == "proxySharing" }
        val on = sharing.set(d, true)
        assertTrue(on.proxySharing)
        assertTrue(on.socksUsername.isNotBlank())
        assertTrue(on.socksPassword.isNotBlank())
        val off = sharing.set(on, false)
        assertFalse(off.proxySharing)
        assertEquals(on.socksPassword, off.socksPassword)
    }

    @Test fun turningLocalProxyOffAlsoDropsTheHttpProxy() {
        val local = all.filterIsInstance<SwitchControl>().first { it.id == "enableLocalProxy" }
        val s = local.set(d.copy(appendHttpProxy = true), false)
        assertFalse(s.enableLocalProxy)
        assertFalse(s.appendHttpProxy)
    }

    @Test fun dependentControlsAreDisabledUntilTheirSwitchIsOn() {
        val fake = all.first { it.id == "fakeDnsEnabled" }
        assertFalse(fake.enabledWhen(d.copy(localDnsEnabled = false)))
        assertTrue(fake.enabledWhen(d.copy(localDnsEnabled = true)))
        val mtu = all.first { it.id == "vpnMtu" }
        assertTrue(mtu.enabledWhen(d.copy(runMode = RunMode.Vpn)))
        assertFalse(mtu.enabledWhen(d.copy(runMode = RunMode.ProxyOnly)))
        val hevLog = all.first { it.id == "hevTunLogLevel" }
        assertFalse(hevLog.visibleWhen(d.copy(useHevTun = false)))
        assertTrue(hevLog.visibleWhen(d.copy(useHevTun = true)))
    }

    @Test fun tunEngineControlOffersAllThreeEngines() {
        val tun = all.filterIsInstance<ChoiceControl>().first { it.id == "tunEngine" }
        assertEquals(setOf("Xray", "Hev", "Zep"), tun.options.map { it.first }.toSet())
        assertEquals(setOf("useHevTun", "useZepTun"), tun.covers)
        assertEquals(TunEngine.Xray.name, tun.get(d))
    }

    @Test fun nextConnectFlagIsSetOnEngineSectionsButNotOnLiveOnes() {
        assertTrue(EngineSettingsTable.section("tun")!!.nextConnect)
        assertTrue(EngineSettingsTable.section("dns")!!.nextConnect)
        assertFalse(EngineSettingsTable.section("tests")!!.nextConnect)
        assertFalse(EngineSettingsTable.section("app")!!.nextConnect)
    }
}
