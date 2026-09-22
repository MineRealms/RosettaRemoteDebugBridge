package com.rosetta.remotedebugbridge.debug;

import com.rosetta.remotedebugbridge.script.util.NetworkUtils;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client capability ack (C0 handshake).
 * Confirms that the server registered the client companion module.
 */
public class ClientCapabilityAck extends NetworkUtils.SimplePacket {
    private boolean accepted;
    private boolean sessionAllowed;
    private String serverId = "";
    private String message = "";

    public ClientCapabilityAck() {
    }

    public ClientCapabilityAck(boolean accepted, boolean sessionAllowed, String serverId, String message) {
        this.accepted = accepted;
        this.sessionAllowed = sessionAllowed;
        this.serverId = serverId == null ? "" : serverId;
        this.message = message == null ? "" : message;
    }

    public boolean isAccepted() {
        return this.accepted;
    }

    public boolean isSessionAllowed() {
        return this.sessionAllowed;
    }

    public String getServerId() {
        return this.serverId;
    }

    public String getMessage() {
        return this.message;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.accepted);
        buf.writeBoolean(this.sessionAllowed);
        buf.writeUtf(this.serverId, 256);
        buf.writeUtf(this.message, 512);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.accepted = buf.readBoolean();
        this.sessionAllowed = buf.readBoolean();
        this.serverId = buf.readUtf(256);
        this.message = buf.readUtf(512);
    }

    @Override
    public void handleClient() {
        ClientDebugAgent.onAck(this);
    }
}
