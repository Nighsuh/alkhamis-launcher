package com.gathin.tvhome;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** The transfer screen. The server runs while this screen is open, unless background is on. */
public final class TransferActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView urlView;
    private TextView pinView;
    private ImageView qrView;
    private TextView backgroundView;
    private TextView statusView;
    private LinearLayout notesView;
    private LinearLayout filesView;
    private int shownNotes;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            paintFiles();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_transfer);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        urlView = findViewById(R.id.transferUrl);
        pinView = findViewById(R.id.transferPin);
        qrView = findViewById(R.id.transferQr);
        backgroundView = findViewById(R.id.transferBackground);
        statusView = findViewById(R.id.transferStatus);
        notesView = findViewById(R.id.transferNotes);
        filesView = findViewById(R.id.transferFiles);
        backgroundView.setOnClickListener(v -> {
            TransferHub.setBackground(this, !TransferHub.background());
            paintBackground();
        });
        backgroundView.requestFocus();
        new Thread(() -> {
            try {
                TransferServer server = TransferHub.server(this);
                String url = "http://" + TransferHub.address() + ":" + server.port();
                runOnUiThread(() -> showAddress(url));
            } catch (Exception e) {
                runOnUiThread(() -> statusView.setText(R.string.file_transfer_failed));
            }
        }, "transfer-start").start();
    }

    private void showAddress(String url) {
        urlView.setText(url);
        pinView.setText(getString(R.string.file_transfer_pin, TransferHub.pin()));
        String link = url + "/?p=" + TransferHub.pin();
        qrView.setImageBitmap(drawQr(link));
        paintBackground();
        paintFiles();
    }

    private void paintBackground() {
        boolean on = TransferHub.background();
        backgroundView.setText(getString(R.string.file_transfer_background)
                + (on ? "  ·  " + getString(R.string.file_transfer_on) : ""));
    }

    private void paintFiles() {
        TransferServer server = null;
        try {
            server = TransferHub.server(this);
        } catch (Exception e) {
            return;
        }
        List<String> notes = server.notes();
        if (notes.size() != shownNotes) {
            notesView.removeAllViews();
            for (String note : notes) addNote(note);
            shownNotes = notes.size();
        }
        File dir = TransferHub.folder(this);
        File[] files = dir == null ? null : dir.listFiles();
        filesView.removeAllViews();
        if (files == null || files.length == 0) {
            TextView empty = row(getString(R.string.file_transfer_empty), false);
            empty.setFocusable(false);
            filesView.addView(empty);
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (File file : files) {
            if (!file.isFile() || file.getName().startsWith(".")) continue;
            String label = file.getName() + "   " + size(file.length()) + "   " + format.format(new Date(file.lastModified()));
            TextView open = row(label, true);
            open.setOnClickListener(v -> open(file));
            filesView.addView(open);
            if (file.getName().toLowerCase(java.util.Locale.US).endsWith(".apk")) {
                TextView install = row(getString(R.string.file_transfer_install) + "  " + file.getName(), true);
                install.setOnClickListener(v -> install(file));
                filesView.addView(install);
            }
            TextView delete = row(getString(R.string.file_transfer_delete) + "  " + file.getName(), true);
            delete.setOnClickListener(v -> {
                file.delete();
                paintFiles();
            });
            filesView.addView(delete);
        }
    }

    private void addNote(String note) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView text = row(note, true);
        text.setOnClickListener(v -> copy(note));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(lp);
        line.addView(text);
        if (note.startsWith("http://") || note.startsWith("https://")) {
            TextView open = row(getString(R.string.file_transfer_open), true);
            open.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(note)));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.file_transfer_failed, Toast.LENGTH_SHORT).show();
                }
            });
            line.addView(open);
        }
        notesView.addView(line);
    }

    private void copy(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("transfer", text));
        Toast.makeText(this, R.string.file_transfer_copied, Toast.LENGTH_SHORT).show();
    }

    private TextView row(String label, boolean focusable) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(16);
        view.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.setPadding(24, 8, 24, 8);
        if (focusable) {
            view.setFocusable(true);
            view.setBackgroundResource(R.drawable.settings_choice_bg);
        }
        return view;
    }

    private void open(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.US);
        String mime = "application/octet-stream";
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm")) mime = "video/*";
        else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) mime = "image/*";
        else if (name.endsWith(".mp3") || name.endsWith(".m4a")) mime = "audio/*";
        else if (name.endsWith(".apk")) { install(file); return; }
        else if (name.endsWith(".txt")) mime = "text/plain";
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".transfer", file);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.file_transfer_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void install(File file) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                Toast.makeText(this, R.string.file_transfer_sources, Toast.LENGTH_LONG).show();
            }
            Toast.makeText(this, R.string.file_transfer_sources, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".transfer", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.file_transfer_sources, Toast.LENGTH_LONG).show();
        }
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static Bitmap drawQr(String text) {
        QrCode code = QrCode.encode(text);
        int quiet = 4;
        int n = code.size + quiet * 2;
        int scale = Math.max(2, 440 / n);
        Bitmap bitmap = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        for (int y = 0; y < code.size; y++) {
            for (int x = 0; x < code.size; x++) {
                if (!code.dark[y][x]) continue;
                canvas.drawRect((x + quiet) * scale, (y + quiet) * scale,
                        (x + quiet + 1) * scale, (y + quiet + 1) * scale, paint);
            }
        }
        return bitmap;
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) TransferHub.onScreenClosed();
        super.onDestroy();
    }
}
