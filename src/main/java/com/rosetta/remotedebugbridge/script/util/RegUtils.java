/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.EntityType$Builder
 *  net.minecraft.world.item.BlockItem
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockBehaviour
 *  net.minecraft.world.level.block.state.BlockBehaviour$Properties
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.neoforge.registries.DeferredRegister
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.core.registries.Registries
 *  net.neoforged.neoforge.registries.DeferredHolder
 */
package com.rosetta.remotedebugbridge.script.util;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RegUtils {
    private static final Map<String, ModRegistries> MOD_REGISTRIES = new ConcurrentHashMap<String, ModRegistries>();

    public static void init(String modId, IEventBus modEventBus) {
        if (modId == null || !modId.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid mod id: " + modId);
        }
        if (modEventBus == null) {
            throw new IllegalArgumentException("modEventBus must not be null for mod '" + modId + "'");
        }
        MOD_REGISTRIES.computeIfAbsent(modId, id -> new ModRegistries((String)id, modEventBus));
    }

    private static ModRegistries getRegistries(String modId) {
        ModRegistries registries = MOD_REGISTRIES.get(modId);
        if (registries == null) {
            throw new IllegalStateException("Mod '" + modId + "' is not initialized! Call RegUtils.init(modId, eventBus) first.");
        }
        return registries;
    }

    public static DeferredRegister<Block> getBlockRegister(String modId) {
        return RegUtils.getRegistries((String)modId).blocks;
    }

    public static DeferredRegister<Item> getItemRegister(String modId) {
        return RegUtils.getRegistries((String)modId).items;
    }

    public static DeferredRegister<EntityType<?>> getEntityRegister(String modId) {
        return RegUtils.getRegistries((String)modId).entities;
    }

    public static <T> DeferredRegister<T> createRegister(String modId, Registry<T> registry, IEventBus eventBus) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        ModRegistries registries = RegUtils.getRegistries(modId);
        IEventBus bus = eventBus != null ? eventBus : registries.eventBus;
        if (bus == null) {
            throw new IllegalStateException("No IEventBus available for mod '" + modId + "'");
        }
        DeferredRegister<T> register = DeferredRegister.create(registry.key(), (String)modId);
        register.register(bus);
        ResourceLocation registryName = registry.key().location();
        registries.customRegisters.put(registryName != null ? registryName.toString() : registry.toString(), register);
        return register;
    }

    public static DeferredRegister<?> getCustomRegister(String modId, String registryName) {
        return RegUtils.getRegistries(modId).customRegisters.get(registryName);
    }

    public static <T> DeferredHolder<T, T> registerCustom(DeferredRegister<T> register, String id, Supplier<T> supplier) {
        return register.register(id, supplier);
    }

    public static <T> DeferredHolder<T, T> registerCustom(DeferredRegister<T> register, String id, T object) {
        return register.register(id, () -> object);
    }

    public static <T> DeferredHolder<T, T> registerCustom(DeferredRegister<T> register, String id, Class<? extends T> clazz) {
        return register.register(id, () -> {
            try {
                return clazz.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to create instance of " + clazz.getName(), e);
            }
        });
    }

    public static <T> DeferredHolder<T, T> registerCustom(DeferredRegister<T> register, String id, Class<? extends T> clazz, Object ... args) {
        return register.register(id, () -> {
            try {
                Constructor<? extends T> constructor = RegUtils.findMatchingConstructor(clazz, args);
                if (constructor == null) {
                    throw new NoSuchMethodException("No matching constructor found for " + clazz.getName());
                }
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to create instance of " + clazz.getName(), e);
            }
        });
    }

    public static DeferredHolder<Block, Block> registerCustomBlock(String modId, String id, Supplier<Block> supplier) {
        return RegUtils.getBlockRegister(modId).register(id, supplier);
    }

    public static DeferredHolder<Item, Item> registerCustomItem(String modId, String id, Supplier<Item> supplier) {
        return RegUtils.getItemRegister(modId).register(id, supplier);
    }

    public static DeferredHolder<EntityType<?>, EntityType<?>> registerCustomEntity(String modId, String id, Supplier<EntityType<?>> supplier) {
        return RegUtils.getEntityRegister(modId).register(id, supplier);
    }

    private static <T> Constructor<? extends T> findMatchingConstructor(Class<? extends T> clazz, Object ... args) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        Class[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; ++i) {
            argTypes[i] = args[i] != null ? args[i].getClass() : null;
        }
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (paramTypes.length != args.length) continue;
            boolean match = true;
            for (int i = 0; i < paramTypes.length; ++i) {
                if (argTypes[i] == null || RegUtils.isAssignable(paramTypes[i], argTypes[i])) continue;
                match = false;
                break;
            }
            if (!match) continue;
            return (Constructor<? extends T>)constructor;
        }
        return null;
    }

    private static boolean isAssignable(Class<?> target, Class<?> source) {
        if (target.isAssignableFrom(source)) {
            return true;
        }
        if (target.isPrimitive()) {
            if (target == Integer.TYPE && source == Integer.class) {
                return true;
            }
            if (target == Long.TYPE && source == Long.class) {
                return true;
            }
            if (target == Float.TYPE && source == Float.class) {
                return true;
            }
            if (target == Double.TYPE && source == Double.class) {
                return true;
            }
            if (target == Boolean.TYPE && source == Boolean.class) {
                return true;
            }
            if (target == Byte.TYPE && source == Byte.class) {
                return true;
            }
            if (target == Short.TYPE && source == Short.class) {
                return true;
            }
            if (target == Character.TYPE && source == Character.class) {
                return true;
            }
        }
        return false;
    }

    public static DeferredHolder<Block, Block> block(String modId, String name, BlockBehaviour.Properties properties) {
        return RegUtils.getRegistries((String)modId).blocks.register(name, () -> new Block(properties));
    }

    public static DeferredHolder<Block, Block> block(String modId, String name) {
        return RegUtils.block(modId, name, BlockBehaviour.Properties.ofFullCopy((Block)Blocks.STONE).strength(1.5f, 6.0f));
    }

    public static <T extends Block> DeferredHolder<Block, T> block(String modId, String name, Supplier<T> blockSupplier) {
        return RegUtils.getRegistries((String)modId).blocks.register(name, blockSupplier);
    }

    public static DeferredHolder<Block, Block> blockWithItem(String modId, String name, BlockBehaviour.Properties properties) {
        DeferredHolder<Block, Block> block = RegUtils.block(modId, name, properties);
        RegUtils.blockItem(modId, name, block);
        return block;
    }

    public static DeferredHolder<Block, Block> blockWithItem(String modId, String name) {
        DeferredHolder<Block, Block> block = RegUtils.block(modId, name);
        RegUtils.blockItem(modId, name, block);
        return block;
    }

    public static DeferredHolder<Item, Item> item(String modId, String name, Item.Properties properties) {
        return RegUtils.getRegistries((String)modId).items.register(name, () -> new Item(properties));
    }

    public static DeferredHolder<Item, Item> item(String modId, String name) {
        return RegUtils.item(modId, name, new Item.Properties());
    }

    public static <T extends Item> DeferredHolder<Item, T> item(String modId, String name, Supplier<T> itemSupplier) {
        return RegUtils.getRegistries((String)modId).items.register(name, itemSupplier);
    }

    public static DeferredHolder<Item, Item> blockItem(String modId, String name, DeferredHolder<Block, ? extends Block> block) {
        return RegUtils.getRegistries((String)modId).items.register(name, () -> new BlockItem((Block)block.get(), new Item.Properties()));
    }

    public static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> entity(String modId, String name, EntityType.Builder<T> builder) {
        return RegUtils.getRegistries((String)modId).entities.register(name, () -> builder.build(name));
    }

    public static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.ofFullCopy((Block)Blocks.STONE).strength(1.5f, 6.0f);
    }

    public static Item.Properties itemProps() {
        return new Item.Properties();
    }

    public static Item.Properties itemProps(int stackSize) {
        return new Item.Properties().stacksTo(stackSize);
    }

    public static BlockBehaviour.Properties copy(Block block) {
        return BlockBehaviour.Properties.ofFullCopy((Block)block);
    }

    public static String[] getRegisteredMods() {
        return MOD_REGISTRIES.keySet().toArray(new String[0]);
    }

    private static class ModRegistries {
        final DeferredRegister<Block> blocks;
        final DeferredRegister<Item> items;
        final DeferredRegister<EntityType<?>> entities;
        final Map<String, DeferredRegister<?>> customRegisters;
        final IEventBus eventBus;

        ModRegistries(String modId, IEventBus eventBus) {
            this.eventBus = eventBus;
            this.blocks = DeferredRegister.create(Registries.BLOCK, (String)modId);
            this.items = DeferredRegister.create(Registries.ITEM, (String)modId);
            this.entities = DeferredRegister.create(Registries.ENTITY_TYPE, (String)modId);
            this.customRegisters = new HashMap();
            this.blocks.register(eventBus);
            this.items.register(eventBus);
            this.entities.register(eventBus);
        }
    }
}
