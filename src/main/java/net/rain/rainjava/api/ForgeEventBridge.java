/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.event.AddPackFindersEvent
 *  net.minecraftforge.event.AddReloadListenerEvent
 *  net.minecraftforge.event.AnvilUpdateEvent
 *  net.minecraftforge.event.AttachCapabilitiesEvent
 *  net.minecraftforge.event.BuildCreativeModeTabContentsEvent
 *  net.minecraftforge.event.CommandEvent
 *  net.minecraftforge.event.DifficultyChangeEvent
 *  net.minecraftforge.event.GameShuttingDownEvent
 *  net.minecraftforge.event.GrindstoneEvent
 *  net.minecraftforge.event.GrindstoneEvent$OnPlaceItem
 *  net.minecraftforge.event.GrindstoneEvent$OnTakeItem
 *  net.minecraftforge.event.ItemAttributeModifierEvent
 *  net.minecraftforge.event.ItemStackedOnOtherEvent
 *  net.minecraftforge.event.LootTableLoadEvent
 *  net.minecraftforge.event.ModMismatchEvent
 *  net.minecraftforge.event.OnDatapackSyncEvent
 *  net.minecraftforge.event.PlayLevelSoundEvent
 *  net.minecraftforge.event.PlayLevelSoundEvent$AtEntity
 *  net.minecraftforge.event.PlayLevelSoundEvent$AtPosition
 *  net.minecraftforge.event.RegisterCommandsEvent
 *  net.minecraftforge.event.RegisterGameTestsEvent
 *  net.minecraftforge.event.RegisterStructureConversionsEvent
 *  net.minecraftforge.event.ServerChatEvent
 *  net.minecraftforge.event.TagsUpdatedEvent
 *  net.minecraftforge.event.TickEvent$ClientTickEvent
 *  net.minecraftforge.event.TickEvent$LevelTickEvent
 *  net.minecraftforge.event.TickEvent$PlayerTickEvent
 *  net.minecraftforge.event.TickEvent$RenderTickEvent
 *  net.minecraftforge.event.TickEvent$ServerTickEvent
 *  net.minecraftforge.event.VanillaGameEvent
 *  net.minecraftforge.event.brewing.PlayerBrewedPotionEvent
 *  net.minecraftforge.event.brewing.PotionBrewEvent$Post
 *  net.minecraftforge.event.brewing.PotionBrewEvent$Pre
 *  net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent
 *  net.minecraftforge.event.entity.EntityAttributeCreationEvent
 *  net.minecraftforge.event.entity.EntityAttributeModificationEvent
 *  net.minecraftforge.event.entity.EntityEvent$EnteringSection
 *  net.minecraftforge.event.entity.EntityEvent$EntityConstructing
 *  net.minecraftforge.event.entity.EntityEvent$Size
 *  net.minecraftforge.event.entity.EntityJoinLevelEvent
 *  net.minecraftforge.event.entity.EntityLeaveLevelEvent
 *  net.minecraftforge.event.entity.EntityMobGriefingEvent
 *  net.minecraftforge.event.entity.EntityMountEvent
 *  net.minecraftforge.event.entity.EntityStruckByLightningEvent
 *  net.minecraftforge.event.entity.EntityTeleportEvent$ChorusFruit
 *  net.minecraftforge.event.entity.EntityTeleportEvent$EnderEntity
 *  net.minecraftforge.event.entity.EntityTeleportEvent$EnderPearl
 *  net.minecraftforge.event.entity.EntityTeleportEvent$SpreadPlayersCommand
 *  net.minecraftforge.event.entity.EntityTeleportEvent$TeleportCommand
 *  net.minecraftforge.event.entity.EntityTravelToDimensionEvent
 *  net.minecraftforge.event.entity.ProjectileImpactEvent
 *  net.minecraftforge.event.entity.SpawnPlacementRegisterEvent
 *  net.minecraftforge.event.entity.item.ItemExpireEvent
 *  net.minecraftforge.event.entity.item.ItemTossEvent
 *  net.minecraftforge.event.entity.living.AnimalTameEvent
 *  net.minecraftforge.event.entity.living.BabyEntitySpawnEvent
 *  net.minecraftforge.event.entity.living.EnderManAngerEvent
 *  net.minecraftforge.event.entity.living.LivingAttackEvent
 *  net.minecraftforge.event.entity.living.LivingBreatheEvent
 *  net.minecraftforge.event.entity.living.LivingChangeTargetEvent
 *  net.minecraftforge.event.entity.living.LivingConversionEvent$Post
 *  net.minecraftforge.event.entity.living.LivingConversionEvent$Pre
 *  net.minecraftforge.event.entity.living.LivingDamageEvent
 *  net.minecraftforge.event.entity.living.LivingDeathEvent
 *  net.minecraftforge.event.entity.living.LivingDestroyBlockEvent
 *  net.minecraftforge.event.entity.living.LivingDropsEvent
 *  net.minecraftforge.event.entity.living.LivingDrownEvent
 *  net.minecraftforge.event.entity.living.LivingEntityUseItemEvent$Finish
 *  net.minecraftforge.event.entity.living.LivingEntityUseItemEvent$Start
 *  net.minecraftforge.event.entity.living.LivingEntityUseItemEvent$Stop
 *  net.minecraftforge.event.entity.living.LivingEntityUseItemEvent$Tick
 *  net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent
 *  net.minecraftforge.event.entity.living.LivingEvent$LivingJumpEvent
 *  net.minecraftforge.event.entity.living.LivingEvent$LivingTickEvent
 *  net.minecraftforge.event.entity.living.LivingEvent$LivingVisibilityEvent
 *  net.minecraftforge.event.entity.living.LivingExperienceDropEvent
 *  net.minecraftforge.event.entity.living.LivingFallEvent
 *  net.minecraftforge.event.entity.living.LivingGetProjectileEvent
 *  net.minecraftforge.event.entity.living.LivingHealEvent
 *  net.minecraftforge.event.entity.living.LivingHurtEvent
 *  net.minecraftforge.event.entity.living.LivingKnockBackEvent
 *  net.minecraftforge.event.entity.living.LivingMakeBrainEvent
 *  net.minecraftforge.event.entity.living.LivingPackSizeEvent
 *  net.minecraftforge.event.entity.living.LivingSwapItemsEvent
 *  net.minecraftforge.event.entity.living.LivingSwapItemsEvent$Hands
 *  net.minecraftforge.event.entity.living.LivingUseTotemEvent
 *  net.minecraftforge.event.entity.living.LootingLevelEvent
 *  net.minecraftforge.event.entity.living.MobEffectEvent$Added
 *  net.minecraftforge.event.entity.living.MobEffectEvent$Applicable
 *  net.minecraftforge.event.entity.living.MobEffectEvent$Expired
 *  net.minecraftforge.event.entity.living.MobEffectEvent$Remove
 *  net.minecraftforge.event.entity.living.MobSpawnEvent$AllowDespawn
 *  net.minecraftforge.event.entity.living.MobSpawnEvent$FinalizeSpawn
 *  net.minecraftforge.event.entity.living.MobSpawnEvent$PositionCheck
 *  net.minecraftforge.event.entity.living.MobSpawnEvent$SpawnPlacementCheck
 *  net.minecraftforge.event.entity.living.PotionColorCalculationEvent
 *  net.minecraftforge.event.entity.living.ShieldBlockEvent
 *  net.minecraftforge.event.entity.living.ZombieEvent$SummonAidEvent
 *  net.minecraftforge.event.entity.player.AdvancementEvent$AdvancementEarnEvent
 *  net.minecraftforge.event.entity.player.AdvancementEvent$AdvancementProgressEvent
 *  net.minecraftforge.event.entity.player.AnvilRepairEvent
 *  net.minecraftforge.event.entity.player.ArrowLooseEvent
 *  net.minecraftforge.event.entity.player.ArrowNockEvent
 *  net.minecraftforge.event.entity.player.AttackEntityEvent
 *  net.minecraftforge.event.entity.player.BonemealEvent
 *  net.minecraftforge.event.entity.player.CriticalHitEvent
 *  net.minecraftforge.event.entity.player.EntityItemPickupEvent
 *  net.minecraftforge.event.entity.player.FillBucketEvent
 *  net.minecraftforge.event.entity.player.ItemFishedEvent
 *  net.minecraftforge.event.entity.player.ItemTooltipEvent
 *  net.minecraftforge.event.entity.player.PermissionsChangedEvent
 *  net.minecraftforge.event.entity.player.PlayerContainerEvent$Close
 *  net.minecraftforge.event.entity.player.PlayerContainerEvent$Open
 *  net.minecraftforge.event.entity.player.PlayerDestroyItemEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$BreakSpeed
 *  net.minecraftforge.event.entity.player.PlayerEvent$Clone
 *  net.minecraftforge.event.entity.player.PlayerEvent$HarvestCheck
 *  net.minecraftforge.event.entity.player.PlayerEvent$ItemCraftedEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$ItemPickupEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$ItemSmeltedEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$LoadFromFile
 *  net.minecraftforge.event.entity.player.PlayerEvent$NameFormat
 *  net.minecraftforge.event.entity.player.PlayerEvent$PlayerChangeGameModeEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$PlayerChangedDimensionEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$PlayerRespawnEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$SaveToFile
 *  net.minecraftforge.event.entity.player.PlayerEvent$StartTracking
 *  net.minecraftforge.event.entity.player.PlayerEvent$StopTracking
 *  net.minecraftforge.event.entity.player.PlayerEvent$TabListNameFormat
 *  net.minecraftforge.event.entity.player.PlayerFlyableFallEvent
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$EntityInteract
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$LeftClickBlock
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$LeftClickEmpty
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickBlock
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickEmpty
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickItem
 *  net.minecraftforge.event.entity.player.PlayerNegotiationEvent
 *  net.minecraftforge.event.entity.player.PlayerSetSpawnEvent
 *  net.minecraftforge.event.entity.player.PlayerSleepInBedEvent
 *  net.minecraftforge.event.entity.player.PlayerSpawnPhantomsEvent
 *  net.minecraftforge.event.entity.player.PlayerWakeUpEvent
 *  net.minecraftforge.event.entity.player.PlayerXpEvent$LevelChange
 *  net.minecraftforge.event.entity.player.PlayerXpEvent$PickupXp
 *  net.minecraftforge.event.entity.player.PlayerXpEvent$XpChange
 *  net.minecraftforge.event.entity.player.SleepingLocationCheckEvent
 *  net.minecraftforge.event.entity.player.SleepingTimeCheckEvent
 *  net.minecraftforge.event.entity.player.TradeWithVillagerEvent
 *  net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent
 *  net.minecraftforge.event.level.AlterGroundEvent
 *  net.minecraftforge.event.level.BlockEvent$BlockToolModificationEvent
 *  net.minecraftforge.event.level.BlockEvent$BreakEvent
 *  net.minecraftforge.event.level.BlockEvent$CreateFluidSourceEvent
 *  net.minecraftforge.event.level.BlockEvent$CropGrowEvent$Post
 *  net.minecraftforge.event.level.BlockEvent$CropGrowEvent$Pre
 *  net.minecraftforge.event.level.BlockEvent$EntityMultiPlaceEvent
 *  net.minecraftforge.event.level.BlockEvent$EntityPlaceEvent
 *  net.minecraftforge.event.level.BlockEvent$FarmlandTrampleEvent
 *  net.minecraftforge.event.level.BlockEvent$FluidPlaceBlockEvent
 *  net.minecraftforge.event.level.BlockEvent$NeighborNotifyEvent
 *  net.minecraftforge.event.level.BlockEvent$PortalSpawnEvent
 *  net.minecraftforge.event.level.ChunkDataEvent$Load
 *  net.minecraftforge.event.level.ChunkDataEvent$Save
 *  net.minecraftforge.event.level.ChunkEvent$Load
 *  net.minecraftforge.event.level.ChunkEvent$Unload
 *  net.minecraftforge.event.level.ChunkTicketLevelUpdatedEvent
 *  net.minecraftforge.event.level.ChunkWatchEvent$UnWatch
 *  net.minecraftforge.event.level.ChunkWatchEvent$Watch
 *  net.minecraftforge.event.level.ExplosionEvent$Detonate
 *  net.minecraftforge.event.level.ExplosionEvent$Start
 *  net.minecraftforge.event.level.LevelEvent$CreateSpawnPosition
 *  net.minecraftforge.event.level.LevelEvent$Load
 *  net.minecraftforge.event.level.LevelEvent$PotentialSpawns
 *  net.minecraftforge.event.level.LevelEvent$Save
 *  net.minecraftforge.event.level.LevelEvent$Unload
 *  net.minecraftforge.event.level.NoteBlockEvent$Change
 *  net.minecraftforge.event.level.NoteBlockEvent$Play
 *  net.minecraftforge.event.level.PistonEvent$Post
 *  net.minecraftforge.event.level.PistonEvent$Pre
 *  net.minecraftforge.event.level.SaplingGrowTreeEvent
 *  net.minecraftforge.event.level.SleepFinishedTimeEvent
 *  net.minecraftforge.event.server.ServerAboutToStartEvent
 *  net.minecraftforge.event.server.ServerStartedEvent
 *  net.minecraftforge.event.server.ServerStartingEvent
 *  net.minecraftforge.event.server.ServerStoppedEvent
 *  net.minecraftforge.event.server.ServerStoppingEvent
 *  net.minecraftforge.event.village.VillageSiegeEvent
 *  net.minecraftforge.event.village.VillagerTradesEvent
 *  net.minecraftforge.event.village.WandererTradesEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus
 *  net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
 *  net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
 *  net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent
 *  net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
 *  net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent
 *  net.minecraftforge.fml.event.lifecycle.InterModProcessEvent
 */
