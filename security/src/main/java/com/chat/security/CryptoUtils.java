
package com.chat.security;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;

public class CryptoUtils {
    private static final String RSA   = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES   = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;

    public static byte[] encrypt(byte[] plain, PublicKey peerPub) throws SecurityException {
        try {
            // AES key
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(AES_KEY_SIZE);
            SecretKey aes = kg.generateKey();
            // encrypt plain
            Cipher ac = Cipher.getInstance(AES);
            byte[] iv = new byte[12];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            ac.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, iv));
            byte[] ct = ac.doFinal(plain);
            // wrap key
            Cipher rc = Cipher.getInstance(RSA);
            rc.init(Cipher.WRAP_MODE, peerPub);
            byte[] wrap = rc.wrap(aes);
            ByteBuffer buf = ByteBuffer.allocate(2+wrap.length+iv.length+ct.length);
            buf.putShort((short)wrap.length);
            buf.put(wrap);
            buf.put(iv);
            buf.put(ct);
            return buf.array();
        } catch (Exception e) {
            throw new SecurityException("Encrypt failed", e);
        }
    }

    public static byte[] decrypt(byte[] hybrid, PrivateKey priv) throws SecurityException {
        try {
            ByteBuffer buf = ByteBuffer.wrap(hybrid);
            int wlen = buf.getShort() & 0xffff;
            byte[] wrap = new byte[wlen];
            buf.get(wrap);
            byte[] iv = new byte[12];
            buf.get(iv);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher rc = Cipher.getInstance(RSA);
            rc.init(Cipher.UNWRAP_MODE, priv);
            Key aes = rc.unwrap(wrap, "AES", Cipher.SECRET_KEY);
            Cipher ac = Cipher.getInstance(AES);
            ac.init(Cipher.DECRYPT_MODE, aes, new GCMParameterSpec(128, iv));
            return ac.doFinal(ct);
        } catch (Exception e) {
            throw new SecurityException("Decrypt failed", e);
        }
    }

    public static byte[] sign(byte[] data, PrivateKey priv) throws SecurityException {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(priv);
            s.update(data);
            return s.sign();
        } catch (Exception e) {
            throw new SecurityException("Sign failed", e);
        }
    }

    public static boolean verify(byte[] data, byte[] sig, PublicKey pub) throws SecurityException {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(pub);
            s.update(data);
            return s.verify(sig);
        } catch (Exception e) {
            throw new SecurityException("Verify failed", e);
        }
    }
}
