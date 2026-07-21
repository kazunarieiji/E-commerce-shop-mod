package net.example.shopmod.network;

import net.example.shopmod.ShopMod;
import net.example.shopmod.client.ClientShopData;
import net.example.shopmod.client.LoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ReadyResponsePayload(boolean ready, int progress) implements CustomPacketPayload {
    public static final Type<ReadyResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShopMod.MOD_ID, "ready_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReadyResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ReadyResponsePayload::ready,
                    ByteBufCodecs.VAR_INT, ReadyResponsePayload::progress,
                    ReadyResponsePayload::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(ReadyResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.ready()) {
                // Close loading screen and open shop
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof LoadingScreen) {
                    mc.setScreen(null);
                    // Send OpenShopPayload to server to actually open the shop
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new OpenShopPayload());
                }
            } else {
                // Not ready - update the loading screen's progress bar; client keeps polling.
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof LoadingScreen loadingScreen) {
                    loadingScreen.setProgress(payload.progress());
                }
            }
        });
    }
}
