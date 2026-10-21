package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.domain.config.DnsTunnelProfile
import dev.valid7996.narcicgeteway.domain.config.Ikev2Profile
import dev.valid7996.narcicgeteway.domain.config.MasterDnsProfile
import dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile
import dev.valid7996.narcicgeteway.domain.config.OpenConnectProfile
import dev.valid7996.narcicgeteway.domain.config.PsiphonProfile
import dev.valid7996.narcicgeteway.domain.config.SniSpoofProfile
import dev.valid7996.narcicgeteway.domain.config.SshProfile
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/**
 * Where the editors write. Each function creates a profile when [existing] is null and replaces it otherwise.
 *
 * The repository's own `addX(..., id = existing.id)` rebuilds the profile and carries over only the group, ping,
 * country and traffic counters; a note and an expiry date set by a subscription would be lost, so they are put
 * back afterwards.
 */
object EditorSave {

    private fun keepMeta(repo: ConfigRepository, existing: VpnProfile?, saved: VpnProfile): VpnProfile {
        if (existing == null) return saved
        if (existing.note == saved.note && existing.expiresAt == saved.expiresAt) return saved
        val restored = saved.copy(note = existing.note, expiresAt = existing.expiresAt)
        repo.update(restored)
        return restored
    }

    fun ssh(repo: ConfigRepository, existing: VpnProfile?, name: String, settings: SshProfile): VpnProfile {
        if (existing == null) return repo.addSsh(settings, name.trim())
        // The repository has no edit call for SSH: replace the stored profile in place, as the reference does.
        val fresh = VpnProfile.fromSsh(settings = settings, id = existing.id, addedAt = existing.addedAt, name = name.trim())
        val merged = fresh.copy(
            subscriptionId = existing.subscriptionId,
            lastPingMs = existing.lastPingMs,
            countryCode = existing.countryCode,
            bytesDown = existing.bytesDown,
            bytesUp = existing.bytesUp,
            note = existing.note,
            expiresAt = existing.expiresAt,
        )
        repo.update(merged)
        return merged
    }

    fun dnsTunnel(repo: ConfigRepository, existing: VpnProfile?, name: String, settings: DnsTunnelProfile): VpnProfile =
        keepMeta(repo, existing, repo.addDnsTunnel(settings, name.trim(), existing?.id))

    fun masterDns(repo: ConfigRepository, existing: VpnProfile?, name: String, settings: MasterDnsProfile): VpnProfile =
        keepMeta(repo, existing, repo.addMasterDns(settings, name.trim(), existing?.id))

    fun ikev2(repo: ConfigRepository, existing: VpnProfile?, name: String, settings: Ikev2Profile): VpnProfile =
        keepMeta(repo, existing, repo.addIkev2(settings, name.trim(), existing?.id))

    fun openConnect(repo: ConfigRepository, existing: VpnProfile?, name: String, settings: OpenConnectProfile): VpnProfile =
        keepMeta(repo, existing, repo.addOpenConnect(settings, name.trim(), existing?.id))

    fun psiphon(repo: ConfigRepository, existing: VpnProfile?, name: String, settings: PsiphonProfile): VpnProfile =
        keepMeta(repo, existing, repo.addPsiphon(settings, name.trim(), existing?.id))

    fun sniSpoof(repo: ConfigRepository, existing: VpnProfile?, name: String, settings: SniSpoofProfile): VpnProfile =
        keepMeta(repo, existing, repo.addSniSpoof(settings, name.trim(), existing?.id))

    fun mhrv(repo: ConfigRepository, existing: VpnProfile?, name: String, settings: MhrvRelayProfile): VpnProfile =
        keepMeta(repo, existing, repo.addMhrvRelay(settings, name.trim(), existing?.id))

    fun tor(repo: ConfigRepository, existing: VpnProfile?, name: String): VpnProfile {
        if (existing == null) return repo.addTor(name.trim())
        val newName = name.trim().ifBlank { existing.name }
        repo.rename(existing.id, newName)
        return repo.profile(existing.id) ?: existing.copy(name = newName)
    }
}
