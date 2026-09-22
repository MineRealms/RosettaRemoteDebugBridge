package com.rosetta.probe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PortalProbePlugin extends JavaPlugin {

    private ProbeLog probeLog;
    private BukkitProbeListener bukkitListener;
    private ForgeProbeListener forgeListener;
    private PortalWatch watch;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            probeLog = new ProbeLog(this);
        } catch (IOException error) {
            getLogger().severe("cannot open portal-probe.log: " + error);
            setEnabled(false);
            return;
        }
        boolean bukkitEvents = getConfig().getBoolean("bukkit-events", true);
        boolean forgeEvents = getConfig().getBoolean("forge-events", true);
        boolean logAllTeleports = getConfig().getBoolean("log-all-teleports", false);
        boolean watchEnabled = getConfig().getBoolean("watch-enabled", true);
        int interval = getConfig().getInt("watch-interval-ticks", 10);
        int silent = getConfig().getInt("silent-failure-ticks", 100);
        int preAfter = getConfig().getInt("precheck-after-ticks", 40);
        int preEvery = getConfig().getInt("precheck-every-ticks", 100);

        watch = new PortalWatch(this, probeLog, interval, silent, preAfter, preEvery);
        bukkitListener = new BukkitProbeListener(probeLog, watch, logAllTeleports);
        getServer().getPluginManager().registerEvents(bukkitListener, this);
        if (watchEnabled) {
            watch.start();
        }
        if (forgeEvents) {
            forgeListener = new ForgeProbeListener(probeLog);
        }
        probeLog.info("[PortalProbe] enabled v" + getDescription().getVersion()
                + " bukkit=" + bukkitEvents + " forge=" + forgeEvents + " watch=" + watchEnabled
                + " (interval=" + interval + "t silent=" + silent + "t) logAllTeleports=" + logAllTeleports);
        probeLog.info("[PortalProbe] log file: " + probeLog.filePath());
        if (getConfig().getBoolean("auto-nether-test", false)) {
            getServer().getScheduler().runTaskLater(this, () -> new NetherSelfTest(this, probeLog).start(), 100L);
        }
    }

    @Override
    public void onDisable() {
        if (watch != null) {
            watch.stop();
            watch = null;
        }
        if (forgeListener != null) {
            forgeListener.unregister();
            forgeListener = null;
        }
        if (probeLog != null) {
            probeLog.info("[PortalProbe] disabled");
            probeLog.close();
            probeLog = null;
        }
        bukkitListener = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (probeLog == null) {
            sender.sendMessage(ChatColor.RED + "[PortalProbe] plugin not active");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "status":
                status(sender);
                return true;
            case "listeners":
                send(sender, PortalDiagnostics.listeners());
                return true;
            case "check": {
                Player player = resolvePlayer(sender, args, 1);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "[PortalProbe] usage: /portalprobe check <player>");
                    return true;
                }
                check(sender, player);
                return true;
            }
            case "diagnose": {
                Player player = resolvePlayer(sender, args, 1);
                status(sender);
                send(sender, PortalDiagnostics.listeners());
                if (player != null) {
                    check(sender, player);
                }
                return true;
            }
            case "scan": {
                int portals = 0;
                for (Player player : getServer().getOnlinePlayers()) {
                    if (PortalDiagnostics.inPortalBlock(player) || "true".equals(PortalDiagnostics.insidePortal(player))) {
                        portals++;
                        sender.sendMessage(ChatColor.YELLOW + "  - " + player.getName() + " " + PortalDiagnostics.snapshot(player));
                    }
                }
                sender.sendMessage(ChatColor.GREEN + "[PortalProbe] players currently in a portal: " + portals);
                return true;
            }
            case "trace": {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "[PortalProbe] usage: /portalprobe trace <player> [seconds]");
                    return true;
                }
                Player player = getServer().getPlayerExact(args[1]);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "[PortalProbe] player not found: " + args[1]);
                    return true;
                }
                int seconds = getConfig().getInt("trace-seconds", 15);
                if (args.length >= 3) {
                    try {
                        seconds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                seconds = Math.max(1, Math.min(300, seconds));
                watch.trace(player, seconds);
                sender.sendMessage(ChatColor.GREEN + "[PortalProbe] tracing " + player.getName() + " for " + seconds
                        + "s -> plugins/PortalProbe/portal-probe.log");
                return true;
            }
            case "nethertest":
                new NetherSelfTest(this, probeLog).start();
                sender.sendMessage(ChatColor.GREEN + "[PortalProbe] headless nether self-test started (see log)");
                return true;
            case "clearlog":
                probeLog.clear();
                sender.sendMessage(ChatColor.GREEN + "[PortalProbe] log cleared: " + probeLog.filePath());
                return true;
            case "reload":
                reloadConfig();
                sender.sendMessage(ChatColor.YELLOW + "[PortalProbe] config.yml reloaded. Real settings change requires"
                        + " a full plugin reload: /plugin reload PortalProbe");
                return true;
            case "help":
            default:
                send(sender, "PortalProbe commands:"
                        + "\n  /portalprobe status - config + worlds + who is standing in a portal"
                        + "\n  /portalprobe listeners - every plugin listening to portal/teleport events"
                        + "\n  /portalprobe check [player] - snapshot + read-only destination precheck"
                        + "\n  /portalprobe diagnose [player] - status + listeners + check"
                        + "\n  /portalprobe trace <player> [seconds] - per-tick trace of a player"
                        + "\n  /portalprobe scan - list players in portals right now"
                        + "\n  /portalprobe clearlog | reload | nethertest"
                        + "\n  log: plugins/PortalProbe/portal-probe.log");
                return true;
        }
    }

    private void status(CommandSender sender) {
        send(sender, "PortalProbe v" + getDescription().getVersion()
                + " log=" + probeLog.filePath()
                + "\n  bukkit-events=" + getConfig().getBoolean("bukkit-events", true)
                + " forge-events=" + getConfig().getBoolean("forge-events", true)
                + " watch-enabled=" + getConfig().getBoolean("watch-enabled", true)
                + " log-all-teleports=" + getConfig().getBoolean("log-all-teleports", false)
                + "\n  watch: interval=" + getConfig().getInt("watch-interval-ticks", 10) + "t"
                + " silent-failure=" + getConfig().getInt("silent-failure-ticks", 100) + "t"
                + " precheck-after=" + getConfig().getInt("precheck-after-ticks", 40) + "t"
                + " tracked=" + watch.trackedPlayers()
                + "\n  events seen: PlayerPortalEvent listeners=" + PortalDiagnostics.listenerCount(
                        org.bukkit.event.player.PlayerPortalEvent.getHandlerList())
                + " PlayerTeleportEvent listeners=" + PortalDiagnostics.listenerCount(
                        org.bukkit.event.player.PlayerTeleportEvent.getHandlerList()));
        send(sender, "  worlds: " + PortalDiagnostics.worldSummary());
    }

    private void check(CommandSender sender, Player player) {
        sender.sendMessage(ChatColor.AQUA + "[PortalProbe] " + player.getName() + " state: "
                + PortalDiagnostics.snapshot(player));
        send(sender, "  " + PortalDiagnostics.precheck(player));
        send(sender, "  " + PortalDiagnostics.silentFailureHint(false, false));
    }

    private Player resolvePlayer(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            return getServer().getPlayerExact(args[index]);
        }
        return sender instanceof Player ? (Player) sender : null;
    }

    private static void send(CommandSender sender, String block) {
        for (String line : block.split("\n")) {
            sender.sendMessage(ChatColor.GRAY + line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList(
                    "status", "listeners", "check", "diagnose", "trace", "scan", "nethertest", "clearlog", "reload", "help"));
            options.removeIf(option -> !option.startsWith(args[0].toLowerCase()));
            return options;
        }
        if (args.length == 2 && Arrays.asList("check", "diagnose", "trace").contains(args[0].toLowerCase())) {
            return null;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trace")) {
            return Arrays.asList("5", "10", "15", "30", "60");
        }
        return Collections.emptyList();
    }
}
