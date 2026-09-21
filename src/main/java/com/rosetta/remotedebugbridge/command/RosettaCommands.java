/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  net.minecraft.ChatFormatting
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.network.chat.ClickEvent
 *  net.minecraft.network.chat.ClickEvent$Action
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.item.ItemStack
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.event.RegisterCommandsEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.DistExecutor
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus
 *  net.minecraftforge.registries.ForgeRegistries
 */
package com.rosetta.remotedebugbridge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.client.RosettaClientEvents;
import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.logging.ScriptError;
import com.rosetta.remotedebugbridge.logging.ScriptErrorCollector;

@Mod.EventBusSubscriber(modid="rosetta_remote_debug_bridge", bus=Mod.EventBusSubscriber.Bus.FORGE)
public class RosettaCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher d = event.getDispatcher();
        d.register(RosettaCommands.buildRoot("java"));
        d.register(RosettaCommands.buildRoot("j"));
        RosettaRemoteDebugBridge.LOGGER.info("RosettaRemoteDebugBridge commands registered (/java, /j)");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String name) {
        return (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)name).requires(s -> s.hasPermission(2))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"reload").then(Commands.literal((String)"startup").executes(ctx -> RosettaCommands.reload((CommandContext<CommandSourceStack>)ctx, ScriptType.STARTUP)))).then(Commands.literal((String)"server").executes(ctx -> RosettaCommands.reload((CommandContext<CommandSourceStack>)ctx, ScriptType.SERVER)))).then(Commands.literal((String)"client").executes(ctx -> RosettaCommands.reload((CommandContext<CommandSourceStack>)ctx, ScriptType.CLIENT)))).executes(RosettaCommands::reloadAll))).then(((LiteralArgumentBuilder)Commands.literal((String)"hand").then(Commands.literal((String)"getId").executes(RosettaCommands::handGetId))).then(Commands.literal((String)"getClass").executes(RosettaCommands::handGetClass)))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"errors").then(Commands.literal((String)"startup").executes(ctx -> RosettaCommands.showErrors((CommandContext<CommandSourceStack>)ctx, ScriptType.STARTUP)))).then(Commands.literal((String)"server").executes(ctx -> RosettaCommands.showErrors((CommandContext<CommandSourceStack>)ctx, ScriptType.SERVER)))).then(Commands.literal((String)"client").executes(ctx -> RosettaCommands.showErrors((CommandContext<CommandSourceStack>)ctx, ScriptType.CLIENT)))).executes(RosettaCommands::showAllErrors));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, ScriptType type) {
        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
        RosettaCommands.feedback(src, ChatFormatting.YELLOW, "\u25b6 RosettaRemoteDebugBridge: Reloading " + type.getName() + " scripts...");
        try {
            ScriptErrorCollector.clear(type);
            DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> RosettaClientEvents.resetShownFlag());
            RosettaRemoteDebugBridge.getCore().reload(type);
            int errCount = ScriptErrorCollector.getErrors(type).size();
            int warnCount = ScriptErrorCollector.getWarnings(type).size();
            if (errCount == 0 && warnCount == 0) {
                RosettaCommands.feedback(src, ChatFormatting.GREEN, "\u2714 RosettaRemoteDebugBridge: " + type.getName() + " scripts reloaded successfully.");
            } else {
                RosettaCommands.feedback(src, ChatFormatting.RED, "\u2718 RosettaRemoteDebugBridge: " + type.getName() + " reload finished with " + errCount + " error(s) and " + warnCount + " warning(s).");
                MutableComponent logLink = Component.literal((String)" [Open Log]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, RosettaLogger.logFile(type).toAbsolutePath().toString())).withColor(ChatFormatting.AQUA).withUnderlined(Boolean.valueOf(true)));
                MutableComponent screenLink = Component.literal((String)" [View Error Screen]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/java errors " + type.getName())).withColor(ChatFormatting.RED).withUnderlined(Boolean.valueOf(true)));
                src.sendSuccess(() -> Component.literal((String)"").append((Component)logLink).append((Component)screenLink), false);
            }
        }
        catch (Exception e) {
            RosettaCommands.feedback(src, ChatFormatting.RED, "\u2718 RosettaRemoteDebugBridge: Reload failed - " + e.getMessage());
            RosettaRemoteDebugBridge.LOGGER.error("Reload failed for {}", (Object)type, (Object)e);
        }
        return 1;
    }

    private static int reloadAll(CommandContext<CommandSourceStack> ctx) {
        RosettaCommands.feedback((CommandSourceStack)ctx.getSource(), ChatFormatting.YELLOW, "\u25b6 RosettaRemoteDebugBridge: Reloading all scripts...");
        for (ScriptType type : ScriptType.values()) {
            RosettaCommands.reload(ctx, type);
        }
        return 1;
    }

    private static int handGetId(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                RosettaCommands.feedback(src, ChatFormatting.RED, "You are not holding any item.");
                return 0;
            }
            String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            MutableComponent label = Component.literal((String)"Item ID: ").withStyle(ChatFormatting.GRAY);
            MutableComponent value = Component.literal((String)id).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id)).withUnderlined(Boolean.valueOf(true)));
            MutableComponent hint = Component.literal((String)" (click to copy)").withStyle(ChatFormatting.DARK_GRAY);
            src.sendSuccess(() -> label.append((Component)value).append((Component)hint), false);
        }
        catch (Exception e) {
            RosettaCommands.feedback(src, ChatFormatting.RED, "Error: " + e.getMessage());
        }
        return 1;
    }

    private static int handGetClass(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                RosettaCommands.feedback(src, ChatFormatting.RED, "You are not holding any item.");
                return 0;
            }
            String className = stack.getItem().getClass().getName();
            MutableComponent label = Component.literal((String)"Item class: ").withStyle(ChatFormatting.GRAY);
            MutableComponent value = Component.literal((String)className).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, className)).withUnderlined(Boolean.valueOf(true)));
            MutableComponent hint = Component.literal((String)" (click to copy)").withStyle(ChatFormatting.DARK_GRAY);
            src.sendSuccess(() -> label.append((Component)value).append((Component)hint), false);
            String stackClass = stack.getClass().getName();
            if (!stackClass.equals(className)) {
                MutableComponent extra = Component.literal((String)("ItemStack class: " + stackClass)).withStyle(ChatFormatting.DARK_GRAY);
                src.sendSuccess(() -> extra, false);
            }
        }
        catch (Exception e) {
            RosettaCommands.feedback(src, ChatFormatting.RED, "Error: " + e.getMessage());
        }
        return 1;
    }

    private static int showErrors(CommandContext<CommandSourceStack> ctx, ScriptType type) {
        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
        int errCount = ScriptErrorCollector.getErrors(type).size();
        int warnCount = ScriptErrorCollector.getWarnings(type).size();
        if (errCount == 0 && warnCount == 0) {
            RosettaCommands.feedback(src, ChatFormatting.GREEN, "\u2714 RosettaRemoteDebugBridge " + type.getName() + ": No errors or warnings.");
        } else {
            RosettaCommands.feedback(src, errCount > 0 ? ChatFormatting.RED : ChatFormatting.YELLOW, "RosettaRemoteDebugBridge " + type.getName() + ": " + errCount + " error(s), " + warnCount + " warning(s).");
            List<ScriptError> errorList = ScriptErrorCollector.getErrors(type);
            int shown = Math.min(5, errorList.size());
            for (int i = 0; i < shown; ++i) {
                ScriptError e = errorList.get(i);
                String line = "  [" + (i + 1) + "] " + e.fileName + (String)(e.lineNumber > 0L ? ":" + e.lineNumber : "") + " - " + e.message;
                RosettaCommands.feedback(src, ChatFormatting.RED, line);
            }
            if (errorList.size() > 5) {
                RosettaCommands.feedback(src, ChatFormatting.GRAY, "  ... and " + (errorList.size() - 5) + " more. Check the log file.");
            }
            String logPath = RosettaLogger.logFile(type).toAbsolutePath().toString();
            MutableComponent logLink = Component.literal((String)("  \u25ba Open " + type.getName() + ".log")).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, logPath)).withColor(ChatFormatting.AQUA).withUnderlined(Boolean.valueOf(true)));
            src.sendSuccess(() -> logLink, false);
        }
        return 1;
    }

    private static int showAllErrors(CommandContext<CommandSourceStack> ctx) {
        for (ScriptType type : ScriptType.values()) {
            RosettaCommands.showErrors(ctx, type);
        }
        return 1;
    }

    private static void feedback(CommandSourceStack src, ChatFormatting color, String text) {
        MutableComponent msg = Component.literal((String)text).withStyle(color);
        if (color == ChatFormatting.RED) {
            src.sendFailure((Component)msg);
        } else {
            src.sendSuccess(() -> msg, false);
        }
    }
}

