package com.gathin.transfer

import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Speaks the TV page's HTTP API. The TV does not ask for a PIN. */
class TvClient(private val host: String, private val port: Int) {
    fun ping() {
        val text = get("/ping")
        if (!text.contains("\"ok\":true") && !text.contains("\"ok\": true")) throw TvFailure(TvFailure.Kind.HTTP, null)
    }

    fun rename(name: String) {
        form("/name", "name=" + URLEncoder.encode(name, "UTF-8"))
    }

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

    fun upload(name: String, mime: String, length: Long, input: InputStream, progress: (Long, Long) -> Unit) {
        val boundary = "alkhamisBoundary"
        val type = if (mime.isBlank()) "application/octet-stream" else mime
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
            "Content-Type: $type\r\n\r\n").toByteArray()
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
        conn.connectTimeout = 8000
        conn.readTimeout = 120_000
        conn.instanceFollowRedirects = false
        return conn
    }

    private fun expect(conn: HttpURLConnection) {
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw TvFailure(TvFailure.Kind.HTTP, java.io.IOException("http $code"))
        } catch (failed: TvFailure) {
            throw failed
        } catch (failed: java.net.SocketTimeoutException) {
            throw TvFailure(TvFailure.Kind.TIMEOUT, failed)
        } catch (failed: java.net.ConnectException) {
            throw TvFailure(TvFailure.Kind.REFUSED, failed)
        } catch (failed: java.net.UnknownHostException) {
            throw TvFailure(TvFailure.Kind.UNREACHABLE, failed)
        } catch (failed: Exception) {
            throw TvFailure(TvFailure.Kind.OTHER, failed)
        }
    }
}

class TvFailure(val kind: Kind, cause: Exception?) : Exception(cause) {
    enum class Kind { TIMEOUT, REFUSED, UNREACHABLE, HTTP, OTHER }
}
