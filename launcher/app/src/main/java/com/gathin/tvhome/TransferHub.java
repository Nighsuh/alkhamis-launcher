package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;

/** The transfer server for this process. It stays up after the screen closes only when asked. */
final class TransferHub {
    /** Tile identity, so the owner can hide it the same way as an app. */
    static final String TILE = "com.gathin.tvhome.transfer";
    private static final String PREF = "transfer_background";

    private static TransferServer server;
    private static String pin;
    private static boolean background;

    private TransferHub() {}

    static File folder(Context ctx) {
        File dir = ctx.getExternalFilesDir("transfer");
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    static synchronized TransferServer server(Context ctx) throws IOException {
        if (server != null) return server;
        if (!TransferFiles.storageReady(ctx)) throw new IOException("storage");
        TransferFiles.migrate(ctx);
        File notes = folder(ctx);
        if (notes == null) throw new IOException("folder");
        if (pin == null) pin = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        background = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(PREF, false);
        TransferPage.bind(ctx.getApplicationContext());
        TransferFiles.Dir files = TransferFiles.open(ctx);
        int port = 8080;
        IOException last = null;
        for (int i = 0; i < 6; i++) {
            try {
                server = TransferServer.start(notes, files, pin, port + i);
                TransferServer.setInstallHook(safe -> TransferInstall.prompt(ctx.getApplicationContext(), safe));
                TransferAnnounce.publish(ctx.getApplicationContext(), server.port());
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
    }

    static synchronized boolean background() {
        return background;
    }

    static synchronized String pin() {
        return pin == null ? "" : pin;
    }

    /** Stops the server when the screen closes, unless background was turned on. */
    static synchronized void onScreenClosed() {
        if (background) return;
        TransferAnnounce.withdraw();
        TransferServer.setInstallHook(null);
        if (server != null) server.stop();
        server = null;
    }

    static String address() {
        String fallback = null;
        try {
            Enumeration<NetworkInterface> faces = NetworkInterface.getNetworkInterfaces();
            while (faces != null && faces.hasMoreElements()) {
                NetworkInterface face = faces.nextElement();
                if (!face.isUp() || face.isLoopback()) continue;
                Enumeration<InetAddress> addresses = face.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    String host = address.getHostAddress();
                    if (address.isSiteLocalAddress()) return host;
                    if (fallback == null) fallback = host;
                }
            }
        } catch (Exception ignored) { }
        return fallback == null ? "127.0.0.1" : fallback;
    }
}
