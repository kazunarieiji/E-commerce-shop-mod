package net.example.shopmod;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fully automatic and precise pricing engine for any modpack.
 * Uses:
 * - Recursive recipe cost calculation with depth‑aware caching.
 * - Tag‑based valuation (via item name keywords and common prefixes).
 * - Reflection to detect hidden reagents (Mekanism chemicals, gases, etc.).
 * - Configurable processing costs (furnace, crafting, modded machines).
 */
public class ShopPricing {

    private static Map<Item, List<RecipeHolder<Recipe<?>>>> recipeMap = null;
    private static final Map<Item, Integer> costCache = new ConcurrentHashMap<>();
    private static final Map<Item, Integer> recipeCostCache = new ConcurrentHashMap<>();
    private static final Map<Item, Integer> recipeCostBudgetCache = new ConcurrentHashMap<>();

    private static final Set<Item> hiddenReagentEstimatedItems = ConcurrentHashMap.newKeySet();

    // Heuristics to identify modded machine recipes with hidden inputs
    private static final String[] HIDDEN_REAGENT_RECIPE_HINTS = {
            "chemical", "infus", "gas", "slurry", "pigment", "crystallizer",
            "enrich", "purify", "inject", "alloy", "ore", "crush", "pulver",
            "compress", "combine", "reaction", "electroly", "centrifug",
            "sawmill", "crusher", "macerator", "grinder"
    };

    // Processing cost for modded machines (can be overridden via config)
    private static double moddedProcessingCost = 25.0;

    public static void setModdedProcessingCost(double cost) {
        moddedProcessingCost = Math.max(0, cost);
    }

    private static boolean looksLikeHiddenReagentRecipe(Recipe<?> recipe) {
        String className = recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        for (String hint : HIDDEN_REAGENT_RECIPE_HINTS) {
            if (className.contains(hint)) return true;
        }
        return false;
    }

    public static Set<Item> getAndClearHiddenReagentEstimatedItems() {
        Set<Item> copy = new HashSet<>(hiddenReagentEstimatedItems);
        hiddenReagentEstimatedItems.clear();
        return copy;
    }

    private static final double HIDDEN_REAGENT_AMOUNT_UNIT = 1000.0;
    private static final double DEFAULT_HIDDEN_REAGENT_COST = 150.0;
    private static final int HIDDEN_REAGENT_SCAN_MAX_DEPTH = 4;

