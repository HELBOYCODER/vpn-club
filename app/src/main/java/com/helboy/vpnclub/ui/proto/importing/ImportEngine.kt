package com.helboy.vpnclub.ui.proto.importing

import dev.valid7996.narcicgeteway.crypto.NrgLink
import dev.valid7996.narcicgeteway.crypto.ZsxMetadata
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.config.RelayKeyRequiredException
import dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor
import dev.valid7996.narcicgeteway.domain.config.OvpnConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The next thing the screen has to do after one import attempt. */
sealed interface ImportOutcome {
    data class Done(val summary: ImportSummary) : ImportOutcome

    /** The text is one web address: ask whether to add it as a subscription. */
    data class OfferSubscription(val url: String) : ImportOutcome

    /** A sealed config that needs the key the seller sent. */
    class NeedKey(val bytes: ByteArray, val meta: ZsxMetadata) : ImportOutcome

    /** A short (relay) link that needs its private key before the blob can be downloaded. */
    data class NeedRelayKey(val text: String) : ImportOutcome

    data class NeedOvpnCredentials(val text: String, val name: String) : ImportOutcome

    data class Failed(val message: String) : ImportOutcome
}

/**
 * The import decision tree: classify, resolve relay links, open sealed configs, import link lists line by line (so a
 * bad line is counted and explained instead of sinking the rest). Everything runs off the main thread.
 */
object ImportEngine {
    private const val PER_LINE_LIMIT = 150
    private const val MAX_DEPTH = 2

