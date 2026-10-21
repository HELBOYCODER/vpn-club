package com.helboy.vpnclub.ui.proto.engine

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.data.net.MtuOptimizer
import dev.valid7996.narcicgeteway.data.net.MtuOverheads
import dev.valid7996.narcicgeteway.data.net.MtuResult
import dev.valid7996.narcicgeteway.data.net.MtuServerHint
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/** Pure rules for the MTU page: what a person may type, what a measurement means in words. */
object MtuLogic {
    /** The settings page clamps to this range, and the engine accepts it. */
    const val MIN_ALLOWED = 1000
    const val MAX_ALLOWED = 9000

    /** Where nearly every real network lives; outside it we warn but still allow. */
    const val USUAL_MIN = MtuOptimizer.MIN_MTU
    const val USUAL_MAX = MtuOptimizer.MAX_MTU

    val PRESETS = listOf(1280, 1400, 1450, 1500)

    fun check(text: String): Checked<Int> = SettingsValidation.checkInt(text, MIN_ALLOWED, MAX_ALLOWED)

    /** A gentle warning for a legal but unusual value, or null. */
    fun softWarning(mtu: Int): String? = when {
        mtu < USUAL_MIN -> "کمتر از ${USUAL_MIN.fa()} معمولا سرعت را بی‌دلیل کم می‌کند و IPv6 را از کار می‌اندازد."
        mtu > USUAL_MAX -> "بیشتر از ${USUAL_MAX.fa()} فقط روی شبکه‌های ویژه جواب می‌دهد؛ روی اینترنت معمولی بسته‌ها گم می‌شوند."
        else -> null
    }

    /** A line of explanation under the measurement; [bad] lines are drawn in the error colour. */
    data class Line(val text: String, val bad: Boolean = false)

    fun describe(result: MtuResult): List<Line> = when (result) {
        MtuResult.VpnActive -> listOf(Line("یک VPN فعال است. ابتدا آن را قطع کنید و دوباره اندازه بگیرید، وگرنه بسته‌های آزمایشی از داخل تونل می‌گذرند.", true))
        is MtuResult.NotMeasurable -> buildList {
            add(Line("اندازه‌گیری ممکن نشد: هیچ سروری به بسته‌ی آزمایشی جواب نداد یا این دستگاه اجازه‌ی ping نمی‌دهد.", true))
            result.linkMtu?.let { add(Line("MTU اینترفیس شبکه: ${it.fa()}")) }
        }
        is MtuResult.Measured -> buildList {
            add(Line("MTU مسیر: ${result.pathMtu.fa()} - مقدار پیشنهادی: ${result.recommended.fa()}"))
            add(
                if (result.toServer) Line("آزمایش روی سرور فعال (${result.host}) انجام شد.")
                else Line("آزمایش روی سرور عمومی (${result.host}) انجام شد، نه سرور شما؛ نتیجه تقریبی است.", true),
            )
            add(Line("سربار پروتکل: ${result.overhead.explain().fa()}"))
            if (!result.confirmed) add(Line("نتیجه با آزمایش دوباره تأیید نشد؛ اگر شبکه ناپایدار است دوباره اندازه بگیرید.", true))
            result.linkMtu?.let { add(Line("MTU اینترفیس شبکه: ${it.fa()}")) }
        }
    }

    /** The value a "use this" button would store, clamped into the allowed range; null unless the measurement succeeded. */
    fun applicable(result: MtuResult?): Int? =
        (result as? MtuResult.Measured)?.recommended?.coerceIn(MIN_ALLOWED, MAX_ALLOWED)

    /** The host to probe and the protocol overhead to subtract for the active profile (worst case when unknown). */
    fun serverHint(profile: VpnProfile?): MtuServerHint? {
        if (profile == null) return null
        val link = (profile.source as? ProfileSource.Link)?.link
        val parsed = link?.let { runCatching { ConfigParser.parse(it) }.getOrNull() }
        val host = parsed?.address?.takeIf { it.isNotBlank() } ?: profile.address
        if (host.isBlank()) return null
        val overhead = parsed?.let { runCatching { MtuOverheads.ofServer(it) }.getOrNull() } ?: MtuOverheads.worstCase()
        return MtuServerHint(host, overhead)
    }
}
