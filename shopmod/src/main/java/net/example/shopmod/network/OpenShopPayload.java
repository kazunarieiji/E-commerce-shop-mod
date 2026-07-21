package net.example.shopmod.network;

import net.example.shopmod.BalanceUtil;
import net.example.shopmod.ShopEntries;
import net.example.shopmod.ShopMenu;
import net.example.shopmod.ShopMod;
import net.example.shopmod.config.ShopConfig;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenShopPayload() implements CustomPacketPayload {
    public static final Type<OpenShopPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShopMod.MOD_ID, "open_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopPayload> STREAM_CODEC = StreamCodec.unit(new OpenShopPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(OpenShopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // This packet is sent from client when they click "Back" in Cart to return to shop.
                // If prices are not ready, we send a request to open loading screen.
                if (!ShopConfig.isReady()) {
                    PacketDistributor.sendToPlayer(player, new RequestReadyPayload());
                    return;
                }
                PacketDistributor.sendToPlayer(player, new ShopSyncPayload(ShopEntries.get()));
                player.openMenu(new MenuProvider() {
                    @Override public Component getDisplayName() { return Component.literal("e-Commerce Market"); }
                    @Override public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                        return new ShopMenu(windowId, inv);
                    }
                });
                BalanceUtil.sync(player);
            }
        });
    }
}
