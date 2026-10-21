package com.helboy.vpnclub.ui.proto.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.formatBytes
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoConfirm
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoError
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.data.assets.GeoAsset
import dev.valid7996.narcicgeteway.data.assets.GeoAssets
import dev.valid7996.narcicgeteway.domain.model.GeoFilesSource
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Progress(val file: String, val index: Int, val count: Int, val done: Long, val total: Long?)

/**
 * geoip.dat / geosite.dat: what is installed, whether a newer file exists, download with progress and cancel,
 * import from a file, restore the copy bundled in the app, and the data source.
 */
@Composable
fun AssetsPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val context = LocalContext.current
    val geo = platform.geoAssets
    val settings by ProtoEnv.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()

    if (geo == null) {
        ProtoPage(title = "فایل‌های داده", onBack = onBack) {
            ProtoEmpty("در این دستگاه پشتیبانی نمی‌شود", "فایل‌های geoip و geosite روی این پلتفرم مدیریت نمی‌شوند.")
        }
        return
    }

    var assets by remember { mutableStateOf<List<GeoAsset>?>(null) }
    var remote by remember { mutableStateOf<Map<String, RemoteInfo>>(emptyMap()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Progress?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var checking by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    val downloading = progress != null

    suspend fun refresh() {
        try {
            assets = withContext(Dispatchers.IO) { geo.state() }
            loadFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailed = true
        }
    }

    LaunchedEffect(geo) { refresh() }

    fun download(names: List<String>) {
        if (job?.isActive == true) return
        val source = settings.geoFilesSource
        message = null
        errors = errors - names.toSet()
        job = scope.launch {
            val failed = mutableMapOf<String, String>()
            var okCount = 0
            try {
                names.forEachIndexed { i, name ->
                    progress = Progress(name, i, names.size, 0, null)
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            GeoFetch.download(
                                geoUrl(source, name),
                                check = { ensureActive() },
                                onProgress = { d, t -> progress = Progress(name, i, names.size, d, t) },
                            )
                        }
                        if (!looksLikeGeoFile(bytes.size.toLong())) throw GeoBadFileException("tiny")
                        val saved = withContext(Dispatchers.IO) { geo.importBytes(bytes, name) }
                        if (saved) okCount++ else failed[name] = "ذخیرهٔ فایل ناموفق بود."
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed[name] = fetchErrorMessage(e)
                    }
                }
                message = if (failed.isEmpty()) "فایل‌ها به‌روز شد." else if (okCount > 0) "بخشی از فایل‌ها دریافت شد." else null
            } catch (e: CancellationException) {
                message = "دریافت لغو شد؛ فایل‌های قبلی دست‌نخورده ماندند."
                throw e
            } finally {
                errors = errors + failed
                progress = null
                withContext(NonCancellable) { refresh() }
            }
        }
    }

    fun check() {
        if (checking) return
        checking = true
        message = null
        scope.launch {
            val source = settings.geoFilesSource
            val found = mutableMapOf<String, RemoteInfo>()
            val failed = mutableMapOf<String, String>()
            withContext(Dispatchers.IO) {
                GeoAssets.FILES.forEach { name ->
                    try {
                        found[name] = GeoFetch.head(geoUrl(source, name))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed[name] = fetchErrorMessage(e)
                    }
                }
            }
            remote = remote + found
            errors = errors - GeoAssets.FILES.toSet() + failed
            val states = (assets ?: emptyList()).map { updateState(it, found[it.name]) }
            message = when {
                failed.size == GeoAssets.FILES.size -> null
                states.any { it == UpdateState.UpdateAvailable } -> "نسخهٔ تازه‌تری موجود است."
                states.all { it == UpdateState.UpToDate } -> "فایل‌های شما به‌روز است."
                else -> "وضعیت همهٔ فایل‌ها مشخص نشد."
            }
            checking = false
        }
    }

    fun restoreBundled() {
        scope.launch {
            val failed = mutableMapOf<String, String>()
            var ok = 0
            withContext(Dispatchers.IO) {
                GeoAssets.FILES.forEach { name ->
                    try {
                        val bytes = context.assets.open(name).use { it.readBytes() }
                        if (geo.importBytes(bytes, name)) ok++ else failed[name] = "ذخیرهٔ فایل ناموفق بود."
                    } catch (e: Exception) {
                        failed[name] = "نسخهٔ همراه برنامه پیدا نشد."
                    }
                }
            }
            errors = errors - GeoAssets.FILES.toSet() + failed
            message = if (ok > 0) "نسخهٔ همراه برنامه برگردانده شد." else null
            remote = emptyMap()
            refresh()
        }
    }

    fun importFile(name: String) {
        scope.launch {
            val pick = platform.pickFileBytes() ?: return@launch
            if (!looksLikeGeoFile(pick.bytes.size.toLong())) {
                errors = errors + (name to "این فایل برای ${name} بیش از حد کوچک است و معتبر به نظر نمی‌رسد.")
                return@launch
            }
            val ok = withContext(Dispatchers.IO) { runCatching { geo.importBytes(pick.bytes, name) }.getOrDefault(false) }
            errors = if (ok) errors - name else errors + (name to "ذخیرهٔ فایل ناموفق بود.")
            if (ok) message = "$name جایگزین شد."
            refresh()
        }
    }

    if (confirmRestore) {
        ProtoConfirm(
            title = "نسخهٔ همراه برنامه برگردد؟",
            text = "فایل‌های geoip و geosite فعلی با نسخه‌ای که همراه برنامه آمده جایگزین می‌شوند (احتمالاً قدیمی‌تر).",
            confirmLabel = "برگردان",
            onConfirm = { confirmRestore = false; restoreBundled() },
            onDismiss = { confirmRestore = false },
        )
    }

    ProtoPage(title = "فایل‌های داده", onBack = onBack) {
        ProtoSection(title = "منبع داده", hint = GEO_SOURCE_HINT) {
            ProtoChoice(
                null,
                GeoFilesSource.entries.map { it.name to it.repo },
                settings.geoFilesSource.name,
                { name ->
                    ProtoEnv.settings.update { it.copy(geoFilesSource = GeoFilesSource.valueOf(name)) }
                    remote = emptyMap()
                },
            )
        }

        val list = assets
        when {
            list == null && loadFailed -> ProtoError("وضعیت فایل‌ها خوانده نشد.", onRetry = { scope.launch { refresh() } })
            list == null -> ProtoLoading()
            else -> list.forEach { asset ->
                AssetCard(
                    asset = asset,
                    state = updateState(asset, remote[asset.name]),
                    remote = remote[asset.name],
                    error = errors[asset.name],
                    busy = downloading,
                    onRetry = { download(listOf(asset.name)) },
                    onImport = { importFile(asset.name) },
                )
            }
        }

        val p = progress
        if (p != null) {
            ProtoSection(title = "در حال دریافت ${p.file} (${(p.index + 1).fa()} از ${p.count.fa()})") {
                val fraction = progressFraction(p.done, p.total)
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().testTag("proto_assets_progress"), color = NeonCyan)
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("proto_assets_progress"), color = NeonCyan)
                Text(
                    formatBytes(p.done) + (p.total?.let { " از ${formatBytes(it)}" } ?: ""),
                    color = TextSecondary, fontSize = 12.sp,
                )
                ProtoOutlined("لغو", onClick = { job?.cancel() }, tag = "proto_assets_cancel")
            }
        } else {
            ProtoButton(
                "دریافت آخرین نسخه", Icons.Rounded.CloudDownload,
                onClick = { download(GeoAssets.FILES) }, tag = "proto_assets_download",
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtoOutlined(
                    if (checking) "در حال بررسی…" else "بررسی به‌روزرسانی",
                    onClick = { check() }, enabled = !checking, modifier = Modifier.weight(1f), tag = "proto_assets_check",
                )
                ProtoOutlined("نسخهٔ همراه برنامه", onClick = { confirmRestore = true }, modifier = Modifier.weight(1f), tag = "proto_assets_restore")
            }
        }

        message?.let { Text(it, color = StatusGreen, fontSize = 13.sp, modifier = Modifier.testTag("proto_assets_message")) }
        Text(GEO_BUNDLED_NOTE, color = TextTertiary, fontSize = 11.sp)
    }
}


