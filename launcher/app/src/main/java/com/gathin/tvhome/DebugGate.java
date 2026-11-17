package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * On-demand debugging. Nothing opens settings by itself. A shrine dialog explains the steps;
 * "Take me there" is the only way this screen navigates. If the user comes back with debugging
 * on, the action that was waiting runs, then debugging can be switched off again.
 */
final class DebugGate {
    static final String OFF_KEY = "debug_off_when_done";

    private static String kind = "";
    private static String pkg = "";
    private static String label = "";
    private static Dialog dialog;

    private DebugGate() {}

    static void requestUninstall(Activity activity, String packageName, String appLabel) {
        request(activity, "uninstall", packageName, appLabel);
    }

    static void requestRestore(Activity activity, String packageName, String appLabel) {
        request(activity, "restore", packageName, appLabel);
    }

    static void request(Activity activity, String action, String packageName, String appLabel) {
        kind = action == null ? "" : action;
        pkg = packageName == null ? "" : packageName;
        label = appLabel == null || appLabel.isEmpty() ? pkg : appLabel;
        if (LocalAdb.get(activity).isConnected()) {
            runPending(activity);
            return;
        }
        if (canWrite(activity)) tryEnableAdb(activity);
        show(activity);
        tryConnect(activity);
    }

    static void onResume(Activity activity) {
        if (kind.isEmpty()) return;
        if (LocalAdb.get(activity).isConnected()) runPending(activity);
    }

    static void onPause(Activity activity) {
    }

