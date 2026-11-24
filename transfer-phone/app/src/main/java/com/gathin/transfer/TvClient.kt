package com.gathin.transfer

import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Speaks the TV page's HTTP API. Bodies are written as they are read, 8 KB at a time. */
class TvClient(private val host: String, private val port: Int, private val pin: String) {
    fun version(): Int {
        val text = get("/version")
        return JSONObject(text).optInt("version", 1)
    }

    fun list(): List<RemoteFile> {
        val json = JSONObject(get("/list"))
        val files = json.optJSONArray("files") ?: return emptyList()
        val out = ArrayList<RemoteFile>(files.length())
        for (i in 0 until files.length()) {
            val row = files.getJSONObject(i)
            out.add(RemoteFile(row.getString("name"), row.optLong("size"), row.optLong("time")))
        }
        return out
    }

    fun upload(name: String, length: Long, input: InputStream, progress: (Long, Long) -> Unit) {
        val boundary = "alkhamisBoundary"
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()
        val conn = open("POST", "/upload")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.setFixedLengthStreamingMode(head.size + length + tail.size)
        conn.doOutput = true
        conn.outputStream.use { out ->
            out.write(head)
            val buf = ByteArray(8192)
            var sent = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                sent += n
                progress(sent, length)
            }
            out.write(tail)
        }
        expect(conn)
    }

    fun download(name: String, out: OutputStream) {
        val conn = open("GET", "/files/" + URLEncoder.encode(name, "UTF-8"))
        expect(conn)
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
    }

    fun delete(name: String) {
        form("/delete", "name=" + URLEncoder.encode(name, "UTF-8"))
    }

    fun install(name: String) {
        form("/install", "name=" + URLEncoder.encode(name, "UTF-8"))
    }

    fun note(text: String) {
        form("/note", "text=" + URLEncoder.encode(text, "UTF-8"))
    }

    private fun get(path: String): String {
        val conn = open("GET", path)
        expect(conn)
        return conn.inputStream.bufferedReader().readText()
    }

    private fun form(path: String, body: String) {
        val bytes = body.toByteArray()
        val conn = open("POST", path)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.doOutput = true
        conn.outputStream.use { it.write(bytes) }
        expect(conn)
    }

    private fun open(method: String, path: String): HttpURLConnection {
        val conn = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Cookie", "tpin=$pin")
        conn.connectTimeout = 8000
        conn.readTimeout = 0
        conn.instanceFollowRedirects = false
        return conn
    }

    private fun expect(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code == 401) throw PinRejected()
        if (code !in 200..299) throw java.io.IOException("http $code")
    }
}
