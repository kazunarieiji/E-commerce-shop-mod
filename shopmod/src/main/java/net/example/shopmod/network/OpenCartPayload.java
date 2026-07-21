package net.example.shopmod.network;

import net.example.shopmod.CartMenu;
import net.example.shopmod.ShopMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenCartPayload() implements CustomPacketPayload {
    public static final Type<OpenCartPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShopMod.MOD_ID, "open_cart"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCartPayload> STREAM_CODEC = StreamCodec.unit(new OpenCartPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handleServer(OpenCartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.openMenu(new MenuProvider() {
                    @Override public Component getDisplayName() { return Component.literal("Shopping Cart"); }
                    @Override public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) { return new CartMenu(windowId, inv); }
                });
            }
        });
    }
}
