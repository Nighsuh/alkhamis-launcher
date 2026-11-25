package com.gathin.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class ProtocolTest {
    @Test
    fun retriesStopAfterThree() {
        assertEquals(1, Queue.nextAttempt(0))
        assertEquals(2, Queue.nextAttempt(1))
        assertNull(Queue.nextAttempt(2))
    }

    @Test
    fun uploadStreamsAndPinIsRequired() {
        val server = ServerSocket(0)
        val thread = Thread {
            val socket = server.accept()
            serve(socket)
        }
        thread.start()
        val client = TvClient("127.0.0.1", server.localPort)
        val payload = ByteArray(200_000) { (it * 31).toByte() }
        client.upload("clip.mp4", "video/mp4", payload.size.toLong(), ByteArrayInputStream(payload)) { _, _ -> }
        thread.join(4000)
        server.close()
        assertTrue(received in 200_000..(200_000 + 400))
    }

    @Volatile private var received = 0

    private fun serve(socket: Socket) {
        socket.use { client ->
            val input = client.getInputStream()
            val header = readHeader(input)
            val text = String(header, StandardCharsets.ISO_8859_1)
            val first = text.substringBefore("\r\n")
            val path = first.split(" ")[1]
            val out = client.getOutputStream()
            if (path.startsWith("/list")) {
                write(out, 200, "{\"version\":1,\"files\":[{\"name\":\"clip.mp4\",\"size\":$received,\"time\":1}],\"notes\":[]}")
                return
            }
            val length = text.substringAfter("Content-Length: ").substringBefore("\r").trim().toInt()
            var left = length
            var count = 0
            val buf = ByteArray(8192)
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size, left))
                if (n < 0) break
                count += n
                left -= n
            }
            received = count
            write(out, 200, "ok")
        }
    }

    private fun readHeader(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var a = 0; var b = 0; var c = 0
        while (out.size() < 8000) {
            val d = input.read()
            if (d < 0) break
            out.write(d)
            if (a == '\r'.code && b == '\n'.code && c == '\r'.code && d == '\n'.code) break
            a = b; b = c; c = d
        }
        return out.toByteArray()
    }

    private fun write(out: java.io.OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray()
        val head = "HTTP/1.1 $code OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(bytes)
    }
}