package net.rain.rainjava.api;

import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.DifficultyChangeEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.ModMismatchEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.event.RegisterStructureConversionsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.brewing.PlayerBrewedPotionEvent;
import net.minecraftforge.event.brewing.PotionBrewEvent;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.EnderManAngerEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingDrownEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingGetProjectileEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingMakeBrainEvent;
import net.minecraftforge.event.entity.living.LivingPackSizeEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.living.PotionColorCalculationEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.living.ZombieEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.ArrowNockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PermissionsChangedEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerFlyableFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.event.level.AlterGroundEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.NoteBlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.village.VillageSiegeEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.rain.rainjava.RainJava;

public class ForgeEventBridge {

    @Mod.EventBusSubscriber(modid="rainjava", bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusHandler {
        @SubscribeEvent
        public static void onFMLCommonSetup(FMLCommonSetupEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFMLClientSetup(FMLClientSetupEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFMLDedicatedServerSetup(FMLDedicatedServerSetupEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onInterModEnqueue(InterModEnqueueEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onInterModProcess(InterModProcessEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFMLLoadComplete(FMLLoadCompleteEvent e) {
            RainJava.EVENT_BUS.post(e);
        }
    }

    @Mod.EventBusSubscriber(modid="rainjava", bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusHandler {
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerChat(ServerChatEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCommand(CommandEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onTagsUpdated(TagsUpdatedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAddReloadListener(AddReloadListenerEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onOnDatapackSync(OnDatapackSyncEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRegisterStructureConversions(RegisterStructureConversionsEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRegisterGameTests(RegisterGameTestsEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAddPackFinders(AddPackFindersEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerAboutToStart(ServerAboutToStartEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onGameShuttingDown(GameShuttingDownEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onModMismatch(ModMismatchEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerNameFormat(PlayerEvent.NameFormat e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerTabListNameFormat(PlayerEvent.TabListNameFormat e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerSaveToFile(PlayerEvent.SaveToFile e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerLoadFromFile(PlayerEvent.LoadFromFile e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerStartTracking(PlayerEvent.StartTracking e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerStopTracking(PlayerEvent.StopTracking e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerHarvestCheck(PlayerEvent.HarvestCheck e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerBreakSpeed(PlayerEvent.BreakSpeed e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onArrowLoose(ArrowLooseEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onArrowNock(ArrowNockEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFillBucket(FillBucketEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBonemeal(BonemealEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCriticalHit(CriticalHitEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityItemPickup(EntityItemPickupEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemPickup(PlayerEvent.ItemPickupEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemToss(ItemTossEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemCrafted(PlayerEvent.ItemCraftedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemFished(ItemFishedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerDestroyItem(PlayerDestroyItemEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAnvilRepair(AnvilRepairEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAnvilUpdate(AnvilUpdateEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemStackedOnOther(ItemStackedOnOtherEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemAttributeModifier(ItemAttributeModifierEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemExpire(ItemExpireEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onGrindstone(GrindstoneEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onGrindstonePlaceItem(GrindstoneEvent.OnPlaceItem e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onGrinstoneTakeItem(GrindstoneEvent.OnTakeItem e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPickupXp(PlayerXpEvent.PickupXp e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onXpChange(PlayerXpEvent.XpChange e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelChange(PlayerXpEvent.LevelChange e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerSleepInBed(PlayerSleepInBedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerWakeUp(PlayerWakeUpEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onSleepingLocationCheck(SleepingLocationCheckEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onSleepingTimeCheck(SleepingTimeCheckEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerSetSpawn(PlayerSetSpawnEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerFlyableFall(PlayerFlyableFallEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerSpawnPhantoms(PlayerSpawnPhantomsEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerContainerOpen(PlayerContainerEvent.Open e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerContainerClose(PlayerContainerEvent.Close e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPermissionsChanged(PermissionsChangedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onTradeWithVillager(TradeWithVillagerEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerNegotiation(PlayerNegotiationEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingAttack(LivingAttackEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingHurt(LivingHurtEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDamage(LivingDamageEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingHeal(LivingHealEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDrops(LivingDropsEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingExperienceDrop(LivingExperienceDropEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingFall(LivingFallEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingJump(LivingEvent.LivingJumpEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingTick(LivingEvent.LivingTickEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingKnockBack(LivingKnockBackEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDestroyBlock(LivingDestroyBlockEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingChangeTarget(LivingChangeTargetEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingEquipmentChange(LivingEquipmentChangeEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLootingLevel(LootingLevelEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingSwapItems(LivingSwapItemsEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingSwapItemsHands(LivingSwapItemsEvent.Hands e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingBreathe(LivingBreatheEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDrown(LivingDrownEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onShieldBlock(ShieldBlockEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingUseTotem(LivingUseTotemEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingMakeBrain(LivingMakeBrainEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingGetProjectile(LivingGetProjectileEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingVisibility(LivingEvent.LivingVisibilityEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingPackSize(LivingPackSizeEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPotionColorCalculation(PotionColorCalculationEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEnderManAnger(EnderManAngerEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingConversionPre(LivingConversionEvent.Pre e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingConversionPost(LivingConversionEvent.Post e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onZombieSummonAid(ZombieEvent.SummonAidEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAllowDespawn(MobSpawnEvent.AllowDespawn e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPositionCheck(MobSpawnEvent.PositionCheck e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAnimalTame(AnimalTameEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBabyEntitySpawn(BabyEntitySpawnEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onSpawnPlacementRegister(SpawnPlacementRegisterEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onMobEffectAdded(MobEffectEvent.Added e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onMobEffectExpired(MobEffectEvent.Expired e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onMobEffectRemove(MobEffectEvent.Remove e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onMobEffectApplicable(MobEffectEvent.Applicable e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onUseItemStart(LivingEntityUseItemEvent.Start e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onUseItemTick(LivingEntityUseItemEvent.Tick e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onUseItemStop(LivingEntityUseItemEvent.Stop e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onUseItemFinish(LivingEntityUseItemEvent.Finish e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityConstructing(EntityEvent.EntityConstructing e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityEnteringSection(EntityEvent.EnteringSection e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntitySize(EntityEvent.Size e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityJoinLevel(EntityJoinLevelEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityLeaveLevel(EntityLeaveLevelEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTravelToDimension(EntityTravelToDimensionEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityMount(EntityMountEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityStruckByLightning(EntityStruckByLightningEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityMobGriefing(EntityMobGriefingEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onProjectileImpact(ProjectileImpactEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<?> e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onVanillaGameEvent(VanillaGameEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityAttributeCreation(EntityAttributeCreationEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityAttributeModification(EntityAttributeModificationEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportChorusFruit(EntityTeleportEvent.ChorusFruit e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportEnderPearl(EntityTeleportEvent.EnderPearl e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportEnderEntity(EntityTeleportEvent.EnderEntity e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportTeleportCommand(EntityTeleportEvent.TeleportCommand e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportSpreadPlayers(EntityTeleportEvent.SpreadPlayersCommand e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBlockBreak(BlockEvent.BreakEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBlockToolModify(BlockEvent.BlockToolModificationEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCropGrowPre(BlockEvent.CropGrowEvent.Pre e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCropGrowPost(BlockEvent.CropGrowEvent.Post e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCreateFluidSource(BlockEvent.CreateFluidSourceEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPortalSpawn(BlockEvent.PortalSpawnEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onNoteBlockPlay(NoteBlockEvent.Play e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onNoteBlockChange(NoteBlockEvent.Change e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPistonPre(PistonEvent.Pre e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPistonPost(PistonEvent.Post e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onSaplingGrowTree(SaplingGrowTreeEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelLoad(LevelEvent.Load e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelSave(LevelEvent.Save e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelCreateSpawnPosition(LevelEvent.CreateSpawnPosition e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPotentialSpawns(LevelEvent.PotentialSpawns e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onSleepFinishedTime(SleepFinishedTimeEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAlterGround(AlterGroundEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkLoad(ChunkEvent.Load e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkUnload(ChunkEvent.Unload e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkDataLoad(ChunkDataEvent.Load e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkDataSave(ChunkDataEvent.Save e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkWatch(ChunkWatchEvent.Watch e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkUnwatch(ChunkWatchEvent.UnWatch e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkTicketLevelUpdated(ChunkTicketLevelUpdatedEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onExplosionStart(ExplosionEvent.Start e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onExplosionDetonate(ExplosionEvent.Detonate e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayLevelSound(PlayLevelSoundEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayLevelSoundAtPosition(PlayLevelSoundEvent.AtPosition e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayLevelSoundAtEntity(PlayLevelSoundEvent.AtEntity e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPotionBrewPre(PotionBrewEvent.Pre e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPotionBrewPost(PotionBrewEvent.Post e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerBrewedPotion(PlayerBrewedPotionEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEnchantmentLevelSet(EnchantmentLevelSetEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFurnaceFuelBurnTime(FurnaceFuelBurnTimeEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onVillagerTrades(VillagerTradesEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onWandererTrades(WandererTradesEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onVillageSiege(VillageSiegeEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLootTableLoad(LootTableLoadEvent e) {
            RainJava.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onDifficultyChange(DifficultyChangeEvent e) {
            RainJava.EVENT_BUS.post(e);
        }
    }
}

