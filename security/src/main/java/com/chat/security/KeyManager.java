
package com.chat.security;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;

public class KeyManager {
    private static final Path KEY_DIR = Paths.get(System.getProperty("user.home"), ".chatkeys");
    private static final String PRIV_FILE = "private.key";
    private static final String PUB_FILE  = "public.key";

    public static void generateKeyPair() throws SecurityException {
        try {
            if (!Files.exists(KEY_DIR)) Files.createDirectories(KEY_DIR);
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            Files.write(KEY_DIR.resolve(PRIV_FILE), kp.getPrivate().getEncoded());
            Files.write(KEY_DIR.resolve(PUB_FILE), kp.getPublic().getEncoded());
            System.out.println("Key pair generated under " + KEY_DIR.toAbsolutePath());
        } catch (Exception e) {
            throw new SecurityException("Key generation failed", e);
        }
    }

    public static PrivateKey loadPrivateKey() throws SecurityException {
        try {
            byte[] keyBytes = Files.readAllBytes(KEY_DIR.resolve(PRIV_FILE));
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new SecurityException("Failed to load private key", e);
        }
    }

    public static PublicKey loadPublicKey() throws SecurityException {
        try {
            byte[] keyBytes = Files.readAllBytes(KEY_DIR.resolve(PUB_FILE));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new SecurityException("Failed to load public key", e);
        }
    }
}
