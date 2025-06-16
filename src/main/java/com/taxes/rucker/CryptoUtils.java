package com.taxes.rucker;

import lombok.extern.slf4j.Slf4j;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
public final class CryptoUtils {

    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private CryptoUtils() {}

    /**
     * Generates a new RSA key pair.
     * @return The generated KeyPair, or null if an error occurs.
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
            keyGen.initialize(KEY_SIZE);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate RSA key pair", e);
            return null;
        }
    }

    /**
     * Signs a message with a private key.
     * @param privateKey The private key to sign with.
     * @param message The message to sign.
     * @return The Base64 encoded signature string, or null on failure.
     */
    public static String sign(PrivateKey privateKey, String message) {
        try {
            Signature privateSignature = Signature.getInstance(SIGNATURE_ALGORITHM);
            privateSignature.initSign(privateKey);
            privateSignature.update(message.getBytes());
            byte[] signature = privateSignature.sign();
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            log.error("Failed to sign message", e);
            return null;
        }
    }

    /**
     * Converts a PrivateKey object to a Base64 encoded string.
     * @param privateKey The private key to encode.
     * @return The Base64 encoded string.
     */
    public static String privateKeyToString(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * Converts a PublicKey object to a full PEM-formatted string.
     * @param publicKey The public key to encode.
     * @return The PEM-formatted string.
     */
    public static String publicKeyToPemString(PublicKey publicKey) {
        // MODIFIED: This now wraps the key in the required PEM headers for the Python backend.
        String key_b64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + key_b64 + "\n-----END PUBLIC KEY-----";
    }

    /**
     * Converts a Base64 encoded string back to a PrivateKey object.
     * @param keyString The Base64 encoded key string.
     * @return The PrivateKey object, or null on failure.
     */
    public static PrivateKey stringToPrivateKey(String keyString) {
        if (keyString == null || keyString.isEmpty()) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyString);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            log.error("Failed to decode private key from string", e);
            return null;
        }
    }
}