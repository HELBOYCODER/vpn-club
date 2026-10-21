package com.helboy.vpnclub.ui.proto.editors2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import dev.valid7996.narcicgeteway.domain.config.AwgConfig
import dev.valid7996.narcicgeteway.domain.config.Protocol

private fun opts(list: List<String>, current: String): List<Pair<String, String>> =
    LinkFormRules.withCurrent(list, current).map { it to it }

private fun digits(s: String, max: Int): String = s.filter(Char::isDigit).take(max)

/**
 * All the fields of a link profile, grouped as the reference form does (server, protocol-specific, transport,
 * security). [check] carries the inline errors; [creating] decides whether changing the protocol resets the port.
 */
@Composable
internal fun LinkFormFields(
    form: LinkForm,
    check: FormCheck,
    creating: Boolean,
    onChange: (LinkForm) -> Unit,
) {
    val f = form
    var awgAdvanced by remember { mutableStateOf(LinkFormRules.hasAdvancedAwg(form.awg)) }

    ProtoSection(title = "سرور") {
        ProtoChoice(
            title = "پروتکل",
            options = Protocol.entries.map { it.name to it.label },
            selected = f.protocol.name,
            onSelect = { onChange(LinkFormRules.withProtocol(f, Protocol.valueOf(it), creating)) },
        )
        ProtoField(
            label = "نام",
            value = f.remark,
            onValueChange = { onChange(f.copy(remark = it)) },
            tag = "proto_manual_name",
        )
        ProtoField(
            label = "آدرس",
            value = f.address,
            onValueChange = { onChange(f.copy(address = it)) },
            keyboard = KeyboardType.Uri,
            error = check.errors["address"],
            tag = "proto_manual_address",
        )
        ProtoField(
            label = "پورت",
            value = f.port,
            onValueChange = { onChange(f.copy(port = digits(it, 5))) },
            keyboard = KeyboardType.Number,
            error = check.errors["port"],
            tag = "proto_manual_port",
        )

        when (f.protocol) {
            Protocol.VLESS -> {
                ProtoField(
                    label = "شناسه (UUID)",
                    value = f.userId,
                    onValueChange = { onChange(f.copy(userId = it)) },
                    error = check.errors["userId"],
                    monospace = true,
                    tag = "proto_manual_userid",
                )
                ProtoChoice(
                    title = "Flow (xtls-rprx-vision)",
                    options = LinkFormRules.withCurrentPairs(LinkFormRules.FLOWS, f.flow),
                    selected = f.flow,
                    onSelect = { onChange(f.copy(flow = it)) },
                )
            }
            Protocol.VMESS -> {
                ProtoField(
                    label = "شناسه (UUID)",
                    value = f.userId,
                    onValueChange = { onChange(f.copy(userId = it)) },
                    error = check.errors["userId"],
                    monospace = true,
                    tag = "proto_manual_userid",
                )
                ProtoField(
                    label = "Alter ID",
                    value = f.alterId,
                    onValueChange = { onChange(f.copy(alterId = digits(it, 4))) },
                    keyboard = KeyboardType.Number,
                    error = check.errors["alterId"],
                )
                ProtoChoice(
                    title = "رمزنگاری",
                    options = opts(LinkFormRules.VMESS_SECURITIES, f.vmessSecurity),
                    selected = f.vmessSecurity,
                    onSelect = { onChange(f.copy(vmessSecurity = it)) },
                )
            }
            Protocol.TROJAN -> ProtoField(
                label = "رمز",
                value = f.userId,
                onValueChange = { onChange(f.copy(userId = it)) },
                error = check.errors["userId"],
                tag = "proto_manual_userid",
            )
            Protocol.SHADOWSOCKS -> {
                ProtoField(
                    label = "رمز",
                    value = f.userId,
                    onValueChange = { onChange(f.copy(userId = it)) },
                    error = check.errors["userId"],
                    tag = "proto_manual_userid",
                )
                ProtoChoice(
                    title = "روش رمزنگاری",
                    options = opts(LinkFormRules.SS_METHODS, f.ssMethod),
                    selected = f.ssMethod,
                    onSelect = { onChange(f.copy(ssMethod = it)) },
                )
            }
            Protocol.SOCKS, Protocol.HTTP -> {
                ProtoField(
                    label = "نام کاربری",
                    value = f.username,
                    onValueChange = { onChange(f.copy(username = it)) },
                )
                ProtoField(
                    label = "رمز",
                    value = f.userId,
                    onValueChange = { onChange(f.copy(userId = it)) },
                )
            }
            Protocol.HYSTERIA -> {
                ProtoField(
                    label = "رشتهٔ احراز",
                    value = f.secretKey,
                    onValueChange = { onChange(f.copy(secretKey = it)) },
                    error = check.errors["secretKey"],
                    tag = "proto_manual_secret",
                )
                ProtoField("رمز مبهم‌سازی (اختیاری)", f.obfsPassword, { onChange(f.copy(obfsPassword = it)) })
                ProtoField("پرش پورت (اختیاری)", f.portHopping, { onChange(f.copy(portHopping = it)) })
                ProtoField("گواهی پین‌شده SHA-256 (اختیاری)", f.pinnedCert, { onChange(f.copy(pinnedCert = it)) }, monospace = true)
                ProtoField(
                    "آپلود Mbps (اختیاری)", f.bandwidthUp, { onChange(f.copy(bandwidthUp = digits(it, 6))) },
                    keyboard = KeyboardType.Number,
                )
                ProtoField(
                    "دانلود Mbps (اختیاری)", f.bandwidthDown, { onChange(f.copy(bandwidthDown = digits(it, 6))) },
                    keyboard = KeyboardType.Number,
                )
                ProtoField("SNI", f.sni, { onChange(f.copy(sni = it)) })
                ProtoChoice(
                    title = "ALPN",
                    options = LinkFormRules.withCurrentPairs(LinkFormRules.ALPNS, f.alpn),
                    selected = f.alpn,
                    onSelect = { onChange(f.copy(alpn = it)) },
                )
                EchFields(f, check, onChange)
            }
            Protocol.WIREGUARD, Protocol.AMNEZIAWG -> {
                ProtoField(
                    label = "کلید خصوصی",
                    value = f.secretKey,
                    onValueChange = { onChange(f.copy(secretKey = it)) },
                    error = check.errors["secretKey"],
                    monospace = true,
                    tag = "proto_manual_secret",
                )
                ProtoField(
                    label = "کلید عمومی همتا",
                    value = f.peerPublicKey,
                    onValueChange = { onChange(f.copy(peerPublicKey = it)) },
                    error = check.errors["peerPublicKey"],
                    monospace = true,
                )
                ProtoField("کلید از پیش‌اشتراکی (اختیاری)", f.presharedKey, { onChange(f.copy(presharedKey = it)) }, monospace = true)
                ProtoField("آدرس محلی", f.localAddress, { onChange(f.copy(localAddress = it)) })
                ProtoField("IPهای مجاز (خالی = همه)", f.allowedIps, { onChange(f.copy(allowedIps = it)) })
                ProtoField("سرورهای DNS (از خود کانفیگ)", f.wgDns, { onChange(f.copy(wgDns = it)) })
                ProtoField("بایت‌های Reserved (اختیاری)", f.reserved, { onChange(f.copy(reserved = it)) })
                ProtoField(
                    "MTU (اختیاری)", f.wgMtu, { onChange(f.copy(wgMtu = digits(it, 4))) },
                    keyboard = KeyboardType.Number, error = check.errors["wgMtu"],
                )
                ProtoField(
                    "Keepalive پایدار، ثانیه (اختیاری)", f.wgKeepalive, { onChange(f.copy(wgKeepalive = digits(it, 4))) },
                    keyboard = KeyboardType.Number, error = check.errors["wgKeepalive"],
                )
                if (f.protocol == Protocol.AMNEZIAWG) {
                    EditorNote(
                        "مبهم‌سازی AmneziaWG: اگر همه را خالی بگذارید مثل وایرگارد معمولی کار می‌کند. " +
                            "مقادیر را با کانفیگ ارائه‌دهنده‌تان یکی کنید.",
                    )
                    AwgConfig.CLASSIC_KEYS.forEach { key -> AwgField(f, key, onChange) }
                    ProtoSwitch(
                        title = "نمایش پارامترهای AmneziaWG 1.5",
                        checked = awgAdvanced,
                        onChange = { awgAdvanced = it },
                    )
                    if (awgAdvanced) {
                        AwgConfig.KEYS.filterNot { it in AwgConfig.CLASSIC_KEYS }.forEach { key -> AwgField(f, key, onChange) }
                    }
                }
            }
        }
    }

    if (f.protocol.supportsTransport) {
        ProtoSection(title = "ترنسپورت") {
            ProtoChoice(
                title = null,
                options = opts(LinkFormRules.NETWORKS, f.network).map { it.first to it.second.uppercase() },
                selected = f.network,
                onSelect = { onChange(f.copy(network = it)) },
            )
            if (f.network != "tcp" && f.network != "kcp" && f.network != "mkcp") {
                ProtoField("هاست", f.host, { onChange(f.copy(host = it)) })
            }
            when (f.network) {
                "tcp" -> {
                    ProtoChoice(
                        title = "نوع هدر",
                        options = opts(LinkFormRules.TCP_HEADERS, f.headerType),
                        selected = f.headerType,
                        onSelect = { onChange(f.copy(headerType = it)) },
                    )
                    if (f.headerType == "http") {
                        ProtoField("هاست", f.host, { onChange(f.copy(host = it)) })
                        ProtoField("مسیر", f.path, { onChange(f.copy(path = it)) })
                    }
                }
                "kcp", "mkcp" -> {
                    ProtoChoice(
                        title = "نوع هدر",
                        options = opts(LinkFormRules.KCP_HEADERS, f.headerType),
                        selected = f.headerType,
                        onSelect = { onChange(f.copy(headerType = it)) },
                    )
                    if (f.headerType == "dns") ProtoField("هاست", f.host, { onChange(f.copy(host = it)) })
                    ProtoField("Seed", f.seed, { onChange(f.copy(seed = it)) })
                    ProtoField(
                        "MTU برای mKCP (اختیاری)", f.kcpMtu, { onChange(f.copy(kcpMtu = digits(it, 5))) },
                        keyboard = KeyboardType.Number, error = check.errors["kcpMtu"],
                    )
                    ProtoField(
                        "TTI برای mKCP (اختیاری)", f.kcpTti, { onChange(f.copy(kcpTti = digits(it, 5))) },
                        keyboard = KeyboardType.Number, error = check.errors["kcpTti"],
                    )
                }
                "ws", "httpupgrade" -> ProtoField("مسیر", f.path, { onChange(f.copy(path = it)) })
                "xhttp" -> {
                    ProtoField("مسیر", f.path, { onChange(f.copy(path = it)) })
                    ProtoChoice(
                        title = "حالت",
                        options = opts(LinkFormRules.XHTTP_MODES, f.streamMode.ifBlank { "auto" }),
                        selected = f.streamMode.ifBlank { "auto" },
                        onSelect = { onChange(f.copy(streamMode = it)) },
                    )
                    ProtoField(
                        "تنظیمات اضافی (JSON، اختیاری)", f.xhttpExtra, { onChange(f.copy(xhttpExtra = it)) },
                        singleLine = false, minLines = 2, monospace = true,
                    )
                }
                "grpc" -> {
                    ProtoField("نام سرویس gRPC", f.serviceName, { onChange(f.copy(serviceName = it)) })
                    ProtoField("Authority", f.authority, { onChange(f.copy(authority = it)) })
                    ProtoChoice(
                        title = "حالت",
                        options = opts(LinkFormRules.GRPC_MODES, f.streamMode.ifBlank { "gun" }),
                        selected = f.streamMode.ifBlank { "gun" },
                        onSelect = { onChange(f.copy(streamMode = it)) },
                    )
                }
            }
            ProtoField(
                "JSON فاینال‌ماسک (اختیاری)", f.finalMask, { onChange(f.copy(finalMask = it)) },
                singleLine = false, minLines = 2, monospace = true,
            )
        }

        ProtoSection(title = "امنیت") {
            ProtoChoice(
                title = null,
                options = LinkFormRules.withCurrentPairs(LinkFormRules.SECURITIES, f.security),
                selected = f.security,
                onSelect = { onChange(f.copy(security = it)) },
            )
            if (f.security.isNotEmpty()) {
                ProtoField("SNI", f.sni, { onChange(f.copy(sni = it)) }, tag = "proto_manual_sni")
                ProtoChoice(
                    title = "اثر انگشت",
                    options = LinkFormRules.withCurrentPairs(LinkFormRules.FINGERPRINTS, f.fingerprint),
                    selected = f.fingerprint,
                    onSelect = { onChange(f.copy(fingerprint = it)) },
                )
                if (f.security == "tls") {
                    ProtoChoice(
                        title = "ALPN",
                        options = LinkFormRules.withCurrentPairs(LinkFormRules.ALPNS, f.alpn),
                        selected = f.alpn,
                        onSelect = { onChange(f.copy(alpn = it)) },
                    )
                    EchFields(f, check, onChange)
                    ProtoField("سایفرسوئیت‌ها (fp unsafe)", f.cipherSuites, { onChange(f.copy(cipherSuites = it)) })
                    ProtoField("گواهی پین‌شده SHA-256 (اختیاری)", f.pinnedCert, { onChange(f.copy(pinnedCert = it)) }, monospace = true)
                    ProtoField("بررسی گواهی با این نام (vcn)", f.verifyCertName, { onChange(f.copy(verifyCertName = it)) })
                    ProtoSwitch(
                        title = "اجازهٔ اتصال ناامن (رد کردن بررسی گواهی)",
                        checked = f.allowInsecure,
                        onChange = { onChange(f.copy(allowInsecure = it)) },
                    )
                } else {
                    ProtoField("کلید عمومی (pbk)", f.publicKey, { onChange(f.copy(publicKey = it)) }, monospace = true)
                    ProtoField("شناسه کوتاه (sid)", f.shortId, { onChange(f.copy(shortId = it)) })
                    ProtoField("spiderX (اختیاری)", f.spiderX, { onChange(f.copy(spiderX = it)) })
                    ProtoField("کلید بررسی پساکوانتومی (pqv)", f.mldsa65Verify, { onChange(f.copy(mldsa65Verify = it)) }, monospace = true)
                }
            }
        }
    }

    check.warnings.forEach { EditorNote(it, NoteTone.Warn) }
    check.errors["link"]?.let { EditorNote(it, NoteTone.Error, tag = "proto_manual_link_error") }
}

/** The ECH config list and the echOutbound option (a raw Xray outbound the ECH query is sent through). */
@Composable
private fun EchFields(f: LinkForm, check: FormCheck, onChange: (LinkForm) -> Unit) {
    ProtoField(
        label = "لیست کانفیگ ECH (اختیاری)",
        value = f.echConfigList,
        onValueChange = { onChange(f.copy(echConfigList = it)) },
        monospace = true,
        tag = "proto_manual_ech",
    )
    ProtoField(
        label = "قالب JSON خام echOutbound: { OutboundObject }",
        value = f.echOutbound,
        onValueChange = { onChange(f.copy(echOutbound = it)) },
        singleLine = false,
        minLines = 2,
        monospace = true,
        error = check.errors["echOutbound"],
        tag = "proto_manual_ech_outbound",
    )
}

@Composable
private fun AwgField(f: LinkForm, key: String, onChange: (LinkForm) -> Unit) {
    val name = AwgConfig.CONF_NAME[key] ?: key
    val desc = AwgConfig.DESCRIPTION[key]
    ProtoField(
        label = if (desc == null) name else "$name — $desc",
        value = f.awg[key],
        onValueChange = { onChange(f.copy(awg = f.awg.with(key, it))) },
    )
}
