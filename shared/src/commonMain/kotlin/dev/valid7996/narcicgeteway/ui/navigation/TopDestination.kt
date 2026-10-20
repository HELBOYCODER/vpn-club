package dev.valid7996.narcicgeteway.ui.navigation

import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.ic_dns
import dev.valid7996.narcicgeteway.shared.resources.ic_home
import dev.valid7996.narcicgeteway.shared.resources.ic_settings
import dev.valid7996.narcicgeteway.shared.resources.nav_home
import dev.valid7996.narcicgeteway.shared.resources.nav_servers
import dev.valid7996.narcicgeteway.shared.resources.nav_settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class TopDestination(
    val labelRes: StringResource,
    val icon: DrawableResource,
) {
    Home(Res.string.nav_home, Res.drawable.ic_home),
    Servers(Res.string.nav_servers, Res.drawable.ic_dns),
    Settings(Res.string.nav_settings, Res.drawable.ic_settings),
}
