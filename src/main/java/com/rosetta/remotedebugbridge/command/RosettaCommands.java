package com.rosetta.remotedebugbridge.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.logging.ScriptError;
import com.rosetta.remotedebugbridge.logging.ScriptErrorCollector;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /java and /j. Registered through the Fabric command callback (fired for both the
 * integrated server and a dedicated server).
 */
public final class RosettaCommands {

    private RosettaCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildRoot("java"));
            dispatcher.register(buildRoot("j"));
            RosettaRemoteDebugBridge.LOGGER.info("RosettaRemoteDebugBridge commands registered (/java, /j)");
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .then(Commands.literal("startup").executes(ctx -> reload(ctx, ScriptType.STARTUP)))
                        .then(Commands.literal("server").executes(ctx -> reload(ctx, ScriptType.SERVER)))
                        .then(Commands.literal("client").executes(ctx -> reload(ctx, ScriptType.CLIENT)))
                        .executes(RosettaCommands::reloadAll))
                .then(Commands.literal("hand")
                        .then(Commands.literal("getId").executes(RosettaCommands::handGetId))
                        .then(Commands.literal("getClass").executes(RosettaCommands::handGetClass)))
                .then(Commands.literal("errors")
                        .then(Commands.literal("startup").executes(ctx -> showErrors(ctx, ScriptType.STARTUP)))
                        .then(Commands.literal("server").executes(ctx -> showErrors(ctx, ScriptType.SERVER)))
                        .then(Commands.literal("client").executes(ctx -> showErrors(ctx, ScriptType.CLIENT)))
                        .executes(RosettaCommands::showAllErrors));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, ScriptType type) {
        CommandSourceStack src = ctx.getSource();
        feedback(src, ChatFormatting.YELLOW, "\u25b6 RosettaRemoteDebugBridge: Reloading " + type.getName() + " scripts...");
        try {
            ScriptErrorCollector.clear(type);
            RosettaRemoteDebugBridge.getCore().reload(type);
            int errCount = ScriptErrorCollector.getErrors(type).size();
            int warnCount = ScriptErrorCollector.getWarnings(type).size();
            if (errCount == 0 && warnCount == 0) {
                feedback(src, ChatFormatting.GREEN, "\u2714 RosettaRemoteDebugBridge: " + type.getName() + " scripts reloaded successfully.");
            } else {
                feedback(src, ChatFormatting.RED, "\u2718 RosettaRemoteDebugBridge: " + type.getName() + " reload finished with "
                        + errCount + " error(s) and " + warnCount + " warning(s).");
                MutableComponent logLink = Component.literal(" [Open Log]").setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, RosettaLogger.logFile(type).toAbsolutePath().toString()))
                        .withColor(ChatFormatting.AQUA).withUnderlined(true));
                src.sendSuccess(() -> logLink, false);
            }
        } catch (Exception error) {
            feedback(src, ChatFormatting.RED, "\u2718 RosettaRemoteDebugBridge: Reload failed - " + error.getMessage());
            RosettaRemoteDebugBridge.LOGGER.error("Reload failed for {}", type, error);
        }
        return 1;
    }

    private static int reloadAll(CommandContext<CommandSourceStack> ctx) {
        feedback(ctx.getSource(), ChatFormatting.YELLOW, "\u25b6 RosettaRemoteDebugBridge: Reloading all scripts...");
        for (ScriptType type : ScriptType.values()) {
            reload(ctx, type);
        }
        return 1;
    }

    private static int handGetId(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                feedback(src, ChatFormatting.RED, "You are not holding any item.");
                return 0;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            MutableComponent label = Component.literal("Item ID: ").withStyle(ChatFormatting.GRAY);
            MutableComponent value = Component.literal(id).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id)).withUnderlined(true));
            MutableComponent hint = Component.literal(" (click to copy)").withStyle(ChatFormatting.DARK_GRAY);
            src.sendSuccess(() -> label.append(value).append(hint), false);
        } catch (Exception error) {
            feedback(src, ChatFormatting.RED, "Error: " + error.getMessage());
        }
        return 1;
    }

    private static int handGetClass(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                feedback(src, ChatFormatting.RED, "You are not holding any item.");
                return 0;
            }
            String className = stack.getItem().getClass().getName();
            MutableComponent label = Component.literal("Item class: ").withStyle(ChatFormatting.GRAY);
            MutableComponent value = Component.literal(className).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, className)).withUnderlined(true));
            MutableComponent hint = Component.literal(" (click to copy)").withStyle(ChatFormatting.DARK_GRAY);
            src.sendSuccess(() -> label.append(value).append(hint), false);
        } catch (Exception error) {
            feedback(src, ChatFormatting.RED, "Error: " + error.getMessage());
        }
        return 1;
    }

    private static int showErrors(CommandContext<CommandSourceStack> ctx, ScriptType type) {
        CommandSourceStack src = ctx.getSource();
        int errCount = ScriptErrorCollector.getErrors(type).size();
        int warnCount = ScriptErrorCollector.getWarnings(type).size();
        if (errCount == 0 && warnCount == 0) {
            feedback(src, ChatFormatting.GREEN, "\u2714 RosettaRemoteDebugBridge " + type.getName() + ": No errors or warnings.");
        } else {
            feedback(src, errCount > 0 ? ChatFormatting.RED : ChatFormatting.YELLOW,
                    "RosettaRemoteDebugBridge " + type.getName() + ": " + errCount + " error(s), " + warnCount + " warning(s).");
            List<ScriptError> errors = ScriptErrorCollector.getErrors(type);
            int shown = Math.min(5, errors.size());
            for (int i = 0; i < shown; i++) {
                ScriptError error = errors.get(i);
                feedback(src, ChatFormatting.RED, "  [" + (i + 1) + "] " + error.fileName
                        + (error.lineNumber > 0L ? ":" + error.lineNumber : "") + " - " + error.message);
            }
            if (errors.size() > 5) {
                feedback(src, ChatFormatting.GRAY, "  ... and " + (errors.size() - 5) + " more. Check the log file.");
            }
            String logPath = RosettaLogger.logFile(type).toAbsolutePath().toString();
            MutableComponent logLink = Component.literal("  \u25ba Open " + type.getName() + ".log").setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, logPath))
                    .withColor(ChatFormatting.AQUA).withUnderlined(true));
            src.sendSuccess(() -> logLink, false);
        }
        return 1;
    }

    private static int showAllErrors(CommandContext<CommandSourceStack> ctx) {
        for (ScriptType type : ScriptType.values()) {
            showErrors(ctx, type);
        }
        return 1;
    }

    private static void feedback(CommandSourceStack src, ChatFormatting color, String text) {
        MutableComponent message = Component.literal(text).withStyle(color);
        if (color == ChatFormatting.RED) {
            src.sendFailure(message);
        } else {
            src.sendSuccess(() -> message, false);
        }
    }
}
