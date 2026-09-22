package com.rosetta.probe;

import java.lang.reflect.Method;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Headless nether-portal self test: builds a portal near spawn, spawns a zombie above it,
 * then watches whether the entity arrives in another dimension (the nether). No player needed.
 *
 * IMPORTANT: with zero players online, Spigot puts entities into inactiveTick(), so vanilla
 * portal processing (baseTick -> handleNetherPortal) and movement (aiStep -> move ->
 * checkInsideBlocks -> entityInside) never run. Like the Rosetta smoke plugin, this test
 * drives the entity manually through reflection:
 *   m_6075_ = Entity#baseTick  (portal handling)
 *   m_8107_ = LivingEntity#aiStep (gravity/movement -> inside-portal detection)
 */
public final class NetherSelfTest {

    private static final int CHECKS = 16;
    private static final long CHECK_PERIOD_TICKS = 10L;

    private final Plugin plugin;
    private final ProbeLog log;
    private World overworld;
    private Zombie zombie;
    private Object handle;
    private BukkitTask driveTask;
    private BukkitTask checkTask;
    private int attempts;
    private int driven;
    private boolean reflectionWarned;

    public NetherSelfTest(Plugin plugin, ProbeLog log) {
        this.plugin = plugin;
        this.log = log;
    }

    public void start() {
        if (overworld != null) {
            log.warn("[NetherTest] already running");
            return;
        }
        overworld = plugin.getServer().getWorlds().get(0);
        Location spawn = overworld.getSpawnLocation();
        int x = spawn.getBlockX() + 24;
        int z = spawn.getBlockZ() + 24;
        int y = overworld.getHighestBlockYAt(x, z) + 1;

        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 5; dy++) {
                overworld.getBlockAt(x + dx, y + dy, z).setType(Material.AIR, false);
            }
        }
        for (int dx = 0; dx < 4; dx++) {
            overworld.getBlockAt(x + dx, y, z).setType(Material.OBSIDIAN, false);
            overworld.getBlockAt(x + dx, y + 4, z).setType(Material.OBSIDIAN, false);
        }
        for (int dy = 1; dy < 4; dy++) {
            overworld.getBlockAt(x, y + dy, z).setType(Material.OBSIDIAN, false);
            overworld.getBlockAt(x + 3, y + dy, z).setType(Material.OBSIDIAN, false);
        }
        Orientable portal = (Orientable) Material.NETHER_PORTAL.createBlockData();
        portal.setAxis(Axis.X);
        for (int dx = 1; dx < 3; dx++) {
            for (int dy = 1; dy < 4; dy++) {
                overworld.getBlockAt(x + dx, y + dy, z).setBlockData(portal, false);
            }
        }
        overworld.setChunkForceLoaded(x >> 4, z >> 4, true);
        // Spawn above the floor so gravity pulls the entity through the portal blocks.
        zombie = overworld.spawn(new Location(overworld, x + 1.5, y + 2.5, z + 0.5), Zombie.class);
        zombie.setPersistent(true);
        zombie.setAI(false);
        zombie.setSilent(true);
        attempts = 0;
        driven = 0;
        reflectionWarned = false;
        log.info("[NetherTest] portal built at " + x + "," + y + "," + z + " (axis=X); zombie="
                + zombie.getUniqueId() + " in world=" + overworld.getName()
                + " portalBlocks=" + overworld.getBlockAt(x + 1, y + 1, z).getType());
        driveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drive, 1L, 1L);
        checkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
    }

    private void drive() {
        if (zombie == null) {
            return;
        }
        try {
            if (handle == null) {
                Method getHandle = zombie.getClass().getMethod("getHandle");
                getHandle.setAccessible(true);
                handle = getHandle.invoke(zombie);
            }
            invokeNoArg(handle, "m_6075_"); // Entity#baseTick - handles the portal
            invokeNoArg(handle, "m_8107_"); // LivingEntity#aiStep - movement / inside detection
            driven++;
            if (driven == 40) {
                log.info("[NetherTest] manually driven 40 ticks (baseTick+aiStep)");
            }
        } catch (Throwable error) {
            if (!reflectionWarned) {
                reflectionWarned = true;
                log.warn("[NetherTest] reflective tick failed: " + error);
            }
        }
    }

    private void check() {
        attempts++;
        if (zombie == null || !zombie.isValid()) {
            finish("FAIL zombie removed before teleport");
            return;
        }
        World current = zombie.getWorld();
        if (current != null && !current.equals(overworld)) {
            finish("PASS teleported to " + current.getName() + " loc=" + format(zombie.getLocation())
                    + " drivenTicks=" + driven);
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            if (world.equals(overworld)) {
                continue;
            }
            Entity found = world.getEntity(zombie.getUniqueId());
            if (found != null) {
                finish("PASS entity found in " + world.getName() + " loc=" + format(found.getLocation())
                        + " drivenTicks=" + driven);
                return;
            }
        }
        if (attempts >= CHECKS) {
            finish("FAIL still in " + overworld.getName() + " loc=" + format(zombie.getLocation())
                    + " blockAtFeet=" + overworld.getBlockAt(zombie.getLocation()).getType()
                    + " drivenTicks=" + driven + " worlds=" + plugin.getServer().getWorlds().size());
        }
    }

    private void finish(String result) {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (driveTask != null) {
            driveTask.cancel();
            driveTask = null;
        }
        log.info("[NetherTest] " + result);
        if (zombie != null) {
            Zombie toRemove = zombie;
            plugin.getServer().getScheduler().runTaskLater(plugin, toRemove::remove, 40L);
        }
        zombie = null;
        handle = null;
        overworld = null;
    }

    private static void invokeNoArg(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                method.invoke(target);
                return;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name + " on " + target.getClass().getName());
    }

    private static String format(Location location) {
        return String.format("%s[%.1f,%.1f,%.1f]",
                location.getWorld() == null ? "?" : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
    }
}
