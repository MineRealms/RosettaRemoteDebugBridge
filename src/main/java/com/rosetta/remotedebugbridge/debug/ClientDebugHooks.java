package com.rosetta.remotedebugbridge.debug;

import com.google.gson.JsonObject;
import java.util.function.Consumer;

/**
 * Client-only capabilities required by {@link ClientDebugAgent}.
 * Implemented by {@code com.rosetta.remotedebugbridge.debug.client.ClientDebugClientBridge}
 * and installed on the client only, so the common agent never links against client classes.
 */
public interface ClientDebugHooks {
    /** Remote server address as seen by the client (for TOFU pinning). */
    String remoteAddress();

    /** Ask the player to confirm a session request. Decision must be delivered asynchronously. */
    void requestSessionConfirm(CrdSessionRequest request, CrdPermission granted, Consumer<Boolean> decision);

    /** Session state changed; used for chat notice + HUD indicator. */
    void notifySession(boolean active, String summary);

    /** Execute a decrypted op on the client thread; result JSON is {ok, result|error}. */
    void execute(String op, JsonObject args, Consumer<JsonObject> callback);

    /** Periodic client tick (idle expiry, HUD refresh). */
    void tick();
}
