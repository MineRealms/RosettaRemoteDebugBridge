package com.rosetta.remotedebugbridge.debug;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Application-layer crypto for Client Remote Debug:
 * ECDH (P-256) -> HKDF-SHA256 -> AES-256-GCM, plus ECDSA identity signatures.
 * Pure JDK, side-safe (no Minecraft types).
 */
public final class CrdCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CrdCrypto() {
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("EC keypair generation failed", e);
        }
    }

    public static byte[] sharedSecret(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(privateKey);
            agreement.doPhase(publicKey, true);
            return agreement.generateSecret();
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("ECDH failed", e);
        }
    }

    public static byte[] deriveSessionKey(byte[] sharedSecret, byte[] salt, byte[] info) {
        return hkdf(sharedSecret, salt, info, CrdProtocol.AES_KEY_BITS / 8);
    }

    public static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt == null || salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] output = new byte[length];
            byte[] block = new byte[0];
            int offset = 0;
            int counter = 1;
            while (offset < length) {
                mac.update(block);
                mac.update(info == null ? new byte[0] : info);
                mac.update((byte)counter++);
                block = mac.doFinal();
                int copy = Math.min(block.length, length - offset);
                System.arraycopy(block, 0, output, offset, copy);
                offset += copy;
            }
            return output;
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("HKDF failed", e);
        }
    }

    public static byte[] encrypt(byte[] key, byte[] nonce, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(CrdProtocol.GCM_TAG_BITS, nonce));
            return cipher.doFinal(plaintext);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] nonce, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(CrdProtocol.GCM_TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("sha256 failed", e);
        }
    }

    public static byte[] concat(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    public static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static String randomHex(int bytes) {
        return hex(randomBytes(bytes));
    }

    public static String fingerprint(PublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return hex(digest);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("fingerprint failed", e);
        }
    }

    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static PublicKey decodePublicKey(String base64) {
        try {
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        }
        catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("invalid public key", e);
        }
    }

    public static String encodePrivateKey(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public static PrivateKey decodePrivateKey(String base64) {
        try {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        }
        catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("invalid private key", e);
        }
    }

    public static byte[] sign(PrivateKey identity, byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(identity);
            signature.update(data);
            return signature.sign();
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("sign failed", e);
        }
    }

    public static boolean verify(PublicKey identity, byte[] data, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(identity);
            signature.update(data);
            return signature.verify(signatureBytes);
        }
        catch (GeneralSecurityException e) {
            return false;
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("invalid hex");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static byte[] utf8(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    public static String utf8(byte[] value) {
        return value == null ? "" : new String(value, StandardCharsets.UTF_8);
    }

    /** Production self-check used by the {@code clientdebug selftest} bridge command. */
    public static boolean selfTest() {
        try {
            KeyPair server = generateKeyPair();
            KeyPair client = generateKeyPair();
            byte[] serverSecret = sharedSecret(server.getPrivate(), client.getPublic());
            byte[] clientSecret = sharedSecret(client.getPrivate(), server.getPublic());
            if (!MessageDigest.isEqual(serverSecret, clientSecret)) {
                return false;
            }
            byte[] salt = randomBytes(16);
            byte[] serverKey = deriveSessionKey(serverSecret, salt, CrdProtocol.info());
            byte[] clientKey = deriveSessionKey(clientSecret, salt, CrdProtocol.info());
            if (!MessageDigest.isEqual(serverKey, clientKey)) {
                return false;
            }
            byte[] nonce = randomBytes(CrdProtocol.GCM_NONCE_BYTES);
            byte[] payload = utf8("rosetta-crd selftest");
            byte[] encrypted = encrypt(serverKey, nonce, payload);
            if (!MessageDigest.isEqual(payload, decrypt(clientKey, nonce, encrypted))) {
                return false;
            }
            byte[] signature = sign(server.getPrivate(), client.getPublic().getEncoded());
            return verify(server.getPublic(), client.getPublic().getEncoded(), signature)
                    && !verify(client.getPublic(), client.getPublic().getEncoded(), signature);
        }
        catch (Throwable t) {
            return false;
        }
    }
}
