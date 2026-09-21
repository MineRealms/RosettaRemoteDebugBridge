package com.rosetta.remotedebugbridge.script.util;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class NetworkUtils {
    private static final Logger LOGGER = LogManager.getLogger("RosettaRemoteDebugBridge/Network");
    private static final String PROTOCOL_VERSION = "1";
    private static final List<PendingMessage<?>> PENDING = new ArrayList<PendingMessage<?>>();
    private static final Set<Class<?>> REGISTERED = new HashSet<Class<?>>();
    private static final Map<String, PacketBuilder.PacketHandler> SERVER_RECEIVERS = new ConcurrentHashMap<String, PacketBuilder.PacketHandler>();
    private static final Map<String, PacketBuilder.ClientPacketHandler> CLIENT_RECEIVERS = new ConcurrentHashMap<String, PacketBuilder.ClientPacketHandler>();
    private static String channelModId;
    private static SimpleChannel channel;
    private static int nextPacketId;

    private NetworkUtils() {
    }

    public static synchronized SimpleChannel init(String modId) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("NetworkUtils.init(modId): modId must not be blank");
        }
        ResourceLocation id = ResourceLocation.tryParse(modId + ":main");
        if (id == null) {
            throw new IllegalArgumentException("NetworkUtils.init(modId): invalid mod id '" + modId + "'");
        }
        if (channel != null) {
            if (!modId.equals(channelModId)) {
                throw new IllegalStateException("RosettaRemoteDebugBridge network channel already initialized for '" + channelModId + "', cannot re-init for '" + modId + "'");
            }
            return channel;
        }
        channel = NetworkRegistry.newSimpleChannel(id, () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
        channelModId = modId;
        nextPacketId = 0;
        REGISTERED.clear();
        registerNow(QuickPacket.class, QuickPacket::new);
        for (PendingMessage<?> pending : PENDING) {
            pending.register();
        }
        PENDING.clear();
        LOGGER.info("[Rosetta/Network] Channel '{}:main' initialized (protocol {})", modId, PROTOCOL_VERSION);
        return channel;
    }

    public static boolean isInitialized() {
        return channel != null;
    }

    public static SimpleChannel getChannel() {
        SimpleChannel current = channel;
        if (current == null) {
            throw new IllegalStateException("RosettaRemoteDebugBridge network channel is not initialized. Call NetworkUtils.init(modId) from your script before sending or registering packets.");
        }
        return current;
    }

    public static synchronized <T extends SimplePacket> void register(Class<T> clazz, Supplier<T> factory) {
        if (clazz == null || factory == null) {
            throw new IllegalArgumentException("NetworkUtils.register(clazz, factory): arguments must not be null");
        }
        if (channel == null) {
            PENDING.add(new PendingMessage<T>(clazz, factory));
            return;
        }
        if (REGISTERED.contains(clazz)) {
            LOGGER.warn("[Rosetta/Network] Message class already registered, skipping: {}", clazz.getName());
            return;
        }
        LOGGER.warn("[Rosetta/Network] Late registration of {} after channel init; register messages during script init for multiplayer compatibility", clazz.getName());
        registerNow(clazz, factory);
    }

    private static synchronized <T extends SimplePacket> void registerNow(Class<T> clazz, Supplier<T> factory) {
        REGISTERED.add(clazz);
        getChannel().messageBuilder(clazz, nextPacketId++)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(buf -> {
                    T packet = factory.get();
                    packet.read(buf);
                    return packet;
                })
                .consumerMainThread((msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> dispatch(msg, ctx));
                    ctx.setPacketHandled(true);
                })
                .add();
    }

    private static <T extends SimplePacket> void dispatch(T msg, NetworkEvent.Context ctx) {
        try {
            if (ctx.getDirection().getReceptionSide() == LogicalSide.SERVER) {
                ServerPlayer sender = ctx.getSender();
                if (sender != null) {
                    msg.handleServer(sender);
                }
            } else {
                msg.handleClient();
            }
        }
        catch (Throwable t) {
            LOGGER.error("[Rosetta/Network] Failed to handle packet {}: {}", msg.getClass().getName(), t.getMessage(), t);
        }
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
            NetworkUtils.getChannel().sendToServer(this);
        }

        public void sendToPlayer(ServerPlayer player) {
            NetworkUtils.getChannel().send(PacketDistributor.PLAYER.with(() -> player), this);
        }

        public void sendToAllPlayers() {
            NetworkUtils.getChannel().send(PacketDistributor.ALL.noArg(), this);
        }

        public void sendToNearby(ServerPlayer origin, double radius) {
            PacketDistributor.TargetPoint point = new PacketDistributor.TargetPoint(origin.getX(), origin.getY(), origin.getZ(), radius, origin.level().dimension());
            NetworkUtils.getChannel().send(PacketDistributor.NEAR.with(() -> point), this);
        }

        public void sendToDimension(ServerPlayer player) {
            NetworkUtils.getChannel().send(PacketDistributor.DIMENSION.with(() -> player.level().dimension()), this);
        }
    }
}
