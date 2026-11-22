package com.gathin.tvhome;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small HTTP server for the transfer folder. It speaks only to callers that know the PIN,
 * writes uploads straight to disk, and refuses paths that leave the folder.
 */
final class TransferServer {
    static final int BUFFER = 8192;
    private static final String NOTES = ".notes.txt";

    private final File root;
    private final String pin;
    private final ServerSocket socket;
    private volatile boolean open = true;

    private TransferServer(File root, String pin, ServerSocket socket) {
        this.root = root;
        this.pin = pin;
        this.socket = socket;
    }

    static TransferServer start(File root, String pin, int port) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("folder");
        ServerSocket socket = new ServerSocket(port);
        TransferServer server = new TransferServer(root, pin, socket);
        Thread t = new Thread(server::acceptLoop, "transfer-accept");
        t.setDaemon(true);
        t.start();
        return server;
    }

    int port() {
        return socket.getLocalPort();
    }

    void stop() {
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) { }
    }

    private void acceptLoop() {
        while (open) {
            try {
                Socket client = socket.accept();
                Thread t = new Thread(() -> handle(client), "transfer-client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (open) continue;
                return;
            }
        }
    }

    private void handle(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(120_000);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            byte[] header = readHeaders(in);
            if (header == null) return;
            String text = new String(header, StandardCharsets.ISO_8859_1);
            int split = text.indexOf("\r\n");
            if (split < 0) return;
            String[] request = text.substring(0, split).split(" ");
            if (request.length < 2) return;
            String method = request[0];
            String target = request[1];
            String headers = text.substring(split + 2);
            long length = contentLength(headers);
            String type = headerValue(headers, "content-type");
            boolean authed = authorized(headers, target);
            OutputStream out = socket.getOutputStream();
            if ("GET".equals(method) && (target.equals("/") || target.startsWith("/?"))) {
                String page = authed ? TransferPage.app() : TransferPage.pin();
                if (queryPin(target) != null && pinOk(queryPin(target))) {
                    send(out, 200, "text/html; charset=utf-8", TransferPage.app().getBytes(StandardCharsets.UTF_8),
                            "Set-Cookie: tpin=" + pin + "; Path=/; HttpOnly");
                } else {
                    send(out, authed ? 200 : 200, "text/html; charset=utf-8",
                            page.getBytes(StandardCharsets.UTF_8), null);
                }
                return;
            }
            if (!authed) {
                send(out, 401, "text/plain; charset=utf-8", "pin".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            if ("GET".equals(method) && "/list".equals(pathOf(target))) {
                send(out, 200, "application/json; charset=utf-8",
                        listJson().getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            if ("GET".equals(method) && pathOf(target).startsWith("/files/")) {
                File file = resolve(pathOf(target).substring("/files/".length()));
                if (file == null || !file.isFile()) {
                    send(out, 404, "text/plain", "missing".getBytes(StandardCharsets.UTF_8), null);
                    return;
                }
                sendFile(out, file);
                return;
            }
            if ("POST".equals(method) && "/upload".equals(pathOf(target))) {
                String boundary = boundary(type);
                if (boundary == null || length < 0) {
                    send(out, 400, "text/plain", "upload".getBytes(StandardCharsets.UTF_8), null);
                    return;
                }
                acceptUpload(in, length, boundary);
                send(out, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            byte[] body = readBody(in, length);
            String form = new String(body, StandardCharsets.UTF_8);
            if ("POST".equals(method) && "/delete".equals(pathOf(target))) {
                File file = resolve(formValue(form, "name"));
                if (file != null && file.isFile()) file.delete();
                send(out, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            if ("POST".equals(method) && "/note".equals(pathOf(target))) {
                String note = formValue(form, "text");
                if (note != null && !note.trim().isEmpty()) appendNote(note.trim());
                send(out, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            send(out, 404, "text/plain", "no".getBytes(StandardCharsets.UTF_8), null);
        } catch (Exception ignored) { }
    }

    private boolean authorized(String headers, String target) {
        if (pinOk(cookiePin(headers))) return true;
        return pinOk(queryPin(target));
    }

    /** Same answer time for a wrong PIN and a missing one. */
    static boolean pinsEqual(String expected, String given) {
        if (expected == null || given == null || expected.length() != given.length()) return false;
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) diff |= expected.charAt(i) ^ given.charAt(i);
        return diff == 0;
    }

    private boolean pinOk(String given) {
        return pinsEqual(pin, given);
    }

    /** A file inside the transfer folder, or null when the name tries to leave it. */
    File resolve(String raw) {
        String name = safeName(raw);
        if (name == null) return null;
        try {
            File file = new File(root, name);
            String base = root.getCanonicalPath();
            String path = file.getCanonicalPath();
            if (!path.equals(base) && !path.startsWith(base + File.separator)) return null;
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    static String safeName(String raw) {
        if (raw == null) return null;
        String name;
        try {
            name = URLDecoder.decode(raw, "UTF-8");
        } catch (Exception e) {
            name = raw;
        }
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < 80; i++) {
            char c = name.charAt(i);
            if (c < 32 || c == '/' || c == '\\' || c == ':' || c == 0x7f) continue;
            out.append(c);
        }
        String cleaned = out.toString().trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..") || cleaned.startsWith(".")) return null;
        return cleaned;
    }

    /** Reads one multipart body, writing each file part to the folder as it arrives. */
    void acceptUpload(InputStream in, long length, String boundary) throws IOException {
        byte[] dash = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] sep = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] buf = new byte[BUFFER];
        int fill = 0;
        long left = length;
        fill = pull(in, buf, fill, left);
        left -= Math.max(0, fill);
        int start = indexOf(buf, fill, dash);
        if (start < 0) return;
        fill = shift(buf, fill, start + dash.length);
        if (fill >= 2 && buf[0] == '\r' && buf[1] == '\n') fill = shift(buf, fill, 2);
        while (true) {
            while (indexOf(buf, fill, new byte[]{'\r', '\n', '\r', '\n'}) < 0 && left > 0) {
                int before = fill;
                fill = pull(in, buf, fill, left);
                left -= fill - before;
                if (fill == before) break;
            }
            int headerEnd = indexOf(buf, fill, new byte[]{'\r', '\n', '\r', '\n'});
            if (headerEnd < 0) return;
            String headers = new String(buf, 0, headerEnd, StandardCharsets.ISO_8859_1);
            fill = shift(buf, fill, headerEnd + 4);
            if (headers.contains("--") && headers.trim().isEmpty()) return;
            String filename = filename(headers);
            if (filename == null) {
                fill = discardUntil(in, buf, fill, left, sep);
                left = 0;
                if (ended(buf, fill, boundary)) return;
                continue;
            }
            File dest = unique(resolve(filename));
            if (dest == null) {
                fill = discardUntil(in, buf, fill, left, sep);
                if (ended(buf, fill, boundary)) return;
                continue;
            }
            try (FileOutputStream file = new FileOutputStream(dest)) {
                fill = writeUntil(in, buf, fill, left, sep, file);
            }
            left = 0;
            if (ended(buf, fill, boundary)) return;
            if (fill >= 2 && buf[0] == '\r' && buf[1] == '\n') fill = shift(buf, fill, 2);
        }
    }

    private boolean ended(byte[] buf, int fill, String boundary) {
        int close = indexOf(buf, fill, ("--" + boundary + "--").getBytes(StandardCharsets.US_ASCII));
        return close >= 0;
    }

    private int discardUntil(InputStream in, byte[] buf, int fill, long left, byte[] sep) throws IOException {
        return writeUntil(in, buf, fill, left, sep, null);
    }

    private int writeUntil(InputStream in, byte[] buf, int fill, long left, byte[] sep, OutputStream file)
            throws IOException {
        while (true) {
            int at = indexOf(buf, fill, sep);
            if (at >= 0) {
                if (file != null && at > 0) file.write(buf, 0, at);
                return shift(buf, fill, at + sep.length);
            }
            int keep = sep.length - 1;
            if (fill > keep) {
                if (file != null) file.write(buf, 0, fill - keep);
                fill = shift(buf, fill, fill - keep);
            }
            if (left <= 0 && fill < buf.length) {
                if (file != null && fill > 0) file.write(buf, 0, fill);
                return 0;
            }
            int before = fill;
            int space = buf.length - fill;
            int want = (int) Math.min(space, Math.max(left, 0));
            if (want <= 0) return fill;
            int n = in.read(buf, fill, want);
            if (n < 0) return fill;
            fill += n;
            left -= n;
            if (fill == before) return fill;
        }
    }

    private int pull(InputStream in, byte[] buf, int fill, long left) throws IOException {
        if (left <= 0 || fill >= buf.length) return fill;
        int n = in.read(buf, fill, (int) Math.min(buf.length - fill, left));
        if (n < 0) return fill;
        return fill + n;
    }

    private File unique(File file) {
        if (file == null) return null;
        if (!file.exists()) return file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            File next = new File(file.getParentFile(), stem + "-" + i + ext);
            if (!next.exists()) return next;
        }
        return file;
    }

    private static String filename(String headers) {
        String lower = headers.toLowerCase(Locale.US);
        int star = lower.indexOf("filename*=");
        if (star >= 0) {
            String v = headers.substring(star + "filename*=".length()).split("\r\n", 2)[0].trim();
            int ticks = v.indexOf("''");
            if (ticks >= 0) v = v.substring(ticks + 2);
            return safeName(v.replace("\"", ""));
        }
        int at = lower.indexOf("filename=");
        if (at < 0) return null;
        String v = headers.substring(at + "filename=".length()).split("\r\n", 2)[0].trim();
        if (v.startsWith("\"")) {
            int end = v.indexOf('"', 1);
            v = end > 1 ? v.substring(1, end) : v.substring(1);
        } else {
            int semi = v.indexOf(';');
            if (semi >= 0) v = v.substring(0, semi).trim();
        }
        return safeName(v);
    }

    private void appendNote(String text) throws IOException {
        String line = text.replace('\r', ' ').replace('\n', ' ');
        if (line.length() > 2000) line = line.substring(0, 2000);
        try (FileOutputStream out = new FileOutputStream(new File(root, NOTES), true)) {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    List<String> notes() {
        List<String> out = new ArrayList<>();
        File file = new File(root, NOTES);
        if (!file.isFile()) return out;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String text = new String(bytes, StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                if (!line.trim().isEmpty()) out.add(line.trim());
            }
        } catch (IOException ignored) { }
        if (out.size() > 20) return out.subList(out.size() - 20, out.size());
        return out;
    }

    private String listJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"files\":[");
        File[] files = root.listFiles();
        boolean first = true;
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File file : files) {
                if (!file.isFile() || file.getName().startsWith(".")) continue;
                if (!first) json.append(',');
                first = false;
                json.append("{\"name\":\"").append(jsonEscape(file.getName()))
                        .append("\",\"size\":").append(file.length())
                        .append(",\"time\":").append(file.lastModified()).append('}');
            }
        }
        json.append("],\"notes\":[");
        List<String> notes = notes();
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) json.append(',');
            json.append('"').append(jsonEscape(notes.get(i))).append('"');
        }
        json.append("]}");
        return json.toString();
    }

    private static String jsonEscape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static byte[] readHeaders(BufferedInputStream in) throws IOException {
        java.io.ByteArrayOutputStream header = new java.io.ByteArrayOutputStream();
        int a = 0, b = 0, c = 0;
        while (header.size() < 32768) {
            int d = in.read();
            if (d < 0) return header.size() == 0 ? null : header.toByteArray();
            header.write(d);
            if (a == '\r' && b == '\n' && c == '\r' && d == '\n') return header.toByteArray();
            a = b;
            b = c;
            c = d;
        }
        return null;
    }

    private static byte[] readBody(InputStream in, long length) throws IOException {
        if (length <= 0 || length > 64 * 1024) return new byte[0];
        byte[] body = new byte[(int) length];
        int got = 0;
        while (got < body.length) {
            int n = in.read(body, got, body.length - got);
            if (n < 0) break;
            got += n;
        }
        return body;
    }

    private static long contentLength(String headers) {
        String v = headerValue(headers, "content-length");
        if (v == null) return -1;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String headerValue(String headers, String name) {
        String prefix = name.toLowerCase(Locale.US) + ":";
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase(Locale.US).startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return null;
    }

    private static String cookiePin(String headers) {
        String cookie = headerValue(headers, "cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith("tpin=")) return p.substring(5);
        }
        return null;
    }

    private static String queryPin(String target) {
        int q = target.indexOf('?');
        if (q < 0) return null;
        return formValue(target.substring(q + 1), "p");
    }

    private static String pathOf(String target) {
        int q = target.indexOf('?');
        return q < 0 ? target : target.substring(0, q);
    }

    static String formValue(String form, String key) {
        if (form == null) return null;
        for (String part : form.split("&")) {
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            if (!key.equals(k)) continue;
            String v = eq < 0 ? "" : part.substring(eq + 1);
            try {
                return URLDecoder.decode(v, "UTF-8");
            } catch (Exception e) {
                return v;
            }
        }
        return null;
    }

    private static String boundary(String type) {
        if (type == null) return null;
        String lower = type.toLowerCase(Locale.US);
        int at = lower.indexOf("boundary=");
        if (at < 0) return null;
        String v = type.substring(at + "boundary=".length()).trim();
        if (v.startsWith("\"")) {
            int end = v.indexOf('"', 1);
            return end > 1 ? v.substring(1, end) : null;
        }
        int semi = v.indexOf(';');
        if (semi >= 0) v = v.substring(0, semi).trim();
        return v.isEmpty() ? null : v;
    }

    private static int indexOf(byte[] data, int length, byte[] needle) {
        if (needle.length == 0 || length < needle.length) return -1;
        outer:
        for (int i = 0; i <= length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static int shift(byte[] buf, int fill, int drop) {
        int keep = fill - drop;
        if (keep > 0) System.arraycopy(buf, drop, buf, 0, keep);
        return Math.max(0, keep);
    }

    private void sendFile(OutputStream out, File file) throws IOException {
        byte[] head = ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: "
                + file.length() + "\r\nContent-Disposition: attachment; filename=\""
                + file.getName().replace("\"", "") + "\"\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        out.write(head);
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[BUFFER];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void send(OutputStream out, int code, String type, byte[] body, String extra) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(code).append(code == 200 ? " OK" : " ERR").append("\r\n");
        head.append("Content-Type: ").append(type).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Connection: close\r\n");
        if (extra != null) head.append(extra).append("\r\n");
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
    }
}
