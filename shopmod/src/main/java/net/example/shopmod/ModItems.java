package net.example.shopmod;

import net.example.shopmod.item.CardItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, ShopMod.MOD_ID);

    public static final DeferredHolder<Item, CardItem> CARD =
            ITEMS.register("card", () -> new CardItem(new Item.Properties().stacksTo(1)));
}
