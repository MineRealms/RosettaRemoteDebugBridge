package com.rosetta.remotedebugbridge.debug;

import com.rosetta.remotedebugbridge.script.util.NetworkUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Client -> Server: session refused (config off, user declined, fingerprint mismatch, ...). */
public class CrdSessionReject extends NetworkUtils.SimplePacket {
    private long sessionId;
    private String reason = "";

    public CrdSessionReject() {
    }

    public CrdSessionReject(long sessionId, String reason) {
        this.sessionId = sessionId;
        this.reason = reason == null ? "" : reason;
    }

    public long getSessionId() {
        return this.sessionId;
    }

    public String getReason() {
        return this.reason;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(this.sessionId);
        buf.writeUtf(this.reason, 512);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.sessionId = buf.readLong();
        this.reason = buf.readUtf(512);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        ServerSessions.onReject(player, this);
    }
}
