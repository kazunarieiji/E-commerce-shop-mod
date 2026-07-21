package net.example.shopmod.network;

import net.example.shopmod.ShopMod;
import net.example.shopmod.client.LoadingScreen;
import net.example.shopmod.config.ShopConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestReadyPayload() implements CustomPacketPayload {
    public static final Type<RequestReadyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShopMod.MOD_ID, "request_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestReadyPayload> STREAM_CODEC = StreamCodec.unit(new RequestReadyPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Received on the server: client is polling for readiness.
    public static void handleServer(RequestReadyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                boolean ready = ShopConfig.isReady();
                int progress = ShopConfig.getProgress();
                PacketDistributor.sendToPlayer(serverPlayer, new ReadyResponsePayload(ready, progress));
            }
        });
    }

    // Received on the client: server is telling us to show the loading screen.
    public static void handleClient(RequestReadyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof LoadingScreen)) {
                mc.setScreen(new LoadingScreen());
            }
        });
    }
}
