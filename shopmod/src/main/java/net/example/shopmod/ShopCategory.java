package net.example.shopmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;

public enum ShopCategory {
    ALL("All Products"),
    BUILDING("Building & Blocks"),
    COMBAT("Combat & Armor"),
    TOOLS("Tools & Tech"),
    REDSTONE("Redstone & Logic"),
    FOOD("Food & Produce"),
    RARE("Rare & Treasures"),
    MODDED("Modded Specials"),
    MISC("General Goods");

    public final String displayName;

    ShopCategory(String displayName) {
        this.displayName = displayName;
    }

    public static ShopCategory detectCategory(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String name = id.getPath().toLowerCase();
        String namespace = id.getNamespace().toLowerCase();

        if (name.contains("creative") || name.contains("command_block") || name.contains("structure") || name.contains("spawner") || name.contains("dragon_egg")) {
            return RARE;
        }
        if (name.contains("allthemodium") || name.contains("vibranium") || name.contains("unobtainium") || name.contains("emerald") || name.contains("diamond") || name.contains("netherite") || name.contains("star") || name.contains("elytra") || name.contains("beacon") || name.contains("totem") || name.contains("awakened")) {
            return RARE;
        }

        if (item instanceof SwordItem || item instanceof ArmorItem || name.contains("shield") || name.contains("bow") || name.contains("arrow")) {
            return COMBAT;
        }
        if (item instanceof TieredItem || name.contains("pickaxe") || name.contains("axe") || name.contains("shovel") || name.contains("hoe") || name.contains("wrench") || name.contains("hammer") || name.contains("drill")) {
            return TOOLS;
        }
        if (item.components().has(DataComponents.FOOD) || name.contains("apple") || name.contains("bread") || name.contains("meat") || name.contains("crop") || name.contains("seed")) {
            return FOOD;
        }

        if (name.contains("redstone") || name.contains("piston") || name.contains("repeater") || name.contains("comparator") || name.contains("observer") || name.contains("wire") || name.contains("cable") || name.contains("circuit") || name.contains("energy")) {
            return REDSTONE;
        }

        if (item instanceof BlockItem || name.contains("stone") || name.contains("plank") || name.contains("brick") || name.contains("glass") || name.contains("log") || name.contains("concrete") || name.contains("obsidian")) {
            return BUILDING;
        }

        if (!namespace.equals("minecraft")) {
            return MODDED;
        }

        return MISC;
    }
}
