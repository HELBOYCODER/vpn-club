package com.helboy.vpnclub.ui.proto

import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/** Every protocol a person can add by hand from the "Protocols" tab. */
enum class ProtoKind {
    Manual, Psiphon, Aether, SniSpoof, Ikev2, OpenConnect, Ssh, DnsTunnel, MasterDns, Tor, Mhrv, ProxyChain, CrossChain, AutoSelect,
}

/** Where the "Protocols" tab can go. The host owns the back stack; pages only ask for a destination. */
sealed interface ProtoRoute {
    data object Import : ProtoRoute
    data object Subscriptions : ProtoRoute
    data class LockShare(val profileId: String) : ProtoRoute
    data class Edit(val profileId: String) : ProtoRoute
    data class New(val kind: ProtoKind) : ProtoRoute
}

/** The editor that fits a stored profile, or null when it is a plain link (those open in the manual editor). */
fun kindOf(p: VpnProfile): ProtoKind? = when (p.source) {
    is ProfileSource.Psiphon -> ProtoKind.Psiphon
    is ProfileSource.Aether -> ProtoKind.Aether
    is ProfileSource.SniSpoof -> ProtoKind.SniSpoof
    is ProfileSource.Ikev2 -> ProtoKind.Ikev2
    is ProfileSource.OpenConnect -> ProtoKind.OpenConnect
    is ProfileSource.Ssh -> ProtoKind.Ssh
    is ProfileSource.Tor -> ProtoKind.Tor
    is ProfileSource.DnsTunnel -> ProtoKind.DnsTunnel
    is ProfileSource.MasterDns -> ProtoKind.MasterDns
    is ProfileSource.MhrvRelay -> ProtoKind.Mhrv
    is ProfileSource.ProxyChain -> ProtoKind.ProxyChain
    is ProfileSource.CrossChain -> ProtoKind.CrossChain
    is ProfileSource.AutoSelect -> ProtoKind.AutoSelect
    is ProfileSource.Link, is ProfileSource.RawJson, is ProfileSource.SingBox, is ProfileSource.SingBoxConfig -> ProtoKind.Manual
    else -> null
}
