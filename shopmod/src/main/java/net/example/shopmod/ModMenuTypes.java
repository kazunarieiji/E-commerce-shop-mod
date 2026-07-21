package net.example.shopmod;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShopMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> SHOP_MENU =
            MENUS.register("shop_menu", () ->
                    IMenuTypeExtension.create((windowId, inv, data) -> new ShopMenu(windowId, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<CartMenu>> CART_MENU =
            MENUS.register("cart_menu", () ->
                    IMenuTypeExtension.create((windowId, inv, data) -> new CartMenu(windowId, inv)));
}
