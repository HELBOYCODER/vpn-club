package com.helboy.vpnclub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.DarkBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary

@Composable
fun VpnInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = DarkBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonCyan
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "درباره VPN Gate و کلاب",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                InfoCard(
                    title = "🌐 پروژه دانشگاه تسوکوبا (ژاپن)",
                    body = "پروژه VPN Gate یک شبکه تحقیقاتی آکادمیک و توزیع‌شده در دانشگاه تسوکوبای ژاپن است که از سال ۲۰۱۳ راه‌اندازی شده و سرورهای آن توسط داوطلبان در سراسر جهان میزبانی می‌شوند."
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoCard(
                    title = "🔑 اتصال خودکار با نام‌کاربری و رمز عبور پیش‌فرض",
                    body = "تمامی سرورهای این شبکه با یوزرنیم vpn و پسورد vpn کار می‌کنند. در این برنامه، این اطلاعات به صورت خودکار و پیش‌فرض در تمام پروفایل‌ها تزریق شده و نیازی به وارد کردن دستی ندارید."
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoCard(
                    title = "🛡 موتور محلی OpenVPN سراسری",
                    body = "این برنامه از موتور کامپایل‌شده و بهینه‌سازی‌شده OpenVPN برای سیستم‌عامل اندروید استفاده می‌کند. ترافیک کل دستگاه بدون نشت DNS و با امنیت رمزنگاری هدایت می‌شود."
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonIndigo,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("متوجه شدم", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
