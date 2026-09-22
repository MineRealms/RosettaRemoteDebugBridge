package com.rosetta.remotedebugbridge.debug.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.debug.ClientDebugAgent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only event hooks for CRD (never registered on the dedicated server).
 */
@Mod.EventBusSubscriber(modid = RosettaRemoteDebugBridge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientDebugClientEvents {
    static {
        ClientDebugClientBridge.install();
    }

    private ClientDebugClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientDebugAgent.onLoggingIn();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDebugAgent.onLoggingOut();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientDebugHud.render(event);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ClientDebugAgent.tick();
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> disconnect = Commands.literal("disconnect").executes(context -> {
            ClientDebugAgent.disconnect("player command");
            context.getSource().sendSuccess(() -> Component.literal("CRD: session disconnected"), false);
            return 1;
        });
        LiteralArgumentBuilder<CommandSourceStack> status = Commands.literal("status").executes(context -> {
            boolean active = ClientDebugAgent.isSessionActive();
            context.getSource().sendSuccess(() -> Component.literal(active
                    ? "CRD: active session - " + ClientDebugAgent.getServerSummary()
                    + " (" + ClientDebugAgent.getGrantedPermission() + ")"
                    : "CRD: no active session"), false);
            return 1;
        });
        event.getDispatcher().register(Commands.literal("crd").then(disconnect).then(status));
    }
}
