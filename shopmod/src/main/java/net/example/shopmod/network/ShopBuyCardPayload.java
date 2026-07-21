package net.example.shopmod.network;

import net.example.shopmod.BalanceUtil;
import net.example.shopmod.item.CardItem;
import net.example.shopmod.ModItems;
import net.example.shopmod.ShopMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShopBuyCardPayload() implements CustomPacketPayload {
    public static final Type<ShopBuyCardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShopMod.MOD_ID, "shop_buy_card"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopBuyCardPayload> STREAM_CODEC = StreamCodec.unit(new ShopBuyCardPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(ShopBuyCardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) giveFreeCard(player);
        });
    }

    private static void giveFreeCard(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == ModItems.CARD.get()) {
                player.displayClientMessage(Component.literal("§eYou already possess an active Premium e-Card!"), true);
                return;
            }
        }
        ItemStack newCard = new ItemStack(ModItems.CARD.get());
        CardItem.setBalance(newCard, 0);
        if (!player.getInventory().add(newCard)) player.drop(newCard, false);
        BalanceUtil.sync(player);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.displayClientMessage(Component.literal("§aWelcome! Issued a Premium e-Card to your inventory."), false);
    }
}
