package net.example.shopmod.network;

import net.example.shopmod.ShopEntry;
import net.example.shopmod.ShopMod;
import net.example.shopmod.client.ClientShopData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ShopSyncPayload(List<ShopEntry> entries) implements CustomPacketPayload {
    public static final Type<ShopSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShopMod.MOD_ID, "shop_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, ShopEntry.STREAM_CODEC), ShopSyncPayload::entries,
                    ShopSyncPayload::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(ShopSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientShopData.setEntries(payload.entries()));
    }
}
