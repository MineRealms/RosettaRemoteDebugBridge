package com.rosetta.remotedebugbridge;

import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import net.fabricmc.api.ClientModInitializer;

public class RosettaClientEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        RosettaLogger logger = RosettaRemoteDebugBridge.getCore().getLogger(ScriptType.CLIENT);
        logger.info("Client setup - loading client scripts");
        try {
            RosettaRemoteDebugBridge.getCore().loadScripts(ScriptType.CLIENT);
        } catch (Exception error) {
            logger.error("Failed to load client scripts: {}", error.getMessage(), error);
        }
    }
}
