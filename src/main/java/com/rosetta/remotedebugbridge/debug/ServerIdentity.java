package com.rosetta.remotedebugbridge.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Long-lived server identity for Client Remote Debug.
 * Persisted at &lt;gamedir&gt;/RosettaRemoteDebugBridge/server-identity.key (EC P-256).
 * The public fingerprint is what players can verify / TOFU-pin.
 */
public final class ServerIdentity {
    private static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge/CRD");
    private static final String FILE_NAME = "server-identity.key";
    private static volatile ServerIdentity instance;

    private final Path file;
    private final KeyPair keyPair;
    private final String fingerprint;

    private ServerIdentity(Path file, KeyPair keyPair) {
        this.file = file;
        this.keyPair = keyPair;
        this.fingerprint = CrdCrypto.fingerprint(keyPair.getPublic());
    }

    public static ServerIdentity get() {
        ServerIdentity current = instance;
        if (current == null) {
            synchronized (ServerIdentity.class) {
                current = instance;
                if (current == null) {
                    current = load();
                    instance = current;
                }
            }
        }
        return current;
    }

    private static ServerIdentity load() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("RosettaRemoteDebugBridge");
        Path file = dir.resolve(FILE_NAME);
        try {
            Files.createDirectories(dir);
            if (Files.exists(file)) {
                String privateB64 = null;
                String publicB64 = null;
                for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.startsWith("private=")) {
                        privateB64 = line.substring("private=".length()).trim();
                    }
                    else if (line.startsWith("public=")) {
                        publicB64 = line.substring("public=".length()).trim();
                    }
                }
                if (privateB64 != null && publicB64 != null) {
                    java.security.PrivateKey privateKey = CrdCrypto.decodePrivateKey(privateB64);
                    java.security.PublicKey publicKey = CrdCrypto.decodePublicKey(publicB64);
                    ServerIdentity identity = new ServerIdentity(file, new KeyPair(publicKey, privateKey));
                    LOGGER.info("[CRD] server identity loaded: fingerprint={}", identity.getFingerprint());
                    return identity;
                }
                LOGGER.warn("[CRD] server identity file is incomplete; regenerating: {}", file);
            }
            KeyPair keyPair = CrdCrypto.generateKeyPair();
            ServerIdentity identity = new ServerIdentity(file, keyPair);
            String content = "# RosettaRemoteDebugBridge server identity (EC P-256) - KEEP THIS FILE PRIVATE\n"
                    + "private=" + CrdCrypto.encodePrivateKey(keyPair.getPrivate()) + "\n"
                    + "public=" + CrdCrypto.encodePublicKey(keyPair.getPublic()) + "\n"
                    + "fingerprint=" + identity.getFingerprint() + "\n";
            Files.writeString(file, content, StandardCharsets.UTF_8);
            LOGGER.info("[CRD] server identity generated: fingerprint={} file={}", identity.getFingerprint(), file);
            return identity;
        }
        catch (IOException e) {
            LOGGER.error("[CRD] failed to persist server identity, using ephemeral key: {}", e.toString());
            return new ServerIdentity(file, CrdCrypto.generateKeyPair());
        }
    }

    public Path getFile() {
        return this.file;
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public String getFingerprint() {
        return this.fingerprint;
    }

    public String getShortFingerprint() {
        return this.fingerprint.length() > 16 ? this.fingerprint.substring(0, 16) : this.fingerprint;
    }
}
