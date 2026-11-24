package com.gathin.transfer

import android.net.Uri

/** The QR on the TV is http://address:port/?p=PIN */
object QrLink {
    data class Target(val host: String, val port: Int, val pin: String?)

    fun parse(text: String?): Target? {
        if (text.isNullOrBlank()) return null
        val uri = Uri.parse(text.trim())
        val host = uri.host ?: return null
        if (host.isEmpty()) return null
        val port = if (uri.port > 0) uri.port else 8080
        return Target(host, port, uri.getQueryParameter("p"))
    }
}
