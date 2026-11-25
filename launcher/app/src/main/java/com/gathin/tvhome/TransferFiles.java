package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The public Download/AlkhamisTransfer folder. Android 10 and later write through MediaStore,
 * so no broad storage permission is needed. Older releases use the public Downloads directory.
 */
final class TransferFiles {
    static final String FOLDER = "AlkhamisTransfer";
    /** Relative path MediaStore expects, including the trailing slash. */
    static final String RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/";
    private static final String MIGRATED = "transfer_public_362";

    private TransferFiles() {}

    static final class Item {
        final String name;
        final long size;
        final long modified;
        final Uri uri;
        final File file;

        Item(String name, long size, long modified, Uri uri, File file) {
            this.name = name;
            this.size = size;
            this.modified = modified;
            this.uri = uri;
            this.file = file;
        }
    }

    /** A new file whose bytes are written as they arrive. */
    static final class Out {
        final String name;
        final OutputStream stream;
        final Uri uri;
        final File file;
        private final Runnable commit;
        private final Runnable abort;
        private boolean settled;

        Out(String name, OutputStream stream, Uri uri, File file, Runnable commit, Runnable abort) {
            this.name = name;
            this.stream = stream;
            this.uri = uri;
            this.file = file;
            this.commit = commit;
            this.abort = abort;
        }

        void commit() throws IOException {
            if (settled) return;
            settled = true;
            stream.close();
            if (commit != null) commit.run();
        }

        void abort() {
            if (settled) return;
            settled = true;
            try {
                stream.close();
            } catch (IOException ignored) { }
            if (abort != null) abort.run();
        }
    }

    interface Dir {
        List<Item> list();

        Item find(String safeName);

        Out begin(String safeName) throws IOException;

        /** Writes under a name that has an extension when the type is known. */
        default Out begin(String safeName, String mime) throws IOException {
            return begin(TransferKind.ensureExtension(safeName, mime));
        }

        InputStream open(Item item) throws IOException;

        boolean delete(String safeName);

        /** Renames a stored file and records its type. Used when the upload had no extension. */
        default void retitle(String from, String to, String mime) { }

        void touch(Out out, long modified);
    }

    static Dir open(Context ctx) {
        if (Build.VERSION.SDK_INT >= 29) return new MediaDir(ctx.getApplicationContext());
        return new FileDir(legacyFolder());
    }

    static File legacyFolder() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    static boolean storageReady(Context ctx) {
        if (Build.VERSION.SDK_INT >= 29) return true;
        return ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Copies files left in the old private folder. Notes stay private. */
    static void migrate(Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(MIGRATED, false)) return;
        if (!storageReady(ctx)) return;
        File old = ctx.getExternalFilesDir("transfer");
        if (old != null && old.isDirectory()) {
            File[] files = old.listFiles();
            if (files != null) {
                Dir dest = open(ctx);
                for (File file : files) {
                    if (!file.isFile() || file.getName().startsWith(".")) continue;
                    try {
                        if (!importFile(dest, file)) return;
                    } catch (IOException e) {
                        return;
                    }
                }
            }
        }
        prefs.edit().putBoolean(MIGRATED, true).apply();
    }

    static boolean importFile(Dir dest, File source) throws IOException {
        String safe = TransferServer.safeName(source.getName());
        if (safe == null) return true;
        Item existing = dest.find(safe);
        if (existing != null && existing.size == source.length()) {
            source.delete();
            return !source.exists();
        }
        Out out = dest.begin(safe);
        if (out == null) return false;
        boolean wrote = false;
        try (InputStream in = new FileInputStream(source)) {
            byte[] buf = new byte[TransferServer.BUFFER];
            int n;
            while ((n = in.read(buf)) > 0) out.stream.write(buf, 0, n);
            out.commit();
            wrote = true;
        } finally {
            if (!wrote) out.abort();
        }
        dest.touch(out, source.lastModified());
        source.delete();
        return !source.exists();
    }

