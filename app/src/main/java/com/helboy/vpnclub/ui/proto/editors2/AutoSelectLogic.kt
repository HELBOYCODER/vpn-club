package com.helboy.vpnclub.ui.proto.editors2

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.core.AutoSelect
import dev.valid7996.narcicgeteway.domain.config.AutoSelectIds
import dev.valid7996.narcicgeteway.domain.config.AutoSelectTuning
import dev.valid7996.narcicgeteway.domain.config.Subscription
import dev.valid7996.narcicgeteway.domain.config.autoSelectTuning
import dev.valid7996.narcicgeteway.domain.model.AppSettings

/*
 * Auto-select is not a stored profile: the repository makes one per group (all servers, the manual servers, or one
 * subscription), and how it behaves comes from the app settings (test URL, check / sweep interval, switch margin,
 * retry). This file holds the mapping between those settings and what the editor shows.
 */

/** The tuning part of AppSettings as the editor edits it. */
data class AutoTuningForm(
    val testUrl: String,
    val checkSeconds: Int,
    val sweepMinutes: Int,
    val marginPercent: Int,
    val retry: Boolean,
) {
    /** [settings] with this tuning written into it; every other setting is left alone. */
    fun applyTo(settings: AppSettings): AppSettings = settings.copy(
        delayTestUrl = testUrl.trim(),
        autoSelectCheckSeconds = checkSeconds,
        autoSelectSweepMinutes = sweepMinutes,
        autoSelectSwitchMargin = marginPercent,
        autoSelectRetry = retry,
    )

    /** What the engine will actually use when a group connects with these settings. */
    fun tuning(base: AppSettings = AppSettings()): AutoSelectTuning = applyTo(base).autoSelectTuning()

    /** A note about the test URL, or null when it is fine. A blank URL means the built-in default. */
    fun urlNote(): String? {
        val u = testUrl.trim()
        if (u.isEmpty()) return null
        if (u.any { it.isWhitespace() }) return "نشانی آزمون نباید فاصله داشته باشد."
        if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
            return "نشانی آزمون باید با http:// یا https:// شروع شود."
        }
        return null
    }

    val valid: Boolean get() = urlNote() == null

    companion object {
        const val DEFAULT_URL = "https://www.gstatic.com/generate_204"

        fun from(settings: AppSettings) = AutoTuningForm(
            testUrl = settings.delayTestUrl,
            checkSeconds = settings.autoSelectCheckSeconds,
            sweepMinutes = settings.autoSelectSweepMinutes,
            marginPercent = settings.autoSelectSwitchMargin,
            retry = settings.autoSelectRetry,
        )
    }
}

object AutoSelectLogic {

    /** The choices the settings screen offers; a value stored outside them is still shown so nothing is lost. */
    val CHECK_SECONDS = listOf(15, 30, 60, 120)
    val SWEEP_MINUTES = listOf(5, 10, 20, 30)
    val MARGIN_PERCENT = listOf(15, 30, 50)

    fun withCurrent(options: List<Int>, current: Int): List<Int> =
        if (current in options) options else (options + current).sorted()

    /** The id the group's auto-select profile has: auto:all, auto:manual or auto:sub:ID. */
    fun idOf(scope: String?): String = AutoSelectIds.of(scope)

    fun scopeOf(id: String): String? = AutoSelectIds.subscriptionOf(id)

    fun isValidId(id: String?): Boolean = AutoSelectIds.isAuto(id)

    /** Name of a group: all servers, the manual servers, or the subscription's own name. */
    fun scopeLabel(scope: String?, subscriptions: List<Subscription>): String = when (scope) {
        null -> "همهٔ سرورها"
        "" -> "دستی"
        else -> subscriptions.firstOrNull { it.id == scope }?.name ?: "گروه ناشناس"
    }

    /** (auto id, label) of every group that can be chosen. */
    fun groupOptions(subscriptions: List<Subscription>): List<Pair<String, String>> = buildList {
        add(idOf(null) to scopeLabel(null, subscriptions))
        add(idOf("") to scopeLabel("", subscriptions))
        subscriptions.forEach { add(idOf(it.id) to it.name) }
    }

    /** A group can be renamed only when it is a subscription or a personal group (all / manual have fixed names). */
    fun renamable(scope: String?): Boolean = !scope.isNullOrEmpty()

    fun phaseText(phase: AutoSelect.Phase): String = when (phase) {
        AutoSelect.Phase.Starting -> "در حال سنجش سرورها…"
        AutoSelect.Phase.Ok -> "فعال"
        AutoSelect.Phase.Degraded -> "سرور فعلی مشکل دارد"
        AutoSelect.Phase.Down -> "هیچ سروری پاسخ نمی‌دهد"
        AutoSelect.Phase.Idle -> "تا وقتی از اتصال استفاده نشود متوقف است"
    }

    fun healthText(health: AutoSelect.Health): String = when (health) {
        AutoSelect.Health.Alive -> "سالم"
        AutoSelect.Health.Suspect -> "ناپایدار"
        AutoSelect.Health.Dead -> "از کار افتاده"
        AutoSelect.Health.Unknown -> "سنجیده نشده"
    }

    /** Sorting key of the live member list: healthy first, then by delay (unmeasured last). */
    fun healthRank(health: AutoSelect.Health): Int = when (health) {
        AutoSelect.Health.Alive -> 0
        AutoSelect.Health.Suspect -> 1
        AutoSelect.Health.Unknown -> 2
        AutoSelect.Health.Dead -> 3
    }

    fun eventText(kind: AutoSelect.Reason, to: String, from: String): String = when (kind) {
        AutoSelect.Reason.Startup -> "شروع روی $to"
        AutoSelect.Reason.Failover -> "رفت روی $to: $from دیگر پاسخ نداد"
        AutoSelect.Reason.Faster -> "رفت روی $to: به‌وضوح از $from سریع‌تر است"
        AutoSelect.Reason.Pinned -> "روی $to ثابت شد"
        AutoSelect.Reason.Recovered -> "$to دوباره در دسترس است"
    }

    /** "5 دقیقه پیش"-style age of an event, in Persian. */
    fun agoText(ageMs: Long): String {
        val seconds = (ageMs / 1000).coerceAtLeast(0)
        return when {
            seconds < 45 -> "همین حالا"
            seconds < 3600 -> "${((seconds + 30) / 60).toString().fa()} دقیقه پیش"
            else -> "${(seconds / 3600).toString().fa()} ساعت پیش"
        }
    }
}
