/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.fml.loading.FMLPaths
 *  org.spongepowered.asm.mixin.Mixins
 *  org.spongepowered.asm.mixin.connect.IMixinConnector
 */
package com.rosetta.remotedebugbridge.mixin;

import net.minecraftforge.fml.loading.FMLPaths;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class RosettaMixinConnector
implements IMixinConnector {
    public void connect() {
        RosettaRemoteDebugBridge.LOGGER.info("\u5c1d\u8bd5\u6dfb\u52a0MixinConfig");
        Mixins.addConfiguration((String)FMLPaths.GAMEDIR.get().resolve(".rosetta_mixin").resolve("rosetta.mixins.json").toString());
        RosettaRemoteDebugBridge.LOGGER.info("\u6dfb\u52a0MixinConfig\u6210\u529f\uff01");
    }
}

