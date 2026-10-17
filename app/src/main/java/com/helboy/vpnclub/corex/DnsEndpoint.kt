package com.helboy.vpnclub.corex

object DnsEndpoint {
    fun isIp(value: String): Boolean {
        if (value.contains(':')) return value.matches(Regex("[0-9a-fA-F:.]+")) &&
            runCatching { java.net.InetAddress.getByName(value) is java.net.Inet6Address }.getOrDefault(false)
        val parts = value.split('.')
        return parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) && (it.toIntOrNull() ?: -1) in 0..255 }
    }
    fun isValid(value: String): Boolean {
        if (isIp(value)) return true
        return runCatching {
            val u = java.net.URI(value)
            // IP-addressed DoH avoids bootstrap DNS outside the selected resolver.
            u.scheme == "https" && isIp(u.host?.removePrefix("[")?.removeSuffix("]").orEmpty()) &&
                u.rawUserInfo == null && u.fragment == null && u.path.isNotBlank() &&
                (u.port == -1 || u.port in 1..65535)
        }.getOrDefault(false)
    }
}