    private inline fun guard(block: () -> ImportOutcome): ImportOutcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ImportOutcome.Failed(ImportLogic.describeError(e))
    }

    private fun one(): ImportOutcome = ImportOutcome.Done(ImportSummary(imported = 1))

    /** Imports pasted / scanned / file text. [fileName] only names an OpenVPN profile. */
    suspend fun importText(repo: ConfigRepository, raw: String, fileName: String? = null): ImportOutcome =
        withContext(Dispatchers.IO) { guard { route(repo, raw, fileName, 0) } }

    private suspend fun route(repo: ConfigRepository, raw: String, fileName: String?, depth: Int): ImportOutcome {
        if (depth > MAX_DEPTH) return ImportOutcome.Failed(ImportLogic.MSG_GENERIC)
        val text = raw.trim()
        return when (ImportLogic.classify(text)) {
            InputKind.Empty -> ImportOutcome.Failed("اول متن، لینک یا فایل کانفیگ را وارد کنید.")
            InputKind.SubscriptionUrl -> ImportOutcome.OfferSubscription(ImportLogic.subscriptionUrlOf(text) ?: text)
            InputKind.Relay -> {
                val resolved = repo.resolveNrgRelay(text)
                val failure = resolved.exceptionOrNull()
                when {
                    failure is RelayKeyRequiredException -> ImportOutcome.NeedRelayKey(text)
                    failure != null -> ImportOutcome.Failed(ImportLogic.describeError(failure))
                    else -> route(repo, resolved.getOrThrow(), fileName, depth + 1)
                }
            }
            InputKind.Locked -> lockedFromText(repo, text)
            InputKind.Ovpn -> ovpn(repo, text, fileName)
            InputKind.Links -> links(repo, text)
            InputKind.Json, InputKind.WireguardConf, InputKind.Base64 -> pasted(repo, text)
            InputKind.Other -> ImportLogic.soleUrlIn(text)?.let { ImportOutcome.OfferSubscription(it) } ?: pasted(repo, text)
        }
    }

    private suspend fun pasted(repo: ConfigRepository, text: String): ImportOutcome {
        val result = repo.importPasted(text)
        val p = result.getOrNull() ?: return ImportOutcome.Failed(
            ImportLogic.describeError(result.exceptionOrNull() ?: IllegalStateException()),
        )
        return when {
            p.subscription -> ImportOutcome.Done(ImportSummary(p.count, p.duplicates, subscriptionName = p.subscriptionName))
            p.count == 0 && p.duplicates == 0 -> ImportOutcome.Failed(ImportLogic.MSG_NOTHING_FOUND)
            else -> ImportOutcome.Done(ImportSummary(imported = p.count, duplicates = p.duplicates))
        }
    }

    private suspend fun links(repo: ConfigRepository, text: String): ImportOutcome {
        val lines = ImportLogic.lines(text)
        if (lines.size == 1) return pasted(repo, lines.first())
        if (lines.size > PER_LINE_LIMIT) {
            // Too many to import one by one (each import rewrites the stored list): import in bulk and estimate.
            val bulk = repo.importPasted(text)
            val p = bulk.getOrNull() ?: return ImportOutcome.Failed(
                ImportLogic.describeError(bulk.exceptionOrNull() ?: IllegalStateException()),
            )
            val failed = (lines.size - p.count - p.duplicates).coerceAtLeast(0)
            return ImportOutcome.Done(
                ImportSummary(
                    imported = p.count, duplicates = p.duplicates, failed = failed,
                    firstFailure = if (failed > 0) "بعضی لینک‌ها معتبر یا پشتیبانی‌شده نبودند." else null,
                ),
            )
        }
        var imported = 0
        var duplicates = 0
        var failed = 0
        var first: String? = null
        for (line in lines) {
            if (ImportLogic.subscriptionUrlOf(line) != null) {
                failed++
                if (first == null) first = "نشانی اشتراک را جداگانه و تنها وارد کنید تا به‌عنوان اشتراک اضافه شود."
                continue
            }
            val r = repo.importPasted(line)
            val p = r.getOrNull()
            when {
                p != null && p.count > 0 -> imported += p.count
                p != null && p.duplicates > 0 -> duplicates += p.duplicates
                else -> {
                    failed++
                    if (first == null) first = r.exceptionOrNull()?.let { ImportLogic.describeError(it) } ?: ImportLogic.MSG_NOTHING_FOUND
                }
            }
        }
        if (imported == 0 && duplicates == 0) return ImportOutcome.Failed(first ?: ImportLogic.MSG_NOTHING_FOUND)
        return ImportOutcome.Done(ImportSummary(imported, duplicates, failed, first))
    }

    // ----- sealed configs -----

    private fun lockedFromText(repo: ConfigRepository, text: String): ImportOutcome {
        val broken = ImportOutcome.Failed("لینک قفل‌شده خراب یا ناقص است؛ دوباره و کامل کپی کنید.")
        val link = LockedConfigExtractor.extractLink(text) ?: return ImportOutcome.Failed(ImportLogic.MSG_GENERIC)
        val bytes = runCatching { NrgLink.decode(link) }.getOrNull() ?: return broken
        val meta = runCatching { repo.peekLocked(bytes) }.getOrNull() ?: return broken
        return openSealed(repo, bytes, meta, LockedConfigExtractor.extractKey(text))
    }

    /** Tries what can be tried without asking (device lock, key found in the message) and asks for the key otherwise. */
    fun openSealed(repo: ConfigRepository, bytes: ByteArray, meta: ZsxMetadata, key: String?): ImportOutcome {
        if (meta.isExpired) return ImportOutcome.Failed(ImportLogic.MSG_EXPIRED)
        if (meta.deviceBound) {
            if (repo.importLocked(bytes, null).isSuccess) return one()
            if (key != null && repo.importLocked(bytes, key).isSuccess) return one()
            return ImportOutcome.Failed(ImportLogic.MSG_OTHER_DEVICE)
        }
        if (meta.passwordProtected) {
            if (key != null) {
                if (repo.isKeyConsumed(key)) return ImportOutcome.Failed(ImportLogic.MSG_CONSUMED)
                if (repo.importLocked(bytes, key).isSuccess) return one()
            }
            return ImportOutcome.NeedKey(bytes, meta)
        }
        val r = repo.importLocked(bytes, null)
        val failure = r.exceptionOrNull() ?: return one()
        return ImportOutcome.Failed(ImportLogic.describeError(failure))
    }

    /** Opens a sealed config with the key typed by the person. Returns null on success, else a Persian reason. */
    suspend fun unlock(repo: ConfigRepository, bytes: ByteArray, meta: ZsxMetadata, key: String): String? =
        withContext(Dispatchers.IO) {
            val clean = key.trim()
            if (clean.isEmpty()) return@withContext "کلید را وارد کنید."
            try {
                val failure = repo.importLocked(bytes, clean).exceptionOrNull()
                if (failure == null) null else ImportLogic.describeUnlockError(failure, meta.deviceBound)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ImportLogic.describeError(e)
            }
        }

    /** Downloads a short link with the key the person typed, then imports what came back. */
    suspend fun importRelayWithKey(repo: ConfigRepository, text: String, key: String): ImportOutcome =
        withContext(Dispatchers.IO) {
            guard {
                val resolved = repo.resolveNrgRelay(text, key.trim())
                val failure = resolved.exceptionOrNull()
                if (failure != null) ImportOutcome.Failed(ImportLogic.describeError(failure))
                else route(repo, resolved.getOrThrow(), null, 1)
            }
        }

    // ----- OpenVPN -----

    private fun ovpn(repo: ConfigRepository, text: String, fileName: String?): ImportOutcome {
        val info = OvpnConfig.inspect(text)
        val name = ImportLogic.baseName(fileName) ?: info.name
        if (info.needsCredentials && !info.hasInlineCredentials) return ImportOutcome.NeedOvpnCredentials(text, name)
        val failure = repo.addOvpn(text, name = name).exceptionOrNull() ?: return one()
        return ImportOutcome.Failed(ImportLogic.describeError(failure))
    }

    suspend fun addOvpn(repo: ConfigRepository, text: String, name: String, user: String, password: String): ImportOutcome =
        withContext(Dispatchers.IO) {
            guard {
                val failure = repo.addOvpn(
                    text, name = name, username = user.trim().ifEmpty { null }, password = password.ifEmpty { null },
                ).exceptionOrNull()
                if (failure == null) one() else ImportOutcome.Failed(ImportLogic.describeError(failure))
            }
        }

    // ----- subscriptions -----

    /** Adds [url] as a subscription and downloads its servers. */
    suspend fun addSubscription(repo: ConfigRepository, url: String, name: String?): ImportOutcome =
        withContext(Dispatchers.IO) {
            guard {
                val result = repo.importSubscriptionUrl(url.trim(), name?.trim()?.takeIf { it.isNotEmpty() })
                val failure = result.exceptionOrNull()
                when {
                    failure == null -> {
                        val r = result.getOrThrow()
                        ImportOutcome.Done(ImportSummary(imported = r.count, subscriptionName = r.subscriptionName))
                    }
                    failure is ConfigRepository.SubscriptionNotFetchedException && failure.savedForLater ->
                        ImportOutcome.Done(ImportSummary(imported = 0, subscriptionName = failure.name, savedOffline = true))
                    else -> ImportOutcome.Failed(ImportLogic.describeError(failure))
                }
            }
        }
}
