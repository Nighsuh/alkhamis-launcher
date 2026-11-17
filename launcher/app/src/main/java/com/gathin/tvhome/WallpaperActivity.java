package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Pick the shrine picture, a file on the TV or a USB stick, or an https image. Dim is 0–90. */
public final class WallpaperActivity extends Activity {
    private static final int PICK = 41;
    private static final int BROWSE = 42;
    private TextView status;
    private boolean busy;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_wallpaper);
        status = findViewById(R.id.status);
        SeekBar dim = findViewById(R.id.dim);
        dim.setProgress(WallpaperFiles.dim(this));
        showDim(dim.getProgress());
        TvFocus.vertical(dim);
        dim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { showDim(value); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { WallpaperFiles.setDim(WallpaperActivity.this, bar.getProgress()); }
        });
        findViewById(R.id.useShrine).setOnClickListener(v -> {
            WallpaperFiles.useShrine(this);
            status.setText(R.string.wallpaper_using_shrine);
        });
        findViewById(R.id.pickDevice).setOnClickListener(v -> pick());
        findViewById(R.id.browse).setOnClickListener(v ->
                startActivityForResult(new Intent(this, WallpaperBrowseActivity.class), BROWSE));
        findViewById(R.id.download).setOnClickListener(v -> download());
        findViewById(R.id.reset).setOnClickListener(v -> {
            WallpaperFiles.reset(this);
            dim.setProgress(45);
            showDim(45);
            status.setText(R.string.wallpaper_reset_done);
        });
        TvFocus.vertical(findViewById(R.id.url));
        findViewById(R.id.useShrine).requestFocus();
    }

    private void showDim(int value) {
        ((TextView) findViewById(R.id.dimLabel)).setText(getString(R.string.wallpaper_dim_value, value));
    }

    private void pick() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.setType("image/*");
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (open.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(open, PICK);
            return;
        }
        Intent get = new Intent(Intent.ACTION_GET_CONTENT);
        get.setType("image/*");
        get.addCategory(Intent.CATEGORY_OPENABLE);
        if (get.resolveActivity(getPackageManager()) != null) startActivityForResult(get, PICK);
        else startActivityForResult(new Intent(this, WallpaperBrowseActivity.class), BROWSE);
    }

    private void download() {
        if (busy) return;
        String text = ((EditText) findViewById(R.id.url)).getText().toString().trim();
        if (!text.startsWith("https://") || text.length() > 500) {
            status.setText(R.string.wallpaper_url_bad);
            return;
        }
        busy = true;
        status.setText(R.string.wallpaper_downloading);
        new Thread(() -> {
            boolean ok = false;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(text).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = conn.getInputStream()) {
                    ok = WallpaperFiles.copyToUser(this, in);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) { }
            boolean saved = ok;
            runOnUiThread(() -> {
                busy = false;
                status.setText(saved ? R.string.wallpaper_saved : R.string.wallpaper_failed);
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == PICK && data.getData() != null) {
            boolean ok = WallpaperFiles.copyUri(this, data.getData());
            status.setText(ok ? R.string.wallpaper_saved : R.string.wallpaper_failed);
        } else if (request == BROWSE) {
            String path = data.getStringExtra(WallpaperBrowseActivity.EXTRA_PATH);
            if (path == null) return;
            try (InputStream in = new FileInputStream(path)) {
                status.setText(WallpaperFiles.copyToUser(this, in) ? R.string.wallpaper_saved : R.string.wallpaper_failed);
            } catch (Exception e) {
                status.setText(R.string.wallpaper_failed);
            }
        }
    }

    /** Pictures, Download, and mounted volumes. Used when the system picker is missing. */
    public static List<File> folders(Activity activity) {
        List<File> out = new ArrayList<>();
        add(out, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));
        add(out, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        StorageManager manager = activity.getSystemService(StorageManager.class);
        if (manager != null) {
            for (StorageVolume volume : manager.getStorageVolumes()) {
                File dir = volume.getDirectory();
                if (dir != null) add(out, dir);
            }
        }
        File storage = new File("/storage");
        File[] roots = storage.listFiles();
        if (roots != null) for (File root : roots) if (root.isDirectory() && !root.getName().equals("emulated") && !root.getName().equals("self")) add(out, root);
        return out;
    }

    private static void add(List<File> out, File dir) {
        if (dir != null && dir.isDirectory()) out.add(dir);
    }
}
