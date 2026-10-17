package com.helboy.vpnclub.corex

/** Stable releases only. Unknown tags must never be advertised as an upgrade. */
object ReleaseVersion {
    private fun parts(value: String): List<Int>? {
        val tag = value.removePrefix("v")
        if (!Regex("[0-9]+(?:\\.[0-9]+){1,3}").matches(tag)) return null
        return tag.split('.').map { it.toIntOrNull() ?: return null }
    }
    fun isNewer(candidate: String, current: String): Boolean {
        val next = parts(candidate) ?: return false
        val installed = parts(current) ?: return false
        for (i in 0 until maxOf(next.size, installed.size)) {
            val a = next.getOrElse(i) { 0 }; val b = installed.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
