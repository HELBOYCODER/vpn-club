package com.helboy.vpnclub.ui.proto.editors2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoNavRow
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform

private const val TITLE = "زنجیرهٔ دو موتوره"

private enum class CrossSlot { Carrier, Exit }

/**
 * Creates or edits a two-engine chain: the carrier (outer) is what the network sees, the exit (inner) is dialled
 * from inside it. [existingId] is the chain to edit, or null for a new one.
 */
@Composable
fun CrossChainEditor(existingId: String?, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val profiles by repo.profiles.collectAsState()
    val existing = existingId?.let { id -> profiles.firstOrNull { it.id == id } }
    when {
        existingId != null && existing == null ->
            EditorUnavailable(TITLE, "این زنجیره دیگر در فهرست نیست؛ شاید حذف شده باشد.", onBack)
        existing != null && existing.isLocked -> EditorLocked(existing, onBack)
        existing != null && !existing.isCrossChain ->
            EditorUnavailable(TITLE, "این پروفایل زنجیرهٔ دو موتوره نیست؛ از ویرایشگر مخصوص خودش باز کنید.", onBack)
        else -> CrossChainForm(existing, profiles, onBack)
    }
}

@Composable
private fun CrossChainForm(existing: VpnProfile?, profiles: List<VpnProfile>, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val platform = LocalPlatform.current

    val carriers = remember(profiles) { CrossChainLogic.carriers(profiles) }
    val exits = remember(profiles) { CrossChainLogic.exits(profiles) }

    val stored = remember(existing?.id) { existing?.crossChainSettings() }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var innerId by remember { mutableStateOf(stored?.first.orEmpty()) }
    var outerId by remember { mutableStateOf(stored?.second.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf(if (stored?.second.isNullOrEmpty()) CrossSlot.Carrier else CrossSlot.Exit) }
    var error by remember { mutableStateOf<String?>(null) }

    val check = CrossChainLogic.check(innerId, outerId, exits, carriers) { inner, outer -> repo.udpMismatch(inner, outer) }
    val options = ChainLogic.filterByQuery(if (slot == CrossSlot.Carrier) carriers else exits, query)
    val emptyText = if (slot == CrossSlot.Carrier) CrossChainLogic.NO_CARRIERS else CrossChainLogic.NO_EXITS

    fun save() {
        error = null
        runCatching { repo.addCrossChain(innerId = innerId, outerId = outerId, name = name.trim(), id = existing?.id) }
            .onSuccess {
                platform.toast(SAVED_TEXT)
                onBack()
            }
            .onFailure { error = "ذخیره نشد: ${it.message?.take(120) ?: "خطای نامشخص"}. دوباره امتحان کنید." }
    }

    ProtoPage(
        title = if (existing == null) "زنجیرهٔ دو موتوره تازه" else "ویرایش زنجیرهٔ دو موتوره",
        onBack = onBack,
        scroll = false,
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("proto_cross_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "intro") { EditorNote(CrossChainLogic.DESCRIPTION) }
            item(key = "name") {
                ProtoField(
                    label = "نام",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = TITLE,
                    tag = "proto_cross_name",
                )
            }
            item(key = "slots") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SlotCard(
                        label = "حامل",
                        picked = check.outer,
                        missing = check.outerMissing,
                        active = slot == CrossSlot.Carrier,
                        onClick = { slot = CrossSlot.Carrier; query = "" },
                        tag = "proto_cross_slot_carrier",
                        modifier = Modifier.weight(1f),
                    )
                    SlotCard(
                        label = "خروجی",
                        picked = check.inner,
                        missing = check.innerMissing,
                        active = slot == CrossSlot.Exit,
                        onClick = { slot = CrossSlot.Exit; query = "" },
                        tag = "proto_cross_slot_exit",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "hint") {
                EditorNote(if (slot == CrossSlot.Carrier) CrossChainLogic.CARRIER_HINT else CrossChainLogic.EXIT_HINT)
            }
            item(key = "search") {
                ProtoField(
                    label = "جست‌وجوی سرور",
                    value = query,
                    onValueChange = { query = it },
                    tag = "proto_cross_search",
                )
            }

            if (options.isEmpty()) {
                item(key = "none") {
                    if (query.isBlank()) ProtoEmpty(title = "گزینه‌ای نیست", hint = emptyText)
                    else EditorNote("چیزی با «${query.trim()}» پیدا نشد")
                }
            }
            items(options, key = { "${slot.name}-${it.id}" }) { p ->
                val chosen = p.id == (if (slot == CrossSlot.Carrier) outerId else innerId)
                val ping = p.lastPingMs?.takeIf { it > 0 }?.let { " · ${it.fa()} ms" }.orEmpty()
                ProtoNavRow(
                    title = p.name,
                    hint = "${p.transportLabel} · ${p.address}$ping",
                    icon = if (chosen) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    tag = "proto_cross_pick_${p.id}",
                    onClick = {
                        if (slot == CrossSlot.Carrier) {
                            outerId = p.id
                            if (innerId.isEmpty()) { slot = CrossSlot.Exit; query = "" }
                        } else {
                            innerId = p.id
                            if (outerId.isEmpty()) { slot = CrossSlot.Carrier; query = "" }
                        }
                    },
                )
            }
        }

        if (check.valid) {
            EditorNote(CrossChainLogic.summary(check.inner!!, check.outer!!), NoteTone.Ok, tag = "proto_cross_summary")
        }
        check.udp?.let { EditorNote(it, NoteTone.Error, tag = "proto_cross_udp") }
        if (check.same) EditorNote(CrossChainLogic.SAME_PROFILE, NoteTone.Error, tag = "proto_cross_same")
        if (check.outerMissing) {
            EditorNote("حاملِ انتخاب‌شده دیگر در فهرست نیست یا نمی‌تواند حامل باشد؛ یکی دیگر انتخاب کنید.", NoteTone.Error)
        }
        if (check.innerMissing) {
            EditorNote("خروجیِ انتخاب‌شده دیگر در فهرست نیست یا نمی‌تواند خروجی باشد؛ یکی دیگر انتخاب کنید.", NoteTone.Error)
        }
        CrossChainLogic.notes(check.inner).forEach { EditorNote(it) }
        error?.let { EditorNote(it, NoteTone.Error, tag = "proto_cross_error") }
        ProtoButton(
            text = "ذخیره",
            icon = Icons.Rounded.Check,
            onClick = { save() },
            enabled = check.valid,
            tag = "proto_save",
        )
    }
}

@Composable
private fun SlotCard(
    label: String,
    picked: VpnProfile?,
    missing: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    ClubCard(
        modifier.clickable(onClick = onClick).testTag(tag),
        border = if (active) NeonCyan else BorderDark,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = if (active) NeonCyan else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                picked?.name ?: if (missing) "پیدا نشد" else "انتخاب نشده",
                color = if (picked == null) TextSecondary else TextPrimary,
                maxLines = 1,
            )
            if (picked != null) Text(picked.transportLabel, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
}