@Composable
private fun AssetCard(
    asset: GeoAsset,
    state: UpdateState,
    remote: RemoteInfo?,
    error: String?,
    busy: Boolean,
    onRetry: () -> Unit,
    onImport: () -> Unit,
) {
    ProtoSection(title = asset.name, border = if (error != null) StatusRed else BorderDark) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(
                updateStateLabel(state),
                when (state) {
                    UpdateState.UpToDate -> StatusGreen
                    UpdateState.UpdateAvailable -> StatusAmber
                    UpdateState.Missing -> StatusRed
                    UpdateState.Unknown -> TextTertiary
                },
            )
            if (asset.present) Chip(formatBytes(asset.sizeBytes), NeonCyan)
        }
        Text(
            if (asset.present) "آخرین به‌روزرسانی: ${formatStamp(asset.updatedAt)}" else "این فایل نصب نیست؛ مسیریابی با geoip/geosite کار نمی‌کند.",
            color = TextSecondary, fontSize = 12.sp,
        )
        if (remote != null && state == UpdateState.UpdateAvailable) {
            Text(
                "نسخهٔ جدید" + (remote.sizeBytes?.let { " · ${formatBytes(it)}" } ?: "") +
                    (remote.lastModifiedMs?.let { " · ${formatStamp(it)}" } ?: ""),
                color = StatusAmber, fontSize = 12.sp,
            )
        }
        if (error != null) {
            Text(error, color = StatusRed, fontSize = 12.sp, modifier = Modifier.testTag("proto_asset_error"))
            ProtoOutlined("تلاش دوباره", onClick = onRetry, enabled = !busy, tag = "proto_asset_retry")
        }
        ProtoOutlined("ورود از فایل", onClick = onImport, enabled = !busy)
    }
}