    static Uri share(Context ctx, Item item) {
        if (item == null) return null;
        if (item.uri != null) return item.uri;
        if (item.file == null) return null;
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".transfer", item.file);
    }

    static String uniqueName(String name, Set<String> taken) {
        if (name == null) return null;
        if (!taken.contains(name)) return name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            String next = stem + " (" + i + ")" + ext;
            if (!taken.contains(next)) return next;
        }
        return name;
    }

    static String mime(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    /** The intent another app uses to open this file. The read grant travels with the clip. */
    static android.content.Intent viewIntent(Context ctx, Item item) {
        android.net.Uri uri = share(ctx, item);
        String type = mime(item == null ? "" : item.name);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        if (uri != null) {
            intent.setDataAndType(uri, type);
            intent.setClipData(android.content.ClipData.newRawUri(item == null ? "" : item.name, uri));
        }
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    static final class FileDir implements Dir {
        private final File root;

        FileDir(File root) {
            this.root = root;
            if (root != null && !root.isDirectory()) root.mkdirs();
        }

        @Override
        public List<Item> list() {
            List<Item> out = new ArrayList<>();
            File[] files = root == null ? null : root.listFiles();
            if (files == null) return out;
            for (File file : files) {
                if (file.isFile() && !file.getName().startsWith(".")) {
                    out.add(new Item(file.getName(), file.length(), file.lastModified(), null, file));
                }
            }
            return out;
        }

        @Override
        public Item find(String safeName) {
            if (safeName == null || root == null) return null;
            File file = new File(root, safeName);
            try {
                String base = root.getCanonicalPath();
                String path = file.getCanonicalPath();
                if (!path.equals(base) && !path.startsWith(base + File.separator)) return null;
            } catch (IOException e) {
                return null;
            }
            if (!file.isFile()) return null;
            return new Item(file.getName(), file.length(), file.lastModified(), null, file);
        }

        @Override
        public Out begin(String safeName) throws IOException {
            if (safeName == null || root == null) return null;
            Set<String> taken = new HashSet<>();
            for (Item item : list()) taken.add(item.name);
            String name = uniqueName(safeName, taken);
            File dest = new File(root, name);
            String base = root.getCanonicalPath();
            String path = dest.getCanonicalPath();
            if (!path.startsWith(base + File.separator)) return null;
            FileOutputStream stream = new FileOutputStream(dest);
            return new Out(name, stream, null, dest, null, dest::delete);
        }

        @Override
        public InputStream open(Item item) throws IOException {
            if (item == null || item.file == null || find(item.name) == null) return null;
            return new FileInputStream(item.file);
        }

        @Override
        public boolean delete(String safeName) {
            Item item = find(safeName);
            return item != null && item.file != null && item.file.delete();
        }

        @Override
        public void retitle(String from, String to, String mime) {
            Item item = find(from);
            if (item == null || item.file == null || to == null || to.equals(from)) return;
            item.file.renameTo(new File(root, to));
        }

        @Override
        public void touch(Out out, long modified) {
            if (out != null && out.file != null) out.file.setLastModified(modified);
        }
    }

    @RequiresApi(29)
    static final class MediaDir implements Dir {
        private final Context ctx;

        MediaDir(Context ctx) {
            this.ctx = ctx;
        }

        private ContentResolver resolver() {
            return ctx.getContentResolver();
        }

        /** Names this install created. Used when the Downloads query returns nothing, which is how
         *  Robolectric behaves and how Android 11 hides rows from a previous install. */
        private File ledgerFile() {
            File dir = ctx.getExternalFilesDir("transfer");
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            return dir == null ? null : new File(dir, "downloads.index");
        }

        private static final class Mark {
            final String name;
            final Uri uri;
            final long size;
            final long modified;

            Mark(String name, Uri uri, long size, long modified) {
                this.name = name;
                this.uri = uri;
                this.size = size;
                this.modified = modified;
            }
        }

        private List<Mark> readLedger() {
            List<Mark> rows = new ArrayList<>();
            File file = ledgerFile();
            if (file == null || !file.isFile()) return rows;
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                for (String line : text.split("\n")) {
                    String[] part = line.split("\t");
                    if (part.length < 4 || part[0].isEmpty()) continue;
                    rows.add(new Mark(part[0], Uri.parse(part[1]), Long.parseLong(part[2]), Long.parseLong(part[3])));
                }
            } catch (Exception ignored) { }
            return rows;
        }

        private void writeLedger(List<Mark> rows) {
            File file = ledgerFile();
            if (file == null) return;
            StringBuilder text = new StringBuilder();
            for (Mark row : rows) {
                text.append(row.name).append('\t').append(row.uri).append('\t')
                        .append(row.size).append('\t').append(row.modified).append('\n');
            }
            try {
                java.nio.file.Files.write(file.toPath(), text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException ignored) { }
        }

        private void remember(String name, Uri uri, long size, long modified) {
            List<Mark> rows = readLedger();
            List<Mark> next = new ArrayList<>();
            for (Mark row : rows) if (!name.equals(row.name)) next.add(row);
            next.add(new Mark(name, uri, size, modified));
            writeLedger(next);
        }

        private void forget(String name) {
            List<Mark> next = new ArrayList<>();
            for (Mark row : readLedger()) if (!name.equals(row.name)) next.add(row);
            writeLedger(next);
        }

        private static final class Counted extends OutputStream {
            private final OutputStream out;
            long n;

            Counted(OutputStream out) {
                this.out = out;
            }

            @Override
            public void write(int b) throws IOException {
                out.write(b);
                n++;
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
                n += len;
            }

            @Override
            public void flush() throws IOException {
                out.flush();
            }

            @Override
            public void close() throws IOException {
                out.close();
            }
        }

        @Override
        public List<Item> list() {
            List<Item> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] cols = {
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.RELATIVE_PATH
            };
            String sel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND ("
                    + MediaStore.MediaColumns.IS_PENDING + " IS NULL OR "
                    + MediaStore.MediaColumns.IS_PENDING + "=0)";
            boolean queried = false;
            try (Cursor cursor = resolver().query(collection, cols, sel,
                    new String[]{Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "%"}, null)) {
                if (cursor != null) {
                    queried = true;
                    int id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                    int name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                    int size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
                    int when = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
                    int path = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                    while (cursor.moveToNext()) {
                        String relative = path >= 0 ? cursor.getString(path) : "";
                        if (relative != null && !relative.contains(FOLDER)) continue;
                        String display = cursor.getString(name);
                        if (display == null || display.startsWith(".")) continue;
                        long bytes = cursor.getLong(size);
                        long modified = cursor.getLong(when);
                        if (modified > 0 && modified < 10_000_000_000L) modified *= 1000L;
                        Uri uri = Uri.withAppendedPath(collection, String.valueOf(cursor.getLong(id)));
                        out.add(new Item(display, bytes, modified, uri, null));
                        seen.add(display);
                    }
                }
            } catch (Exception ignored) { }
            if (!queried) {
                for (Mark row : readLedger()) {
                    if (row.name == null || row.name.startsWith(".") || seen.contains(row.name)) continue;
                    out.add(new Item(row.name, row.size, row.modified, row.uri, null));
                    seen.add(row.name);
                }
            }
            File dir = legacyFolder();
            File[] extra = dir.listFiles();
            if (extra != null) {
                for (File file : extra) {
                    if (!file.isFile() || file.getName().startsWith(".") || seen.contains(file.getName())) continue;
                    out.add(new Item(file.getName(), file.length(), file.lastModified(), null, file));
                }
            }
            return out;
        }

        @Override
        public Item find(String safeName) {
            if (safeName == null || safeName.indexOf('/') >= 0 || safeName.indexOf('\\') >= 0
                    || safeName.contains("..") || safeName.startsWith(".")) return null;
            for (Item item : list()) {
                if (safeName.equals(item.name)) return item;
            }
            return null;
        }

        @Override
        public Out begin(String safeName) throws IOException {
            return begin(safeName, safeName == null ? null : mime(safeName));
        }

        private String readName(Uri uri) {
            try (Cursor cursor = resolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
            } catch (Exception ignored) { }
            return null;
        }

        @Override
        public InputStream open(Item item) throws IOException {
            if (item == null) return null;
            if (item.uri != null) return resolver().openInputStream(item.uri);
            if (item.file != null && item.file.isFile()) return new FileInputStream(item.file);
            return null;
        }

        @Override
        public Out begin(String safeName, String mime) throws IOException {
            String named = TransferKind.ensureExtension(safeName, mime);
            String type = mime == null || mime.isEmpty() ? mime(named) : mime;
            return beginTyped(named, type);
        }

        private Out beginTyped(String name, String type) throws IOException {
            if (name == null) return null;
            Set<String> taken = new HashSet<>();
            for (Item item : list()) taken.add(item.name);
            String unique = uniqueName(name, taken);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, unique);
            values.put(MediaStore.MediaColumns.MIME_TYPE, type);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("store");
            OutputStream stream = resolver().openOutputStream(uri);
            if (stream == null) {
                resolver().delete(uri, null, null);
                throw new IOException("store");
            }
            String read = readName(uri);
            final String actual = (read == null || read.isEmpty()) ? unique : read;
            Counted counted = new Counted(stream);
            ContentResolver resolver = resolver();
            return new Out(actual, counted, uri, null, () -> {
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                done.put(MediaStore.MediaColumns.MIME_TYPE, type);
                resolver.update(uri, done, null, null);
                remember(actual, uri, counted.n, System.currentTimeMillis());
            }, () -> {
                resolver.delete(uri, null, null);
                forget(actual);
            });
        }

        public boolean delete(String safeName) {
            if (safeName == null) return false;
            boolean removed = false;
            try {
                int n = resolver().delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                        new String[]{safeName, RELATIVE_PATH + "%"});
                removed = n > 0;
            } catch (Exception ignored) { }
            Item item = find(safeName);
            if (item != null && item.uri != null) {
                try {
                    removed = resolver().delete(item.uri, null, null) > 0 || removed;
                } catch (Exception ignored) { }
            }
            if (item != null && item.file != null) removed = item.file.delete() || removed;
            File legacy = new File(legacyFolder(), safeName);
            if (legacy.isFile()) removed = legacy.delete() || removed;
            forget(safeName);
            return removed;
        }

        @Override
        public void retitle(String from, String to, String mime) {
            if (from == null || to == null || to.equals(from)) return;
            Item item = find(from);
            if (item == null) return;
            if (item.uri != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, to);
                if (mime != null) values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver().update(item.uri, values, null, null);
                forget(from);
                remember(to, item.uri, item.size, item.modified);
            }
            if (item.file != null && item.file.getParentFile() != null) {
                item.file.renameTo(new File(item.file.getParentFile(), to));
            }
        }

        @Override
        public void touch(Out out, long modified) {
            if (out == null || modified <= 0) return;
            if (out.uri != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATE_MODIFIED, modified / 1000L);
                resolver().update(out.uri, values, null, null);
            }
            List<Mark> next = new ArrayList<>();
            for (Mark row : readLedger()) {
                if (out.name.equals(row.name)) next.add(new Mark(row.name, row.uri, row.size, modified));
                else next.add(row);
            }
            writeLedger(next);
        }
    }
}
