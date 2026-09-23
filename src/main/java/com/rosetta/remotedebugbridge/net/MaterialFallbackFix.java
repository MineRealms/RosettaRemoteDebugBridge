package com.rosetta.remotedebugbridge.net;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Experimental Bukkit compatibility fix (config gated, default on).
 *
 * Some mods (e.g. VanillaBackport) register items under the "minecraft:" namespace that do not
 * exist in the 1.20.1 Bukkit Material enum. Mohist's CraftMagicNumbers maps them to null, and any
 * InventoryClickEvent on such an item crashes inside Mohist's built-in inventory listener
 * (IllegalArgumentException: Material cannot be null), silently dropping the click.
 *
 * Instead of registering new Material enum constants (which invalidates runtime switch-map caches
 * and causes ArrayIndexOutOfBoundsException elsewhere), this fix maps affected items to safe
 * existing materials: BlockItems -> STONE, all other items -> PAPER. The client still renders and
 * uses the original item; only Bukkit-facing APIs see the placeholder material.
 *
 * Runs at ServerStarted (Bukkit fully initialized, before players interact).
 */
public final class MaterialFallbackFix {

    private static final Logger LOGGER = LogManager.getLogger("RosettaNexus/Compat");

    private static boolean applied;

    private MaterialFallbackFix() {
    }

    public static synchronized void applyIfEnabled() {
        if (applied) {
            return;
        }
        applied = true;
        if (!configEnabled()) {
            LOGGER.info("[compat] material fallback disabled by config");
            return;
        }
        try {
            Class<?> itemClass = Class.forName("net.minecraft.world.item.Item");
            Class<?> blockItemClass = Class.forName("net.minecraft.world.item.BlockItem");
            Class<?> craftMagicNumbers = Class.forName("org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers");
            Field itemMaterialField = craftMagicNumbers.getDeclaredField("ITEM_MATERIAL");
            itemMaterialField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Object, Object> itemMaterial = (Map<Object, Object>) itemMaterialField.get(null);

            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum> materialEnum = (Class<? extends Enum>) Class.forName("org.bukkit.Material");
            Object paper = Enum.valueOf(materialEnum, "PAPER");
            Object stone = Enum.valueOf(materialEnum, "STONE");

            int remapped = 0;
            for (Map.Entry<Object, Object> entry : itemMaterial.entrySet()) {
                Object value = entry.getValue();
                boolean affected = value == null || String.valueOf(value).startsWith("ROSETTA_");
                if (!affected || !itemClass.isInstance(entry.getKey())) {
                    continue;
                }
                entry.setValue(blockItemClass.isInstance(entry.getKey()) ? stone : paper);
                remapped++;
            }
            LOGGER.info("[compat] material fallback applied: remapped={}", remapped);
        } catch (Throwable error) {
            LOGGER.warn("[compat] material fallback failed: {}", error.toString());
        }
    }

    private static boolean configEnabled() {
        try {
            Path config = Path.of("config", "rosetta_nexus.properties");
            Properties properties = new Properties();
            if (Files.exists(config)) {
                try (InputStream in = Files.newInputStream(config)) {
                    properties.load(in);
                }
            } else {
                Files.createDirectories(config.getParent());
                Files.writeString(config, ""
                        + "# RosettaNexus experimental features\n"
                        + "# Maps items without a Bukkit Material mapping (e.g. VanillaBackport items\n"
                        + "# registered under the minecraft: namespace) to placeholder materials\n"
                        + "# (blocks -> STONE, items -> PAPER) so clicking them does not break Mohist's\n"
                        + "# inventory listener. Client rendering is unaffected.\n"
                        + "experimental.bukkit.material_fallback=true\n", StandardCharsets.UTF_8);
            }
            return Boolean.parseBoolean(properties.getProperty("experimental.bukkit.material_fallback", "true"));
        } catch (Throwable error) {
            return true;
        }
    }
}
