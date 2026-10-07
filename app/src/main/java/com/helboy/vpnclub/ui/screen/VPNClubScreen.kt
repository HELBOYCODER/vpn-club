package com.helboy.vpnclub.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.ConnectButton
import com.helboy.vpnclub.ui.components.LogViewerDialog
import com.helboy.vpnclub.ui.components.SelectedServerCard
import com.helboy.vpnclub.ui.components.ServerListBottomSheet
import com.helboy.vpnclub.ui.components.SpeedDashboardCard
import com.helboy.vpnclub.ui.components.VpnInfoDialog
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.CardBgElevated
import com.helboy.vpnclub.ui.theme.DarkBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.viewmodel.MainViewModel

@Composable
fun VPNClubScreen(
    viewModel: MainViewModel,
    onConnectRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val servers by viewModel.servers.collectAsState()
    val filteredServers by viewModel.filteredServers.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val downloadSpeed by viewModel.downloadSpeed.collectAsState()
    val uploadSpeed by viewModel.uploadSpeed.collectAsState()
    val durationSeconds by viewModel.sessionDurationSeconds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val countryFilter by viewModel.countryFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val availableCountries by viewModel.availableCountries.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()

    var showServerSheet by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val refreshTransition = rememberInfiniteTransition(label = "refreshRotation")
    val refreshRotation by refreshTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = DarkBg,
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "VPN CLUB",
                                color = NeonCyan,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Live pill
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(StatusGreen.copy(alpha = 0.15f))
                                    .border(1.dp, StatusGreen.copy(alpha = 0.4f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${servers.size} سرور فعال",
                                    color = StatusGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "شبکه توزیع‌شده دانشگاه تسوکوبا (VPNGate)",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Action buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.loadServers(forceRefresh = true) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "بروزرسانی سرورها",
                                tint = if (isRefreshing) NeonCyan else TextSecondary,
                                modifier = Modifier.rotate(if (isRefreshing) refreshRotation else 0f)
                            )
                        }

                        IconButton(onClick = { showLogDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "لاگ سیستم",
                                tint = TextSecondary
                            )
                        }

                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "اطلاعات",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Security & DNS Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBgElevated)
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡 احراز هویت خودکار (vpn / vpn) • بدون نشت DNS",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "OpenVPN TUN",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Selected Server Card
                SelectedServerCard(
                    server = selectedServer,
                    onClick = { showServerSheet = true }
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Big Animated Connect Button
                ConnectButton(
                    state = connectionState,
                    statusMessage = statusMessage,
                    onClick = onConnectRequested
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Smart Fastest Connect Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonIndigo.copy(alpha = 0.12f))
                        .border(1.dp, NeonIndigo.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable {
                            viewModel.autoConnectFastest()
                            onConnectRequested()
                        }
                        .padding(vertical = 12.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚡ اتصال هوشمند (بهترین پینگ و پهنای باند)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Telemetry & Speed Dashboard
                SpeedDashboardCard(
                    downloadSpeed = downloadSpeed,
                    uploadSpeed = uploadSpeed,
                    pingFormatted = selectedServer?.pingFormatted ?: "نامشخص",
                    durationFormatted = viewModel.formatDuration(durationSeconds)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Sheets & Dialogs
            if (showServerSheet) {
                ServerListBottomSheet(
                    servers = filteredServers,
                    selectedServer = selectedServer,
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    countries = availableCountries,
                    selectedCountry = countryFilter,
                    onCountrySelect = { viewModel.setCountryFilter(it) },
                    sortOption = sortOption,
                    onSortChange = { viewModel.setSortOption(it) },
                    onServerSelect = {
                        viewModel.selectServer(it)
                        onConnectRequested()
                    },
                    onDismiss = { showServerSheet = false }
                )
            }

            if (showLogDialog) {
                LogViewerDialog(
                    logs = logEntries,
                    onDismiss = { showLogDialog = false }
                )
            }

            if (showInfoDialog) {
                VpnInfoDialog(
                    onDismiss = { showInfoDialog = false }
                )
            }
        }
    }
}
