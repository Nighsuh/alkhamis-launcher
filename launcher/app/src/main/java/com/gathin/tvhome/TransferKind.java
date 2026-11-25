package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** File type from a name, a MIME type, or the first bytes of the file. */
final class TransferKind {
    private TransferKind() {}

    static boolean hasExtension(String name) {
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) return false;
        String ext = name.substring(dot + 1);
        if (ext.length() > 5) return false;
        for (int i = 0; i < ext.length(); i++) {
            if (!Character.isLetterOrDigit(ext.charAt(i))) return false;
        }
        return true;
    }

    /** Adds `.jpg` and the like when the name has none and the type is known. */
    static String ensureExtension(String name, String mime) {
        if (name == null) return null;
        if (hasExtension(name)) return name;
        String ext = extension(mime);
        if (ext == null) return name;
        return name + "." + ext;
    }

    static String extension(String mime) {
        if (mime == null) return null;
        String type = mime.toLowerCase(Locale.US).trim();
        int semi = type.indexOf(';');
        if (semi >= 0) type = type.substring(0, semi).trim();
        if (type.equals("image/jpeg")) return "jpg";
        if (type.equals("image/png")) return "png";
        if (type.equals("image/webp")) return "webp";
        if (type.equals("image/gif")) return "gif";
        if (type.equals("video/mp4")) return "mp4";
        if (type.equals("video/webm")) return "webm";
        if (type.equals("video/x-matroska")) return "mkv";
        if (type.equals("audio/mpeg")) return "mp3";
        if (type.equals("audio/mp4")) return "m4a";
        if (type.equals("application/pdf")) return "pdf";
        if (type.equals("application/vnd.android.package-archive")) return "apk";
        if (type.equals("text/plain")) return "txt";
        if (type.equals("application/zip")) return "zip";
        return null;
    }

    /** jpg, png, webp, gif, mp4, mkv, mp3, pdf, apk (a zip that names AndroidManifest), or zip. */
    static String sniff(byte[] head) {
        if (head == null || head.length < 4) return null;
        if ((head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8 && (head[2] & 0xff) == 0xff) return "image/jpeg";
        if (head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') return "image/png";
        if (head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') return "application/pdf";
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F') return "image/gif";
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') return "image/webp";
        if (head.length >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return "video/mp4";
        if ((head[0] & 0xff) == 0x1a && (head[1] & 0xff) == 0x45 && (head[2] & 0xff) == 0xdf && (head[3] & 0xff) == 0xa3)
            return "video/x-matroska";
        if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return "audio/mpeg";
        if (head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
            String ascii = new String(head, StandardCharsets.ISO_8859_1);
            if (ascii.contains("AndroidManifest")) return "application/vnd.android.package-archive";
            return "application/zip";
        }
        return null;
    }

    static void repair(TransferFiles.Dir dir) {
        if (dir == null) return;
        java.util.List<TransferFiles.Item> items = new java.util.ArrayList<>(dir.list());
        for (TransferFiles.Item item : items) {
            if (item == null || hasExtension(item.name)) continue;
            byte[] head = new byte[512];
            int n = 0;
            try (java.io.InputStream in = dir.open(item)) {
                if (in == null) continue;
                while (n < head.length) {
                    int got = in.read(head, n, head.length - n);
                    if (got < 0) break;
                    n += got;
                }
            } catch (java.io.IOException ignored) {
                continue;
            }
            byte[] slice = n == head.length ? head : java.util.Arrays.copyOf(head, n);
            String mime = sniff(slice);
            if (mime == null) continue;
            String next = ensureExtension(item.name, mime);
            if (next != null && !next.equals(item.name)) dir.retitle(item.name, next, mime);
        }
    }
}
