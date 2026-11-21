package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to this TV's own adbd. Every socket is to 127.0.0.1 or another address of this device,
 * never a host the owner typed. The client is created on the first Connect press, not at launch.
 */
final class LocalAdb {
    static final String LOCALHOST = "127.0.0.1";

    interface Result {
        void onResult(boolean ok, String message);
    }

    private static LocalAdb instance;
    private final Context app;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AdbLink link;
    /** True for the whole handshake, including the minute spent waiting for Allow. */
    private volatile boolean connecting;

    private LocalAdb(Context ctx) {
        app = ctx.getApplicationContext();
    }

    static synchronized LocalAdb get(Context ctx) {
        if (instance == null) instance = new LocalAdb(ctx);
        return instance;
    }

    boolean isConnected() {
        AdbLink current = link;
        return current != null && current.isOpen();
    }

    boolean isConnecting() { return connecting; }

    /** Network debugging on port 5555. A second call while Allow is open does not open another socket. */
    void connectNetwork(Result result) {
        if (isConnected()) {
            deliver(result, true, "connected");
            return;
        }
        if (connecting) {
            deliver(result, false, "waiting");
            return;
        }
        connecting = true;
        worker.execute(() -> {
            try {
                connectOnce(result);
            } finally {
                connecting = false;
            }
        });
    }

    /**
     * Wireless pairing needs a TLS stack this launcher does not ship. The debugging port is still
     * tried with ordinary ADB, which is what Network debugging uses.
     */
    void pairAndConnect(int pairPort, String code, int debugPort, Result result) {
        deliver(result, false, "use_network");
    }

    void disable(String pkg, Result result) {
        worker.execute(() -> {
            if (!PackageGuard.safeName(pkg) || PackageGuard.blocked(app.getPackageName(), pkg)
                    || HomeSwitch.denied(pkg)) {
                deliver(result, false, "blocked");
                return;
            }
            if (!isConnected()) {
                deliver(result, false, "not_listening");
                return;
            }
            try {
                String output = link.shell(PackageGuard.disableCommand(pkg));
                boolean ok = output.toLowerCase().contains("disabled") || output.toLowerCase().contains("new state");
                deliver(result, ok, output.isEmpty() ? "The TV sent an empty reply." : output);
            } catch (Exception e) {
                deliver(result, false, e.getMessage() == null ? "The command failed." : e.getMessage());
            }
        });
    }

    void uninstall(String pkg, Result result) {
        runGuarded(pkg, true, result);
    }

    void restore(String pkg, Result result) {
        runGuarded(pkg, false, result);
    }

