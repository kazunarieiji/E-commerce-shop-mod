package net.example.shopmod.network;

import net.example.shopmod.ShopMod;
import net.example.shopmod.client.ClientShopData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShopBalancePayload(int balance) implements CustomPacketPayload {
    public static final Type<ShopBalancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShopMod.MOD_ID, "shop_balance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopBalancePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ShopBalancePayload::balance, ShopBalancePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handleClient(ShopBalancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientShopData.setBalance(payload.balance()));
    }
}
