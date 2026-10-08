package com.helboy.vpnclub.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.data.model.AuthMode
import com.helboy.vpnclub.data.model.VpnServer
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.CardBgElevated
import com.helboy.vpnclub.ui.theme.DarkBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import com.helboy.vpnclub.ui.viewmodel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListBottomSheet(
    servers: List<VpnServer>,
    selectedServer: VpnServer?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    countries: List<Pair<String, String>>,
    selectedCountry: String?,
    onCountrySelect: (String?) -> Unit,
    onlyIranCompatible: Boolean,
    onOnlyIranCompatibleToggle: () -> Unit,
    isProbing: Boolean,
    onProbeRequested: () -> Unit,
    sortOption: SortOption,
    onSortChange: (SortOption) -> Unit,
    onServerSelect: (VpnServer) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val spinTransition = rememberInfiniteTransition(label = "spin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "angle"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderDark)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "لیست سرورها (${servers.size} سرور)",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (onlyIranCompatible) "فیلتر: فقط سرورهای سازگار با اینترنت ایران فعال است" else "نمایش تمام سرورهای شبکه",
                        color = if (onlyIranCompatible) StatusGreen else TextSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("جستجو در نام کشور، آی‌پی یا پورت...", color = TextTertiary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "جستجو", tint = TextTertiary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "پاک کردن", tint = TextTertiary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = BorderDark,
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Iran Filters & Probe Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Iran filter pill
                FilterPill(
                    label = if (onlyIranCompatible) "🇮🇷 فقط سازگار با ایران (فعال)" else "🌐 تمام سرورها",
                    isSelected = onlyIranCompatible,
                    accentColor = StatusGreen,
                    onClick = onOnlyIranCompatibleToggle
                )

                // Live Probe Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isProbing) NeonCyan.copy(alpha = 0.2f) else CardBgElevated)
                        .border(1.dp, if (isProbing) NeonCyan else BorderDark, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isProbing, onClick = onProbeRequested)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (isProbing) NeonCyan else TextSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(if (isProbing) spinAngle else 0f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isProbing) "سنجش زنده..." else "⚡ تست زنده تاخیر از خط شما",
                            color = if (isProbing) NeonCyan else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FilterPill(
                    label = "⚡ کمترین پینگ",
                    isSelected = sortOption == SortOption.PING,
                    onClick = { onSortChange(SortOption.PING) }
                )

                FilterPill(
                    label = "🚀 بالاترین سرعت",
                    isSelected = sortOption == SortOption.SPEED,
                    onClick = { onSortChange(SortOption.SPEED) }
                )

                FilterPill(
                    label = "👥 کاربران آنلاین",
                    isSelected = sortOption == SortOption.SESSIONS,
                    onClick = { onSortChange(SortOption.SESSIONS) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Countries horizontal row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    label = "🌐 همه کشورها",
                    isSelected = selectedCountry == null,
                    onClick = { onCountrySelect(null) }
                )
                countries.forEach { (country, flag) ->
                    FilterPill(
                        label = "$flag $country",
                        isSelected = selectedCountry == country,
                        onClick = { onCountrySelect(if (selectedCountry == country) null else country) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Servers List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(servers) { server ->
                    ServerListItem(
                        server = server,
                        isSelected = selectedServer?.ip == server.ip,
                        onClick = {
                            onServerSelect(server)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    accentColor: Color = NeonCyan,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.18f) else CardBg)
            .border(
                1.dp,
                if (isSelected) accentColor else BorderDark,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) accentColor else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ServerListItem(
    server: VpnServer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val pingColor = when {
        server.probedLatencyMs != null && server.probedLatencyMs!! in 1..250 -> StatusGreen
        server.probedLatencyMs != null && server.probedLatencyMs!! > 250 -> StatusAmber
        server.ping in 1..80 -> StatusGreen
        server.ping in 81..180 -> StatusAmber
        else -> StatusRed
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) NeonCyan.copy(alpha = 0.08f) else CardBg)
            .border(
                1.dp,
                if (isSelected) NeonCyan.copy(alpha = 0.8f) else BorderDark,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Flag
                Text(
                    text = server.countryFlag,
                    fontSize = 26.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server.countryLong,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Protocol tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CardBgElevated)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = server.protocolDetected,
                                color = NeonIndigo,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Iran Compatibility badge
                        if (server.isIranCompatible) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(StatusGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = when {
                                        server.authMode == AuthMode.CLIENT_CERT -> "🔒 ${server.provider.shortCode}"
                                        server.port == 995 -> "🇮🇷 SSTP/995"
                                        else -> "🇮🇷 سازگار با ایران"
                                    },
                                    color = StatusGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (server.isTsukubaSubnet) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(StatusRed.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "⚠️ فیلتر در ایران",
                                    color = StatusRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "${server.ip} • ${server.numVpnSessions} کاربر آنلاین",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            // Stats column
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.pingFormatted,
                        color = pingColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "انتخاب شده",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = server.speedMbpsFormatted,
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
