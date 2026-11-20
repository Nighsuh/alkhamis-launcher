package com.gathin.tvhome;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Debugging key helpers that never cast the private key. Android 11's Conscrypt key
 * ({@code OpenSSLRSAPrivateKey}) is not an {@code RSAPrivateCrtKey}, so the modulus and the
 * public exponent are read from the stored PKCS#8 bytes, and the AUTH token is signed with
 * {@code NONEwithRSA} over the SHA-1 DigestInfo.
 */
final class AdbAuth {
    /** SHA-1 DigestInfo prefix, without the 20-byte hash. */
    private static final byte[] SHA1_PREFIX = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    private AdbAuth() {}

    /** Loads a stored PKCS#8 key the way the device does: no CRT cast. */
    static PrivateKey privateKey(byte[] pkcs8) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    /** Modulus then public exponent, from the RSAPrivateKey inside PKCS#8. */
    static BigInteger[] modulusAndExponent(byte[] pkcs8) {
        Der der = new Der(pkcs8);
        der.readSequence();
        der.skip(); // version
        der.skip(); // algorithm
        byte[] inner = der.octetString();
        Der rsa = new Der(inner);
        rsa.readSequence();
        rsa.skip(); // version
        BigInteger modulus = rsa.integer();
        BigInteger exponent = rsa.integer();
        return new BigInteger[]{modulus, exponent};
    }

    static byte[] digestInfo(byte[] token) {
        if (token == null || token.length != 20) throw new IllegalArgumentException("token");
        byte[] info = new byte[SHA1_PREFIX.length + token.length];
        System.arraycopy(SHA1_PREFIX, 0, info, 0, SHA1_PREFIX.length);
        System.arraycopy(token, 0, info, SHA1_PREFIX.length, token.length);
        return info;
    }

    /** Signs the 20-byte ADB token. Works for a Conscrypt key and for a software key. */
    static byte[] signToken(PrivateKey key, byte[] token) throws Exception {
        Signature signature = Signature.getInstance("NONEwithRSA");
        signature.initSign(key);
        signature.update(digestInfo(token));
        return signature.sign();
    }

    /** AOSP mincrypt public key (little-endian modulus, n0inv, R^2, exponent) plus the key name. */
    static byte[] androidPublicKey(BigInteger modulus, BigInteger exponent) {
        int len = (modulus.bitLength() + 7) / 8;
        if (len < 256) len = 256;
        byte[] raw = new byte[4 + 4 + len + len + 4];
        writeLE32(raw, 0, len / 4);
        BigInteger n0 = modulus.mod(BigInteger.ONE.shiftLeft(32));
        BigInteger n0inv = n0.modInverse(BigInteger.ONE.shiftLeft(32)).negate();
        writeLE32(raw, 4, n0inv.intValue());
        System.arraycopy(fixedLE(modulus, len), 0, raw, 8, len);
        BigInteger r = BigInteger.ONE.shiftLeft(len * 8);
        System.arraycopy(fixedLE(r.multiply(r).mod(modulus), len), 0, raw, 8 + len, len);
        writeLE32(raw, 8 + len * 2, exponent.intValue());
        String named = Base64.getEncoder().encodeToString(raw) + " cleanyourtv@tv\0";
        return named.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fixedLE(BigInteger value, int len) {
        byte[] be = value.toByteArray();
        byte[] le = new byte[len];
        int n = Math.min(len, be.length);
        for (int i = 0; i < n; i++) le[i] = be[be.length - 1 - i];
        return le;
    }

    private static void writeLE32(byte[] dest, int at, int value) {
        dest[at] = (byte) value;
        dest[at + 1] = (byte) (value >>> 8);
        dest[at + 2] = (byte) (value >>> 16);
        dest[at + 3] = (byte) (value >>> 24);
    }

    /** Just enough DER to walk a PKCS#8 RSA private key. */
    static final class Der {
        private final byte[] data;
        private int pos;

        Der(byte[] data) { this.data = data; }

        void readSequence() {
            int tag = readByte();
            if (tag != 0x30) throw new IllegalArgumentException("Expected a sequence.");
            int len = readLength();
            if (pos + len > data.length) throw new IllegalArgumentException("Sequence is cut off.");
        }

        void skip() {
            int tag = readByte();
            int len = readLength();
            if (tag == 0x30) {
                int end = pos + len;
                while (pos < end) skip();
                return;
            }
            pos += len;
        }

        BigInteger integer() {
            int tag = readByte();
            if (tag != 0x02) throw new IllegalArgumentException("Expected an integer.");
            int len = readLength();
            if (len < 1 || pos + len > data.length) throw new IllegalArgumentException("Integer is cut off.");
            byte[] raw = Arrays.copyOfRange(data, pos, pos + len);
            pos += len;
            return new BigInteger(raw);
        }

        byte[] octetString() {
            int tag = readByte();
            if (tag != 0x04) throw new IllegalArgumentException("Expected an octet string.");
            int len = readLength();
            if (pos + len > data.length) throw new IllegalArgumentException("Octet string is cut off.");
            byte[] raw = Arrays.copyOfRange(data, pos, pos + len);
            pos += len;
            return raw;
        }

        private int readByte() {
            if (pos >= data.length) throw new IllegalArgumentException("Key ended early.");
            return data[pos++] & 0xff;
        }

        private int readLength() {
            int first = readByte();
            if ((first & 0x80) == 0) return first;
            int bytes = first & 0x7f;
            if (bytes == 0 || bytes > 4) throw new IllegalArgumentException("Bad length.");
            int len = 0;
            for (int i = 0; i < bytes; i++) len = (len << 8) | readByte();
            return len;
        }
    }
}