    void grantWriteSecure(Result result) {
        runFixed("pm grant " + app.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS", result);
    }

    /** A command this class already decided is safe. Anything else is refused. */
    void runFixed(String command, Result result) {
        worker.execute(() -> {
            if (!allowed(command)) {
                deliver(result, false, "That command is not one this launcher runs.");
                return;
            }
            if (!isConnected()) {
                deliver(result, false, "Debugging is not connected.");
                return;
            }
            try {
                String output = link.shell(command);
                boolean bad = output.contains("Error") || output.contains("Failure")
                        || output.contains("Exception") || output.contains("Unknown");
                deliver(result, !bad, output.isEmpty() ? (bad ? "The TV did not confirm it." : "Done.") : output);
            } catch (Exception e) {
                deliver(result, false, e.getMessage() == null ? "The command failed." : e.getMessage());
            }
        });
    }

    /**
     * Same allow-list as {@link #runFixed}, but the TV's text is returned as it arrived, including
     * an empty reply. The caller decides what a successful command looks like.
     */
    void runRaw(String command, Result result) {
        worker.execute(() -> {
            if (!allowed(command)) {
                deliver(result, false, "That command is not one this launcher runs.");
                return;
            }
            if (!isConnected()) {
                deliver(result, false, "Debugging is not connected.");
                return;
            }
            try {
                String output = link.shell(command);
                deliver(result, true, output == null ? "" : output);
            } catch (Exception e) {
                deliver(result, false, e.getMessage() == null ? "The command failed." : e.getMessage());
            }
        });
    }

    /**
     * Localhost first, then this TV's own addresses. The first host that shows Allow ends the
     * search, so a second socket cannot raise the dialog again.
     */
    private void connectOnce(Result result) {
        AdbIdentity id;
        try {
            id = AdbIdentity.loadOrCreate(app);
        } catch (Exception e) {
            deliver(result, false, e.getMessage() == null ? "The debugging key could not be read." : e.getMessage());
            return;
        }
        Exception last = null;
        for (String host : hosts()) {
            AdbLink next = new AdbLink(id.privateKey, id.adbPublicKey);
            try {
                next.connect(host, 5555);
                AdbLink previous = link;
                link = next;
                if (previous != null && previous != next) previous.close();
                deliver(result, true, "connected");
                return;
            } catch (AdbLink.Unauthorized e) {
                next.close();
                deliver(result, false, "unauthorized");
                return;
            } catch (Exception e) {
                next.close();
                last = e;
            }
        }
        if (last instanceof ConnectException) deliver(result, false, "not_listening");
        else deliver(result, false, last == null || last.getMessage() == null ? "not_listening" : last.getMessage());
    }

    private void runGuarded(String pkg, boolean uninstall, Result result) {
        worker.execute(() -> {
            if (!PackageGuard.safeName(pkg)) {
                deliver(result, false, "That package name cannot be used.");
                return;
            }
            if (!isConnected()) {
                deliver(result, false, "Debugging is not connected.");
                return;
            }
            String command = uninstall ? PackageGuard.uninstallCommand(pkg) : PackageGuard.restoreCommand(pkg);
            try {
                String output = link.shell(command);
                boolean ok = uninstall ? output.contains("Success") : output.toLowerCase().contains("install");
                deliver(result, ok, output.isEmpty() ? (ok ? "Done." : "The TV did not confirm it.") : output);
            } catch (Exception e) {
                deliver(result, false, e.getMessage() == null ? "The command failed." : e.getMessage());
            }
        });
    }

    private boolean allowed(String command) {
        if (command == null || command.indexOf('\n') >= 0 || command.indexOf(';') >= 0) return false;
        String pkg = app.getPackageName();
        if (command.equals("pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS")) return true;
        if (command.equals("cmd package set-home-activity " + pkg + "/.MainActivity")) return true;
        if (command.equals("cmd package set-home-activity --user 0 " + pkg + "/.MainActivity")) return true;
        if (command.equals("cmd role add-role-holder --user 0 android.app.role.HOME " + pkg + " 0")) return true;
        if (command.equals("cmd role add-role-holder android.app.role.HOME " + pkg)) return true;
        if (HomeSwitch.commandAllowed(command, pkg)) return true;
        String disable = "pm disable-user --user 0 ";
        if (command.startsWith(disable)) {
            String name = command.substring(disable.length());
            return PackageGuard.safeName(name) && !HomeSwitch.denied(name);
        }
        String zone = "cmd alarm set-timezone ";
        if (command.startsWith(zone)) return command.substring(zone.length()).matches("[A-Za-z0-9_/+\\-]{1,80}");
        return command.equals("settings put global auto_time_zone 0")
                || command.equals("settings put global auto_time_zone 1");
    }

    /** Localhost first, then this device's own IPv4 addresses. Never an address from outside. */
    static List<String> hosts() {
        List<String> hosts = new ArrayList<>();
        hosts.add(LOCALHOST);
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        if (host != null && !hosts.contains(host)) hosts.add(host);
                    }
                }
            }
        } catch (Exception ignored) { }
        return Collections.unmodifiableList(hosts);
    }

    private static boolean portOk(int port) {
        return port > 0 && port < 65536;
    }

    private void deliver(Result result, boolean ok, String message) {
        main.post(() -> result.onResult(ok, message));
    }
}
