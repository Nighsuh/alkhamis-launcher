package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The transfer server: the PIN, a streamed upload, and paths that try to leave the folder. */
public class TransferServerTest {
    @Test
    public void pinBlocksOtherCallers() throws Exception {
        File dir = Files.createTempDirectory("transfer").toFile();
        TransferServer server = TransferServer.start(dir, "482913", 0);
        try {
            assertEquals(401, get(server, "/list", null));
            assertEquals(401, get(server, "/list", "000000"));
            assertEquals(200, get(server, "/list", "482913"));
            assertTrue(TransferServer.pinsEqual("482913", "482913"));
            assertFalse(TransferServer.pinsEqual("482913", "482914"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void uploadStreamsAndDownloadMatches() throws Exception {
        File dir = Files.createTempDirectory("transfer").toFile();
        TransferServer server = TransferServer.start(dir, "123456", 0);
        try {
            byte[] payload = new byte[200_000];
            for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 31);
            assertEquals(200, upload(server, "clip.mp4", payload));
            assertEquals(200, upload(server, "clip.mp4", new byte[] {9, 9, 9}));
            assertTrue(new File(dir, "clip (1).mp4").isFile());
            assertNull(new TransferFiles.FileDir(dir).begin("../secret.txt"));
            File saved = new File(dir, "clip.mp4");
            assertTrue(saved.isFile());
            assertEquals(payload.length, saved.length());
            byte[] back = download(server, "clip.mp4");
            assertEquals(payload.length, back.length);
            for (int i = 0; i < payload.length; i += 1000) assertEquals(payload[i], back[i]);
            assertTrue(TransferServer.BUFFER <= 8192);
        } finally {
            server.stop();
        }
    }

    @Test
    public void pathTraversalIsRejected() throws Exception {
        File dir = Files.createTempDirectory("transfer").toFile();
        File outside = new File(dir.getParentFile(), "secret.txt");
        Files.write(outside.toPath(), "no".getBytes(StandardCharsets.UTF_8));
        TransferServer server = TransferServer.start(dir, "123456", 0);
        try {
            assertNull(server.resolve(".."));
            assertNull(server.resolve("."));
            File inside = server.resolve("../secret.txt");
            assertTrue(inside.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator));
            assertFalse(inside.getCanonicalPath().equals(outside.getCanonicalPath()));
            assertEquals(404, get(server, "/files/..%2Fsecret.txt", "123456"));
            assertEquals("no", new String(Files.readAllBytes(outside.toPath()), StandardCharsets.UTF_8));
            assertEquals(200, post(server, "/note", "text=" + java.net.URLEncoder.encode("https://example.com/a", "UTF-8")));
            assertTrue(server.notes().contains("https://example.com/a"));
        } finally {
            server.stop();
            outside.delete();
        }
    }

    private static int get(TransferServer server, String path, String pin) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + path).openConnection();
        if (pin != null) conn.setRequestProperty("Cookie", "tpin=" + pin);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try {
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private static int post(TransferServer server, String path, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Cookie", "tpin=123456");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
        }
        try {
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private static int upload(TransferServer server, String name, byte[] payload) throws Exception {
        String boundary = "tvhomeBoundary";
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + "/upload").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Cookie", "tpin=123456");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(head.length + payload.length + tail.length);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(10000);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(head);
            int at = 0;
            while (at < payload.length) {
                int n = Math.min(4096, payload.length - at);
                out.write(payload, at, n);
                at += n;
            }
            out.write(tail);
        }
        try {
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] download(TransferServer server, String name) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + server.port() + "/files/" + name).openConnection();
        conn.setRequestProperty("Cookie", "tpin=123456");
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }
}
