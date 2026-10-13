/*
 * RosettaRemoteDebugBridge - NeoForge 1.21.1 event bridge.
 *
 * Forwards NeoForge mod-bus and game-bus events onto the script event bus
 * (RosettaRemoteDebugBridge.EVENT_BUS). Event classes removed or renamed in
 * NeoForge 1.21.1 are dropped or mapped to their replacements; see comments.
 */
package com.rosetta.remotedebugbridge.api;

import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.DifficultyChangeEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.ModMismatchEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.RegisterStructureConversionsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerFlyableFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerNegotiationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.event.level.AlterGroundEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.level.block.CreateFluidSourceEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.VillageSiegeEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

public class ForgeEventBridge {

    @EventBusSubscriber(modid="rosetta_remote_debug_bridge", bus=EventBusSubscriber.Bus.MOD)
    public static class ModBusHandler {
        @SubscribeEvent
        public static void onFMLCommonSetup(FMLCommonSetupEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFMLClientSetup(FMLClientSetupEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFMLDedicatedServerSetup(FMLDedicatedServerSetupEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onInterModEnqueue(InterModEnqueueEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onInterModProcess(InterModProcessEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFMLLoadComplete(FMLLoadCompleteEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }
    }

    @EventBusSubscriber(modid="rosetta_remote_debug_bridge", bus=EventBusSubscriber.Bus.GAME)
    public static class ForgeBusHandler {
        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Pre e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelTick(LevelTickEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRenderTick(RenderFrameEvent.Pre e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerChat(ServerChatEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCommand(CommandEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onTagsUpdated(TagsUpdatedEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAddReloadListener(AddReloadListenerEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onOnDatapackSync(OnDatapackSyncEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRegisterStructureConversions(RegisterStructureConversionsEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRegisterGameTests(RegisterGameTestsEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAddPackFinders(AddPackFindersEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerAboutToStart(ServerAboutToStartEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onGameShuttingDown(GameShuttingDownEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onModMismatch(ModMismatchEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerNameFormat(PlayerEvent.NameFormat e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerTabListNameFormat(PlayerEvent.TabListNameFormat e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerSaveToFile(PlayerEvent.SaveToFile e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerLoadFromFile(PlayerEvent.LoadFromFile e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerStartTracking(PlayerEvent.StartTracking e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerStopTracking(PlayerEvent.StopTracking e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerHarvestCheck(PlayerEvent.HarvestCheck e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerBreakSpeed(PlayerEvent.BreakSpeed e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onArrowLoose(ArrowLooseEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onArrowNock(ArrowNockEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBonemeal(BonemealEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCriticalHit(CriticalHitEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // EntityItemPickupEvent -> ItemEntityPickupEvent (NeoForge split into Pre/Post).
        @SubscribeEvent
        public static void onItemEntityPickup(ItemEntityPickupEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemToss(ItemTossEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemCrafted(PlayerEvent.ItemCraftedEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemFished(ItemFishedEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerDestroyItem(PlayerDestroyItemEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAnvilRepair(AnvilRepairEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAnvilUpdate(AnvilUpdateEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemAttributeModifier(ItemAttributeModifierEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemStackedOnOther(ItemStackedOnOtherEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onItemExpire(ItemExpireEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onGrindstonePlaceItem(GrindstoneEvent.OnPlaceItem e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onGrinstoneTakeItem(GrindstoneEvent.OnTakeItem e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPickupXp(PlayerXpEvent.PickupXp e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onXpChange(PlayerXpEvent.XpChange e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelChange(PlayerXpEvent.LevelChange e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerWakeUp(PlayerWakeUpEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // SleepingLocationCheckEvent -> CanPlayerSleepEvent, SleepingTimeCheckEvent -> CanContinueSleepingEvent.
        @SubscribeEvent
        public static void onCanPlayerSleep(CanPlayerSleepEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCanContinueSleeping(CanContinueSleepingEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerSetSpawn(PlayerSetSpawnEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerFlyableFall(PlayerFlyableFallEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerSpawnPhantoms(PlayerSpawnPhantomsEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerContainerOpen(PlayerContainerEvent.Open e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerContainerClose(PlayerContainerEvent.Close e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPermissionsChanged(PermissionsChangedEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onTradeWithVillager(TradeWithVillagerEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerNegotiation(PlayerNegotiationEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // LivingAttackEvent / LivingHurtEvent -> LivingIncomingDamageEvent (pre-damage).
        @SubscribeEvent
        public static void onLivingIncomingDamage(LivingIncomingDamageEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDamage(LivingDamageEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingHeal(LivingHealEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDrops(LivingDropsEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingExperienceDrop(LivingExperienceDropEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingFall(LivingFallEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingJump(LivingEvent.LivingJumpEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // LivingEvent.LivingTickEvent -> EntityTickEvent.Post.
        @SubscribeEvent
        public static void onEntityTick(EntityTickEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingKnockBack(LivingKnockBackEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDestroyBlock(LivingDestroyBlockEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingChangeTarget(LivingChangeTargetEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingEquipmentChange(LivingEquipmentChangeEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingSwapItemsHands(LivingSwapItemsEvent.Hands e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingBreathe(LivingBreatheEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingDrown(LivingDrownEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // ShieldBlockEvent -> LivingShieldBlockEvent.
        @SubscribeEvent
        public static void onLivingShieldBlock(LivingShieldBlockEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingUseTotem(LivingUseTotemEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingGetProjectile(LivingGetProjectileEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingVisibility(LivingEvent.LivingVisibilityEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEnderManAnger(EnderManAngerEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingConversionPre(LivingConversionEvent.Pre e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLivingConversionPost(LivingConversionEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // MobSpawnEvent.FinalizeSpawn -> FinalizeSpawnEvent.
        @SubscribeEvent
        public static void onFinalizeSpawn(FinalizeSpawnEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // MobSpawnEvent.AllowDespawn -> MobDespawnEvent.
        @SubscribeEvent
        public static void onMobDespawn(MobDespawnEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPositionCheck(MobSpawnEvent.PositionCheck e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAnimalTame(AnimalTameEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBabyEntitySpawn(BabyEntitySpawnEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // SpawnPlacementRegisterEvent -> RegisterSpawnPlacementsEvent.
        @SubscribeEvent
        public static void onSpawnPlacementRegister(RegisterSpawnPlacementsEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onMobEffectAdded(MobEffectEvent.Added e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onMobEffectExpired(MobEffectEvent.Expired e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onMobEffectRemove(MobEffectEvent.Remove e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onMobEffectApplicable(MobEffectEvent.Applicable e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onUseItemStart(LivingEntityUseItemEvent.Start e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onUseItemTick(LivingEntityUseItemEvent.Tick e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onUseItemStop(LivingEntityUseItemEvent.Stop e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onUseItemFinish(LivingEntityUseItemEvent.Finish e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityConstructing(EntityEvent.EntityConstructing e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityEnteringSection(EntityEvent.EnteringSection e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntitySize(EntityEvent.Size e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityJoinLevel(EntityJoinLevelEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityLeaveLevel(EntityLeaveLevelEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTravelToDimension(EntityTravelToDimensionEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityMount(EntityMountEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityStruckByLightning(EntityStruckByLightningEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityMobGriefing(EntityMobGriefingEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onProjectileImpact(ProjectileImpactEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onVanillaGameEvent(VanillaGameEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityAttributeCreation(EntityAttributeCreationEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityAttributeModification(EntityAttributeModificationEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportChorusFruit(EntityTeleportEvent.ChorusFruit e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportEnderPearl(EntityTeleportEvent.EnderPearl e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportEnderEntity(EntityTeleportEvent.EnderEntity e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportTeleportCommand(EntityTeleportEvent.TeleportCommand e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEntityTeleportSpreadPlayers(EntityTeleportEvent.SpreadPlayersCommand e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBlockBreak(BlockEvent.BreakEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onBlockToolModify(BlockEvent.BlockToolModificationEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // BlockEvent.CropGrowEvent moved to level.block.CropGrowEvent.
        @SubscribeEvent
        public static void onCropGrowPre(CropGrowEvent.Pre e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onCropGrowPost(CropGrowEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // BlockEvent.CreateFluidSourceEvent moved to level.block.CreateFluidSourceEvent.
        @SubscribeEvent
        public static void onCreateFluidSource(CreateFluidSourceEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPortalSpawn(BlockEvent.PortalSpawnEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onNoteBlockPlay(NoteBlockEvent.Play e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onNoteBlockChange(NoteBlockEvent.Change e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPistonPre(PistonEvent.Pre e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPistonPost(PistonEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        // SaplingGrowTreeEvent -> BlockGrowFeatureEvent.
        @SubscribeEvent
        public static void onBlockGrowFeature(BlockGrowFeatureEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelLoad(LevelEvent.Load e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelSave(LevelEvent.Save e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLevelCreateSpawnPosition(LevelEvent.CreateSpawnPosition e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPotentialSpawns(LevelEvent.PotentialSpawns e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onSleepFinishedTime(SleepFinishedTimeEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onAlterGround(AlterGroundEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkLoad(ChunkEvent.Load e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkUnload(ChunkEvent.Unload e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkDataLoad(ChunkDataEvent.Load e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkDataSave(ChunkDataEvent.Save e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkWatch(ChunkWatchEvent.Watch e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkUnwatch(ChunkWatchEvent.UnWatch e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onChunkTicketLevelUpdated(ChunkTicketLevelUpdatedEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onExplosionStart(ExplosionEvent.Start e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onExplosionDetonate(ExplosionEvent.Detonate e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayLevelSound(PlayLevelSoundEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayLevelSoundAtPosition(PlayLevelSoundEvent.AtPosition e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayLevelSoundAtEntity(PlayLevelSoundEvent.AtEntity e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPotionBrewPre(PotionBrewEvent.Pre e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPotionBrewPost(PotionBrewEvent.Post e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onPlayerBrewedPotion(PlayerBrewedPotionEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onEnchantmentLevelSet(EnchantmentLevelSetEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onFurnaceFuelBurnTime(FurnaceFuelBurnTimeEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onVillagerTrades(VillagerTradesEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onWandererTrades(WandererTradesEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onVillageSiege(VillageSiegeEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onLootTableLoad(LootTableLoadEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }

        @SubscribeEvent
        public static void onDifficultyChange(DifficultyChangeEvent e) {
            RosettaRemoteDebugBridge.EVENT_BUS.post(e);
        }
    }
}
