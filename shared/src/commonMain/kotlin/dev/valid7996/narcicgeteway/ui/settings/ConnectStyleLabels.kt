package dev.valid7996.narcicgeteway.ui.settings

import dev.valid7996.narcicgeteway.domain.model.ConnectButtonStyle
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.connect_style_bar
import dev.valid7996.narcicgeteway.shared.resources.connect_style_hero
import dev.valid7996.narcicgeteway.shared.resources.connect_style_icon
import dev.valid7996.narcicgeteway.shared.resources.connect_style_pill
import dev.valid7996.narcicgeteway.shared.resources.connect_style_ring
import dev.valid7996.narcicgeteway.shared.resources.connect_style_swipe
import dev.valid7996.narcicgeteway.shared.resources.connect_style_switch
import org.jetbrains.compose.resources.StringResource

fun ConnectButtonStyle.labelRes(): StringResource = when (this) {
    ConnectButtonStyle.Pill -> Res.string.connect_style_pill
    ConnectButtonStyle.Hero -> Res.string.connect_style_hero
    ConnectButtonStyle.Ring -> Res.string.connect_style_ring
    ConnectButtonStyle.Bar -> Res.string.connect_style_bar
    ConnectButtonStyle.Icon -> Res.string.connect_style_icon
    ConnectButtonStyle.Switch -> Res.string.connect_style_switch
    ConnectButtonStyle.Slide -> Res.string.connect_style_swipe
}
