package com.rosetta.remotedebugbridge.debug;

import com.rosetta.remotedebugbridge.script.util.NetworkUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Bidirectional encrypted payload envelope (post-handshake). */
public class CrdEnvelope extends NetworkUtils.SimplePacket {
    private long sessionId;
    private long seq;
    private byte[] nonce = new byte[0];
    private byte[] ciphertext = new byte[0];

    public CrdEnvelope() {
    }

    public CrdEnvelope(long sessionId, long seq, byte[] nonce, byte[] ciphertext) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.nonce = nonce == null ? new byte[0] : nonce;
        this.ciphertext = ciphertext == null ? new byte[0] : ciphertext;
    }

    public long getSessionId() {
        return this.sessionId;
    }

    public long getSeq() {
        return this.seq;
    }

    public byte[] getNonce() {
        return this.nonce;
    }

    public byte[] getCiphertext() {
        return this.ciphertext;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(this.sessionId);
        buf.writeVarLong(this.seq);
        buf.writeByteArray(this.nonce);
        buf.writeByteArray(this.ciphertext);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.sessionId = buf.readLong();
        this.seq = buf.readVarLong();
        this.nonce = buf.readByteArray(CrdProtocol.GCM_NONCE_BYTES);
        this.ciphertext = buf.readByteArray(CrdProtocol.MAX_PAYLOAD_BYTES);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        ServerSessions.onClientEnvelope(player, this);
    }

    @Override
    public void handleClient() {
        ClientDebugAgent.onEnvelope(this);
    }
}
