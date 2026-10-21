package com.helboy.vpnclub.ui.proto.engine

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.domain.config.LocalPorts
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import java.security.SecureRandom

/** Result of checking something a person typed: either the parsed value or a one-line Persian reason. */
sealed interface Checked<out T> {
    data class Ok<T>(val value: T) : Checked<T>
    data class Bad(val message: String) : Checked<Nothing>
}

/** Pure input checks shared by the engine settings table and the pages. No Android, no Compose. */
object SettingsValidation {
    const val NOT_A_NUMBER = "یک عدد صحیح وارد کنید"
    const val REQUIRED = "این فیلد نباید خالی باشد"

    fun checkInt(text: String, min: Int, max: Int): Checked<Int> {
        val n = text.trim().toIntOrNull() ?: return Checked.Bad(NOT_A_NUMBER)
        if (n < min || n > max) return Checked.Bad("عدد باید بین ${min.fa()} و ${max.fa()} باشد")
        return Checked.Ok(n)
    }

    fun checkNotBlank(text: String): String? = if (text.isBlank()) REQUIRED else null

    /** "50" or "50-100": both ends are whole numbers, the first not above the second. */
    fun checkRange(text: String): String? {
        val m = RANGE.matchEntire(text.trim()) ?: return "به شکل «کم-زیاد» بنویسید، مثلا ۵۰-۱۰۰"
        val lo = m.groupValues[1].toIntOrNull() ?: return "عدد بیش از حد بزرگ است"
        val hi = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: lo
        if (hi < lo) return "عدد اول باید از عدد دوم بزرگ‌تر نباشد"
        return null
    }

    /** The port the shared LAN proxy may listen on; the engine silently falls back otherwise, so say it up front. */
    fun checkLanPort(n: Int): String? =
        if (LocalPorts.lanSocksPort(n) == n) null
        else "پورت باید بین ${1024.fa()} و ${65534.fa()} باشد و با پورت‌های داخلی برنامه برخورد نکند"

    fun checkUrl(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return REQUIRED
        if (t.any { it.isWhitespace() }) return "نشانی نباید فاصله داشته باشد"
        if (!t.startsWith("http://") && !t.startsWith("https://")) return "نشانی باید با http:// یا https:// شروع شود"
        return null
    }

    /** Hev's "read,write" timeout pair in seconds, e.g. "300,60". */
    fun checkHevTimeout(text: String): String? {
        val parts = text.trim().split(',').map { it.trim() }
        if (parts.size != 2 || parts.any { it.toIntOrNull()?.let { n -> n > 0 } != true }) {
            return "دو عدد مثبت با ویرگول بنویسید، مثلا ۳۰۰,۶۰"
        }
        return null
    }

    fun checkPortText(n: Int): String? = if (n in 1..65535) null else "پورت باید بین ${1.fa()} و ${65535.fa()} باشد"

    /** A random printable password for the shared LAN proxy. */
    fun randomToken(length: Int = 16, random: java.util.Random = SecureRandom()): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    private val RANGE = Regex("""(\d+)(?:-(\d+))?""")
}

/** Which TUN implementation moves the packets. "Xray" is the engine's own TUN inbound; Hev and Zep are userspace stacks. */
enum class TunEngine(val label: String) {
    Xray("Xray-TUN"),
    Hev("Hev"),
    Zep("ZepTun");

    companion object {
        fun of(s: AppSettings): TunEngine = when {
            !s.useHevTun -> Xray
            s.useZepTun -> Zep
            else -> Hev
        }
    }

    fun applyTo(s: AppSettings): AppSettings = when (this) {
        Xray -> s.copy(useHevTun = false)
        Hev -> s.copy(useHevTun = true, useZepTun = false)
        Zep -> s.copy(useHevTun = true, useZepTun = true)
    }
}
