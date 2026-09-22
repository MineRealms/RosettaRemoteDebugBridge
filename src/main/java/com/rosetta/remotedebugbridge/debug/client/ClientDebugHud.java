package com.rosetta.remotedebugbridge.debug.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiEvent;

/**
 * Persistent HUD indicator while a CRD session is active (cannot be hidden).
 */
public final class ClientDebugHud {
    private static volatile boolean active;
    private static volatile String summary = "";

    private ClientDebugHud() {
    }

    static void set(boolean activeSession, String sessionSummary) {
        active = activeSession;
        summary = sessionSummary == null ? "" : sessionSummary;
    }

    public static boolean isActive() {
        return active;
    }

    public static void render(RenderGuiEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null || minecraft.options.hideGui) {
            return;
        }
        String text = "CRD " + summary + "  -  /crd disconnect";
        Font font = minecraft.font;
        GuiGraphics graphics = event.getGuiGraphics();
        int width = font.width(text);
        int x = 4;
        int y = 4;
        graphics.fill(x, y, x + width + 8, y + 14, 0x90000000);
        graphics.fill(x, y, x + 3, y + 14, 0xFFCC3333);
        graphics.drawString(font, text, x + 6, y + 3, 0xFF8080, false);
    }
}