    /** One connection check. A press while Allow is open does not open a second socket. */
    static void test(Activity activity) {
        if (LocalAdb.get(activity).isConnecting()) {
            Toast.makeText(activity, R.string.adb_waiting, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(activity, R.string.adb_allow_hint, Toast.LENGTH_LONG).show();
        LocalAdb.get(activity).connectNetwork((ok, message) ->
                Toast.makeText(activity, explain(activity, ok, message), Toast.LENGTH_LONG).show());
    }

    private static void show(Activity activity) {
        if (activity.isFinishing()) return;
        if (dialog != null && dialog.isShowing()) {
            bind(activity);
            return;
        }
        Dialog next = new Dialog(activity);
        next.setContentView(R.layout.dialog_debug);
        Window window = next.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        next.setCancelable(true);
        next.setOnCancelListener(d -> cancel());
        dialog = next;
        bind(activity);
        next.show();
        activity.findViewById(android.R.id.content);
        View go = next.findViewById(R.id.debugGo);
        if (go != null) go.requestFocus();
    }

    private static String offLabel(Activity activity, boolean on) {
        return activity.getString(R.string.debug_off_when_done) + "  ·  "
                + activity.getString(on ? R.string.on : R.string.off);
    }

    private static void bind(Activity activity) {
        if (dialog == null) return;
        Reason reason = diagnose(activity);
        TextView title = dialog.findViewById(R.id.debugTitle);
        TextView why = dialog.findViewById(R.id.debugReason);
        TextView steps = dialog.findViewById(R.id.debugSteps);
        TextView honest = dialog.findViewById(R.id.debugHonest);
        title.setText(R.string.debug_title);
        why.setText(reasonText(reason));
        steps.setText(R.string.debug_steps_bilingual);
        honest.setText(R.string.adb_allow_hint);
        TextView off = dialog.findViewById(R.id.debugOff);
        off.setText(offLabel(activity, offWhenDone(activity)));
        off.setOnClickListener(v -> {
            boolean now = !offWhenDone(activity);
            activity.getSharedPreferences(Config.SETTINGS_NAME, Activity.MODE_PRIVATE)
                    .edit().putBoolean(OFF_KEY, now).apply();
            off.setText(offLabel(activity, now));
        });
        dialog.findViewById(R.id.debugGo).setOnClickListener(v -> {
            Intent page = reason.page();
            if (page.resolveActivity(activity.getPackageManager()) != null) activity.startActivity(page);
            else activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        });
        dialog.findViewById(R.id.debugStepsOnly).setOnClickListener(v -> steps.requestFocus());
        dialog.findViewById(R.id.debugCancel).setOnClickListener(v -> {
            cancel();
            dialog.dismiss();
        });
        View test = dialog.findViewById(R.id.debugTest);
        if (test != null) test.setOnClickListener(v -> {
            if (LocalAdb.get(activity).isConnecting()) {
                why.setText(R.string.adb_waiting);
                return;
            }
            why.setText(R.string.adb_allow_hint);
            tryConnect(activity);
        });
    }

    private static void cancel() {
        kind = "";
        pkg = "";
        label = "";
    }

    private static void tryConnect(Activity activity) {
        if (LocalAdb.get(activity).isConnecting()) return;
        if (LocalAdb.get(activity).isConnected()) {
            if (!kind.isEmpty()) runPending(activity);
            return;
        }
        LocalAdb.get(activity).connectNetwork((ok, message) -> {
            if (ok || LocalAdb.get(activity).isConnected()) {
                if (!kind.isEmpty()) runPending(activity);
                return;
            }
            if (dialog != null && dialog.isShowing()) {
                TextView why = dialog.findViewById(R.id.debugReason);
                if (why != null) why.setText(explain(activity, false, message));
            }
        });
    }

    static String explain(Activity activity, boolean ok, String message) {
        if (ok || "connected".equals(message)) {
            String finger = "";
            try {
                finger = "\n" + AdbIdentity.fingerprint(AdbIdentity.loadOrCreate(activity).adbPublicKey);
            } catch (Exception ignored) { }
            return activity.getString(R.string.adb_connected) + finger;
        }
        if ("unauthorized".equals(message)) return activity.getString(R.string.adb_unauthorized);
        if ("waiting".equals(message)) return activity.getString(R.string.adb_waiting);
        if ("not_listening".equals(message)) return activity.getString(R.string.adb_not_listening);
        if ("use_network".equals(message)) return activity.getString(R.string.adb_use_network);
        if ("blocked".equals(message)) return activity.getString(R.string.delete_blocked);
        return message == null ? activity.getString(R.string.adb_not_listening) : message;
    }

    private static void runPending(Activity activity) {
        String action = kind;
        String packageName = pkg;
        String appLabel = label;
        if (!LocalAdb.get(activity).isConnected()) return;
        LocalAdb adb = LocalAdb.get(activity);
        LocalAdb.Result done = (ok, message) -> {
            if (!ok) {
                Toast.makeText(activity, explain(activity, false, message), Toast.LENGTH_LONG).show();
                return;
            }
            if ("uninstall".equals(action)) {
                RemovedApps.add(activity, packageName, appLabel);
                java.util.List<String> dock = UserDock.read(activity);
                if (dock != null && dock.remove(packageName)) UserDock.write(activity, dock);
                HomeEdits.notifyChanged(activity);
            }
            if ("restore".equals(action)) RemovedApps.remove(activity, packageName);
            turnOffIfWanted(activity);
            kind = "";
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
            Toast.makeText(activity, activity.getString(R.string.debug_done) + "\n" + message, Toast.LENGTH_LONG).show();
            if ("uninstall".equals(action) && activity instanceof DeleteAppActivity) activity.finish();
        };
        if (!canWrite(activity)) {
            adb.grantWriteSecure((ok, message) -> dispatch(activity, adb, action, packageName, done));
            return;
        }
        dispatch(activity, adb, action, packageName, done);
    }

    private static void dispatch(Activity activity, LocalAdb adb, String action, String packageName, LocalAdb.Result done) {
        switch (action) {
            case "restore":
                adb.restore(packageName, done);
                break;
            case "set-home":
            case "home-alkhamis":
            case "stock-off":
                if (activity instanceof DefaultHomeActivity) ((DefaultHomeActivity) activity).runAlkhamis(done);
                else done.onResult(false, activity.getString(R.string.not_available));
                break;
            case "home-original":
            case "stock-on":
                if (activity instanceof DefaultHomeActivity) ((DefaultHomeActivity) activity).runOriginal(done);
                else done.onResult(false, activity.getString(R.string.not_available));
                break;
            case "fmt-off":
            case "fmt-on":
            case "rival-off":
            case "rival-on":
                done.onResult(false, activity.getString(R.string.not_available));
                break;
            case "disable-stock":
                if (!isStock(packageName)) done.onResult(false, activity.getString(R.string.debug_not_stock));
                else adb.runFixed("pm disable-user --user 0 " + packageName, done);
                break;
            case "sleep":
                try {
                    ScreenSaver.apply(activity);
                    done.onResult(true, "");
                } catch (SecurityException e) {
                    done.onResult(false, activity.getString(R.string.debug_need_secure));
                }
                break;
            case "set-timezone":
                if (!packageName.matches("[A-Za-z0-9_/+\\-]{1,80}")) {
                    done.onResult(false, activity.getString(R.string.zone_bad));
                } else {
                    adb.runFixed("cmd alarm set-timezone " + packageName, done);
                }
                break;
            case "auto-zone":
                if (!"0".equals(packageName) && !"1".equals(packageName)) {
                    done.onResult(false, activity.getString(R.string.zone_bad));
                } else {
                    adb.runFixed("settings put global auto_time_zone " + packageName, done);
                }
                break;
            case "uninstall":
            default:
                adb.uninstall(packageName, done);
                break;
        }
    }

    private static boolean isStock(String packageName) {
        return "com.google.android.tvlauncher".equals(packageName)
                || "com.google.android.apps.tv.launcherx".equals(packageName)
                || "com.google.android.leanbacklauncher".equals(packageName);
    }

    enum Reason {
        DEV_OFF, ADB_OFF, READY;

        Intent page() {
            if (this == DEV_OFF) return new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
            return new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        }
    }

    static Reason diagnose(Activity activity) {
        int dev = Settings.Global.getInt(activity.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
        if (dev == 0) return Reason.DEV_OFF;
        int adb = Settings.Global.getInt(activity.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
        int wifi = Settings.Global.getInt(activity.getContentResolver(), "adb_wifi_enabled", 0);
        if (adb == 0 && wifi == 0 && !portOpen()) return Reason.ADB_OFF;
        return Reason.READY;
    }

    private static String reasonText(Reason reason) {
        if (reason == Reason.DEV_OFF) return "Developer options are off.\nخيارات المطوّر غير مفعّلة.";
        if (reason == Reason.ADB_OFF) return "Network debugging is off.\nتصحيح الشبكة غير مفعّل.";
        return "Debugging is not connected yet.\nالتصحيح غير متصل بعد.";
    }

    private static boolean portOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 350);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean canWrite(Activity activity) {
        return ScreenSaver.canWrite(activity);
    }

    private static void tryEnableAdb(Activity activity) {
        if (!canWrite(activity)) return;
        try {
            Settings.Global.putInt(activity.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
            Settings.Global.putInt(activity.getContentResolver(), "adb_wifi_enabled", 1);
        } catch (SecurityException ignored) { }
    }

    private static void turnOffIfWanted(Activity activity) {
        if (!offWhenDone(activity) || !canWrite(activity)) return;
        try {
            Settings.Global.putInt(activity.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            Settings.Global.putInt(activity.getContentResolver(), "adb_wifi_enabled", 0);
        } catch (SecurityException ignored) { }
    }

    private static boolean offWhenDone(Activity activity) {
        return activity.getSharedPreferences(Config.SETTINGS_NAME, Activity.MODE_PRIVATE).getBoolean(OFF_KEY, true);
    }
}
