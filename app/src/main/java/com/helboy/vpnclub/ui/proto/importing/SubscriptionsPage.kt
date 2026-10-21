package com.helboy.vpnclub.ui.proto.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoDialog
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.domain.config.Subscription
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.launch

private const val UA_DEFAULT = "default"
private const val UA_ALT = "alt"
private const val UA_CUSTOM = "custom"

private val UA_OPTIONS = listOf(UA_DEFAULT to "v2rayNG (پیش‌فرض)", UA_ALT to "Narcic", UA_CUSTOM to "سفارشی")

private fun uaChoiceOf(ua: String?): String = when {
    ua.isNullOrBlank() || ua == ConfigRepository.DEFAULT_UA -> UA_DEFAULT
    ua == ConfigRepository.ALT_UA -> UA_ALT
    else -> UA_CUSTOM
}

/** The user-agent to store for a chosen preset; blank means "use the default". */
private fun uaValueOf(choice: String, custom: String): String = when (choice) {
    UA_ALT -> ConfigRepository.ALT_UA
    UA_CUSTOM -> custom.trim()
    else -> ""
}

/** Subscriptions and groups: add, edit, refresh one or all, enable, delete. */
@Composable
fun SubscriptionsPage(onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val subs by repo.subscriptions.collectAsState()
    val profiles by repo.profiles.collectAsState()

    // Global auto-update setting; absent (e.g. no settings store installed) simply hides the line.
    val auto by produceState<Pair<Boolean, Int>?>(null) {
        val flow = runCatching { ProtoEnv.settings.settings }.getOrNull() ?: return@produceState
        flow.collect { value = it.autoUpdateSubscriptions to it.subscriptionUpdateIntervalHours }
    }

    var busy by remember { mutableStateOf(setOf<String>()) }
    var notes by remember { mutableStateOf(mapOf<String, Pair<Boolean, String>>()) } // id -> (ok, text)
    var updatingAll by remember { mutableStateOf(false) }

    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var newUaChoice by remember { mutableStateOf(UA_DEFAULT) }
    var newUaCustom by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var groupName by remember { mutableStateOf("") }

    var editTarget by remember { mutableStateOf<Subscription?>(null) }
    var deleteTarget by remember { mutableStateOf<Subscription?>(null) }

    suspend fun refresh(id: String) {
        busy = busy + id
        val result = repo.updateSubscription(id)
        busy = busy - id
        notes = notes + (id to result.fold(
            onSuccess = { true to "${it.fa()} سرور دریافت شد" },
            onFailure = { false to ImportLogic.describeUpdateError(it) },
        ))
    }

    fun update(id: String) {
        if (id in busy || updatingAll) return
        scope.launch { refresh(id) }
    }

    fun updateAll() {
        if (updatingAll) return
        val targets = subs.filter { it.enabled && it.url.isNotBlank() }.map { it.id }
        if (targets.isEmpty()) {
            platform.toast("اشتراک فعالی برای به‌روزرسانی نیست.")
            return
        }
        updatingAll = true
        scope.launch {
            targets.forEach { refresh(it) }
            updatingAll = false
        }
    }

    ProtoPage(title = "اشتراک‌ها", onBack = onBack) {
        auto?.let { (enabled, hours) ->
            Text(ImportLogic.autoUpdateText(enabled, hours), color = TextTertiary, fontSize = 11.sp, modifier = Modifier.testTag("proto_subs_auto"))
        }

        ProtoButton(
            text = "به‌روزرسانی همه", icon = Icons.Rounded.Sync, running = updatingAll,
            enabled = subs.any { it.enabled && it.url.isNotBlank() }, onClick = { updateAll() }, tag = "proto_subs_update_all",
        )
        if (updatingAll) ProtoLoading("در حال به‌روزرسانی اشتراک‌ها…")

        // ----- list -----
        if (subs.isEmpty()) {
            ProtoEmpty(
                title = "هنوز اشتراکی ندارید",
                hint = "نشانی اشتراک را پایین اضافه کنید تا سرورهایش خودکار دریافت شود، یا یک گروه بسازید تا سرورها را دسته‌بندی کنید.",
            )
        }
        subs.forEach { sub ->
            SubscriptionCard(
                sub = sub,
                serverCount = profiles.count { it.subscriptionId == sub.id },
                busy = sub.id in busy,
                note = notes[sub.id],
                onToggle = { repo.setSubscriptionEnabled(sub.id, it) },
                onUpdate = { update(sub.id) },
                onEdit = { editTarget = sub },
                onDelete = { deleteTarget = sub },
                onCopy = {
                    platform.copyToClipboard(sub.url)
                    platform.toast("نشانی اشتراک کپی شد")
                },
                onOpen = { platform.openUri(it) },
            )
        }

        // ----- add -----
        ProtoSection(title = "افزودن اشتراک", hint = "سرورها از این نشانی دریافت می‌شوند و هر بار که به‌روز کنید جایگزین می‌شوند.") {
            ProtoField(label = "نام (اختیاری)", value = newName, onValueChange = { newName = it }, tag = "proto_subs_name")
            ProtoField(
                label = "نشانی اشتراک", value = newUrl, onValueChange = { newUrl = it; urlError = null },
                placeholder = "https://", error = urlError, monospace = true, tag = "proto_subs_url",
            )
            ProtoChoice(
                title = "User-Agent", options = UA_OPTIONS, selected = newUaChoice, onSelect = { newUaChoice = it },
                hint = "بعضی پنل‌ها بسته به برنامه‌ای که می‌شناسند خروجی متفاوت می‌دهند؛ اگر لیست خالی آمد حالت دیگری را امتحان کنید.",
            )
            if (newUaChoice == UA_CUSTOM) {
                ProtoField(label = "User-Agent دلخواه", value = newUaCustom, onValueChange = { newUaCustom = it }, monospace = true)
            }
            ProtoButton(
                text = "افزودن و دریافت", icon = Icons.Rounded.Add, enabled = newUrl.isNotBlank(), tag = "proto_subs_add",
                onClick = {
                    if (!ImportLogic.isHttpUrl(newUrl)) {
                        urlError = "نشانی باید یک آدرس کامل و بدون فاصله باشد و با http:// یا https:// شروع شود."
                        return@ProtoButton
                    }
                    val sub = repo.addSubscription(newName, newUrl.trim(), uaValueOf(newUaChoice, newUaCustom).ifEmpty { null })
                    newName = ""; newUrl = ""; newUaChoice = UA_DEFAULT; newUaCustom = ""
                    update(sub.id)
                },
            )
        }

        ProtoSection(title = "افزودن گروه", hint = "گروه یک پوشهٔ خالی برای دسته‌بندی دستی سرورهاست و نشانی ندارد.") {
            ProtoField(label = "نام گروه", value = groupName, onValueChange = { groupName = it }, tag = "proto_subs_group_name")
            ProtoButton(
                text = "افزودن گروه", icon = Icons.Rounded.Add, enabled = groupName.isNotBlank(), tag = "proto_subs_group_add",
                onClick = {
                    repo.addGroup(groupName)
                    groupName = ""
                },
            )
        }
    }

    editTarget?.let { target -> EditSubscriptionDialog(target, onDismiss = { editTarget = null }) }

    deleteTarget?.let { target ->
        DeleteSubscriptionDialog(
            sub = target,
            serverCount = profiles.count { it.subscriptionId == target.id },
            onDismiss = { deleteTarget = null },
            onDelete = { alsoServers ->
                if (!alsoServers) {
                    // Keep the servers: move them to the manual list so they do not point at a group that is gone.
                    repo.moveToGroup(profiles.filter { it.subscriptionId == target.id }.map { it.id }, "")
                }
                repo.removeSubscription(target.id, alsoRemoveServers = alsoServers)
                notes = notes - target.id
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun SubscriptionCard(
    sub: Subscription,
    serverCount: Int,
    busy: Boolean,
    note: Pair<Boolean, String>?,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val now = System.currentTimeMillis()
    val isGroup = sub.url.isBlank()
    val usage = ImportLogic.usageText(sub)
    val fraction = ImportLogic.usageFraction(sub)
    val expiry = ImportLogic.expiryText(sub, now)
    val expiredOrFull = (fraction != null && fraction >= 1f) || sub.expireAt?.let { it <= now } == true

    ProtoSection(modifier = Modifier.testTag("proto_sub_card_${sub.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sub.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    if (isGroup) "گروه دستی" else ImportLogic.shortUrl(sub.url),
                    color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
                )
            }
            Switch(checked = sub.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("proto_sub_switch_${sub.id}"))
        }
        Text(
            buildString {
                append("${serverCount.fa()} سرور")
                if (!isGroup) append(" · ${ImportLogic.agoText(now, sub.lastUpdated)}")
                if (!sub.enabled) append(" · غیرفعال")
            },
            color = TextSecondary, fontSize = 12.sp,
        )
        if (usage != null || expiry != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(usage.orEmpty(), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (expiry != null) {
                    Text(expiry, color = if (expiredOrFull) StatusRed else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                color = if (fraction >= 0.8f) StatusAmber else NeonCyan,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
        if (busy) ProtoLoading("در حال دریافت…")
        note?.let { (ok, text) -> StatusLine(text, if (ok) NeonCyan else StatusRed, tag = "proto_sub_note_${sub.id}") }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            if (!isGroup) {
                ProtoOutlined("به‌روزرسانی", onClick = onUpdate, enabled = !busy, modifier = Modifier.weight(1f).testTag("proto_sub_update_${sub.id}"))
            }
            ProtoOutlined("ویرایش", onClick = onEdit, modifier = Modifier.weight(1f).testTag("proto_sub_edit_${sub.id}"))
            ProtoOutlined("حذف", onClick = onDelete, modifier = Modifier.weight(1f).testTag("proto_sub_delete_${sub.id}"))
        }
        if (!isGroup) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ProtoOutlined("کپی نشانی", onClick = onCopy, modifier = Modifier.weight(1f))
                sub.supportUrl?.let { url -> ProtoOutlined("پشتیبانی", onClick = { onOpen(url) }, modifier = Modifier.weight(1f)) }
                sub.webPageUrl?.let { url -> ProtoOutlined("وب‌سایت", onClick = { onOpen(url) }, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EditSubscriptionDialog(sub: Subscription, onDismiss: () -> Unit) {
    val repo = ProtoEnv.repo
    val isGroup = sub.url.isBlank()
    var name by remember(sub.id) { mutableStateOf(sub.name) }
    var url by remember(sub.id) { mutableStateOf(sub.url) }
    var uaChoice by remember(sub.id) { mutableStateOf(uaChoiceOf(sub.userAgent)) }
    var uaCustom by remember(sub.id) { mutableStateOf(if (uaChoiceOf(sub.userAgent) == UA_CUSTOM) sub.userAgent.orEmpty() else "") }
    var urlError by remember(sub.id) { mutableStateOf<String?>(null) }

    ProtoDialog(
        title = if (isGroup) "ویرایش گروه" else "ویرایش اشتراک",
        onDismiss = onDismiss,
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            if (!isGroup && !ImportLogic.isHttpUrl(url)) {
                urlError = "نشانی باید با http:// یا https:// شروع شود و فاصله نداشته باشد."
            } else {
                repo.editSubscription(
                    sub.id, name,
                    if (isGroup) null else uaValueOf(uaChoice, uaCustom).ifEmpty { null },
                    if (isGroup) null else url,
                )
                onDismiss()
            }
        },
    ) {
        ProtoField(label = "نام", value = name, onValueChange = { name = it }, tag = "proto_sub_edit_name")
        if (!isGroup) {
            ProtoField(
                label = "نشانی اشتراک", value = url, onValueChange = { url = it; urlError = null },
                singleLine = false, minLines = 2, monospace = true, error = urlError, tag = "proto_sub_edit_url",
            )
            ProtoChoice(title = "User-Agent", options = UA_OPTIONS, selected = uaChoice, onSelect = { uaChoice = it })
            if (uaChoice == UA_CUSTOM) {
                ProtoField(label = "User-Agent دلخواه", value = uaCustom, onValueChange = { uaCustom = it }, monospace = true)
            }
        }
    }
}

@Composable
private fun DeleteSubscriptionDialog(sub: Subscription, serverCount: Int, onDismiss: () -> Unit, onDelete: (alsoServers: Boolean) -> Unit) {
    var choice by remember(sub.id) { mutableStateOf(if (serverCount > 0) "servers" else "keep") }
    ProtoDialog(
        title = "حذف «${sub.name}»",
        onDismiss = onDismiss,
        confirmLabel = "حذف",
        onConfirm = { onDelete(choice == "servers") },
    ) {
        if (serverCount > 0) {
            Text("این ${if (sub.url.isBlank()) "گروه" else "اشتراک"} ${serverCount.fa()} سرور دارد. با آنها چه شود؟", color = TextSecondary, fontSize = 13.sp)
            ProtoChoice(
                title = null,
                options = listOf("servers" to "سرورها هم حذف شوند", "keep" to "سرورها بمانند"),
                selected = choice, onSelect = { choice = it },
                hint = if (choice == "servers") "سرورهای این ${if (sub.url.isBlank()) "گروه" else "اشتراک"} برای همیشه پاک می‌شوند."
                else "سرورها به بخش «دستی» منتقل می‌شوند.",
            )
        } else {
            Text("این مورد سروری ندارد و فقط خودش حذف می‌شود.", color = TextSecondary, fontSize = 13.sp)
        }
    }
}
