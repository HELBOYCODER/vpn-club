package com.helboy.vpnclub.ui.proto.engine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.data.net.MtuOptimizer
import dev.valid7996.narcicgeteway.data.net.MtuResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MTU: measure the path to the active profile's server, apply the recommendation, or type a value by hand.
 * Measuring runs off the main thread and is cancelled when the page is left.
 */
@Composable
fun MtuPage(onBack: () -> Unit) {
    val repo = ProtoEnv.settings
    val settings by repo.settings.collectAsState()
    val profiles by ProtoEnv.repo.profiles.collectAsState()
    val activeId by ProtoEnv.repo.activeId.collectAsState()
    val profile = remember(profiles, activeId) { profiles.firstOrNull { it.id == activeId } }
    val hint = remember(profile) { MtuLogic.serverHint(profile) }

    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MtuResult?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(settings.vpnMtu.toString()) }
    var saved by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    fun measure() {
        if (busy) return
        busy = true
        result = null
        failure = null
        job = scope.launch(Dispatchers.Default) {
            try {
                val r = if (hint != null) MtuOptimizer.optimize(hint.host, hint.overhead) else MtuOptimizer.optimize()
                result = r
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failure = "اندازه‌گیری با خطا روبه‌رو شد؛ اتصال اینترنت را بررسی و دوباره امتحان کنید."
            } finally {
                busy = false
            }
        }
    }

    fun store(mtu: Int) {
        repo.update { it.copy(vpnMtu = mtu) }
        manual = mtu.toString()
        saved = true
    }

    val check = MtuLogic.check(manual)
    val error = (check as? Checked.Bad)?.message
    val warning = (check as? Checked.Ok)?.let { MtuLogic.softWarning(it.value) }

    ProtoPage(title = "بهینه‌ساز MTU", onBack = onBack) {
        ProtoSection(
            title = "MTU فعلی: ${settings.vpnMtu.fa()}",
            hint = "MTU بزرگ‌ترین بسته‌ای است که بدون تکه شدن از شبکه می‌گذرد. مقدار نادرست باعث کندی یا قطع شدن بعضی سایت‌ها می‌شود. " +
                "تغییر از اتصال بعدی اعمال می‌شود و فقط در حالت VPN اثر دارد.",
        ) {}

        ProtoSection(
            title = "اندازه‌گیری خودکار",
            hint = "اول VPN را خاموش کنید؛ وگرنه بسته‌های آزمایشی از داخل تونل می‌گذرند و چیزی اندازه‌گیری نمی‌شود.",
        ) {
            Text(
                if (hint != null) "سرور هدف: ${hint.host.fa()} (پروفایل «${profile?.name.orEmpty()}»)"
                else "پروفایل فعالی نیست؛ اندازه‌گیری روی سرورهای عمومی انجام می‌شود و نتیجه تقریبی است.",
                color = TextSecondary, fontSize = 12.sp,
            )
            if (busy) {
                ProtoLoading("در حال اندازه‌گیری… ممکن است نیم دقیقه طول بکشد")
                ProtoOutlined("توقف", { job?.cancel(); busy = false }, tag = "proto_mtu_cancel")
            } else {
                ProtoButton("شروع اندازه‌گیری", Icons.Rounded.NetworkCheck, { measure() }, tag = "proto_mtu_measure")
            }
            failure?.let { Text(it, color = StatusRed, fontSize = 12.sp) }
            result?.let { r ->
                MtuLogic.describe(r).forEach { line ->
                    Text(line.text, color = if (line.bad) StatusAmber else TextPrimary, fontSize = 13.sp)
                }
                MtuLogic.applicable(r)?.let { mtu ->
                    ProtoButton("اعمال ${mtu.fa()}", Icons.Rounded.Check, { store(mtu) }, accent = StatusGreen, tag = "proto_mtu_apply")
                }
            }
        }

        ProtoSection(
            title = "ورود دستی",
            hint = "عدد مجاز بین ${MtuLogic.MIN_ALLOWED.fa()} و ${MtuLogic.MAX_ALLOWED.fa()}. در اکثر شبکه‌ها ${MtuLogic.USUAL_MIN.fa()} تا ${MtuLogic.USUAL_MAX.fa()} درست است.",
        ) {
            ProtoField(
                label = "MTU", value = manual,
                onValueChange = { manual = it; saved = false },
                error = error, keyboard = KeyboardType.Number, tag = "proto_mtu_manual",
            )
            ProtoChoice(
                title = null, options = MtuLogic.PRESETS.map { it.toString() to it.fa() },
                selected = manual.trim(), onSelect = { manual = it; saved = false },
            )
            if (warning != null) Text(warning, color = StatusAmber, fontSize = 12.sp, modifier = Modifier.testTag("proto_mtu_warning"))
            ProtoButton(
                "ذخیره", Icons.Rounded.Save,
                { (check as? Checked.Ok)?.let { store(it.value) } },
                enabled = check is Checked.Ok, tag = "proto_mtu_save",
            )
            if (saved) Text("ذخیره شد. از اتصال بعدی اعمال می‌شود.", color = StatusGreen, fontSize = 12.sp, modifier = Modifier.testTag("proto_mtu_saved"))
        }
    }
}
