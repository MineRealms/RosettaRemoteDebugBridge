package com.rosetta.remotedebugbridge.debug;

import java.nio.charset.StandardCharsets;

/**
 * Protocol constants shared by client and server sides of Client Remote Debug.
 */
public final class CrdProtocol {
    public static final int PROTOCOL_VERSION = 1;
    public static final String HKDF_INFO = "rosetta-crd-v1";
    public static final int AES_KEY_BITS = 256;
    public static final int GCM_NONCE_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;
    public static final int MAX_PAYLOAD_BYTES = 512 * 1024;
    public static final int MESSAGES_PER_SECOND = 20;
    public static final int SESSION_IDLE_TIMEOUT_SECONDS = 1800;
    public static final int REQUEST_TIMEOUT_MS = 30_000;

    public static final String OP_COLLECT_INFO = "collect_info";
    public static final String OP_TAIL_LOG = "tail_log";
    public static final String OP_RESOURCE_RELOAD = "resource_reload";
    public static final String OP_PUSH_RESOURCE_PACK = "push_resource_pack";
    public static final String OP_RUN_CLIENT_ACTION = "run_client_action";
    public static final String OP_EVAL_CLIENT_SCRIPT = "eval_client_script";
    public static final String OP_CLOSE = "close";

    private CrdProtocol() {
    }

    public static CrdPermission requiredPermission(String op) {
        if (op == null) {
            return CrdPermission.SCRIPT;
        }
        return switch (op) {
            case OP_COLLECT_INFO, OP_TAIL_LOG -> CrdPermission.READ;
            case OP_RESOURCE_RELOAD, OP_PUSH_RESOURCE_PACK -> CrdPermission.RELOAD;
            case OP_RUN_CLIENT_ACTION -> CrdPermission.ACTION;
            case OP_EVAL_CLIENT_SCRIPT -> CrdPermission.SCRIPT;
            case OP_CLOSE -> CrdPermission.READ;
            default -> CrdPermission.SCRIPT;
        };
    }

    public static byte[] info() {
        return HKDF_INFO.getBytes(StandardCharsets.UTF_8);
    }
}
