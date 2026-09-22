package com.rosetta.remotedebugbridge.debug;

import com.rosetta.remotedebugbridge.script.util.NetworkUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Client -> Server: session accepted; carries the client ephemeral ECDH key and granted permission. */
public class CrdSessionAccept extends NetworkUtils.SimplePacket {
    private long sessionId;
    private String clientEphemeralKey = "";
    private String clientNonceHex = "";
    private String grantedPermission = "READ";
    private int timeoutSeconds = CrdProtocol.SESSION_IDLE_TIMEOUT_SECONDS;

    public CrdSessionAccept() {
    }

    public CrdSessionAccept(long sessionId, String clientEphemeralKey, String clientNonceHex,
                            String grantedPermission, int timeoutSeconds) {
        this.sessionId = sessionId;
        this.clientEphemeralKey = clientEphemeralKey;
        this.clientNonceHex = clientNonceHex;
        this.grantedPermission = grantedPermission;
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getSessionId() {
        return this.sessionId;
    }

    public String getClientEphemeralKey() {
        return this.clientEphemeralKey;
    }

    public String getClientNonceHex() {
        return this.clientNonceHex;
    }

    public String getGrantedPermission() {
        return this.grantedPermission;
    }

    public int getTimeoutSeconds() {
        return this.timeoutSeconds;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(this.sessionId);
        buf.writeUtf(this.clientEphemeralKey, 512);
        buf.writeUtf(this.clientNonceHex, 128);
        buf.writeUtf(this.grantedPermission, 32);
        buf.writeVarInt(this.timeoutSeconds);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.sessionId = buf.readLong();
        this.clientEphemeralKey = buf.readUtf(512);
        this.clientNonceHex = buf.readUtf(128);
        this.grantedPermission = buf.readUtf(32);
        this.timeoutSeconds = buf.readVarInt();
    }

    @Override
    public void handleServer(ServerPlayer player) {
        ServerSessions.onAccept(player, this);
    }
}
