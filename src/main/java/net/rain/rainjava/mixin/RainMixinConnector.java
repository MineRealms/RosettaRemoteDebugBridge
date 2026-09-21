/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.fml.loading.FMLPaths
 *  org.spongepowered.asm.mixin.Mixins
 *  org.spongepowered.asm.mixin.connect.IMixinConnector
 */
package net.rain.rainjava.mixin;

import net.minecraftforge.fml.loading.FMLPaths;
import net.rain.rainjava.RainJava;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class RainMixinConnector
implements IMixinConnector {
    public void connect() {
        RainJava.LOGGER.info("\u5c1d\u8bd5\u6dfb\u52a0MixinConfig");
        Mixins.addConfiguration((String)FMLPaths.GAMEDIR.get().resolve(".rain_mixin").resolve("rainjava.mixins.json").toString());
        RainJava.LOGGER.info("\u6dfb\u52a0MixinConfig\u6210\u529f\uff01");
    }
}

