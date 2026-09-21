/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.Unpooled
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraftforge.network.NetworkDirection
 *  net.minecraftforge.network.NetworkEvent$Context
 *  net.minecraftforge.network.NetworkRegistry
 *  net.minecraftforge.network.PacketDistributor
 *  net.minecraftforge.network.PacketDistributor$TargetPoint
 *  net.minecraftforge.network.simple.SimpleChannel
 */
package net.rain.rainjava.java.util;

import io.netty.buffer.Unpooled;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkUtils {
    private static SimpleChannel CHANNEL;
    private static int packetId;
    private static final String PROTOCOL_VERSION = "1";

    public static void init(String modid) {
        CHANNEL = NetworkRegistry.newSimpleChannel((ResourceLocation)new ResourceLocation(modid, "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    }

    public static <T extends SimplePacket> void register(Class<T> clazz, Supplier<T> factory) {
        CHANNEL.messageBuilder(clazz, packetId++, NetworkDirection.PLAY_TO_SERVER).encoder((msg, buf) -> msg.write((FriendlyByteBuf)buf)).decoder(buf -> {
            T packet = factory.get();
            packet.read((FriendlyByteBuf)buf);
            return packet;
        }).consumerMainThread((msg, ctx) -> {
            ((NetworkEvent.Context)ctx.get()).enqueueWork(() -> NetworkUtils.handleServerPacket((Supplier)ctx, msg));
            ((NetworkEvent.Context)ctx.get()).setPacketHandled(true);
        }).add();
        CHANNEL.messageBuilder(clazz, packetId++, NetworkDirection.PLAY_TO_CLIENT).encoder((msg, buf) -> msg.write((FriendlyByteBuf)buf)).decoder(buf -> {
            T packet = factory.get();
            packet.read((FriendlyByteBuf)buf);
            return packet;
        }).consumerMainThread((msg, ctx) -> {
            ((NetworkEvent.Context)ctx.get()).enqueueWork(() -> msg.handleClient());
            ((NetworkEvent.Context)ctx.get()).setPacketHandled(true);
        }).add();
    }

    public static PacketBuilder createPacket() {
        return new PacketBuilder();
    }

    public static void registerQuickPacket() {
        NetworkUtils.register(QuickPacket.class, QuickPacket::new);
    }

    private static /* synthetic */ void handleServerPacket(Supplier ctx, SimplePacket msg) {
        ServerPlayer player = ((NetworkEvent.Context)ctx.get()).getSender();
        if (player != null) {
            msg.handleServer(player);
        }
    }

    static {
        packetId = 0;
        NetworkUtils.registerQuickPacket();
    }

    public static class PacketBuilder {
        private final FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        private PacketHandler serverHandler;
        private ClientPacketHandler clientHandler;

        private PacketBuilder() {
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
            this.serverHandler = handler;
            return this;
        }

        public PacketBuilder onClientReceive(ClientPacketHandler handler) {
            this.clientHandler = handler;
            return this;
        }

        public QuickPacket build() {
            byte[] data = new byte[this.tempBuf.readableBytes()];
            this.tempBuf.readBytes(data);
            return new QuickPacket(data, this.serverHandler, this.clientHandler);
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
        private byte[] data;
        private final PacketBuilder.PacketHandler serverHandler;
        private final PacketBuilder.ClientPacketHandler clientHandler;

        private QuickPacket(byte[] data, PacketBuilder.PacketHandler serverHandler, PacketBuilder.ClientPacketHandler clientHandler) {
            this.data = data;
            this.serverHandler = serverHandler;
            this.clientHandler = clientHandler;
        }

        public QuickPacket() {
            this(new byte[0], null, null);
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeInt(this.data.length);
            buf.writeBytes(this.data);
        }

        @Override
        public void read(FriendlyByteBuf buf) {
            int length = buf.readInt();
            this.data = new byte[length];
            buf.readBytes(this.data);
        }

        @Override
        public void handleServer(ServerPlayer player) {
            if (this.serverHandler != null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer((byte[])this.data));
                this.serverHandler.handle(buf, player);
            }
        }

        @Override
        public void handleClient() {
            if (this.clientHandler != null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer((byte[])this.data));
                this.clientHandler.handle(buf);
            }
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
            CHANNEL.sendToServer((Object)this);
        }

        public void sendToPlayer(ServerPlayer player) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), (Object)this);
        }

        public void sendToAllPlayers() {
            CHANNEL.send(PacketDistributor.ALL.noArg(), (Object)this);
        }

        public void sendToNearby(ServerPlayer origin, double radius) {
            PacketDistributor.TargetPoint point = new PacketDistributor.TargetPoint(origin.getX(), origin.getY(), origin.getZ(), radius, origin.level().dimension());
            CHANNEL.send(PacketDistributor.NEAR.with(() -> point), (Object)this);
        }

        public void sendToDimension(ServerPlayer player) {
            CHANNEL.send(PacketDistributor.DIMENSION.with(() -> player.level().dimension()), (Object)this);
        }
    }
}

