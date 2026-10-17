package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.helboy.vpnclub.corex.AppSettings
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.theme.Palette

@Composable
fun LanSharingCard(settings: AppSettings) {
    var enabled by remember { mutableStateOf(settings.lanShare) }
    var password by remember { mutableStateOf(settings.lanPassword) }
    var reveal by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    ClubCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("اشتراک روی شبکه محلی", color = Palette.textPrimary)
                    Text("دستگاه دوم را به همان Wi-Fi یا هات‌اسپات وصل کنید. پس از تغییر، VPN را قطع و دوباره وصل کنید.", color = Palette.textSecondary)
                }
                Switch(enabled, onCheckedChange = { on ->
                    if (on && password.length < 12) {
                        val bytes = ByteArray(18).also { java.security.SecureRandom().nextBytes(it) }
                        password = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                        settings.lanPassword = password
                    }
                    enabled = on; settings.lanShare = on
                })
            }
            if (enabled) {
                Text("برای اتصال سرور Xray، WARP یا بدون سرور؛ در سایفون مستقیم فعال نیست. نام کاربری: hellboy · HTTP: 10819 · SOCKS: 10818", color = Palette.textSecondary)
                Text("آدرس: IP محلی این گوشی در تنظیمات Wi-Fi یا هات‌اسپات", color = Palette.textSecondary)
                Text("رمز: " + if (reveal) password else "••••••••••••", color = Palette.textPrimary)
                Row {
                    TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "پنهان کردن" else "نمایش رمز") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(password)) }) { Text("کپی رمز") }
                }
            }
        }
    }
}
