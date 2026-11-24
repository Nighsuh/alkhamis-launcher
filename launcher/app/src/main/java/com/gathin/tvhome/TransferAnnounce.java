package com.gathin.tvhome;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.security.SecureRandom;

/** Tells phones on this Wi-Fi that the transfer page is open. The PIN is not included. */
final class TransferAnnounce {
    static final String TYPE = "_alkhamis._tcp.";
    private static final String PREF = "transfer_nsd";

    interface Registrar {
        void register(Context ctx, String instance, int port);

        void unregister();
    }

    private static Registrar registrar = new NsdRegistrar();

    private TransferAnnounce() {}

    static void use(Registrar next) {
        registrar = next == null ? new NsdRegistrar() : next;
    }

    static void publish(Context ctx, int port) {
        try {
            registrar.register(ctx, ctx == null ? "Alkhamis-test" : instance(ctx), port);
        } catch (Exception ignored) { }
    }

    static void withdraw() {
        try {
            registrar.unregister();
        } catch (Exception ignored) { }
    }

    static String instance(Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(PREF, null);
        if (name == null || name.isEmpty()) {
            name = "Alkhamis-" + Integer.toHexString(new SecureRandom().nextInt(0x10000));
            prefs.edit().putString(PREF, name).apply();
        }
        return name;
    }

    private static final class NsdRegistrar implements Registrar {
        private NsdManager manager;
        private NsdManager.RegistrationListener listener;

        @Override
        public void register(Context ctx, String instance, int port) {
            unregister();
            NsdManager nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
            if (nsd == null) return;
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName(instance);
            info.setServiceType(TYPE);
            info.setPort(port);
            info.setAttribute("v", "1");
            NsdManager.RegistrationListener next = new NsdManager.RegistrationListener() {
                @Override public void onRegistrationFailed(NsdServiceInfo s, int error) { }
                @Override public void onUnregistrationFailed(NsdServiceInfo s, int error) { }
                @Override public void onServiceRegistered(NsdServiceInfo s) { }
                @Override public void onServiceUnregistered(NsdServiceInfo s) { }
            };
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, next);
            manager = nsd;
            listener = next;
        }

        @Override
        public void unregister() {
            if (manager != null && listener != null) {
                try {
                    manager.unregisterService(listener);
                } catch (Exception ignored) { }
            }
            manager = null;
            listener = null;
        }
    }
}
