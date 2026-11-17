package com.gathin.tvhome;

import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Loads a stored PKCS#8 key the way the device does and signs an ADB token. No CRT cast. */
public class AdbAuthTest {
    @Test
    public void storedKeySignsATokenWithoutACrtCast() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        byte[] stored = pair.getPrivate().getEncoded();

        PrivateKey loaded = AdbAuth.privateKey(stored);
        byte[] token = new byte[20];
        Arrays.fill(token, (byte) 0x5a);
        byte[] signature = AdbAuth.signToken(loaded, token);
        assertEquals(256, signature.length);

        Signature check = Signature.getInstance("NONEwithRSA");
        check.initVerify(pair.getPublic());
        check.update(AdbAuth.digestInfo(token));
        assertTrue(check.verify(signature));

        BigInteger[] parts = AdbAuth.modulusAndExponent(stored);
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        assertEquals(pub.getModulus(), parts[0]);
        assertEquals(pub.getPublicExponent(), parts[1]);
        String named = new String(AdbAuth.androidPublicKey(parts[0], parts[1]), "UTF-8");
        assertTrue(named.contains("cleanyourtv@tv"));
    }
}
