package net.example.shopmod;

import net.example.shopmod.item.CardItem;
import net.example.shopmod.network.ShopBalancePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class BalanceUtil {

    private static ItemStack findCard(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CardItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static int getBalance(ServerPlayer player) {
        ItemStack card = findCard(player);
        return card.isEmpty() ? 0 : CardItem.getBalance(card);
    }

    public static void setBalance(ServerPlayer player, int newBalance) {
        ItemStack card = findCard(player);
        if (!card.isEmpty()) CardItem.setBalance(card, newBalance);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ShopBalancePayload(getBalance(player)));
    }
}
