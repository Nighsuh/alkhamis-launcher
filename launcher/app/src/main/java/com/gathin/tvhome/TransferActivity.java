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
        if (!TransferFiles.storageReady(this)) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 40);
            statusView.setText(R.string.file_transfer_storage);
            return;
        }
        startTransfer();
    }

    private void startTransfer() {
        new Thread(() -> {
            try {
                TransferServer server = TransferHub.server(this);
                String url = "http://" + TransferHub.address(TransferActivity.this) + ":" + server.port();
                runOnUiThread(() -> showAddress(url));
            } catch (Exception e) {
                runOnUiThread(() -> statusView.setText(R.string.file_transfer_failed));
            }
        }, "transfer-start").start();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 40 && TransferFiles.storageReady(this)) startTransfer();
    }

    private void showAddress(String url) {
        urlView.setText(url);
        TextView nameView = findViewById(R.id.transferName);
        nameView.setText(TransferHub.displayName());
        findViewById(R.id.transferRename).setOnClickListener(v -> rename());
        String link = url.endsWith("/") ? url : url + "/";
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
        List<TransferFiles.Item> files = new ArrayList<>(TransferFiles.open(this).list());
        files.sort((a, b) -> Long.compare(b.modified, a.modified));
        StringBuilder sig = new StringBuilder();
        for (TransferFiles.Item file : files) sig.append(file.name).append(':').append(file.size)
                .append(':').append(file.modified).append(';');
        String next = sig.toString();
        emptyView.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        // An empty list used to match the signature cleared just before this call, so the last row stayed.
        if (next.equals(filesSig) && !(files.isEmpty() && filesView.getChildCount() > 0)) {
            wireUp();
            return;
        }
        String keep = focusedTag();
        filesView.removeAllViews();
        List<List<TextView>> rows = new ArrayList<>();
        for (TransferFiles.Item file : files) rows.add(addFile(file));
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

    private List<TextView> addFile(TransferFiles.Item file) {
        int pad = dp(12);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, dp(8), pad, dp(8));
        row.setMinimumHeight(dp(68));

        ImageView kind = new ImageView(this);
        kind.setImageResource(kindIcon(file.name));
        kind.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        kind.setBackgroundResource(R.drawable.transfer_kind);
        int box = dp(40);
        LinearLayout.LayoutParams kindLp = new LinearLayout.LayoutParams(box, box);
        kindLp.setMarginEnd(dp(10));
        kind.setLayoutParams(kindLp);
        kind.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.addView(kind);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        int cap = dp(280);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textLp.setMarginEnd(dp(8));
        text.setLayoutParams(textLp);
        TextView name = new TextView(this);
        name.setText(file.name);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(16);
        name.setSingleLine(true);
        name.setMaxWidth(cap);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(name);
        TextView meta = new TextView(this);
        meta.setText(size(file.size) + "    " + stamp(file.modified));
        meta.setTextColor(0xFFACB8C1);
        meta.setTextSize(13);
        meta.setSingleLine(true);
        meta.setMaxWidth(cap);
        meta.setTextDirection(View.TEXT_DIRECTION_LTR);
        text.addView(meta);
        row.addView(text);

        List<TextView> chips = new ArrayList<>();
        boolean apk = file.name.toLowerCase(Locale.US).endsWith(".apk");
        TextView open = chip(getString(R.string.file_transfer_open), "open:" + file.name);
        open.setOnClickListener(v -> open(file));
        chips.add(open);
        if (apk) {
            TextView install = chip(getString(R.string.file_transfer_install), "install:" + file.name);
            install.setOnClickListener(v -> install(file));
            chips.add(install);
        }
        TextView delete = chip(getString(R.string.file_transfer_delete), "delete:" + file.name);
        delete.setOnClickListener(v -> {
            TransferFiles.open(this).delete(file.name);
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

    private static int kindIcon(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".apk")) return R.drawable.ic_kind_apk;
        if (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm")) return R.drawable.ic_kind_video;
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) return R.drawable.ic_kind_image;
        if (n.endsWith(".mp3") || n.endsWith(".m4a")) return R.drawable.ic_kind_audio;
        if (n.endsWith(".txt") || n.endsWith(".pdf")) return R.drawable.ic_kind_text;
        if (n.endsWith(".gif")) return R.drawable.ic_kind_image;
        return R.drawable.ic_kind_file;
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

    private void open(TransferFiles.Item file) {
        if (file.name.toLowerCase(Locale.US).endsWith(".apk")) {
            install(file);
            return;
        }
        try {
            Intent intent = TransferFiles.viewIntent(this, file);
            if (intent.getData() == null || getPackageManager().resolveActivity(intent, 0) == null) {
                Toast.makeText(this, R.string.file_transfer_no_app, Toast.LENGTH_LONG).show();
                return;
            }
            Intent chooser = Intent.createChooser(intent, getString(R.string.file_transfer_open));
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(this, R.string.file_transfer_no_app, Toast.LENGTH_LONG).show();
        }
    }

    private void rename() {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_tv_rename, null);
        android.widget.EditText field = view.findViewById(R.id.renameField);
        field.setText(TransferHub.displayName());
        field.setSelection(field.getText().length());
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(view).create();
        view.findViewById(R.id.renameCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.renameSave).setOnClickListener(v -> {
            String next = field.getText().toString().trim();
            if (next.isEmpty() || next.length() > 30) {
                field.setError(getString(R.string.file_transfer_rename_invalid));
                return;
            }
            ((TextView) findViewById(R.id.transferName)).setText(TransferHub.rename(next));
            dialog.dismiss();
        });
        dialog.setOnShowListener(shown -> {
            field.requestFocus();
            android.view.inputmethod.InputMethodManager keyboard =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    private void install(TransferFiles.Item file) {
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
            Uri uri = TransferFiles.share(this, file);
            if (uri == null) throw new IllegalStateException("uri");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setClipData(ClipData.newRawUri(file.name, uri));
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
