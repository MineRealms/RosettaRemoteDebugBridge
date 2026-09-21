/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.TitleScreen
 *  net.minecraft.network.chat.ClickEvent
 *  net.minecraft.network.chat.ClickEvent$Action
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.event.TickEvent$ClientTickEvent
 *  net.minecraftforge.event.TickEvent$Phase
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus
 */
package net.rain.rainjava.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.rain.rainjava.client.RainJavaErrorScreen;
import net.rain.rainjava.core.ScriptType;
import net.rain.rainjava.logging.ScriptErrorCollector;

@Mod.EventBusSubscriber(modid="rainjava", bus=Mod.EventBusSubscriber.Bus.FORGE, value={Dist.CLIENT})
public class RainJavaClientEvents {
    private static boolean startupScreenShown = false;
    private static boolean worldNotified = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        ScriptType target;
        boolean hasIssues;
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!startupScreenShown && ScriptErrorCollector.hasErrors(ScriptType.STARTUP)) {
            if (mc.screen instanceof TitleScreen) {
                startupScreenShown = true;
                mc.setScreen((Screen)new RainJavaErrorScreen(mc.screen, ScriptType.STARTUP));
            }
            return;
        }
        if (worldNotified) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        ScriptType typeWithErrors = RainJavaClientEvents.firstNonStartupTypeWithErrors();
        boolean bl = hasIssues = typeWithErrors != null || RainJavaClientEvents.hasNonStartupIssues();
        if (!hasIssues) {
            return;
        }
        worldNotified = true;
        ScriptType scriptType = target = typeWithErrors != null ? typeWithErrors : RainJavaClientEvents.firstNonStartupTypeWithWarnings();
        if (target != null) {
            RainJavaClientEvents.sendErrorChatMessage(mc, target);
        }
    }

    private static void sendErrorChatMessage(Minecraft mc, ScriptType type) {
        int errCount = ScriptErrorCollector.getErrors(type).size();
        int warnCount = ScriptErrorCollector.getWarnings(type).size();
        MutableComponent link = Component.literal((String)"[Click to view errors]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(Boolean.valueOf(true)).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rainjava_errors " + type.getName())));
        MutableComponent message = Component.literal((String)"[RainJava] ").withStyle(ChatFormatting.GOLD).append((Component)Component.literal((String)(type.getName() + " scripts: ")).withStyle(ChatFormatting.YELLOW)).append((Component)Component.literal((String)(errCount + " error(s)")).withStyle(errCount > 0 ? ChatFormatting.RED : ChatFormatting.GRAY)).append((Component)Component.literal((String)", ").withStyle(ChatFormatting.WHITE)).append((Component)Component.literal((String)(warnCount + " warning(s).  ")).withStyle(warnCount > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY)).append((Component)link);
        mc.gui.getChat().addMessage((Component)message);
    }

    private static ScriptType firstNonStartupTypeWithErrors() {
        for (ScriptType t : ScriptType.values()) {
            if (t == ScriptType.STARTUP || ScriptErrorCollector.getErrors(t).isEmpty()) continue;
            return t;
        }
        return null;
    }

    private static ScriptType firstNonStartupTypeWithWarnings() {
        for (ScriptType t : ScriptType.values()) {
            if (t == ScriptType.STARTUP || ScriptErrorCollector.getWarnings(t).isEmpty()) continue;
            return t;
        }
        return null;
    }

    private static boolean hasNonStartupIssues() {
        for (ScriptType t : ScriptType.values()) {
            if (t == ScriptType.STARTUP) continue;
            if (!ScriptErrorCollector.getErrors(t).isEmpty()) {
                return true;
            }
            if (ScriptErrorCollector.getWarnings(t).isEmpty()) continue;
            return true;
        }
        return false;
    }

    public static void resetShownFlag() {
        worldNotified = false;
    }
}

