package net.example.shopmod.item;

import net.example.shopmod.ModComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CardItem extends Item {
    public CardItem(Properties properties) { super(properties); }

    public static int getBalance(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CardItem)) return 0;
        Integer value = stack.get(ModComponents.BALANCE.get());
        return value != null ? value : 0;
    }

    public static void setBalance(ItemStack stack, int balance) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CardItem)) return;
        stack.set(ModComponents.BALANCE.get(), Math.max(0, balance));
    }
}
