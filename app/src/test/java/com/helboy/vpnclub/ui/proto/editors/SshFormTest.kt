package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.SshProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshFormTest {
    private val valid = SshForm(host = "example.com", username = "root", password = "secret")

    @Test fun `an empty form is invalid and says what is missing`() {
        val c = SshForm().check()
        assertFalse(c.ok)
        assertNotNull(c.errors[SshForm.F_HOST])
        assertNotNull(c.errors[SshForm.F_PASSWORD])
    }

    @Test fun `host port and password make it valid`() {
        assertTrue(valid.check().ok)
    }

    @Test fun `a bad port blocks saving`() {
        assertNotNull(valid.copy(port = "70000").check().errors[SshForm.F_PORT])
        assertNotNull(valid.copy(port = "").check().errors[SshForm.F_PORT])
    }

    @Test fun `a host with a space is rejected`() {
        assertNotNull(valid.copy(host = "exa mple.com").check().errors[SshForm.F_HOST])
    }

    @Test fun `key auth needs a key, not a password`() {
        val keyForm = valid.copy(authType = SshProfile.AUTH_KEY, password = "")
        assertNotNull(keyForm.check().errors[SshForm.F_KEY])
        assertNull(keyForm.check().errors[SshForm.F_PASSWORD])
        val withKey = keyForm.copy(privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----")
        assertTrue(withKey.check().ok)
        assertTrue(keyForm.copy(privateKey = "not a key").check().warnings.containsKey(SshForm.F_KEY))
    }

    @Test fun `mapping to the profile trims and keeps the secrets`() {
        val p = SshForm(host = " h.example ", port = "2222", username = " me ", password = "pw", authType = SshProfile.AUTH_PASSWORD).toProfile()
        assertEquals("h.example", p.host)
        assertEquals(2222, p.port)
        assertEquals("me", p.username)
        assertEquals("pw", p.password)
    }

    @Test fun `from and back is lossless`() {
        val p = SshProfile("h.example", 2200, "u", SshProfile.AUTH_KEY, "pw", "KEY PRIVATE KEY", "pass")
        assertEquals(p, SshForm.from(p, "n").toProfile())
        assertEquals("n", SshForm.from(p, "n").name)
    }

    @Test fun `a new form starts on port 22 with password auth`() {
        val f = SshForm.from(null)
        assertEquals("22", f.port)
        assertFalse(f.usesKey)
    }
}
