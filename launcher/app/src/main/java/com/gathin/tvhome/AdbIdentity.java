package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;

/**
 * RSA key the TV's debugging prompt shows. Stored in private files. Built with the platform
 * provider, so the launcher does not ship Bouncy Castle.
 */
final class AdbIdentity {
    private static final Object LOCK = new Object();
    private static AdbIdentity cached;

    final PrivateKey privateKey;
    final byte[] adbPublicKey;

    private AdbIdentity(PrivateKey privateKey, byte[] adbPublicKey) {
        this.privateKey = privateKey;
        this.adbPublicKey = adbPublicKey;
    }

    /** One key for the life of the install. A second call never mints another pair. */
    static AdbIdentity loadOrCreate(Context ctx) throws Exception {
        synchronized (LOCK) {
            if (cached != null) return cached;
            File dir = new File(ctx.getApplicationContext().getFilesDir(), "adb-identity");
            File keyFile = new File(dir, "key.pk8");
            if (keyFile.isFile() && keyFile.length() > 0) {
                cached = fromFile(keyFile);
                return cached;
            }
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("identity directory");
            if (keyFile.isFile() && keyFile.length() > 0) {
                cached = fromFile(keyFile);
                return cached;
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            java.security.KeyPair pair = gen.generateKeyPair();
            byte[] encoded = pair.getPrivate().getEncoded();
            writeAtomic(keyFile, encoded);
            cached = fromEncoded(encoded);
            return cached;
        }
    }

    private static AdbIdentity fromFile(File keyFile) throws Exception {
        return fromEncoded(read(keyFile));
    }

    /** PKCS#8 in, no cast of the platform private key. */
    private static AdbIdentity fromEncoded(byte[] pkcs8) throws Exception {
        java.math.BigInteger[] parts = AdbAuth.modulusAndExponent(pkcs8);
        PrivateKey key = AdbAuth.privateKey(pkcs8);
        return new AdbIdentity(key, AdbAuth.androidPublicKey(parts[0], parts[1]));
    }

    /** MD5 of the key the TV prints, so a repeated dialog can be checked against this one key. */
    static String fingerprint(byte[] namedKey) throws Exception {
        String text = new String(namedKey, java.nio.charset.StandardCharsets.UTF_8);
        int space = text.indexOf(' ');
        byte[] raw = Base64.decode(space > 0 ? text.substring(0, space) : text, Base64.NO_WRAP);
        byte[] dig = java.security.MessageDigest.getInstance("MD5").digest(raw);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < dig.length; i++) {
            if (i > 0) out.append(':');
            out.append(String.format(java.util.Locale.US, "%02X", dig[i] & 0xff));
        }
        return out.toString();
    }

    private static byte[] read(File file) throws java.io.IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            return buf;
        }
    }

    /** Writes beside the real file and renames. An existing key is never replaced. */
    private static void writeAtomic(File file, byte[] bytes) throws java.io.IOException {
        if (file.isFile() && file.length() > 0) return;
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (file.isFile() && file.length() > 0) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(file)) throw new java.io.IOException("Could not store the debugging key.");
    }

    private static void write(File file, byte[] bytes) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }
}
