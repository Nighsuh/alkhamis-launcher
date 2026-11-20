package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** A short list of pictures under Pictures, Download and USB volumes. */
public final class WallpaperBrowseActivity extends Activity {
    static final String EXTRA_PATH = "path";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        ((TextView) findViewById(R.id.title)).setText(R.string.wallpaper_browse);
        ((TextView) findViewById(R.id.hint)).setText(R.string.wallpaper_browse_hint);
        LinearLayout rows = findViewById(R.id.rows);
        List<File> images = new ArrayList<>();
        for (File folder : WallpaperActivity.folders(this)) images.addAll(imagesIn(folder));
        if (images.isEmpty()) {
            TextView note = new TextView(this);
            note.setText(R.string.wallpaper_browse_empty);
            note.setTextColor(0xFFACB8C1);
            note.setTextSize(16);
            rows.addView(note);
            return;
        }
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        for (File image : images) {
            TextView row = new TextView(this);
            row.setText(image.getName());
            row.setBackgroundResource(R.drawable.settings_choice_bg);
            row.setTextColor(0xFFFFFFFF);
            row.setTextSize(18);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(pad, pad / 2, pad, pad / 2);
            row.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = pad / 2;
            row.setOnClickListener(v -> {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_PATH, image.getAbsolutePath()));
                finish();
            });
            rows.addView(row, lp);
        }
        rows.getChildAt(0).requestFocus();
    }

    private static List<File> imagesIn(File folder) {
        List<File> out = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            if (!file.isFile() || file.length() <= 0 || file.length() > 15 * 1024 * 1024) continue;
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                out.add(file);
            }
            if (out.size() == 40) break;
        }
        return out;
    }
}
