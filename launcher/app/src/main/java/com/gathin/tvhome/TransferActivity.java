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
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The transfer screen. The server runs while this screen is open, unless background is on. */
public final class TransferActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView urlView;
    private TextView pinView;
    private ImageView qrView;
    private View backgroundView;
    private TextView switchView;
    private TextView statusView;
    private TextView emptyView;
    private LinearLayout notesView;
    private LinearLayout filesView;
    private int shownNotes;
    private String filesSig = "";

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
        switchView = findViewById(R.id.transferSwitch);
        statusView = findViewById(R.id.transferStatus);
        emptyView = findViewById(R.id.transferEmpty);
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
        switchView.setText("");
        switchView.setContentDescription(getString(on ? R.string.file_transfer_on : R.string.file_transfer_off));
        switchView.setBackgroundResource(on ? R.drawable.transfer_switch_on : R.drawable.transfer_switch_off);
    }

    private void paintFiles() {
        TransferServer server = null;
        try {
            server = TransferHub.server(this);
        } catch (Exception e) {
            statusView.setText(R.string.file_transfer_off);
            return;
        }
        if (server.busyPercent >= 0) {
            statusView.setText(getString(R.string.file_transfer_uploading, server.busyName, server.busyPercent));
        } else if (server.clientCount() == 0) {
            statusView.setText(R.string.file_transfer_on);
        } else {
            statusView.setText(getString(R.string.file_transfer_status,
                    getString(R.string.file_transfer_on), server.clientCount()));
        }
        List<String> notes = server.notes();
        if (notes.size() != shownNotes) {
            notesView.removeAllViews();
            for (String note : notes) addNote(note);
            shownNotes = notes.size();
        }
        File dir = TransferHub.folder(this);
        File[] listed = dir == null ? null : dir.listFiles();
        List<File> files = new ArrayList<>();
        if (listed != null) {
            for (File file : listed) {
                if (file.isFile() && !file.getName().startsWith(".")) files.add(file);
            }
        }
        files.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        StringBuilder sig = new StringBuilder();
        for (File file : files) sig.append(file.getName()).append(':').append(file.length())
                .append(':').append(file.lastModified()).append(';');
        String next = sig.toString();
        emptyView.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        if (next.equals(filesSig)) {
            wireUp();
            return;
        }
        String keep = focusedTag();
        filesView.removeAllViews();
        List<List<TextView>> rows = new ArrayList<>();
        for (File file : files) rows.add(addFile(file));
        filesSig = next;
        chain(rows);
        wireUp();
        if (keep != null) {
            View again = filesView.findViewWithTag(keep);
            if (again != null) again.requestFocus();
        }
    }

    private String focusedTag() {
        View focused = getCurrentFocus();
        if (focused == null || focused.getTag() == null) return null;
        return String.valueOf(focused.getTag());
    }

    private List<TextView> addFile(File file) {
        int pad = dp(12);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, dp(8), pad, dp(8));
        row.setMinimumHeight(dp(68));

        TextView kind = new TextView(this);
        kind.setText(kindOf(file.getName()));
        kind.setTextColor(0xFFE6C57A);
        kind.setTextSize(12);
        kind.setGravity(Gravity.CENTER);
        kind.setBackgroundResource(R.drawable.transfer_kind);
        int box = dp(44);
        LinearLayout.LayoutParams kindLp = new LinearLayout.LayoutParams(box, box);
        kindLp.setMarginEnd(dp(12));
        kind.setLayoutParams(kindLp);
        row.addView(kind);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(textLp);
        TextView name = new TextView(this);
        name.setText(file.getName());
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(16);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(name);
        TextView meta = new TextView(this);
        meta.setText(size(file.length()) + "    " + stamp(file.lastModified()));
        meta.setTextColor(0xFFACB8C1);
        meta.setTextSize(13);
        meta.setTextDirection(View.TEXT_DIRECTION_LTR);
        text.addView(meta);
        row.addView(text);

        List<TextView> chips = new ArrayList<>();
        boolean apk = file.getName().toLowerCase(Locale.US).endsWith(".apk");
        TextView open = chip(getString(R.string.file_transfer_open), "open:" + file.getName());
        open.setOnClickListener(v -> open(file));
        chips.add(open);
        if (apk) {
            TextView install = chip(getString(R.string.file_transfer_install), "install:" + file.getName());
            install.setOnClickListener(v -> install(file));
            chips.add(install);
        }
        TextView delete = chip(getString(R.string.file_transfer_delete), "delete:" + file.getName());
        delete.setOnClickListener(v -> {
            file.delete();
            filesSig = "";
            paintFiles();
        });
        chips.add(delete);
        for (TextView action : chips) row.addView(action);
        filesView.addView(row);
        return chips;
    }

    private TextView chip(String label, String tag) {
        TextView view = new TextView(this);
        view.setId(View.generateViewId());
        view.setTag(tag);
        view.setText(label);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(15);
        view.setMinWidth(dp(76));
        view.setMinHeight(dp(40));
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setBackgroundResource(R.drawable.settings_choice_bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(8));
        view.setLayoutParams(lp);
        view.setOnFocusChangeListener((v, has) -> {
            if (has) reveal(v);
        });
        return view;
    }

    private void reveal(View view) {
        ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
        if (!(parent instanceof ScrollView)) return;
        ScrollView scroll = (ScrollView) parent;
        if (scroll.getChildCount() == 0) return;
        int top = 0;
        View current = view;
        while (current != null && current != scroll) {
            top += current.getTop();
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        int target = Math.max(0, top - dp(24));
        int max = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
        scroll.scrollTo(0, Math.min(target, max));
    }

    private void wireUp() {
        View last = null;
        for (int i = 0; i < notesView.getChildCount(); i++) {
            View line = notesView.getChildAt(i);
            if (!(line instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) line;
            for (int c = 0; c < row.getChildCount(); c++) {
                View child = row.getChildAt(c);
                if (!child.isFocusable()) continue;
                if (last != null) last.setNextFocusDownId(child.getId());
                child.setNextFocusUpId(last == null ? child.getId() : last.getId());
                last = child;
            }
        }
        if (last != null) {
            last.setNextFocusDownId(backgroundView.getId());
            backgroundView.setNextFocusUpId(last.getId());
        }
    }

    private void chain(List<List<TextView>> rows) {
        boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        backgroundView.setNextFocusUpId(backgroundView.getId());
        if (rows.isEmpty()) {
            backgroundView.setNextFocusDownId(backgroundView.getId());
            return;
        }
        backgroundView.setNextFocusDownId(rows.get(0).get(0).getId());
        for (int r = 0; r < rows.size(); r++) {
            List<TextView> chips = rows.get(r);
            int up = r == 0 ? backgroundView.getId() : rows.get(r - 1).get(0).getId();
            int down = r + 1 < rows.size() ? rows.get(r + 1).get(0).getId() : View.NO_ID;
            for (int i = 0; i < chips.size(); i++) {
                TextView chip = chips.get(i);
                int prev = Math.max(0, i - 1);
                int next = Math.min(chips.size() - 1, i + 1);
                if (rtl) {
                    chip.setNextFocusLeftId(chips.get(next).getId());
                    chip.setNextFocusRightId(chips.get(prev).getId());
                } else {
                    chip.setNextFocusRightId(chips.get(next).getId());
                    chip.setNextFocusLeftId(chips.get(prev).getId());
                }
                chip.setNextFocusUpId(up);
                chip.setNextFocusDownId(down == View.NO_ID ? chip.getId() : down);
            }
        }
    }

    static String stamp(long ms) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(ms);
        String text = String.format(Locale.US, "%04d/%02d/%02d %02d:%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
        return "\u2066" + text + "\u2069";
    }

    private static String kindOf(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".apk")) return "APK";
        if (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm")) return "▶";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) return "IMG";
        if (n.endsWith(".mp3") || n.endsWith(".m4a")) return "♪";
        if (n.endsWith(".txt") || n.endsWith(".pdf")) return "TXT";
        return "FILE";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void addNote(String note) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView text = noteRow(note);
        text.setOnClickListener(v -> copy(note));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(lp);
        line.addView(text);
        if (note.startsWith("http://") || note.startsWith("https://")) {
            TextView open = noteRow(getString(R.string.file_transfer_open));
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

    private TextView noteRow(String label) {
        TextView view = new TextView(this);
        view.setId(View.generateViewId());
        view.setText(label);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(14);
        view.setMinHeight(dp(40));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setBackgroundResource(R.drawable.settings_choice_bg);
        view.setNextFocusDownId(backgroundView.getId());
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
