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
    public void pageFollowsTheChosenLanguage() {
        String arabic = TransferPage.app("ar");
        String english = TransferPage.app("en");
        assertTrue(arabic.contains("dir=\"rtl\""));
        assertTrue(arabic.contains("أسقط الملفات هنا"));
        assertTrue(arabic.contains("?lang=en"));
        assertTrue(english.contains("dir=\"ltr\""));
        assertTrue(english.contains("Drop files here"));
        assertTrue(english.contains("?lang=ar"));
        assertTrue(english.contains("Alkhamis TV Transfer"));
        assertTrue(english.contains(">EN<"));
    }

    @Test
    public void pinBlocksOtherCallers() throws Exception {
        File dir = Files.createTempDirectory("transfer").toFile();
        TransferServer server = TransferServer.start(dir, 0);
        try {
            assertEquals(200, get(server, "/list", null));
            assertEquals(200, get(server, "/ping", null));
            assertEquals(200, post(server, "/name", "name=Living%20room", null));
        } finally {
            server.stop();
        }
    }

    @Test
    public void uploadStreamsAndDownloadMatches() throws Exception {
        File dir = Files.createTempDirectory("transfer").toFile();
        TransferServer server = TransferServer.start(dir, 0);
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
    public void installAsksForTheApkInsideTheFolder() throws Exception {
        File dir = Files.createTempDirectory("transfer").toFile();
        java.util.concurrent.atomic.AtomicReference<String> asked = new java.util.concurrent.atomic.AtomicReference<>();
        TransferServer.setInstallHook(asked::set);
        TransferServer server = TransferServer.start(dir, 0);
        try {
            assertEquals(404, post(server, "/install", "name=app.apk", null));
            assertEquals(200, upload(server, "app.apk", new byte[] {1, 2, 3}));
            assertEquals(200, post(server, "/install", "name=app.apk", "123456"));
            assertEquals("app.apk", asked.get());
            assertEquals(404, post(server, "/install", "name=..%2Fsecret.txt", "123456"));
            assertEquals(200, get(server, "/version", "123456"));
            assertEquals("_alkhamis._tcp.", TransferAnnounce.TYPE);
            StringBuilder seen = new StringBuilder();
            TransferAnnounce.use(new TransferAnnounce.Registrar() {
                @Override public void register(android.content.Context ctx, String instance, int port) {
                    seen.append(instance).append(':').append(port);
                }
                @Override public void unregister() { seen.append(":off"); }
            });
            TransferAnnounce.publish(null, server.port());
            TransferAnnounce.withdraw();
            TransferAnnounce.use(null);
            assertTrue(seen.toString().startsWith("Alkhamis-test:" + server.port()));
            assertTrue(seen.toString().endsWith(":off"));
        } finally {
            TransferServer.setInstallHook(null);
            server.stop();
        }
    }

    @Test
    public void severalTypesKeepTheirBytesAndGainExtensions() throws Exception {
        File dir = Files.createTempDirectory("types").toFile();
        TransferServer server = TransferServer.start(dir, 0);
        try {
            checkUpload(server, dir, "image1000424971", "image/jpeg", "image1000424971.jpg", jpeg());
            checkUpload(server, dir, "clip", "video/mp4", "clip.mp4", mp4());
            checkUpload(server, dir, "page", "application/pdf", "page.pdf", pdf());
            checkUpload(server, dir, "icon", "image/png", "icon.png", png());
            byte[] apk = apk();
            checkUpload(server, dir, "app", "application/vnd.android.package-archive", "app.apk", apk);
            for (String name : new String[]{"image1000424971.jpg", "clip.mp4", "page.pdf", "icon.png", "app.apk"}) {
                assertEquals(200, post(server, "/delete", "name=" + name, null));
            }
            String list = body(server, "/list");
            assertFalse(list.contains("image1000424971"));
            assertFalse(list.contains("clip.mp4"));
            assertTrue(list.contains("\"files\":[]") || !list.contains("\"name\""));
        } finally {
            server.stop();
        }
    }

    @Test
    public void aVirtualAddressDoesNotWin() {
        java.util.List<TransferHub.Nic> faces = java.util.Arrays.asList(
                new TransferHub.Nic("docker0", "172.17.0.1"),
                new TransferHub.Nic("wlan0", "192.168.1.20"),
                new TransferHub.Nic("eth0", "10.0.0.4"));
        assertEquals("192.168.1.20", TransferHub.choose(null, faces));
        assertEquals("10.0.0.4", TransferHub.choose("10.0.0.4", faces));
        assertTrue(TransferHub.rank("docker0", "172.17.0.1") < 0);
    }

    @Test
    public void pathTraversalIsRejected() throws Exception {
        File dir = Files.createTempDirectory("transfer").toFile();
        File outside = new File(dir.getParentFile(), "secret.txt");
        Files.write(outside.toPath(), "no".getBytes(StandardCharsets.UTF_8));
        TransferServer server = TransferServer.start(dir, 0);
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

    private static void checkUpload(TransferServer server, File dir, String sent, String mime, String stored, byte[] payload)
            throws Exception {
        assertEquals(200, uploadTyped(server, sent, mime, payload));
        File file = new File(dir, stored);
        assertTrue(stored + " missing", file.isFile());
        byte[] back = Files.readAllBytes(file.toPath());
        assertEquals(payload.length, back.length);
        assertEquals(sha(payload), sha(back));
    }

    private static String sha(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] jpeg() {
        byte[] bytes = new byte[64];
        bytes[0] = (byte) 0xff; bytes[1] = (byte) 0xd8; bytes[2] = (byte) 0xff; bytes[3] = (byte) 0xd9;
        for (int i = 4; i < bytes.length - 1; i++) bytes[i] = (byte) i;
        return bytes;
    }

    private static byte[] png() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0x89; bytes[1] = 'P'; bytes[2] = 'N'; bytes[3] = 'G';
        return bytes;
    }

    private static byte[] pdf() {
        return "%PDF-1.4\nhello\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] mp4() {
        byte[] bytes = new byte[32];
        bytes[4] = 'f'; bytes[5] = 't'; bytes[6] = 'y'; bytes[7] = 'p';
        return bytes;
    }

    private static byte[] apk() {
        byte[] text = "PK\u0003\u0004AndroidManifest.xml".getBytes(StandardCharsets.ISO_8859_1);
        byte[] bytes = new byte[64];
        System.arraycopy(text, 0, bytes, 0, text.length);
        return bytes;
    }

    private static String body(TransferServer server, String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + path).openConnection();
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
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
        return post(server, path, body, "123456");
    }

    private static int post(TransferServer server, String path, String body, String pin) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + path).openConnection();
        conn.setRequestMethod("POST");
        if (pin != null) conn.setRequestProperty("Cookie", "tpin=" + pin);
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
        return uploadTyped(server, name, "application/octet-stream", payload);
    }

    private static int uploadTyped(TransferServer server, String name, String mime, byte[] payload) throws Exception {
        String boundary = "tvhomeBoundary";
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
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
