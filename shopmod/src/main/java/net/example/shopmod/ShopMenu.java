package net.example.shopmod;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class ShopMenu extends AbstractContainerMenu {
    public ShopMenu(int windowId, Inventory playerInventory) {
        super(ModMenuTypes.SHOP_MENU.get(), windowId);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
}
