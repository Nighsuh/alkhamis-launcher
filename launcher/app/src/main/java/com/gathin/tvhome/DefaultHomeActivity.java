package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Two home screens: Alkhamis Launcher, or the TV's factory home. The switch runs over local
 * debugging. Free My TV is never turned off from here. If the stock launcher will not move,
 * the Home button service is enabled in the background, with no extra row.
 */
public final class DefaultHomeActivity extends Activity {
    private View alkhamisRow;
    private View originalRow;
    private TextView alkhamisTitle;
    private TextView alkhamisSub;
    private TextView alkhamisCheck;
    private TextView originalTitle;
    private TextView originalSub;
    private TextView originalCheck;
    private TextView note;
    private TextView fmtNote;
    private boolean working;
    private LocalAdb.Result pendingDone;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home_screen);
        alkhamisRow = findViewById(R.id.homeAlkhamis);
        originalRow = findViewById(R.id.homeOriginal);
        alkhamisTitle = findViewById(R.id.homeAlkhamisTitle);
        alkhamisSub = findViewById(R.id.homeAlkhamisSub);
        alkhamisCheck = findViewById(R.id.homeAlkhamisCheck);
        originalTitle = findViewById(R.id.homeOriginalTitle);
        originalSub = findViewById(R.id.homeOriginalSub);
        originalCheck = findViewById(R.id.homeOriginalCheck);
        note = findViewById(R.id.homeNote);
        fmtNote = findViewById(R.id.homeFmtNote);
        alkhamisRow.setOnClickListener(v -> pick(true));
        originalRow.setOnClickListener(v -> pick(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        DebugGate.onResume(this);
        paint();
    }

    @Override
    protected void onPause() {
        DebugGate.onPause(this);
        super.onPause();
    }

    /** Called once debugging on this TV is connected. */
    void runAlkhamis(LocalAdb.Result done) {
        if (working) {
            if (done != null) done.onResult(false, getString(R.string.home_screen_working));
            return;
        }
        if (freeMyTvHome()) {
            paint();
            if (done != null) done.onResult(false, getString(R.string.home_screen_fmt));
            else Toast.makeText(this, R.string.home_screen_fmt, Toast.LENGTH_LONG).show();
            return;
        }
        pendingDone = done;
        working = true;
        note.setText(R.string.home_screen_working);
        HomeSwitch.turnStockOff(this, (ours, resolved, log, stopped, changed) -> {
            if (ours || (stopped != null && !stopped.isEmpty())) {
                finishPick(ours, resolved, stopped, true);
            } else {
                enableHomeKey(() -> finishPick(ours, resolved, stopped, true));
            }
        });
    }

    /** Called once debugging on this TV is connected. */
    void runOriginal(LocalAdb.Result done) {
        if (working) {
            if (done != null) done.onResult(false, getString(R.string.home_screen_working));
            return;
        }
        pendingDone = done;
        working = true;
        note.setText(R.string.home_screen_working);
        HomeSwitch.turnStockOn(this, (ours, resolved, log, stopped, changed) ->
                finishPick(ours, resolved, stopped, false));
    }

    private void pick(boolean alkhamis) {
        if (working) return;
        if (alkhamis && freeMyTvHome()) {
            paint();
            Toast.makeText(this, R.string.home_screen_fmt, Toast.LENGTH_LONG).show();
            return;
        }
        if (!LocalAdb.get(this).isConnected()) {
            DebugGate.request(this, alkhamis ? "home-alkhamis" : "home-original",
                    getPackageName(), getString(R.string.home_screen_label));
            return;
        }
        if (alkhamis) runAlkhamis(null);
        else runOriginal(null);
    }

    private void finishPick(boolean ours, String resolved, String stopped, boolean wantedOurs) {
        working = false;
        paint();
        if (HomeSwitch.STOP_FMT.equals(stopped)) {
            report(false, getString(R.string.home_screen_fmt));
            return;
        }
        if (HomeSwitch.STOP_WIFI.equals(stopped)) {
            report(false, getString(R.string.home_screen_wifi));
            return;
        }
        boolean ok = wantedOurs ? ours : !ours;
        if (ok) {
            report(true, "");
            return;
        }
        String who = wantedOurs ? labelOf(resolved) : getString(R.string.home_screen_alkhamis_title);
        report(false, getString(R.string.home_screen_fail, who));
    }

    private String labelOf(String pkg) {
        if (pkg == null || pkg.isEmpty()) return getString(R.string.home_none);
        if (getPackageName().equals(pkg)) return getString(R.string.home_screen_alkhamis_title);
        if (HomeSwitch.FREE_MY_TV.equals(pkg)) return "Free My TV";
        return pkg;
    }

    private void report(boolean ok, String message) {
        LocalAdb.Result done = pendingDone;
        pendingDone = null;
        if (done != null) {
            done.onResult(ok, message == null ? "" : message);
            return;
        }
        if (!ok && !isFinishing() && message != null && !message.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    /** Haier boards still send Home to the stock launcher. The service catches that key. No row for it. */
    private void enableHomeKey(Runnable then) {
        String mine = getPackageName() + "/.HomeKeyService";
        LocalAdb adb = LocalAdb.get(this);
        adb.runRaw("settings get secure enabled_accessibility_services", (ok, message) -> {
            String list = message == null ? "" : message.replace("\n", "").replace("\r", "").trim();
            if (!ok || "null".equals(list)) list = "";
            if (!list.contains("HomeKeyService")) list = list.isEmpty() ? mine : list + ":" + mine;
            if (!list.matches("[A-Za-z0-9_./:]+") || !list.contains(mine)) {
                then.run();
                return;
            }
            adb.runRaw("settings put secure enabled_accessibility_services " + list, (okPut, ignored) ->
                    adb.runRaw("settings put secure accessibility_enabled 1", (okOn, ignoredOn) -> then.run()));
        });
    }

    private void paint() {
        boolean ours = getPackageName().equals(resolvedHome());
        boolean fmt = freeMyTvHome();
        fmtNote.setVisibility(fmt ? View.VISIBLE : View.GONE);
        if (!working) note.setText(R.string.home_screen_note);
        paintRow(alkhamisRow, alkhamisTitle, alkhamisSub, alkhamisCheck, ours);
        paintRow(originalRow, originalTitle, originalSub, originalCheck, !ours);
        View focus = ours ? alkhamisRow : originalRow;
        if (getCurrentFocus() == null) focus.requestFocus();
    }

    private void paintRow(View row, TextView title, TextView sub, TextView check, boolean on) {
        row.setSelected(on);
        title.setTextColor(on ? 0xFF15202B : 0xFFFFFFFF);
        sub.setTextColor(on ? 0xFF5C6B78 : 0xFFACB8C1);
        check.setTextColor(on ? 0xFF15202B : 0xFFFFFFFF);
        check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    private boolean freeMyTvHome() {
        return HomeSwitch.FREE_MY_TV.equals(resolvedHome());
    }

    private String resolvedHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null || info.activityInfo.packageName == null) return "";
        if ("android".equals(info.activityInfo.packageName)) return "";
        return info.activityInfo.packageName;
    }
}
