package com.rosetta.remotedebugbridge.debug;

import com.rosetta.remotedebugbridge.script.util.NetworkUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Server -> Client: request to open a debug session (plaintext, carries identity + ephemeral ECDH key). */
public class CrdSessionRequest extends NetworkUtils.SimplePacket {
    private long sessionId;
    private String serverName = "";
    private String serverAddress = "";
    private String fingerprint = "";
    private String identityKey = "";
    private String ephemeralKey = "";
    private String signature = "";
    private String requestedPermission = "READ";
    private int timeoutSeconds = CrdProtocol.SESSION_IDLE_TIMEOUT_SECONDS;

    public CrdSessionRequest() {
    }

    public CrdSessionRequest(long sessionId, String serverName, String serverAddress, String fingerprint,
                             String identityKey, String ephemeralKey, String signature,
                             String requestedPermission, int timeoutSeconds) {
        this.sessionId = sessionId;
        this.serverName = serverName;
        this.serverAddress = serverAddress;
        this.fingerprint = fingerprint;
        this.identityKey = identityKey;
        this.ephemeralKey = ephemeralKey;
        this.signature = signature;
        this.requestedPermission = requestedPermission;
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getSessionId() {
        return this.sessionId;
    }

    public String getServerName() {
        return this.serverName;
    }

    public String getServerAddress() {
        return this.serverAddress;
    }

    public String getFingerprint() {
        return this.fingerprint;
    }

    public String getIdentityKey() {
        return this.identityKey;
    }

    public String getEphemeralKey() {
        return this.ephemeralKey;
    }

    public String getSignature() {
        return this.signature;
    }

    public String getRequestedPermission() {
        return this.requestedPermission;
    }

    public int getTimeoutSeconds() {
        return this.timeoutSeconds;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(this.sessionId);
        buf.writeUtf(this.serverName, 256);
        buf.writeUtf(this.serverAddress, 256);
        buf.writeUtf(this.fingerprint, 128);
        buf.writeUtf(this.identityKey, 512);
        buf.writeUtf(this.ephemeralKey, 512);
        buf.writeUtf(this.signature, 512);
        buf.writeUtf(this.requestedPermission, 32);
        buf.writeVarInt(this.timeoutSeconds);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.sessionId = buf.readLong();
        this.serverName = buf.readUtf(256);
        this.serverAddress = buf.readUtf(256);
        this.fingerprint = buf.readUtf(128);
        this.identityKey = buf.readUtf(512);
        this.ephemeralKey = buf.readUtf(512);
        this.signature = buf.readUtf(512);
        this.requestedPermission = buf.readUtf(32);
        this.timeoutSeconds = buf.readVarInt();
    }

    @Override
    public void handleClient() {
        ClientDebugAgent.onSessionRequest(this);
    }

    @Override
    public void handleServer(ServerPlayer player) {
    }
}
