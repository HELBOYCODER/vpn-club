package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.data.tools.TelegramProxyItem
import dev.valid7996.narcicgeteway.data.tools.TelegramProxyRepository
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TelegramProxySheet(
    onDismiss: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.3f) else Color(0xFF3C3C43).copy(alpha = 0.3f)),
            )
        },
    ) {
        dev.valid7996.narcicgeteway.ui.components.ProvidePageBack(onBack = onDismiss) {
            TelegramProxyScreen(
                contentPadding = PaddingValues(bottom = 28.dp),
            )
        }
    }
}

@Composable
fun TelegramProxyScreen(
    settings: AppSettings? = null,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val platform = LocalPlatform.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scope = rememberCoroutineScope()

    val proxies = remember { mutableStateListOf<TelegramProxyItem>().apply { addAll(TelegramProxyRepository.getInitialProxies()) } }
    var channelInput by remember { mutableStateOf("ProxyMTProto") }
    var isTestingAll by remember { mutableStateOf(false) }
    var isFetchingOnline by remember { mutableStateOf(false) }
    var activeFetchJob by remember { mutableStateOf<Job?>(null) }
    var activeTestJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeFetchJob?.cancel()
            activeTestJob?.cancel()
        }
    }

    fun fetchFromChannel(targetChannel: String = channelInput) {
        if (isFetchingOnline) return
        isFetchingOnline = true
        val channelToFetch = targetChannel.ifBlank { "ProxyMTProto" }
        activeFetchJob = scope.launch {
            val updated = TelegramProxyRepository.fetchOnlineProxies(channel = channelToFetch, limit = 45)
            proxies.clear()
            proxies.addAll(updated)
            isFetchingOnline = false
            val cleanChannelName = channelToFetch.trim().removePrefix("@").removePrefix("https://t.me/")
            platform.toast("${updated.size} پروکسی زنده از @$cleanChannelName دریافت شد")
        }
    }

    fun testAll() {
        if (isTestingAll) return
        isTestingAll = true
        activeTestJob = scope.launch {
            val updated = TelegramProxyRepository.pingAll(proxies)
            proxies.clear()
            proxies.addAll(updated)
            isTestingAll = false
        }
    }

    fun testSingle(index: Int) {
        val target = proxies.getOrNull(index) ?: return
        scope.launch {
            proxies[index] = target.copy(isTesting = true)
            val res = TelegramProxyRepository.pingSingle(target)
            proxies[index] = res
        }
    }

    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val accentColor = Color(0xFF007AFF)

    val quickChannels = listOf(
        "ProxyMTProto",
        "MTProxy",
        "TelMTProto",
        "ProxyMTProto_Tel",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PageHeader(
                    title = "پروکسی‌های تلگرام (Telegram Proxies)",
                    subtitle = "MTProto Direct & Fast Channel Fetcher",
                )
            }

            // Telegram Channel Source Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "کانال تلگرام مبدأ پروکسی‌ها",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1C1C1E),
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "کانال عمومی (Public)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = channelInput,
                                onValueChange = { channelInput = it },
                                label = { Text("آیدی کانال تلگرام") },
                                placeholder = { Text("مثال: ProxyMTProto یا @ProxyMTProto") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = cardBorder,
                                ),
                                modifier = Modifier.weight(1f),
                            )

                            Button(
                                onClick = { fetchFromChannel(channelInput) },
                                enabled = !isFetchingOnline,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34C759),
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.height(54.dp),
                            ) {
                                if (isFetchingOnline) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text("دریافت 📥", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Quick channel chips
                        Text(
                            text = "کانال‌های پیشنهادی:",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            quickChannels.forEach { ch ->
                                val isSelected = channelInput.equals(ch, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) accentColor else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                    modifier = Modifier.clickable {
                                        channelInput = ch
                                        fetchFromChannel(ch)
                                    },
                                ) {
                                    Text(
                                        text = "@$ch",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else (if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E)),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Summary Info Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34C759)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${proxies.size} پروکسی آماده اتصال",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E),
                                )
                            }

                            val bestPing = proxies.filter { it.pingMs > 0 }.minOfOrNull { it.pingMs }
                            if (bestPing != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF34C759).copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "بهترین: $bestPing ms",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34C759),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "پروکسی‌ها مستقیماً با پروتکل MTProto و استتار TLS باز می‌شوند. با زدن «اتصال در تلگرام»، اپلیکیشن تلگرام باز شده و پروکسی بلافاصله اضافه می‌شود.",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            // Controls Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { testAll() },
                            enabled = !isTestingAll,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.weight(1.2f).height(46.dp),
                        ) {
                            if (isTestingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("در حال تست...", fontSize = 12.sp)
                            } else {
                                Text("تست همه پینگ‌ها ⚡", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                proxies.sortWith(compareBy<TelegramProxyItem> {
                                    if (it.pingMs > 0) 0 else 1
                                }.thenBy { if (it.pingMs > 0) it.pingMs else Long.MAX_VALUE })
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                contentColor = if (isDark) Color.White else Color.Black,
                            ),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Text("مرتب‌سازی پینگ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                val allLinks = proxies.joinToString("\n\n") { "${it.title}: ${it.webUri}" }
                                platform.copyToClipboard(allLinks)
                                platform.toast("همه پروکسی‌ها کپی شدند")
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                contentColor = if (isDark) Color.White else Color.Black,
                            ),
                            modifier = Modifier.weight(0.9f).height(46.dp),
                        ) {
                            Text("کپی همه", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Proxy Cards List
            items(proxies.indices.toList(), key = { proxies[it].id }) { index ->
                val proxy = proxies[index]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg,
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = proxy.country,
                                    fontSize = 20.sp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = proxy.title.ifEmpty { "MTProto Proxy" },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000),
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${proxy.server}:${proxy.port}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                                    )
                                }
                            }

                            // Ping badge
                            if (proxy.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = accentColor,
                                )
                            } else {
                                val (bg, fg, text) = when {
                                    proxy.pingMs in 1..299 -> Triple(Color(0xFF34C759).copy(alpha = 0.15f), Color(0xFF34C759), "${proxy.pingMs} ms")
                                    proxy.pingMs in 300..799 -> Triple(Color(0xFFFF9500).copy(alpha = 0.15f), Color(0xFFFF9500), "${proxy.pingMs} ms")
                                    proxy.pingMs >= 800 -> Triple(Color(0xFFFF3B30).copy(alpha = 0.15f), Color(0xFFFF3B30), "${proxy.pingMs} ms")
                                    else -> Triple(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73), "تست پینگ")
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = bg,
                                    modifier = Modifier.clickable { testSingle(index) },
                                ) {
                                    Text(
                                        text = text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = fg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { platform.openUri(proxy.tgUri) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.weight(1.5f).height(38.dp),
                            ) {
                                Text("اتصال در تلگرام 🚀", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    platform.copyToClipboard(proxy.webUri)
                                    platform.toast("لینک پروکسی کپی شد")
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                    contentColor = if (isDark) Color.White else Color.Black,
                                ),
                                modifier = Modifier.weight(1f).height(38.dp),
                            ) {
                                Text("کپی لینک", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
