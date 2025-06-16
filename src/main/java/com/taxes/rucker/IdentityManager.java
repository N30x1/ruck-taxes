package com.taxes.rucker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

@Slf4j
public class IdentityManager {

    private static final Path RUCKTAXES_DIR = RuneLite.RUNELITE_DIR.toPath().resolve("rucktaxes");
    private static final Path PRIVATE_KEY_FILE = RUCKTAXES_DIR.resolve("identity.key");
    private static final Path USERNAME_FILE = RUCKTAXES_DIR.resolve("identity.username");

    /**
     * Loads the private key from the file system.
     * @return The PrivateKey object, or null if not found or an error occurs.
     */
    public PrivateKey loadPrivateKey() {
        if (!Files.exists(PRIVATE_KEY_FILE)) {
            return null;
        }
        try {
            String keyString = Files.readString(PRIVATE_KEY_FILE, StandardCharsets.UTF_8);
            return CryptoUtils.stringToPrivateKey(keyString);
        } catch (IOException e) {
            log.error("Failed to read private key file", e);
            return null;
        }
    }

    /**
     * Loads the generated username from the file system.
     * @return The username string, or null if not found or an error occurs.
     */
    public String loadGeneratedUsername() {
        if (!Files.exists(USERNAME_FILE)) {
            return null;
        }
        try {
            return Files.readString(USERNAME_FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read username file", e);
            return null;
        }
    }

    /**
     * Saves the identity (private key and username) to the file system.
     * @param privateKey The private key to save.
     * @param username The username to save.
     */
    public void saveIdentity(PrivateKey privateKey, String username) {
        try {
            // Ensure the directory exists
            if (!Files.exists(RUCKTAXES_DIR)) {
                Files.createDirectories(RUCKTAXES_DIR);
            }

            // Save the private key (Base64 encoded)
            String keyString = CryptoUtils.privateKeyToString(privateKey);
            Files.writeString(PRIVATE_KEY_FILE, keyString, StandardCharsets.UTF_8);

            // Save the username
            Files.writeString(USERNAME_FILE, username, StandardCharsets.UTF_8);

            log.info("Client identity saved successfully to {}", RUCKTAXES_DIR);
        } catch (IOException e) {
            log.error("Failed to save client identity files", e);
        }
    }
}