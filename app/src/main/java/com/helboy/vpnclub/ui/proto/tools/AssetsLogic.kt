package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.data.assets.GeoAsset
import dev.valid7996.narcicgeteway.domain.model.GeoFilesSource
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** What the server says about the latest file (both values can be missing). */
data class RemoteInfo(val sizeBytes: Long?, val lastModifiedMs: Long?)

enum class UpdateState { Missing, UpToDate, UpdateAvailable, Unknown }

/** Thrown for a non-2xx answer. */
class GeoHttpException(val code: Int) : IOException("HTTP $code")

/** Thrown when what came back cannot be a geo file. */
class GeoBadFileException(message: String) : IOException(message)

fun geoUrl(source: GeoFilesSource, name: String): String = "https://github.com/${source.repo}/releases/latest/download/$name"

/**
 * Compares the installed file with the latest one. A different size or a newer remote timestamp means an update.
 * The local timestamp is the time of installation, so a bundled copy older than the release shows as an update.
 */
fun updateState(local: GeoAsset, remote: RemoteInfo?): UpdateState = when {
    !local.present -> UpdateState.Missing
    remote == null || (remote.sizeBytes == null && remote.lastModifiedMs == null) -> UpdateState.Unknown
    remote.sizeBytes != null && remote.sizeBytes != local.sizeBytes -> UpdateState.UpdateAvailable
    remote.lastModifiedMs != null && local.updatedAt > 0 && remote.lastModifiedMs > local.updatedAt + 60_000 -> UpdateState.UpdateAvailable
    else -> UpdateState.UpToDate
}

fun updateStateLabel(state: UpdateState): String = when (state) {
    UpdateState.Missing -> "نصب نشده"
    UpdateState.UpToDate -> "به‌روز است"
    UpdateState.UpdateAvailable -> "نسخهٔ تازه موجود است"
    UpdateState.Unknown -> "بررسی نشده"
}

/** geoip.dat / geosite.dat are megabytes; anything tiny is an error page or a truncated download. */
fun looksLikeGeoFile(sizeBytes: Long): Boolean = sizeBytes >= MIN_GEO_BYTES

const val MIN_GEO_BYTES = 50_000L

/** 0..1, or null when the total is unknown. */
fun progressFraction(done: Long, total: Long?): Float? =
    if (total == null || total <= 0) null else (done.toFloat() / total).coerceIn(0f, 1f)

/** Plain Persian reason for a failed fetch; never an exception dump. */
fun fetchErrorMessage(e: Throwable): String = when (e) {
    is UnknownHostException, is ConnectException -> "اتصال به اینترنت برقرار نشد."
    is SocketTimeoutException -> "زمان اتصال تمام شد."
    is SSLException -> "اتصال امن برقرار نشد."
    is GeoHttpException -> if (e.code == 404) "این فایل در منبع انتخاب‌شده پیدا نشد؛ منبع دیگری را امتحان کنید." else "سرور خطا داد (${e.code})."
    is GeoBadFileException -> "فایل دریافتی معتبر نبود."
    else -> "دریافت ناموفق بود."
}

const val GEO_SOURCE_HINT =
    "منبع‌ها یکسان نیستند: دسته‌های ایران فقط در Chocolate4U و دسته‌های روسیه فقط در runetfreedom هستند. " +
        "اگر قانونی دسته‌ای را بخواهد که در فایل شما نیست، هسته اجرا نمی‌شود."

const val GEO_BUNDLED_NOTE = "نسخه‌ای از این فایل‌ها همراه برنامه نصب می‌شود تا مسیریابی از همان ابتدا کار کند؛ برای نسخهٔ تازه‌تر دریافت کنید."