    private static double estimateHiddenReagentCost(Recipe<?> recipe) {
        try {
            double found = scanForReagentCost(recipe, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
            return found >= 0 ? found : DEFAULT_HIDDEN_REAGENT_COST;
        } catch (Exception e) {
            return DEFAULT_HIDDEN_REAGENT_COST;
        }
    }

    private static double scanForReagentCost(Object obj, int depth, Set<Object> seen) {
        if (obj == null || depth > HIDDEN_REAGENT_SCAN_MAX_DEPTH) return -1;
        if (obj.getClass().isPrimitive() || obj instanceof Number || obj instanceof CharSequence
                || obj instanceof Boolean || obj instanceof Class<?> || obj instanceof Enum<?>) {
            return -1;
        }
        if (!seen.add(obj)) return -1;

        // Handle collections/arrays
        if (obj instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                double found = scanForReagentCost(element, depth + 1, seen);
                if (found >= 0) return found;
            }
            return -1;
        }
        if (obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                double found = scanForReagentCost(java.lang.reflect.Array.get(obj, i), depth + 1, seen);
                if (found >= 0) return found;
            }
            return -1;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                double found = scanForReagentCost(value, depth + 1, seen);
                if (found >= 0) return found;
            }
            return -1;
        }

        Double amount = null;
        String idText = null;

        // Check for common mod ingredient classes: e.g., ChemicalStack, GasStack, InfusionStack
        Class<?> clazz = obj.getClass();
        String className = clazz.getSimpleName().toLowerCase(Locale.ROOT);
        if (className.contains("chemical") || className.contains("gas") || className.contains("infusion")
                || className.contains("slurry") || className.contains("pigment")) {
            // Try to get resource name and amount via reflection
            for (java.lang.reflect.Field f : allFieldsOf(clazz)) {
                f.setAccessible(true);
                Object val;
                try {
                    val = f.get(obj);
                } catch (Exception e) {
                    continue;
                }
                if (val == null) continue;
                String fname = f.getName().toLowerCase(Locale.ROOT);
                if (amount == null && (val instanceof Integer || val instanceof Long)
                        && (fname.contains("amount") || fname.contains("count") || fname.contains("qty"))) {
                    amount = ((Number) val).doubleValue();
                }
                if (idText == null && val instanceof CharSequence) {
                    String s = val.toString();
                    if (s.contains(":")) {
                        idText = s;
                    }
                }
                // Also look for a "type" field that might contain the resource
                if (idText == null && fname.equals("type") && val instanceof CharSequence) {
                    String s = val.toString();
                    if (s.contains(":")) {
                        idText = s;
                    }
                }
            }
        }

        // General reflection for fields
        for (java.lang.reflect.Field f : allFieldsOf(clazz)) {
            f.setAccessible(true);
            Object val;
            try {
                val = f.get(obj);
            } catch (Exception e) {
                continue;
            }
            if (val == null) continue;
            String fname = f.getName().toLowerCase(Locale.ROOT);

            if (amount == null && (val instanceof Integer || val instanceof Long)
                    && (fname.contains("amount") || fname.contains("count") || fname.contains("qty"))) {
                amount = ((Number) val).doubleValue();
            }

            String s = val.toString();
            if (s != null) {
                int colon = s.indexOf(':');
                if (colon > 0 && colon < s.length() - 1 && s.length() < 256) {
                    if (idText == null || s.length() < idText.length()) {
                        idText = s;
                    }
                }
            }
        }

        if (idText != null) {
            // Extract a clean resource path from e.g. "mekanism:diamond" or "ResourceKey[ ... / ... ]"
            String tail = idText;
            int lastSlash = tail.lastIndexOf('/');
            if (lastSlash >= 0) tail = tail.substring(lastSlash + 1);
            int lastColon = tail.lastIndexOf(':');
            String path = lastColon >= 0 ? tail.substring(lastColon + 1) : tail;
            path = path.replaceAll("[\\]\\[]", "").trim();

            double tierValue = tierValueForName(path.toLowerCase(Locale.ROOT));
            double units = amount != null ? amount : HIDDEN_REAGENT_AMOUNT_UNIT;
            return tierValue * (units / HIDDEN_REAGENT_AMOUNT_UNIT);
        }

        // Recurse into nested fields
        for (java.lang.reflect.Field f : allFieldsOf(clazz)) {
            f.setAccessible(true);
            Object val;
            try {
                val = f.get(obj);
            } catch (Exception e) {
                continue;
            }
            if (val == null) continue;
            double nested = scanForReagentCost(val, depth + 1, seen);
            if (nested >= 0) return nested;
        }
        return -1;
    }

    private static List<java.lang.reflect.Field> allFieldsOf(Class<?> clazz) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
            c = c.getSuperclass();
        }
        return fields;
    }

    public static void initCache(RecipeManager recipeManager, HolderLookup.Provider registryAccess) {
        recipeMap = new HashMap<>();
        costCache.clear();
        recipeCostCache.clear();
        recipeCostBudgetCache.clear();
        hiddenReagentEstimatedItems.clear();
        for (RecipeHolder<?> raw : recipeManager.getRecipes()) {
            @SuppressWarnings("unchecked")
            RecipeHolder<Recipe<?>> holder = (RecipeHolder<Recipe<?>>) raw;
            ItemStack resultStack;
            try {
                resultStack = holder.value().getResultItem(registryAccess);
            } catch (Exception e) {
                continue;
            }
            if (resultStack == null || resultStack.isEmpty()) continue;
            Item result = resultStack.getItem();
            recipeMap.computeIfAbsent(result, k -> new ArrayList<>()).add(holder);
        }
    }

    public static void clearCache() {
        if (recipeMap != null) recipeMap.clear();
        costCache.clear();
        recipeCostCache.clear();
        recipeCostBudgetCache.clear();
        recipeMap = null;
    }

    public static int calculateItemBaseCost(Item item, HolderLookup.Provider registryAccess, int maxDepth) {
        if (costCache.containsKey(item)) return costCache.get(item);

        int recipeCost = computeRecipeCost(item, registryAccess, maxDepth, 0, new HashSet<>());
        int materialFloor = calculateMaterialValue(item);

        int rawCost = recipeCost > 0 ? Math.max(recipeCost, materialFloor) : materialFloor;

        int finalCost = applyRarityAndTypeModifiers(item, rawCost);
        costCache.put(item, finalCost);
        return finalCost;
    }

    private static int computeRecipeCost(Item item, HolderLookup.Provider registryAccess, int maxDepth, int depth, Set<Item> visited) {
        if (depth > maxDepth || visited.contains(item) || recipeMap == null) return -1;

        int remainingBudget = maxDepth - depth;
        Integer memoized = recipeCostCache.get(item);
        Integer memoizedBudget = recipeCostBudgetCache.get(item);
        if (memoized != null && memoizedBudget != null && memoizedBudget >= remainingBudget) {
            return memoized;
        }

        visited.add(item);

        List<RecipeHolder<Recipe<?>>> recipes = recipeMap.getOrDefault(item, Collections.emptyList());
        if (recipes.isEmpty()) return -1;

        int lowestCost = -1;

        for (RecipeHolder<Recipe<?>> holder : recipes) {
            Recipe<?> recipe = holder.value();
            boolean hiddenReagentRecipe = looksLikeHiddenReagentRecipe(recipe);

            ItemStack resultStack;
            try {
                resultStack = recipe.getResultItem(registryAccess);
            } catch (Exception e) {
                continue;
            }
            if (resultStack == null || resultStack.isEmpty()) continue;
            int resultCount = Math.max(1, resultStack.getCount());

            List<Ingredient> ingredients;
            try {
                ingredients = recipe.getIngredients();
            } catch (Exception e) {
                continue;
            }
            long ingredientCount = ingredients.stream().filter(i -> !i.isEmpty()).count();
            if (ingredientCount == 0 && !hiddenReagentRecipe) continue;

            double totalCost = 0;
            boolean validRecipe = true;

            for (Ingredient ingredient : ingredients) {
                if (ingredient.isEmpty()) continue;
                ItemStack[] matching = ingredient.getItems();
                if (matching.length == 0) continue;

                int cheapestIngCost = -1;
                for (ItemStack match : matching) {
                    if (match.isEmpty()) continue;
                    Item ingItem = match.getItem();
                    int ingCost;
                    Integer finalCached = costCache.get(ingItem);
                    if (finalCached != null) {
                        ingCost = finalCached;
                    } else if (visited.contains(ingItem)) {
                        ingCost = calculateMaterialValue(ingItem);
                    } else {
                        ingCost = computeRecipeCost(ingItem, registryAccess, maxDepth, depth + 1, new HashSet<>(visited));
                    }
                    if (ingCost <= 0) ingCost = calculateMaterialValue(ingItem);
                    if (cheapestIngCost == -1 || ingCost < cheapestIngCost) {
                        cheapestIngCost = ingCost;
                    }
                }
                if (cheapestIngCost == -1) {
                    validRecipe = false;
                    break;
                }
                totalCost += cheapestIngCost;
            }

            if (!validRecipe) continue;
            if (hiddenReagentRecipe) {
                hiddenReagentEstimatedItems.add(item);
                totalCost += estimateHiddenReagentCost(recipe);
            }
            totalCost += estimateProcessingCost(recipe, resultCount);
            int unitCost = Math.max(1, (int) Math.round(totalCost / resultCount * 1.05));
            if (lowestCost == -1 || unitCost < lowestCost) {
                lowestCost = unitCost;
            }
        }

        if (lowestCost > 0) {
            Integer existingBudget = recipeCostBudgetCache.get(item);
            if (existingBudget == null || remainingBudget > existingBudget) {
                recipeCostCache.put(item, lowestCost);
                recipeCostBudgetCache.put(item, remainingBudget);
            }
        }
        return lowestCost;
    }

    private static final double SMELTING_FUEL_COST_PER_ITEM = 45.0 / 8.0;

    private static double estimateProcessingCost(Recipe<?> recipe, int resultCount) {
        if (recipe instanceof CampfireCookingRecipe) {
            return 0.0;
        }
        if (recipe instanceof AbstractCookingRecipe) {
            return SMELTING_FUEL_COST_PER_ITEM * resultCount;
        }
        if (recipe instanceof CraftingRecipe || recipe instanceof StonecutterRecipe || recipe instanceof SmithingRecipe) {
            return 0.0;
        }
        // Modded machine: use configurable cost per operation
        return moddedProcessingCost * resultCount;
    }

    public static int calculateIntrinsicValue(Item item) {
        return applyRarityAndTypeModifiers(item, calculateMaterialValue(item));
    }

    // Extended tier detection – also uses common tags via name keywords
    private static double tierValueForName(String name) {
        double base = 20.0;
        if (name.contains("unobtainium") || name.contains("infinity") || name.contains("creative")) base = 100000;
        else if (name.contains("awakened") || name.contains("supremium") || name.contains("pellet_antimatter") || name.contains("chaotic")) base = 50000;
        else if (name.contains("vibranium") || name.contains("neutronium") || name.contains("imperium")) base = 25000;
        else if (name.contains("allthemodium") || name.contains("terrasteel") || name.contains("enderium") || name.contains("wyvern")) base = 10000;
        else if (name.contains("draconium") || name.contains("starmetal") || name.contains("netherite") || name.contains("atomic_alloy")) base = 5000;
        else if (name.contains("emerald") || name.contains("nether_star") || name.contains("dragon") || name.contains("beacon") || name.contains("cobalt") || name.contains("manyullyn") || name.contains("refined_obsidian") || name.contains("reinforced_alloy")) base = 2000;
        else if (name.contains("diamond") || name.contains("elytra") || name.contains("totem") || name.contains("heart_of_the_sea") || name.contains("star") || name.contains("refined_glowstone") || name.contains("infused_alloy") || name.contains("fluorite")) base = 1200;
        else if (name.contains("gold") || name.contains("ender_pearl") || name.contains("blaze") || name.contains("shulker") || name.contains("lumium") || name.contains("signalum") || name.contains("electrum") || name.contains("invar") || name.contains("steel") || name.contains("platinum")) base = 400;
        else if (name.contains("iron") || name.contains("redstone") || name.contains("lapis") || name.contains("quartz") || name.contains("copper") || name.contains("osmium") || name.contains("tin") || name.contains("lead") || name.contains("uranium") || name.contains("nickel") || name.contains("silver") || name.contains("zinc") || name.contains("bronze") || name.contains("constantan") || name.contains("aluminum")) base = 100;
        else if (name.contains("coal") || name.contains("amethyst") || name.contains("glowstone") || name.contains("prismarine") || name.contains("obsidian") || name.contains("slime")) base = 45;
        else if (name.contains("log") || name.contains("wood") || name.contains("leather") || name.contains("bone") || name.contains("clay") || name.contains("string")) base = 25;
        else if (name.contains("dirt") || name.contains("cobblestone") || name.contains("sand") || name.contains("gravel") || name.contains("stone") || name.contains("andesite") || name.contains("diorite") || name.contains("granite")) base = 5;
        // Additional modded materials
        else if (name.contains("certus") || name.contains("fluix") || name.contains("ender") || name.contains("chorus")) base = 80;
        else if (name.contains("silicon") || name.contains("circuit") || name.contains("processor")) base = 150;
        // Fallback for unknown modded items: if namespace is not minecraft, give a moderate base
        // but this is handled in calculateMaterialValue with a multiplier.
        return base;
    }

    public static int calculateMaterialValue(Item item) {
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(item);
        String name = loc.getPath().toLowerCase();
        String namespace = loc.getNamespace().toLowerCase();

        double base = tierValueForName(name);

        double multiplier = 1.0;
        if (name.contains("octuple_compressed")) multiplier = 43046721.0;
        else if (name.contains("septuple_compressed")) multiplier = 4782969.0;
        else if (name.contains("sextuple_compressed")) multiplier = 531441.0;
        else if (name.contains("quintuple_compressed")) multiplier = 59049.0;
        else if (name.contains("quadruple_compressed")) multiplier = 6561.0;
        else if (name.contains("triple_compressed")) multiplier = 729.0;
        else if (name.contains("double_compressed")) multiplier = 81.0;
        else if (name.contains("compressed") || name.endsWith("_block") || name.startsWith("block_") || name.contains("block_of_") || (name.contains("raw_") && name.contains("_block"))) multiplier = 9.0;
        else if (name.endsWith("_gear") || name.contains("gear_")) multiplier = 4.0;
        else if (name.endsWith("_plate") || name.contains("plate_") || name.endsWith("_dust") || name.contains("dust_") || name.startsWith("raw_")) multiplier = 1.0;
        else if (name.endsWith("_rod") || name.contains("rod_")) multiplier = 0.5;
        else if (name.endsWith("_nugget") || name.contains("nugget_") || name.endsWith("_piece") || name.endsWith("_shard")) multiplier = 1.0 / 9.0;

        base *= multiplier;

        ItemStack stack = new ItemStack(item);
        if (stack.has(DataComponents.MAX_DAMAGE)) {
            Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
            if (maxDamage != null && maxDamage > 0) {
                base += (maxDamage * 0.8);
            }
        }
        if (stack.has(DataComponents.FOOD)) {
            var food = stack.get(DataComponents.FOOD);
            if (food != null) {
                base += (food.nutrition() * 5.0) + (food.saturation() * 10.0);
            }
        }
        // For modded items that are otherwise not recognized, give a baseline so they aren't dirt cheap
        if (!namespace.equals("minecraft") && base <= 25.0 * multiplier) {
            base = 350.0 * multiplier;
        }

        return Math.max(1, (int) Math.round(base));
    }

    private static int applyRarityAndTypeModifiers(Item item, int rawCost) {
        ItemStack stack = new ItemStack(item);
        double base = rawCost;
        Rarity rarity = stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        if (rarity == Rarity.EPIC) base *= 5.0;
        else if (rarity == Rarity.RARE) base *= 3.0;
        else if (rarity == Rarity.UNCOMMON) base *= 1.5;
        if (stack.getMaxStackSize() == 1) {
            base *= 2.5;
        }
        return Math.max(1, (int) Math.round(base));
    }
}
