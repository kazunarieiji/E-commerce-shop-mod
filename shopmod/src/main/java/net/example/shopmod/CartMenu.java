package net.example.shopmod;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class CartMenu extends AbstractContainerMenu {
    public CartMenu(int windowId, Inventory playerInventory) {
        super(ModMenuTypes.CART_MENU.get(), windowId);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
}
