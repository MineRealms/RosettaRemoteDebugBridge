package com.rosetta.remotedebugbridge.debug;

import com.rosetta.remotedebugbridge.script.util.NetworkUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> Server capability hello (C0 handshake).
 * Sent by the client companion module right after logging into a server.
 */
public class ClientCapabilityHello extends NetworkUtils.SimplePacket {
    public static final int MODULE_VERSION = 1;

    private int moduleVersion = MODULE_VERSION;
    private boolean authorized;
    private String maxPermission = "READ";
    private String clientVersion = "unknown";

    public ClientCapabilityHello() {
    }

    public ClientCapabilityHello(int moduleVersion, boolean authorized, String maxPermission, String clientVersion) {
        this.moduleVersion = moduleVersion;
        this.authorized = authorized;
        this.maxPermission = maxPermission == null ? "READ" : maxPermission;
        this.clientVersion = clientVersion == null ? "unknown" : clientVersion;
    }

    public int getModuleVersion() {
        return this.moduleVersion;
    }

    public boolean isAuthorized() {
        return this.authorized;
    }

    public String getMaxPermission() {
        return this.maxPermission;
    }

    public String getClientVersion() {
        return this.clientVersion;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.moduleVersion);
        buf.writeBoolean(this.authorized);
        buf.writeUtf(this.maxPermission, 64);
        buf.writeUtf(this.clientVersion, 128);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.moduleVersion = buf.readVarInt();
        this.authorized = buf.readBoolean();
        this.maxPermission = buf.readUtf(64);
        this.clientVersion = buf.readUtf(128);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        ClientSessionManager.onHello(player, this);
    }
}
