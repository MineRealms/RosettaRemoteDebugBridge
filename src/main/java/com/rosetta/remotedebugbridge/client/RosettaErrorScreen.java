/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.Util
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.AbstractSelectionList$Entry
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.ObjectSelectionList
 *  net.minecraft.client.gui.components.ObjectSelectionList$Entry
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.ClickEvent
 *  net.minecraft.network.chat.ClickEvent$Action
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.Style
 *  net.minecraft.util.FormattedCharSequence
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.api.distmarker.OnlyIn
 *  net.minecraftforge.fml.loading.FMLPaths
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package com.rosetta.remotedebugbridge.client;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.logging.ScriptError;
import com.rosetta.remotedebugbridge.logging.ScriptErrorCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@OnlyIn(value=Dist.CLIENT)
public class RosettaErrorScreen
extends Screen {
    private static final int ROW_HEIGHT = 52;
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 32;
    @Nullable
    private final Screen lastScreen;
    private final ScriptType scriptType;
    private final List<ScriptError> errors;
    private final List<ScriptError> warnings;
    private List<ScriptError> viewing;
    private ErrorList list;

    public RosettaErrorScreen(@Nullable Screen lastScreen, ScriptType type) {
        super((Component)Component.empty());
        this.lastScreen = lastScreen;
        this.scriptType = type;
        this.errors = new ArrayList<ScriptError>(ScriptErrorCollector.getErrors(type));
        this.warnings = new ArrayList<ScriptError>(ScriptErrorCollector.getWarnings(type));
        this.viewing = this.errors.isEmpty() && !this.warnings.isEmpty() ? this.warnings : this.errors;
    }

    protected void init() {
        super.init();
        int listBottom = this.height - 32;
        this.list = new ErrorList(this, this.minecraft, this.width, this.height, 32, listBottom, this.viewing);
        this.addWidget(this.list);
        int btnY = this.height - 32 + 6;
        int cx = this.width / 2;
        Button openLog = this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Open Log File"), b -> this.openLogFile()).bounds(cx - 155, btnY, 150, 20).build());
        openLog.active = Files.exists(RosettaLogger.logFile(this.scriptType), new LinkOption[0]);
        String closeLabel = this.scriptType == ScriptType.STARTUP ? "Quit Game" : "Close";
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)closeLabel), b -> this.quitOrClose()).bounds(cx + 5, btnY, 150, 20).build());
        String toggleLabel = this.viewing == this.errors ? "View Warnings [" + this.warnings.size() + "]" : "View Errors [" + this.errors.size() + "]";
        Button toggle = this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), b -> this.toggleView()).bounds(this.width - 110, 7, 103, 18).build());
        toggle.active = !this.errors.isEmpty() && !this.warnings.isEmpty();
    }

    public void render(@NotNull GuiGraphics g, int mx, int my, float delta) {
        this.renderBackground(g);
        this.list.render(g, mx, my, delta);
        boolean isError = this.viewing == this.errors;
        String title = "RosettaRemoteDebugBridge " + this.scriptType.getName() + " script " + (isError ? "errors" : "warnings");
        g.drawCenteredString(this.font, title, this.width / 2, 10, isError ? 0xFF5555 : 0xFFAA00);
        String countInfo = this.errors.size() + " error(s)   " + this.warnings.size() + " warning(s)";
        g.drawCenteredString(this.font, countInfo, this.width / 2, 20, 0xAAAAAA);
        super.render(g, mx, my, delta);
    }

    public boolean shouldCloseOnEsc() {
        return this.scriptType != ScriptType.STARTUP;
    }

    public void onClose() {
        assert (this.minecraft != null);
        this.minecraft.setScreen(this.lastScreen);
    }

    private void quitOrClose() {
        if (this.scriptType == ScriptType.STARTUP) {
            assert (this.minecraft != null);
            this.minecraft.stop();
        } else {
            this.onClose();
        }
    }

    private void openLogFile() {
        Path log = RosettaLogger.logFile(this.scriptType);
        this.handleComponentClicked(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, log.toAbsolutePath().toString())));
    }

    private void toggleView() {
        this.viewing = this.viewing == this.errors ? this.warnings : this.errors;
        this.rebuildWidgets();
    }

    @OnlyIn(value=Dist.CLIENT)
    public static class ErrorList
    extends ObjectSelectionList<ErrorEntry> {
        final RosettaErrorScreen screen;

        public ErrorList(RosettaErrorScreen screen, Minecraft mc, int width, int height, int y0, int y1, List<ScriptError> lines) {
            super(mc, width, height, y0, y1, 52);
            this.screen = screen;
            this.setRenderBackground(false);
            for (int i = 0; i < lines.size(); ++i) {
                this.addEntry(new ErrorEntry(this, mc, i, lines.get(i)));
            }
        }

        public int getRowWidth() {
            return (int)((double)this.width * 0.93);
        }

        protected int getScrollbarPosition() {
            return this.width - 6;
        }
    }

    @OnlyIn(value=Dist.CLIENT)
    public static class ErrorEntry
    extends ObjectSelectionList.Entry<ErrorEntry> {
        private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss");
        private final ErrorList parent;
        private final Minecraft mc;
        private final ScriptError line;
        private long lastClickMs;
        private final FormattedCharSequence idxText;
        private final FormattedCharSequence fileText;
        private final FormattedCharSequence timeText;
        private final List<FormattedCharSequence> msgText;
        private final List<FormattedCharSequence> traceText;

        public ErrorEntry(ErrorList parent, Minecraft mc, int index, ScriptError line) {
            this.parent = parent;
            this.mc = mc;
            this.line = line;
            this.idxText = Component.literal((String)("#" + (index + 1))).getVisualOrderText();
            this.fileText = Component.literal((String)(line.fileName + (String)(line.lineNumber > 0L ? ":" + line.lineNumber : ""))).getVisualOrderText();
            this.timeText = Component.literal((String)SDF.format(new Date(line.timestamp))).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText();
            ArrayList<FormattedCharSequence> msg = new ArrayList<FormattedCharSequence>(mc.font.split((FormattedText)Component.literal((String)(line.message != null ? line.message : "(no message)")), parent.getRowWidth() - 8));
            if (msg.size() > 3) {
                msg.subList(3, msg.size()).clear();
            }
            this.msgText = msg;
            this.traceText = !line.stackTrace.isEmpty() ? mc.font.split((FormattedText)Component.literal((String)String.join((CharSequence)"\n", line.stackTrace)).withStyle(ChatFormatting.GRAY), Integer.MAX_VALUE) : List.of();
        }

        @NotNull
        public Component getNarration() {
            return Component.empty();
        }

        public void render(@NotNull GuiGraphics g, int idx, int y, int x, int w, int h, int mx, int my, boolean hovered, float delta) {
            int col;
            int n = col = this.line.type == ScriptError.Type.ERROR ? 16735075 : 0xFFBB5B;
            if (hovered) {
                g.fill(x, y, x + w, y + h, 0x22FFFFFF);
            }
            g.drawString(this.mc.font, this.idxText, x + 2, y + 2, col);
            g.drawCenteredString(this.mc.font, this.fileText, x + w / 2, y + 2, 0xFFFFFF);
            int tsW = this.mc.font.width(this.timeText);
            g.drawString(this.mc.font, this.timeText, x + w - tsW - 4, y + 2, 0x666666);
            for (int i = 0; i < this.msgText.size(); ++i) {
                g.drawString(this.mc.font, this.msgText.get(i), x + 2, y + 14 + i * 10, col);
            }
            if (hovered && !this.traceText.isEmpty()) {
                List<FormattedCharSequence> shown = Screen.hasShiftDown() ? this.traceText : this.traceText.subList(0, Math.min(4, this.traceText.size()));
                this.parent.screen.setTooltipForNextRenderPass(shown);
            }
        }

        public boolean mouseClicked(double mx, double my, int btn) {
            this.parent.setSelected(this);
            long now = Util.getMillis();
            if (now - this.lastClickMs < 250L) {
                if (btn == 1) {
                    this.mc.keyboardHandler.setClipboard(String.join((CharSequence)"\n", this.line.stackTrace));
                } else {
                    this.openFile();
                }
                return true;
            }
            this.lastClickMs = now;
            return true;
        }

        private void openFile() {
            if (this.line.fileName == null || this.line.fileName.isBlank()) {
                return;
            }
            Path base = FMLPaths.GAMEDIR.get().resolve("RosettaRemoteDebugBridge").resolve(this.line.scriptType.getName()).resolve(this.line.fileName);
            if (!Files.exists(base, new LinkOption[0])) {
                return;
            }
            try {
                Desktop desk;
                Desktop desktop = desk = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
                if (desk != null && desk.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    desk.browseFileDirectory(base.toFile());
                    return;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.parent.screen.handleComponentClicked(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, base.toAbsolutePath().toString())));
        }
    }
}

