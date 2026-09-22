package com.rosetta.remotedebugbridge.debug;

import com.rosetta.remotedebugbridge.script.util.NetworkUtils;
import net.minecraft.network.FriendlyByteBuf;

/** Server -> Client: encrypted session bootstrap (contains the one-time session token). */
public class CrdSessionReady extends NetworkUtils.SimplePacket {
    private long sessionId;
    private byte[] nonce = new byte[0];
    private byte[] ciphertext = new byte[0];

    public CrdSessionReady() {
    }

    public CrdSessionReady(long sessionId, byte[] nonce, byte[] ciphertext) {
        this.sessionId = sessionId;
        this.nonce = nonce == null ? new byte[0] : nonce;
        this.ciphertext = ciphertext == null ? new byte[0] : ciphertext;
    }

    public long getSessionId() {
        return this.sessionId;
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
        buf.writeByteArray(this.nonce);
        buf.writeByteArray(this.ciphertext);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.sessionId = buf.readLong();
        this.nonce = buf.readByteArray(CrdProtocol.MAX_PAYLOAD_BYTES);
        this.ciphertext = buf.readByteArray(CrdProtocol.MAX_PAYLOAD_BYTES);
    }

    @Override
    public void handleClient() {
        ClientDebugAgent.onSessionReady(this);
    }
}
