package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/** The transfer server for this process. It stays up after the screen closes only when asked. */
final class TransferHub {
    /** Tile identity, so the owner can hide it the same way as an app. */
    static final String TILE = "com.gathin.tvhome.transfer";
    private static final String PREF = "transfer_background";
    private static final String NAME = "transfer_tv_name";

    private static TransferServer server;
    private static boolean background;
    private static Context app;
    private static String shown = "Alkhamis";

    private TransferHub() {}

    static final class Nic {
        final String name;
        final String host;

        Nic(String name, String host) {
            this.name = name;
            this.host = host;
        }
    }

    static File folder(Context ctx) {
        File dir = ctx.getExternalFilesDir("transfer");
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    static synchronized TransferServer server(Context ctx) throws IOException {
        app = ctx.getApplicationContext();
        shown = readName(app);
        if (server != null) {
            TransferAnnounce.publish(app, server.port());
            return server;
        }
        if (!TransferFiles.storageReady(ctx)) throw new IOException("storage");
        TransferFiles.migrate(ctx);
        File notes = folder(ctx);
        if (notes == null) throw new IOException("folder");
        background = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(PREF, false);
        TransferPage.bind(app);
        TransferFiles.Dir files = TransferFiles.open(ctx);
        TransferKind.repair(files);
        int port = 8080;
        IOException last = null;
        for (int i = 0; i < 6; i++) {
            try {
                server = TransferServer.start(notes, files, port + i);
                TransferServer.setInstallHook(safe -> TransferInstall.prompt(app, safe));
                TransferAnnounce.publish(app, server.port());
                return server;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("port") : last;
    }

    static synchronized void setBackground(Context ctx, boolean on) {
        background = on;
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply();
        if (on && server != null) TransferAnnounce.publish(ctx.getApplicationContext(), server.port());
    }

    static synchronized boolean background() {
        return background;
    }

    static synchronized String displayName() {
        return shown == null || shown.isEmpty() ? "Alkhamis" : shown;
    }

    /** Saves the name the phone and the browser show, and advertises it again. */
    static synchronized String rename(String raw) {
        String clean = sanitize(raw);
        if (clean.isEmpty()) clean = "Alkhamis";
        shown = clean;
        if (app != null) {
            app.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit().putString(NAME, clean).commit();
            if (server != null) TransferAnnounce.publish(app, server.port());
        }
        return clean;
    }

    static String sanitize(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim().replace('\n', ' ').replace('\r', ' ');
        if (trimmed.length() > 40) trimmed = trimmed.substring(0, 40).trim();
        return trimmed;
    }

    private static String readName(Context ctx) {
        String saved = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(NAME, "");
        if (saved != null && !saved.trim().isEmpty()) return sanitize(saved);
        return TransferAnnounce.instance(ctx);
    }

    /** Stops the server when the screen closes, unless background was turned on. */
    static synchronized void onScreenClosed() {
        if (background) return;
        TransferAnnounce.withdraw();
        TransferServer.setInstallHook(null);
        if (server != null) server.stop();
        server = null;
    }

    static String address(Context ctx) {
        String active = activeIpv4(ctx);
        List<Nic> faces = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all != null && all.hasMoreElements()) {
                NetworkInterface face = all.nextElement();
                if (!face.isUp() || face.isLoopback()) continue;
                Enumeration<InetAddress> addresses = face.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    faces.add(new Nic(face.getName(), address.getHostAddress()));
                }
            }
        } catch (Exception ignored) { }
        return choose(active, faces);
    }

    /** Picks a Wi-Fi or Ethernet address on this LAN. Virtual interfaces such as docker lose. */
    static String choose(String active, List<Nic> faces) {
        String bestHost = null;
        int best = -1;
        if (faces != null) {
            for (Nic nic : faces) {
                int score = rank(nic.name, nic.host);
                if (active != null && active.equals(nic.host)) score += 50;
                if (score > best) {
                    best = score;
                    bestHost = nic.host;
                }
            }
        }
        if (bestHost != null) return bestHost;
        if (rank("active", active) >= 0) return active;
        return "127.0.0.1";
    }

    static int rank(String name, String host) {
        if (host == null || host.isEmpty()) return -1;
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        if (n.contains("docker") || n.startsWith("br-") || n.startsWith("veth") || n.startsWith("virbr")
                || n.startsWith("dummy") || n.startsWith("tun") || n.startsWith("tap")
                || n.contains("rmnet") || n.contains("p2p")) return -1;
        if (!(host.startsWith("192.168.") || host.startsWith("10.") || private172(host))) return -1;
        int score = 40;
        if (n.contains("wlan") || n.contains("wifi")) score = 100;
        else if (n.startsWith("eth") || n.startsWith("en")) score = 80;
        if (host.startsWith("192.168.")) score += 30;
        else if (host.startsWith("10.")) score += 20;
        else score += 5;
        return score;
    }

    private static boolean private172(String host) {
        if (!host.startsWith("172.")) return false;
        int dot = host.indexOf('.', 4);
        if (dot < 0) return false;
        try {
            int second = Integer.parseInt(host.substring(4, dot));
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String activeIpv4(Context ctx) {
        if (ctx == null) return null;
        try {
            ConnectivityManager manager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return null;
            Network network = manager.getActiveNetwork();
            if (network == null) return null;
            LinkProperties props = manager.getLinkProperties(network);
            if (props == null) return null;
            for (LinkAddress link : props.getLinkAddresses()) {
                InetAddress address = link.getAddress();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address.getHostAddress();
            }
        } catch (Exception ignored) { }
        return null;
    }
}
