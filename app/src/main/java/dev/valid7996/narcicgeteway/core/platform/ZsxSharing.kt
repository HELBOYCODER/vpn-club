package dev.valid7996.narcicgeteway.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.ArrayList

object ZsxSharing {
    fun share(context: Context, fileName: String, bytes: ByteArray) =
        shareMany(context, listOf(fileName to bytes))

    fun shareText(context: Context, fileName: String, text: String) {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val targetName = if (fileName.endsWith(".json") || fileName.endsWith(".txt")) fileName else dev.valid7996.narcicgeteway.crypto.nrgFileName(fileName)
            val file = File(dir, targetName).apply { writeText(text) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (targetName.endsWith(".json")) "application/json" else "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        }
    }

    fun shareMany(context: Context, files: List<Pair<String, ByteArray>>) {
        if (files.isEmpty()) return
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val uris = ArrayList<Uri>()
        files.forEach { (name, bytes) ->
            val targetName = if (name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".nrg") || name.endsWith(".zsx")) name else dev.valid7996.narcicgeteway.crypto.nrgFileName(name)
            val file = File(dir, targetName).apply { writeBytes(bytes) }
            uris += FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val isJson = files.all { it.first.endsWith(".json") }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = if (isJson) "application/json" else "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (isJson) "application/json" else "application/octet-stream"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
