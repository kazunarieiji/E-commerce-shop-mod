package net.example.shopmod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ShopEntry {
    public final Item item;
    public final int buyPricePerItem;
    public final int sellPricePerItem;
    public final String category;
    public final String modId;

    public ShopEntry(Item item, int buyPricePerItem, int sellPricePerItem, String category) {
        this.item = item;
        this.buyPricePerItem = buyPricePerItem;
        this.sellPricePerItem = sellPricePerItem;
        this.category = category;
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(item);
        this.modId = loc != null ? loc.getNamespace() : "minecraft";
    }

    public ItemStack createStack(int count) {
        return new ItemStack(item, count);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(BuiltInRegistries.ITEM), e -> e.item,
            ByteBufCodecs.VAR_INT, e -> e.buyPricePerItem,
            ByteBufCodecs.VAR_INT, e -> e.sellPricePerItem,
            ByteBufCodecs.STRING_UTF8, e -> e.category,
            ShopEntry::new
    );
}
