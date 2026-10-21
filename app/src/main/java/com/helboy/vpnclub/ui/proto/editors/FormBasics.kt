package com.helboy.vpnclub.ui.proto.editors

/**
 * Pure helpers shared by the protocol editors: the result of checking a form, and the small text rules every
 * form needs (ports, digits, lists). Nothing here touches Compose or Android, so it is covered by plain JUnit.
 */

/** What is wrong with a form. [errors] block saving; [warnings] are shown but let the person save. Keys are field ids. */
data class FormCheck(
    val errors: Map<String, String> = emptyMap(),
    val warnings: Map<String, String> = emptyMap(),
) {
    val ok: Boolean get() = errors.isEmpty()
}

/** Builds a [FormCheck] without a pile of `if` statements. */
internal class FormCheckBuilder {
    private val errors = linkedMapOf<String, String>()
    private val warnings = linkedMapOf<String, String>()

    fun error(key: String, message: String) {
        errors.putIfAbsent(key, message)
    }

    fun warn(key: String, message: String) {
        warnings.putIfAbsent(key, message)
    }

    fun build() = FormCheck(errors.toMap(), warnings.toMap())
}

internal inline fun formCheck(block: FormCheckBuilder.() -> Unit): FormCheck =
    FormCheckBuilder().apply(block).build()

object FormText {
    /** Persian and Arabic-Indic digits turned into the ASCII digits the engine understands. */
    fun asciiDigits(input: String): String = buildString(input.length) {
        for (c in input) {
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                },
            )
        }
    }

    /** Only the digits of [input] (after [asciiDigits]), at most [max] of them. */
    fun digitsOnly(input: String, max: Int = 9): String = asciiDigits(input).filter { it in '0'..'9' }.take(max)

    /** Digits and at most one dot, for decimal fields such as a rate limit. */
    fun decimalOnly(input: String, max: Int = 9): String {
        var dot = false
        return asciiDigits(input).filter { c ->
            when {
                c in '0'..'9' -> true
                c == '.' && !dot -> { dot = true; true }
                else -> false
            }
        }.take(max)
    }

    /** A port from text, or null when it is not 1..65535. */
    fun port(text: String): Int? = asciiDigits(text).trim().toIntOrNull()?.takeIf { it in 1..65535 }

    /** The non-blank, trimmed lines or comma separated items of [raw]. */
    fun items(raw: String): List<String> = raw.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    /** Items separated by spaces, tabs, new lines, commas or semicolons. */
    fun tokens(raw: String): List<String> = raw.split(' ', '\t', '\n', '\r', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun hasWhitespace(text: String): Boolean = text.trim().any { it.isWhitespace() }

    /** True for `host:port` where the port is valid and the host has no spaces (IPv6 in brackets is accepted). */
    fun isHostPort(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || hasWhitespace(t)) return false
        val sep = t.lastIndexOf(':')
        if (sep <= 0) return false
        val host = t.substring(0, sep).trim('[', ']')
        return host.isNotEmpty() && port(t.substring(sep + 1)) != null
    }
}
