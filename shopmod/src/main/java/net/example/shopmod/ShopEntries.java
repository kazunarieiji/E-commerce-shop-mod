package net.example.shopmod;

import net.example.shopmod.config.ShopConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;

public class ShopEntries {

    public static List<ShopEntry> get() {
        List<ShopEntry> list = new ArrayList<>();
        Map<String, ShopConfig.ConfigItemEntry> priceMap = ShopConfig.getPriceMap();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String itemId = id.toString();

            if (id.getPath().contains("creative") || id.getPath().contains("dev_tool")) continue;

            if (priceMap.containsKey(itemId)) {
                ShopConfig.ConfigItemEntry cfg = priceMap.get(itemId);
                list.add(new ShopEntry(item, cfg.buy_price, cfg.sell_price, cfg.category));
            } else {
                ShopConfig.ConfigItemEntry cfg = ShopConfig.getEntry(item);
                list.add(new ShopEntry(item, cfg.buy_price, cfg.sell_price, cfg.category));
            }
        }

        list.sort(Comparator.comparing(e -> BuiltInRegistries.ITEM.getKey(e.item).toString()));
        return Collections.unmodifiableList(list);
    }
}
