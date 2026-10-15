package com.helboy.vpnclub.corex

/** Keep known successes and verified/public feed diversity before expensive tunnel tests. */
internal object DiscoverySelection {
    fun <T> select(saved: List<T>, priority: List<T>, public: List<T>, limit: Int, key: (T) -> String): List<T> {
        val result = LinkedHashMap<String, T>()
        fun add(value: T) { if (result.size < limit) result.putIfAbsent(key(value), value) }
        saved.forEach(::add)
        for (i in 0 until maxOf(priority.size, public.size)) {
            priority.getOrNull(i)?.let(::add)
            public.getOrNull(i)?.let(::add)
            if (result.size >= limit) break
        }
        return result.values.toList()
    }
}
