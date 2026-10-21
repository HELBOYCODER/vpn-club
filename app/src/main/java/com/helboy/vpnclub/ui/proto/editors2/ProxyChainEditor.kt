package com.helboy.vpnclub.ui.proto.editors2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform

private const val TITLE = "زنجیرهٔ پروکسی"

/**
 * Creates or edits a proxy chain: traffic enters the first member and leaves from the last. [existingId] is the
 * chain to edit, or null to build a new one; [onBack] is called after a save and when the person leaves.
 */
@Composable
fun ProxyChainEditor(existingId: String?, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val profiles by repo.profiles.collectAsState()
    val existing = existingId?.let { id -> profiles.firstOrNull { it.id == id } }
    when {
        existingId != null && existing == null ->
            EditorUnavailable(TITLE, "این زنجیره دیگر در فهرست نیست؛ شاید حذف شده باشد.", onBack)
        existing != null && existing.isLocked -> EditorLocked(existing, onBack)
        existing != null && !existing.isProxyChain ->
            EditorUnavailable(TITLE, "این پروفایل زنجیرهٔ پروکسی نیست؛ از ویرایشگر مخصوص خودش باز کنید.", onBack)
        else -> ProxyChainForm(existing, profiles, onBack)
    }
}

@Composable
private fun ProxyChainForm(existing: VpnProfile?, profiles: List<VpnProfile>, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val platform = LocalPlatform.current

    val candidates = remember(profiles) { ChainLogic.chainCandidates(profiles) }
    val byId = remember(candidates) { candidates.associateBy { it.id } }
    val stored = remember(existing?.id) { existing?.proxyChainSettings().orEmpty() }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(ChainLogic.keptMembers(stored, byId)) }
    val dropped = remember { ChainLogic.droppedCount(stored, byId) }
    var error by remember { mutableStateOf<String?>(null) }

    // A member can disappear while the page is open (deleted from another screen): it simply leaves the order.
    val live = selected.filter { byId.containsKey(it) }
    val check = ChainLogic.check(live, byId)
    val available = ChainLogic.filterByQuery(candidates.filter { it.id !in live }, query)

    fun save() {
        error = null
        runCatching { repo.addProxyChain(live, name.trim(), id = existing?.id) }
            .onSuccess {
                platform.toast(SAVED_TEXT)
                onBack()
            }
            .onFailure { error = "ذخیره نشد: ${it.message?.take(120) ?: "خطای نامشخص"}. دوباره امتحان کنید." }
    }

    ProtoPage(
        title = if (existing == null) "زنجیرهٔ پروکسی تازه" else "ویرایش زنجیره",
        onBack = onBack,
        scroll = false,
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("proto_chain_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "intro") { EditorNote(ChainLogic.DESCRIPTION) }
            item(key = "name") {
                ProtoField(
                    label = "نام",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = TITLE,
                    tag = "proto_chain_name",
                )
            }

            if (candidates.isEmpty()) {
                item(key = "empty") {
                    ProtoEmpty(
                        title = "سروری برای زنجیره نیست",
                        hint = ChainLogic.NO_CANDIDATES_TEXT,
                    )
                }
            }

            if (live.isNotEmpty()) {
                item(key = "sel-head") {
                    Text("ترتیب زنجیره (گرهٔ آخر = خروجی)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(live, key = { _, id -> "sel-$id" }) { i, id ->
                    val p = byId.getValue(id)
                    ClubCard(Modifier.fillMaxWidth().testTag("proto_chain_member_$i")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${(i + 1).fa()}.  ${p.name}", color = TextPrimary, maxLines = 1)
                                Text(
                                    if (i == live.lastIndex) "خروجی" else p.transportLabel,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                            IconButton(
                                onClick = { selected = ChainLogic.move(live, i, i - 1) },
                                enabled = i > 0,
                                modifier = Modifier.testTag("proto_chain_up_$i"),
                            ) { Icon(Icons.Rounded.KeyboardArrowUp, "بالا", tint = NeonCyan) }
                            IconButton(
                                onClick = { selected = ChainLogic.move(live, i, i + 1) },
                                enabled = i < live.lastIndex,
                                modifier = Modifier.testTag("proto_chain_down_$i"),
                            ) { Icon(Icons.Rounded.KeyboardArrowDown, "پایین", tint = NeonCyan) }
                            IconButton(
                                onClick = { selected = ChainLogic.remove(live, i) },
                                modifier = Modifier.testTag("proto_chain_remove_$i"),
                            ) { Icon(Icons.Rounded.Close, "حذف از زنجیره", tint = TextSecondary) }
                        }
                    }
                }
            }

            if (candidates.any { it.id !in live }) {
                item(key = "avail-head") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("سرورهای در دسترس", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        ProtoField(
                            label = "جست‌وجوی سرور",
                            value = query,
                            onValueChange = { query = it },
                            tag = "proto_chain_search",
                        )
                    }
                }
                items(available, key = { "avail-${it.id}" }) { p ->
                    val ping = p.lastPingMs?.takeIf { it > 0 }?.let { " · ${it.fa()} ms" }.orEmpty()
                    ProtoNavRow(
                        title = p.name,
                        hint = "${p.transportLabel} · ${p.address}$ping",
                        onClick = { selected = ChainLogic.addMember(live, p, byId) },
                        icon = Icons.Rounded.Add,
                        tag = "proto_chain_add_${p.id}",
                    )
                }
                if (available.isEmpty() && query.isNotBlank()) {
                    item(key = "noresult") { EditorNote("چیزی با «${query.trim()}» پیدا نشد") }
                }
            }

            if (existing != null) {
                item(key = "share") {
                    ProtoOutlined(
                        text = "کپی لینک اشتراک زنجیره",
                        onClick = {
                            val link = repo.shareLinkOf(existing)
                            if (link == null) {
                                platform.toast("لینک ساخته نشد؛ یکی از عضوهای زنجیره از فهرست حذف شده یا لینک ندارد.")
                            } else {
                                platform.copyToClipboard(link)
                                platform.toast("لینک کپی شد")
                            }
                        },
                        tag = "proto_chain_share",
                    )
                }
            }
        }

        if (dropped > 0) EditorNote(ChainLogic.droppedText(dropped), NoteTone.Warn, tag = "proto_chain_dropped")
        check.udpWarning?.let { EditorNote(ChainLogic.udpHopText(it), NoteTone.Error, tag = "proto_chain_udp") }
        if (check.tooFew) EditorNote(ChainLogic.MIN_TEXT, tag = "proto_chain_min")
        error?.let { EditorNote(it, NoteTone.Error, tag = "proto_chain_error") }
        ProtoButton(
            text = "ذخیره",
            icon = Icons.Rounded.Check,
            onClick = { save() },
            enabled = check.canSave,
            tag = "proto_save",
        )
    }
}
