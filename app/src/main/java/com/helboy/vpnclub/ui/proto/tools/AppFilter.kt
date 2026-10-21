package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.ui.platform.InstalledApp

/** Which apps go through the tunnel. Off = all apps, Include = only the selected ones, Exclude = all except the selected. */
enum class PerAppMode { Off, Include, Exclude }

fun perAppMode(enabled: Boolean, bypassMode: Boolean): PerAppMode = when {
    !enabled -> PerAppMode.Off
    bypassMode -> PerAppMode.Exclude
    else -> PerAppMode.Include
}

/** The pair (perAppProxyEnabled, perAppBypassMode) that stores [mode]. */
fun PerAppMode.toFlags(): Pair<Boolean, Boolean> = when (this) {
    PerAppMode.Off -> false to true
    PerAppMode.Include -> true to false
    PerAppMode.Exclude -> true to true
}

/**
 * Apps to list: optionally without system apps, matching [query] in the label or package name, with the
 * packages in [pinned] first (so the apps picked earlier are on top), then by label.
 */
fun filterApps(apps: List<InstalledApp>, query: String, showSystem: Boolean, pinned: Set<String> = emptySet()): List<InstalledApp> {
    val q = query.trim()
    return apps.asSequence()
        .filter { showSystem || !it.isSystem }
        .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        .sortedWith(compareByDescending<InstalledApp> { it.packageName in pinned }.thenBy { it.label.lowercase() })
        .toList()
}

fun selectAll(selected: Set<String>, shown: List<InstalledApp>): Set<String> = selected + shown.map { it.packageName }

fun selectNone(selected: Set<String>, shown: List<InstalledApp>): Set<String> = selected - shown.map { it.packageName }.toSet()

fun invert(selected: Set<String>, shown: List<InstalledApp>): Set<String> {
    val names = shown.map { it.packageName }.toSet()
    return (selected - names) + names.filterNot { it in selected }
}

fun toggle(selected: Set<String>, pkg: String): Set<String> = if (pkg in selected) selected - pkg else selected + pkg

/** Package names from a pasted list (one per line or comma separated); entries with spaces are ignored. */
fun parsePackageList(text: String): Set<String> =
    text.split('\n', ',', '\r').map { it.trim() }.filter { it.isNotEmpty() && it.none(Char::isWhitespace) }.toSet()

fun exportPackageList(selected: Set<String>): String = selected.sorted().joinToString("\n")

/** Selected packages that are no longer installed. */
fun staleSelection(apps: List<InstalledApp>, selected: Set<String>): Set<String> {
    val installed = apps.map { it.packageName }.toSet()
    return selected.filterNot { it in installed }.toSet()
}
