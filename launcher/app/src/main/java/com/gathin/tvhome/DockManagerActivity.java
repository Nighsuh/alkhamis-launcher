package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Featured dock, the list of every launchable app, hidden apps, and apps removed for this user. */
public final class DockManagerActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String MODE_DOCK = "dock";
    static final String MODE_ADD = "add";
    static final String MODE_HIDDEN = "hidden";
    static final String MODE_REMOVED = "removed";
    private static final int CONNECT_REQUEST = 7;
    private static final int MAX_DOCK = 12;

    private String mode = MODE_DOCK;
    private String pendingRestore;
    private LinearLayout rows;
    private int focusIndex;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        String extra = getIntent().getStringExtra(EXTRA_MODE);
        if (extra != null) mode = extra;
        rows = findViewById(R.id.rows);
        TextView title = findViewById(R.id.title);
        TextView hint = findViewById(R.id.hint);
        if (MODE_ADD.equals(mode)) {
            title.setText(R.string.dock_add_title);
            hint.setText(R.string.dock_add_hint);
        } else if (MODE_HIDDEN.equals(mode)) {
            title.setText(R.string.hidden_apps);
            hint.setText(R.string.hidden_apps_hint);
        } else if (MODE_REMOVED.equals(mode)) {
            title.setText(R.string.restore_deleted);
            hint.setText(R.string.restore_deleted_hint);
        } else {
            title.setText(R.string.featured_dock);
            hint.setText(R.string.featured_dock_hint);
        }
        if (state != null) focusIndex = state.getInt("focus");
    }

    @Override
    protected void onResume() {
        super.onResume();
        DebugGate.onResume(this);
        show();
    }

    @Override
    protected void onPause() {
        DebugGate.onPause(this);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("focus", focusIndex);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == CONNECT_REQUEST && pendingRestore != null && LocalAdb.get(this).isConnected()) {
            String pkg = pendingRestore;
            pendingRestore = null;
            restore(pkg);
        }
    }

    private void show() {
        rows.removeAllViews();
        if (MODE_ADD.equals(mode)) showAdd();
        else if (MODE_HIDDEN.equals(mode)) showHidden();
        else if (MODE_REMOVED.equals(mode)) showRemoved();
        else showDock();
        if (rows.getChildCount() > 0) {
            int at = Math.max(0, Math.min(focusIndex, rows.getChildCount() - 1));
            rows.getChildAt(at).requestFocus();
        }
    }

    private void showDock() {
        List<String> dock = currentDock();
        boolean custom = UserDock.read(this) != null;
        addLink(getString(R.string.dock_add), () -> open(MODE_ADD));
        if (dock.isEmpty()) addNote(getString(R.string.dock_empty));
        for (int i = 0; i < dock.size(); i++) {
            String pkg = dock.get(i);
            String label = label(pkg);
            if (!installed(pkg)) label = getString(R.string.dock_missing, label);
            final int index = i;
            TextView row = addRow(label, () -> appMenu(index));
            row.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) { move(index, -1); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT) { move(index, 1); return true; }
                return false;
            });
        }
        addLink(getString(R.string.hidden_apps), () -> open(MODE_HIDDEN));
        addLink(getString(R.string.restore_deleted), () -> open(MODE_REMOVED));
        if (custom) addLink(getString(R.string.dock_use_theme), this::resetThemeDock);
    }

    private void showAdd() {
        List<String> dock = currentDock();
        List<String[]> apps = launchable();
        int shown = 0;
        for (String[] app : apps) {
            if (dock.contains(app[1]) || UserHidden.read(this).contains(app[1])) continue;
            shown++;
            addRow(app[0], () -> {
                addPackage(app[1]);
                finish();
            });
        }
        if (shown == 0) addNote(getString(R.string.dock_nothing_to_add));
    }

    private void showHidden() {
        List<String> hidden = UserHidden.read(this);
        if (hidden.isEmpty()) addNote(getString(R.string.hidden_empty));
        for (String pkg : hidden) {
            addRow(label(pkg), () -> {
                UserHidden.remove(this, pkg);
                HomeEdits.notifyChanged(this);
                focusIndex = 0;
                show();
            });
        }
    }

    private void showRemoved() {
        List<RemovedApps.Item> items = RemovedApps.read(this);
        if (items.isEmpty()) addNote(getString(R.string.restore_empty));
        for (RemovedApps.Item item : items) {
            addRow(item.label, () -> {
                if (!LocalAdb.get(this).isConnected()) {
                    pendingRestore = item.pkg;
                    startActivityForResult(new Intent(this, DeleteAppActivity.class)
                            .putExtra(DeleteAppActivity.EXTRA_MODE, DeleteAppActivity.MODE_CONNECT), CONNECT_REQUEST);
                    return;
                }
                restore(item.pkg);
            });
        }
    }

    private void appMenu(int index) {
        List<String> dock = currentDock();
        if (index < 0 || index >= dock.size()) return;
        String pkg = dock.get(index);
        String name = label(pkg);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (index > 0) { labels.add(getString(R.string.dock_move_earlier)); actions.add(() -> move(index, -1)); }
        if (index + 1 < dock.size()) { labels.add(getString(R.string.dock_move_later)); actions.add(() -> move(index, 1)); }
        labels.add(getString(R.string.dock_remove));
        actions.add(() -> removeAt(index));
        labels.add(getString(R.string.hide_app));
        actions.add(() -> hide(pkg));
        ApplicationInfo info = info(pkg);
        if (info != null && !PackageGuard.blocked(getPackageName(), pkg)) {
            int kind = PackageGuard.kind(info.flags);
            if (kind == PackageGuard.USER) {
                labels.add(getString(R.string.uninstall_app));
                actions.add(() -> uninstallDialog(pkg));
            } else if (kind == PackageGuard.UPDATED_SYSTEM) {
                labels.add(getString(R.string.uninstall_updates));
                actions.add(() -> uninstallDialog(pkg));
            }
            if (kind != PackageGuard.USER) {
                labels.add(getString(R.string.delete_completely));
                actions.add(() -> startActivity(DeleteAppActivity.delete(this, pkg, name)));
            }
        }
        if (SystemSettings.appInfo(this, pkg) != null) {
            labels.add(getString(R.string.library_app_info));
            actions.add(() -> startActivity(SystemSettings.appInfo(this, pkg)));
        }
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void move(int index, int delta) {
        List<String> dock = new ArrayList<>(currentDock());
        int to = index + delta;
        if (to < 0 || to >= dock.size()) return;
        Collections.swap(dock, index, to);
        save(dock);
        focusIndex = to + 1;
        show();
    }

    private void removeAt(int index) {
        List<String> dock = new ArrayList<>(currentDock());
        if (index < 0 || index >= dock.size()) return;
        dock.remove(index);
        save(dock);
        focusIndex = Math.min(index + 1, rows.getChildCount() - 1);
        show();
    }

    private void addPackage(String pkg) {
        List<String> dock = new ArrayList<>(currentDock());
        if (dock.size() >= MAX_DOCK) {
            Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!dock.contains(pkg)) dock.add(pkg);
        save(dock);
    }

    private void hide(String pkg) {
        UserHidden.add(this, pkg);
        List<String> dock = new ArrayList<>(currentDock());
        dock.remove(pkg);
        save(dock);
        Toast.makeText(this, R.string.hidden_done, Toast.LENGTH_SHORT).show();
        show();
    }

    private void resetThemeDock() {
        UserDock.clear(this);
        HomeEdits.notifyChanged(this);
        Toast.makeText(this, R.string.dock_theme_restored, Toast.LENGTH_SHORT).show();
        focusIndex = 0;
        show();
    }

    private void restore(String pkg) {
        if (!LocalAdb.get(this).isConnected()) {
            DebugGate.requestRestore(this, pkg, pkg);
            return;
        }
        LocalAdb.get(this).restore(pkg, (ok, message) -> {
            if (ok) {
                RemovedApps.remove(this, pkg);
                HomeEdits.notifyChanged(this);
                Toast.makeText(this, R.string.restore_done, Toast.LENGTH_SHORT).show();
                show();
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void uninstallDialog(String pkg) {
        startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
    }

    /** The owner's list, or the theme's list the first time this screen is opened. */
    private List<String> currentDock() {
        List<String> saved = UserDock.read(this);
        if (saved != null) return saved;
        return new ArrayList<>(Config.load(this).dockApps);
    }

    private void save(List<String> dock) {
        if (!UserDock.write(this, dock)) {
            Toast.makeText(this, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show();
            return;
        }
        HomeEdits.notifyChanged(this);
    }

    private void open(String next) {
        startActivity(new Intent(this, DockManagerActivity.class).putExtra(EXTRA_MODE, next));
    }

    private TextView addLink(String text, Runnable action) {
        return addRow(text, action);
    }

    private TextView addRow(String text, Runnable action) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextAppearance(android.R.style.TextAppearance_Material_Body1);
        // SettingsRow supplies height, padding, focus background and text colour.
        row.setId(View.generateViewId());
        applyRowStyle(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        rows.addView(row, lp);
        row.setOnClickListener(v -> {
            focusIndex = rows.indexOfChild(v);
            action.run();
        });
        return row;
    }

    private void applyRowStyle(TextView row) {
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(18);
        row.setFocusable(true);
        row.setClickable(true);
        int px = (int) (18 * getResources().getDisplayMetrics().density);
        row.setPadding(px, px / 2, px, px / 2);
        row.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    }

    private void addNote(String text) {
        TextView note = new TextView(this);
        note.setText(text);
        note.setTextColor(0xFFACB8C1);
        note.setTextSize(16);
        note.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        rows.addView(note);
    }

    private List<String[]> launchable() {
        PackageManager pm = getPackageManager();
        Map<String, String> labels = new LinkedHashMap<>();
        Intent[] queries = {
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        };
        for (Intent query : queries) {
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || labels.containsKey(pkg)) continue;
                CharSequence label = info.loadLabel(pm);
                labels.put(pkg, label == null ? pkg : label.toString());
            }
        }
        List<String[]> apps = new ArrayList<>();
        for (Map.Entry<String, String> e : labels.entrySet()) apps.add(new String[]{e.getValue(), e.getKey()});
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        return apps;
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return label == null ? pkg : label.toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private boolean installed(String pkg) {
        return info(pkg) != null;
    }

    private ApplicationInfo info(String pkg) {
        try {
            return getPackageManager().getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
