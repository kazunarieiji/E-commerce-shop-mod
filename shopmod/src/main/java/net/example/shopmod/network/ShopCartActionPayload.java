package net.example.shopmod.network;

import net.example.shopmod.BalanceUtil;
import net.example.shopmod.item.CardItem;
import net.example.shopmod.ShopEntries;
import net.example.shopmod.ShopEntry;
import net.example.shopmod.ShopMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ShopCartActionPayload(List<Integer> entryIndices, List<Integer> quantities, boolean isBuy)
        implements CustomPacketPayload {

    public static final Type<ShopCartActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShopMod.MOD_ID, "shop_cart_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopCartActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), ShopCartActionPayload::entryIndices,
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), ShopCartActionPayload::quantities,
                    ByteBufCodecs.BOOL, ShopCartActionPayload::isBuy,
                    ShopCartActionPayload::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(ShopCartActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (payload.entryIndices().size() != payload.quantities().size()) return;

            List<ShopEntry> allEntries = ShopEntries.get();
            Map<ShopEntry, Integer> lines = new LinkedHashMap<>();
            for (int i = 0; i < payload.entryIndices().size(); i++) {
                int index = payload.entryIndices().get(i);
                int qty = payload.quantities().get(i);
                if (index < 0 || index >= allEntries.size() || qty <= 0) continue;
                lines.merge(allEntries.get(index), qty, Integer::sum);
            }
            if (lines.isEmpty()) return;

            if (payload.isBuy()) handleBuy(player, lines);
            else handleSell(player, lines);
        });
    }

    private static void handleBuy(ServerPlayer player, Map<ShopEntry, Integer> lines) {
        ItemStack card = findCard(player);
        if (card.isEmpty()) {
            player.displayClientMessage(Component.literal("§c[e-Commerce] You need a Premium e-Card to checkout items!"), true);
            return;
        }

        long total = 0;
        for (Map.Entry<ShopEntry, Integer> line : lines.entrySet()) {
            total += (long) line.getKey().buyPricePerItem * line.getValue();
        }
        int balance = CardItem.getBalance(card);
        if (total > balance) {
            player.displayClientMessage(Component.literal("§c[e-Commerce] Transaction Declined: Insufficient Coins!"), true);
            return;
        }

        CardItem.setBalance(card, (int) (balance - total));
        for (Map.Entry<ShopEntry, Integer> line : lines.entrySet()) giveOrDrop(player, line.getKey().item, line.getValue());

        BalanceUtil.sync(player);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.displayClientMessage(Component.literal("§a📦 Order Placed! Paid " + total + " Coins. Free Prime Express Delivery included!"), false);
    }

    private static void handleSell(ServerPlayer player, Map<ShopEntry, Integer> lines) {
        ItemStack card = findCard(player);
        if (card.isEmpty()) {
            player.displayClientMessage(Component.literal("§c[e-Commerce] You need a Premium e-Card to receive funds!"), true);
            return;
        }

        Inventory inv = player.getInventory();
        for (Map.Entry<ShopEntry, Integer> line : lines.entrySet()) {
            if (countItem(inv, line.getKey().item) < line.getValue()) {
                player.displayClientMessage(Component.literal("§c[e-Commerce] Liquidation Error: Missing items in inventory!"), true);
                return;
            }
        }

        long total = 0;
        for (Map.Entry<ShopEntry, Integer> line : lines.entrySet()) {
            removeItems(inv, line.getKey().item, line.getValue());
            total += (long) line.getKey().sellPricePerItem * line.getValue();
        }

        CardItem.setBalance(card, (int) (CardItem.getBalance(card) + total));
        BalanceUtil.sync(player);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        player.displayClientMessage(Component.literal("§a💰 Trade-In Complete! Received " + total + " Coins."), false);
    }

    private static ItemStack findCard(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CardItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static int countItem(Inventory inv, Item item) {
        int total = 0;
        for (ItemStack stack : inv.items) if (stack.getItem() == item) total += stack.getCount();
        return total;
    }

    private static void removeItems(Inventory inv, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.getItem() == item) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void giveOrDrop(Player player, Item item, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, item.getDefaultMaxStackSize());
        while (remaining > 0) {
            int take = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, take);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            remaining -= take;
        }
    }
}
