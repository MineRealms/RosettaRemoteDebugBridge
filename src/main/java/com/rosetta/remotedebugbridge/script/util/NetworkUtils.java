/*
 * RosettaRemoteDebugBridge - NeoForge 1.21.1 networking layer.
 *
 * The public script API (SimplePacket / PacketBuilder / QuickPacket) is preserved,
 * but the transport is rebased onto NeoForge's CustomPacketPayload system: every
 * registered SimplePacket is wrapped into a single RosettaPayload carrying
 * (message id, encoded body), registered once via RegisterPayloadHandlersEvent.
 */
package com.rosetta.remotedebugbridge.script.util;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class NetworkUtils {
    private static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge/Network");
    private static final String PROTOCOL_VERSION = "1";
    private static final List<PendingMessage<?>> PENDING = new ArrayList<PendingMessage<?>>();
    private static final Set<Class<?>> REGISTERED = new HashSet<Class<?>>();
    private static final Map<String, PacketBuilder.PacketHandler> SERVER_RECEIVERS = new ConcurrentHashMap<String, PacketBuilder.PacketHandler>();
    private static final Map<String, PacketBuilder.ClientPacketHandler> CLIENT_RECEIVERS = new ConcurrentHashMap<String, PacketBuilder.ClientPacketHandler>();
    private static final Map<ResourceLocation, Supplier<? extends SimplePacket>> FACTORIES = new ConcurrentHashMap<ResourceLocation, Supplier<? extends SimplePacket>>();
    private static final Map<Class<?>, ResourceLocation> IDS = new ConcurrentHashMap<Class<?>, ResourceLocation>();
    private static String channelModId;
    private static CustomPacketPayload.Type<RosettaPayload> payloadType;

    private NetworkUtils() {
    }

    public static synchronized <T extends SimplePacket> T init(String modId) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("NetworkUtils.init(modId): modId must not be blank");
        }
        ResourceLocation id = ResourceLocation.tryParse(modId + ":main");
        if (id == null) {
            throw new IllegalArgumentException("NetworkUtils.init(modId): invalid mod id '" + modId + "'");
        }
        if (channelModId != null) {
            if (!modId.equals(channelModId)) {
                throw new IllegalStateException("RosettaRemoteDebugBridge network channel already initialized for '" + channelModId + "', cannot re-init for '" + modId + "'");
            }
            return null;
        }
        channelModId = modId;
        payloadType = new CustomPacketPayload.Type<RosettaPayload>(id);
        REGISTERED.clear();
        registerNow(QuickPacket.class, QuickPacket::new);
        for (PendingMessage<?> pending : PENDING) {
            pending.register();
        }
        PENDING.clear();
        LOGGER.info("[Rosetta/Network] Channel '{}:main' initialized (protocol {})", modId, PROTOCOL_VERSION);
        return null;
    }

    public static boolean isInitialized() {
        return channelModId != null;
    }

    /**
     * Called from a RegisterPayloadHandlersEvent listener on the mod event bus.
     * Registers the single transport payload used to carry every SimplePacket.
     */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        CustomPacketPayload.Type<RosettaPayload> type = payloadType;
        if (type == null) {
            LOGGER.warn("[Rosetta/Network] registerPayloads called before init(); skipping payload registration");
            return;
        }
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playBidirectional(type, RosettaPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.flow() == PacketFlow.SERVERBOUND) {
                ctx.enqueueWork(() -> handleServerPayload(payload, (ServerPlayer) ctx.player()));
            } else {
                ctx.enqueueWork(() -> handleClientPayload(payload));
            }
        });
        LOGGER.info("[Rosetta/Network] Transport payload '{}' registered ({} message type(s))", type.id(), FACTORIES.size());
    }

    public static synchronized <T extends SimplePacket> void register(Class<T> clazz, Supplier<T> factory) {
        if (clazz == null || factory == null) {
            throw new IllegalArgumentException("NetworkUtils.register(clazz, factory): arguments must not be null");
        }
        if (channelModId == null) {
            PENDING.add(new PendingMessage<T>(clazz, factory));
            return;
        }
        if (REGISTERED.contains(clazz)) {
            LOGGER.warn("[Rosetta/Network] Message class already registered, skipping: {}", clazz.getName());
            return;
        }
        registerNow(clazz, factory);
    }

    private static synchronized <T extends SimplePacket> void registerNow(Class<T> clazz, Supplier<T> factory) {
        REGISTERED.add(clazz);
        ResourceLocation id = idFor(clazz);
        FACTORIES.put(id, factory);
        IDS.put(clazz, id);
    }

    private static ResourceLocation idFor(Class<?> clazz) {
        String path = clazz.getName().toLowerCase(Locale.ROOT).replace('.', '/').replace('$', '/');
        return ResourceLocation.fromNamespaceAndPath(channelModId, path);
    }

    private static RosettaPayload wrap(SimplePacket packet) {
        ResourceLocation id = IDS.get(packet.getClass());
        if (id == null) {
            throw new IllegalStateException("Packet class not registered: " + packet.getClass().getName() + ". Call NetworkUtils.register(clazz, factory) first.");
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new RosettaPayload(id, data);
    }

    private static void handleServerPayload(RosettaPayload payload, ServerPlayer sender) {
        SimplePacket msg = decode(payload);
        if (msg == null) {
            return;
        }
        try {
            if (sender != null) {
                msg.handleServer(sender);
            }
        }
        catch (Throwable t) {
            LOGGER.error("[Rosetta/Network] Failed to handle server packet {}: {}", msg.getClass().getName(), t.getMessage(), t);
        }
    }

    private static void handleClientPayload(RosettaPayload payload) {
        SimplePacket msg = decode(payload);
        if (msg == null) {
            return;
        }
        try {
            msg.handleClient();
        }
        catch (Throwable t) {
            LOGGER.error("[Rosetta/Network] Failed to handle client packet {}: {}", msg.getClass().getName(), t.getMessage(), t);
        }
    }

    private static SimplePacket decode(RosettaPayload payload) {
        Supplier<? extends SimplePacket> factory = FACTORIES.get(payload.id());
        if (factory == null) {
            LOGGER.warn("[Rosetta/Network] No registered message for payload '{}'", payload.id());
            return null;
        }
        SimplePacket msg = factory.get();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
        msg.read(buf);
        return msg;
    }

    public static PacketBuilder createPacket() {
        return new PacketBuilder();
    }

    public static PacketBuilder createPacket(String channelName) {
        return new PacketBuilder().channel(channelName);
    }

    public static void registerQuickPacket() {
        register(QuickPacket.class, QuickPacket::new);
    }

    public static void onServerReceive(String channelName, PacketBuilder.PacketHandler handler) {
        if (channelName == null || handler == null) {
            throw new IllegalArgumentException("onServerReceive(channel, handler): arguments must not be null");
        }
        SERVER_RECEIVERS.put(channelName, handler);
    }

    public static void onClientReceive(String channelName, PacketBuilder.ClientPacketHandler handler) {
        if (channelName == null || handler == null) {
            throw new IllegalArgumentException("onClientReceive(channel, handler): arguments must not be null");
        }
        CLIENT_RECEIVERS.put(channelName, handler);
    }

    private static final class PendingMessage<T extends SimplePacket> {
        private final Class<T> clazz;
        private final Supplier<T> factory;

        private PendingMessage(Class<T> clazz, Supplier<T> factory) {
            this.clazz = clazz;
            this.factory = factory;
        }

        private void register() {
            registerNow(this.clazz, this.factory);
        }
    }

    /** Transport payload: a single NeoForge payload type carrying every SimplePacket. */
    public record RosettaPayload(ResourceLocation id, byte[] data) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, RosettaPayload> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, RosettaPayload>() {
            @Override
            public RosettaPayload decode(FriendlyByteBuf buf) {
                ResourceLocation id = buf.readResourceLocation();
                int length = buf.readVarInt();
                byte[] data = new byte[length];
                buf.readBytes(data);
                return new RosettaPayload(id, data);
            }

            @Override
            public void encode(FriendlyByteBuf buf, RosettaPayload payload) {
                buf.writeResourceLocation(payload.id());
                buf.writeVarInt(payload.data().length);
                buf.writeBytes(payload.data());
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return payloadType;
        }
    }

    public static class PacketBuilder {
        private final FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        private String channelName = "default";

        private PacketBuilder() {
        }

        public PacketBuilder channel(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Packet channel name must not be blank");
            }
            this.channelName = name;
            return this;
        }

        public PacketBuilder writeString(String str) {
            this.tempBuf.writeUtf(str);
            return this;
        }

        public PacketBuilder writeInt(int value) {
            this.tempBuf.writeInt(value);
            return this;
        }

        public PacketBuilder writeLong(long value) {
            this.tempBuf.writeLong(value);
            return this;
        }

        public PacketBuilder writeFloat(float value) {
            this.tempBuf.writeFloat(value);
            return this;
        }

        public PacketBuilder writeDouble(double value) {
            this.tempBuf.writeDouble(value);
            return this;
        }

        public PacketBuilder writeBoolean(boolean value) {
            this.tempBuf.writeBoolean(value);
            return this;
        }

        public PacketBuilder writeBytes(byte[] bytes) {
            this.tempBuf.writeBytes(bytes);
            return this;
        }

        public PacketBuilder onServerReceive(PacketHandler handler) {
            NetworkUtils.onServerReceive(this.channelName, handler);
            return this;
        }

        public PacketBuilder onClientReceive(ClientPacketHandler handler) {
            NetworkUtils.onClientReceive(this.channelName, handler);
            return this;
        }

        public QuickPacket build() {
            byte[] data = new byte[this.tempBuf.readableBytes()];
            this.tempBuf.readBytes(data);
            return new QuickPacket(this.channelName, data);
        }

        @FunctionalInterface
        public static interface PacketHandler {
            public void handle(FriendlyByteBuf var1, ServerPlayer var2);
        }

        @FunctionalInterface
        public static interface ClientPacketHandler {
            public void handle(FriendlyByteBuf var1);
        }
    }

    public static class QuickPacket
    extends SimplePacket {
        private String channelName;
        private byte[] data;

        public QuickPacket() {
            this("default", new byte[0]);
        }

        public QuickPacket(String channelName, byte[] data) {
            this.channelName = channelName == null ? "default" : channelName;
            this.data = data == null ? new byte[0] : data;
        }

        public String getChannelName() {
            return this.channelName;
        }

        public byte[] getData() {
            return this.data;
        }

        public FriendlyByteBuf buffer() {
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(this.data));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(this.channelName, 256);
            buf.writeByteArray(this.data);
        }

        @Override
        public void read(FriendlyByteBuf buf) {
            this.channelName = buf.readUtf(256);
            this.data = buf.readByteArray();
        }

        @Override
        public void handleServer(ServerPlayer player) {
            PacketBuilder.PacketHandler handler = SERVER_RECEIVERS.get(this.channelName);
            if (handler == null) {
                LOGGER.warn("[Rosetta/Network] No server receiver registered for quick-packet channel '{}'", this.channelName);
                return;
            }
            handler.handle(this.buffer(), player);
        }

        @Override
        public void handleClient() {
            PacketBuilder.ClientPacketHandler handler = CLIENT_RECEIVERS.get(this.channelName);
            if (handler == null) {
                LOGGER.warn("[Rosetta/Network] No client receiver registered for quick-packet channel '{}'", this.channelName);
                return;
            }
            handler.handle(this.buffer());
        }
    }

    public static abstract class SimplePacket {
        public abstract void write(FriendlyByteBuf var1);

        public abstract void read(FriendlyByteBuf var1);

        public void handleServer(ServerPlayer player) {
        }

        public void handleClient() {
        }

        public void sendToServer() {
            PacketDistributor.sendToServer(wrap(this));
        }

        public void sendToPlayer(ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, wrap(this));
        }

        public void sendToAllPlayers() {
            PacketDistributor.sendToAllPlayers(wrap(this));
        }

        public void sendToNearby(ServerPlayer origin, double radius) {
            if (!(origin.level() instanceof ServerLevel level)) {
                return;
            }
            PacketDistributor.sendToPlayersNear(level, null, origin.getX(), origin.getY(), origin.getZ(), radius, wrap(this));
        }

        public void sendToDimension(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            PacketDistributor.sendToPlayersInDimension(level, wrap(this));
        }
    }
}
