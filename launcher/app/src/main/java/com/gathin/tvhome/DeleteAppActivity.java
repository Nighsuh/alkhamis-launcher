package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Warning, debugging setup, and the confirm button for removing a system app for this user. */
public final class DeleteAppActivity extends Activity {
    static final String EXTRA_PACKAGE = "pkg";
    static final String EXTRA_LABEL = "label";
    static final String EXTRA_MODE = "mode";
    static final String MODE_DELETE = "delete";
    static final String MODE_CONNECT = "connect";

    private String mode = MODE_DELETE;
    private String pkg = "";
    private String label = "";
    private TextView status;
    private TextView confirm;
    private boolean busy;

    static Intent delete(Context ctx, String pkg, String label) {
        return new Intent(ctx, DeleteAppActivity.class)
                .putExtra(EXTRA_MODE, MODE_DELETE)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_LABEL, label);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_delete_app);
        String extraMode = getIntent().getStringExtra(EXTRA_MODE);
        if (extraMode != null) mode = extraMode;
        pkg = getIntent().getStringExtra(EXTRA_PACKAGE);
        label = getIntent().getStringExtra(EXTRA_LABEL);
        if (pkg == null) pkg = "";
        if (label == null || label.isEmpty()) label = pkg;
        TextView title = findViewById(R.id.title);
        TextView warning = findViewById(R.id.warning);
        TextView steps = findViewById(R.id.steps);
        status = findViewById(R.id.status);
        confirm = findViewById(R.id.confirm);
        steps.setText(getString(R.string.adb_allow_hint) + "\n\n" + getString(R.string.adb_steps));
        if (MODE_CONNECT.equals(mode)) {
            title.setText(R.string.adb_connect_title);
            warning.setText(R.string.adb_connect_body);
            confirm.setText(R.string.adb_done);
        } else {
            title.setText(getString(R.string.delete_title, label));
            warning.setText(getString(R.string.delete_warning, label));
            if (!PackageGuard.safeName(pkg) || PackageGuard.blocked(getPackageName(), pkg)) {
                warning.setText(R.string.delete_blocked);
                confirm.setVisibility(View.GONE);
            }
        }
        TvFocus.vertical(findViewById(R.id.pairCode));
        TvFocus.vertical(findViewById(R.id.pairPort));
        TvFocus.vertical(findViewById(R.id.debugPort));
        findViewById(R.id.connect).setOnClickListener(v -> connect());
        findViewById(R.id.pair).setOnClickListener(v -> pair());
        confirm.setOnClickListener(v -> confirm());
        findViewById(R.id.disable).setOnClickListener(v -> disable());
        refresh();
        findViewById(R.id.connect).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        DebugGate.onResume(this);
        refresh();
    }

    @Override
    protected void onPause() {
        DebugGate.onPause(this);
        super.onPause();
    }

    private void connect() {
        if (busy) return;
        busy = true;
        status.setText(R.string.adb_allow_hint);
        LocalAdb.get(this).connectNetwork((ok, message) -> {
            busy = false;
            status.setText(DebugGate.explain(this, ok, message));
            refresh();
        });
    }

    private void pair() {
        if (busy) return;
        String code = text(R.id.pairCode);
        int pairPort = number(R.id.pairPort);
        int debugPort = number(R.id.debugPort);
        busy = true;
        status.setText(R.string.adb_pairing);
        LocalAdb.get(this).pairAndConnect(pairPort, code, debugPort, (ok, message) -> {
            busy = false;
            status.setText(DebugGate.explain(this, ok, message));
            refresh();
        });
    }

    private void confirm() {
        if (MODE_CONNECT.equals(mode)) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        if (busy) return;
        if (!LocalAdb.get(this).isConnected()) {
            DebugGate.requestUninstall(this, pkg, label);
            return;
        }
        if (!PackageGuard.safeName(pkg) || PackageGuard.blocked(getPackageName(), pkg)) return;
        busy = true;
        status.setText(R.string.delete_working);
        LocalAdb.get(this).uninstall(pkg, (ok, message) -> {
            busy = false;
            String shown = (ok ? getString(R.string.delete_done) : getString(R.string.delete_failed))
                    + "\n" + (message == null ? "" : message);
            status.setText(shown);
            if (!ok) {
                findViewById(R.id.disable).setVisibility(View.VISIBLE);
                return;
            }
            RemovedApps.add(this, pkg, label);
            List<String> dock = UserDock.read(this);
            if (dock != null) {
                dock.remove(pkg);
                UserDock.write(this, dock);
            }
            HomeEdits.notifyChanged(this);
            Toast.makeText(this, shown, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        });
    }

    private void disable() {
        if (busy || !PackageGuard.safeName(pkg) || PackageGuard.blocked(getPackageName(), pkg)) {
            status.setText(R.string.delete_blocked);
            return;
        }
        if (!LocalAdb.get(this).isConnected()) {
            status.setText(R.string.adb_not_listening);
            return;
        }
        busy = true;
        LocalAdb.get(this).disable(pkg, (ok, message) -> {
            busy = false;
            String shown = (ok ? getString(R.string.delete_disabled) : getString(R.string.delete_failed))
                    + "\n" + (message == null ? "" : message);
            status.setText(shown);
            Toast.makeText(this, shown, Toast.LENGTH_LONG).show();
        });
    }

    private void refresh() {
        boolean connected = LocalAdb.get(this).isConnected();
        confirm.setEnabled(true);
        confirm.setAlpha(1f);
        if (connected && status.getText().length() == 0) status.setText(R.string.adb_already);
    }

    private String text(int id) {
        EditText field = findViewById(id);
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private int number(int id) {
        try {
            return Integer.parseInt(text(id));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
