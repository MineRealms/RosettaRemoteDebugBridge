package com.rosetta.remotedebugbridge.debug.client;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Confirmation screen for CRD (session open / SCRIPT execution).
 * Nothing is executed before the player explicitly accepts.
 */
public class ClientDebugConfirmScreen extends Screen {
    private final List<String> lines;
    private final Consumer<Boolean> decision;
    private boolean decided;

    public ClientDebugConfirmScreen(String title, List<String> lines, Consumer<Boolean> decision) {
        super(Component.literal(title));
        this.lines = lines;
        this.decision = decision;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 + 40;
        this.addRenderableWidget(Button.builder(Component.literal("Decline"), button -> this.decide(false))
                .bounds(centerX - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Accept"), button -> this.decide(true))
                .bounds(centerX + 10, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = this.height / 2 - 70;
        this.drawCentered(graphics, this.title.getString(), centerX, y, 0xFF5555);
        y += 22;
        for (String line : this.lines) {
            this.drawCentered(graphics, line, centerX, y, 0xE0E0E0);
            y += 12;
        }
    }

    private void drawCentered(GuiGraphics graphics, String text, int centerX, int y, int color) {
        int width = this.font.width(text);
        graphics.drawString(this.font, text, centerX - width / 2, y, color, false);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.decide(false);
    }

    private void decide(boolean accepted) {
        if (this.decided) {
            return;
        }
        this.decided = true;
        this.decision.accept(accepted);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }
}
