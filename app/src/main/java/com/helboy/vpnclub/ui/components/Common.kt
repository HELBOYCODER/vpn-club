package com.helboy.vpnclub.ui.components

import com.helboy.vpnclub.ui.theme.Palette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary

/** کارت استاندارد برنامه. */
@Composable
fun ClubCard(
    modifier: Modifier = Modifier,
    border: Color = BorderDark,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, border),
    ) { Box(Modifier.padding(16.dp)) { content() } }
}

/** برچسب کوچک رنگی. */
@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
}

/** کاشی آمار: عنوان کوچک + مقدار درشت. */
@Composable
fun StatTile(
    title: String, value: String, sub: String,
    accent: Color, modifier: Modifier = Modifier
) {
    ClubCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.background(accent, RoundedCornerShape(50)).padding(4.dp))
                Text(title, color = TextSecondary, fontSize = 12.sp)
            }
            Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = TextSecondary, fontSize = 11.sp)
        }
    }
}


/** دکمه‌ی عملیات با حالت «در حال اجرا». */
@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    running: Boolean,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !running,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent, contentColor = Palette.onAccent,
            disabledContainerColor = accent.copy(alpha = 0.25f), disabledContentColor = Palette.textSecondary),
    ) {
        if (running) CircularProgressIndicator(
            modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp, color = Palette.textSecondary)
        else Icon(icon, null, modifier = Modifier.padding(end = 6.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
