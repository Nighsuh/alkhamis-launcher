package com.gathin.transfer

import android.net.Uri

/**
 * The TV draws a QR of `http://address:port/?p=PIN` (TransferActivity.showAddress).
 * A result without that shape is not a TV link.
 */
object QrLink {
    data class Target(val host: String, val port: Int, val pin: String?)

    fun parse(text: String?): Target? {
        if (text.isNullOrBlank()) return null
        val uri = Uri.parse(text.trim())
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if (host.isEmpty()) return null
        val port = if (uri.port in 1..65535) uri.port else 8080
        val pin = uri.getQueryParameter("p")
        val kept = if (pin != null && pin.matches(Regex("\\d{6}"))) pin else null
        return Target(host, port, kept)
    }
}
