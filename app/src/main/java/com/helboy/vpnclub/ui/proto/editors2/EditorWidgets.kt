package com.helboy.vpnclub.ui.proto.editors2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.proto.ProtoError
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/** Small helpers shared by the five editors of this package. */

internal enum class NoteTone { Info, Warn, Error, Ok }

/** One line (or paragraph) of explanation under a control, coloured by how serious it is. */
@Composable
internal fun EditorNote(text: String, tone: NoteTone = NoteTone.Info, modifier: Modifier = Modifier, tag: String? = null) {
    val color = when (tone) {
        NoteTone.Info -> TextSecondary
        NoteTone.Warn -> StatusAmber
        NoteTone.Error -> StatusRed
        NoteTone.Ok -> StatusGreen
    }
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        modifier = modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
    )
}

/** A block of monospace text (a link, a command) on its own line, selectable like any other text. */
@Composable
internal fun EditorCode(text: String, modifier: Modifier = Modifier, tag: String? = null, maxLines: Int = 6) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = maxLines,
        modifier = modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
    )
}

internal const val LOCKED_TEXT =
    "این پروفایل قفل‌شده است (گاوصندوق). محتوای آن پنهان می‌ماند و در این صفحه قابل دیدن یا ویرایش نیست."

internal const val SAVED_TEXT = "ذخیره شد"

/** The page a locked profile gets: what it is, and why it cannot be edited. */
@Composable
internal fun EditorLocked(profile: VpnProfile, onBack: () -> Unit) {
    ProtoPage(title = profile.name, onBack = onBack) {
        EditorNote(LOCKED_TEXT, NoteTone.Warn, tag = "proto_locked")
        ProtoSection("مشخصات") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name, color = TextPrimary)
                Text(profile.transportLabel, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

/** The page shown when the profile to edit is gone, or is of another kind than this editor handles. */
@Composable
internal fun EditorUnavailable(title: String, message: String, onBack: () -> Unit) {
    ProtoPage(title = title, onBack = onBack) {
        ProtoError(message)
    }
}
