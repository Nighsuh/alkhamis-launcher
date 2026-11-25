package com.gathin.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/** The name and type a content URI should have when it arrives on the TV. */
object Outgoing {
    data class Named(val name: String, val mime: String)

    fun describe(ctx: Context, uri: Uri): Named {
        var name: String? = null
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0)
            }
        } catch (ignored: Exception) { }
        if (name.isNullOrBlank()) name = uri.lastPathSegment ?: "file"
        var leaf = name!!.substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
        val mime = ctx.contentResolver.getType(uri) ?: guess(leaf)
        if (!hasExtension(leaf)) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (!ext.isNullOrBlank()) leaf = "$leaf.$ext"
        }
        return Named(leaf, mime)
    }

    /** Copies the bytes while this app still has permission to read the share URI. */
    fun stage(ctx: Context, uri: Uri): Uri {
        if (uri.scheme == "file") return uri
        val named = describe(ctx, uri)
        val dir = File(ctx.cacheDir, "outbox")
        if (!dir.isDirectory) dir.mkdirs()
        val dest = File(dir, System.currentTimeMillis().toString() + "-" + named.name)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it, 8192) }
        } ?: return uri
        return Uri.fromFile(dest)
    }

    fun hasExtension(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return false
        val ext = name.substring(dot + 1)
        return ext.length <= 5 && ext.all { it.isLetterOrDigit() }
    }

    private fun guess(name: String): String {
        val ext = name.substringAfterLast('.', "")
        if (ext.isEmpty() || ext == name) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: "application/octet-stream"
    }
}
