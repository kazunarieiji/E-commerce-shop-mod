package net.example.shopmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.example.shopmod.ShopCategory;
import net.example.shopmod.ShopPricing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShopConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("shopmod-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("shopmod-prices.json");

    public static class ConfigItemEntry {
        public int buy_price;
        public int sell_price;
        public int base_cost;
        public String category;
        public boolean manual;

        public ConfigItemEntry(int buy, int sell, int base, String category, boolean manual) {
            this.buy_price = buy;
            this.sell_price = sell;
            this.base_cost = base;
            this.category = category;
            this.manual = manual;
        }
    }

    public static boolean autoGenerateMissing = true;
    public static boolean enableRecipeHeuristics = true;
    public static int maxRecipeScanDepth = 20;
    public static double defaultBuyMargin = 1.5;
    public static double defaultSellMargin = 0.5;
    // Added processing cost multiplier for modded machines (can be overridden in config)
    public static double moddedProcessingCost = 25.0;

    private static final Map<String, ConfigItemEntry> PRICE_MAP = new TreeMap<>();

    private static final AtomicBoolean isReady = new AtomicBoolean(false);
    private static final AtomicInteger progress = new AtomicInteger(0);
    private static CompletableFuture<Void> generationTask = null;

    // Dedicated low-priority thread so the (potentially slow) recipe scan never
    // competes with Minecraft/other mods' work on the shared ForkJoinPool.commonPool().
    private static final ThreadFactory PRICE_THREAD_FACTORY = r -> {
        Thread t = new Thread(r, "shopmod-price-gen");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    };
    private static final Executor PRICE_EXECUTOR = Executors.newSingleThreadExecutor(PRICE_THREAD_FACTORY);

    public static boolean isReady() { return isReady.get(); }
    public static int getProgress() { return progress.get(); }

    public static synchronized void initAsync(ServerLevel level) {
        if (generationTask != null && !generationTask.isDone()) {
            LOGGER.info("[shopmod] Price generation already running.");
            return;
        }
        progress.set(0);
        isReady.set(false);
        generationTask = CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("[shopmod] Starting background price generation...");
                loadConfig();
                if (autoGenerateMissing && level != null) {
                    generateMissingPrices(level);
                    saveConfig();
                }
                isReady.set(true);
                progress.set(100);
                LOGGER.info("[shopmod] Price generation completed.");
            } catch (Exception e) {
                LOGGER.error("[shopmod] Background price generation failed!", e);
                isReady.set(true);
                progress.set(100);
            }
        }, PRICE_EXECUTOR);
    }

    public static synchronized void reloadAsync(ServerLevel level) {
        if (generationTask != null && !generationTask.isDone()) {
            LOGGER.warn("[shopmod] Reload requested while generation is still running – cancelling old task.");
            generationTask.cancel(true);
        }
        progress.set(0);
        isReady.set(false);
        generationTask = CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("[shopmod] Reloading prices...");
                PRICE_MAP.clear();
                loadConfig();
                if (autoGenerateMissing && level != null) {
                    generateMissingPrices(level);
                    saveConfig();
                }
                isReady.set(true);
                progress.set(100);
                LOGGER.info("[shopmod] Reload completed.");
            } catch (Exception e) {
                LOGGER.error("[shopmod] Reload failed!", e);
                isReady.set(true);
                progress.set(100);
            }
        }, PRICE_EXECUTOR);
    }

    public static synchronized void setPrice(String itemId, int buyPrice, int sellPrice) {
        ConfigItemEntry existing = PRICE_MAP.get(itemId);
        String category = existing != null ? existing.category : "MISC";
        int base = (int) Math.round(buyPrice / defaultBuyMargin);
        PRICE_MAP.put(itemId, new ConfigItemEntry(buyPrice, sellPrice, base, category, true));
        saveConfig();
    }

    private static void loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            LOGGER.info("[shopmod] No existing price config at {}, one will be generated.", CONFIG_FILE);
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("auto_generate_missing_prices")) {
                autoGenerateMissing = root.get("auto_generate_missing_prices").getAsBoolean();
            }
            if (root.has("enable_recipe_heuristics")) {
                enableRecipeHeuristics = root.get("enable_recipe_heuristics").getAsBoolean();
            }
            if (root.has("max_recipe_scan_depth")) {
                maxRecipeScanDepth = root.get("max_recipe_scan_depth").getAsInt();
            }
            if (root.has("default_buy_margin")) {
                defaultBuyMargin = root.get("default_buy_margin").getAsDouble();
            }
            if (root.has("default_sell_margin")) {
                defaultSellMargin = root.get("default_sell_margin").getAsDouble();
            }
            if (root.has("modded_processing_cost")) {
                moddedProcessingCost = root.get("modded_processing_cost").getAsDouble();
            }

            if (root.has("items")) {
                JsonObject itemsObj = root.getAsJsonObject("items");
                for (String key : itemsObj.keySet()) {
                    JsonObject entryObj = itemsObj.getAsJsonObject(key);
                    int buy = entryObj.has("buy_price") ? entryObj.get("buy_price").getAsInt() : 100;
                    int sell = entryObj.has("sell_price") ? entryObj.get("sell_price").getAsInt() : 40;
                    int base = entryObj.has("base_cost") ? entryObj.get("base_cost").getAsInt() : 66;
                    String category = entryObj.has("category") ? entryObj.get("category").getAsString() : "MISC";
                    boolean manual = entryObj.has("manual") && entryObj.get("manual").getAsBoolean();

                    PRICE_MAP.put(key, new ConfigItemEntry(buy, sell, base, category, manual));
                }
            }
        } catch (Exception e) {
            LOGGER.error("[shopmod] Failed to load price config from {} - existing prices will not be applied!", CONFIG_FILE, e);
        }
    }

    private static void generateMissingPrices(ServerLevel level) {
        var recipeManager = level.getRecipeManager();
        var registryAccess = level.registryAccess();

        // Pass processing cost to ShopPricing
        ShopPricing.setModdedProcessingCost(moddedProcessingCost);

        if (enableRecipeHeuristics) ShopPricing.initCache(recipeManager, registryAccess);

        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) items.add(item);
        }
        int total = items.size();
        AtomicInteger processedCounter = new AtomicInteger(0);

        // Scan in parallel across a handful of low-priority worker threads.
        int workerCount = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService scanPool = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, "shopmod-price-scan-worker");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Item item : items) {
                futures.add(scanPool.submit(() -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    String itemId = id.toString();

                    ConfigItemEntry existing;
                    synchronized (PRICE_MAP) {
                        existing = PRICE_MAP.get(itemId);
                    }
                    if (existing == null || !existing.manual) {
                        int baseCost;
                        if (enableRecipeHeuristics) {
                            baseCost = ShopPricing.calculateItemBaseCost(item, registryAccess, maxRecipeScanDepth);
                        } else {
                            baseCost = ShopPricing.calculateIntrinsicValue(item);
                        }

                        int buyPrice = (int) Math.max(1, Math.round(baseCost * defaultBuyMargin));
                        int sellPrice = (int) Math.max(1, Math.round(baseCost * defaultSellMargin));
                        String category = ShopCategory.detectCategory(item).name();

                        synchronized (PRICE_MAP) {
                            PRICE_MAP.put(itemId, new ConfigItemEntry(buyPrice, sellPrice, baseCost, category, false));
                        }
                    }

                    int processed = processedCounter.incrementAndGet();
                    progress.set((int) ((double) processed / total * 100));
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    LOGGER.error("[shopmod] Item price scan task failed", e);
                }
            }
        } finally {
            scanPool.shutdown();
        }

        if (enableRecipeHeuristics) {
            logHiddenReagentItems();
            ShopPricing.clearCache();
        }
    }

    private static void logHiddenReagentItems() {
        Set<net.minecraft.world.item.Item> flagged = ShopPricing.getAndClearHiddenReagentEstimatedItems();
        if (flagged.isEmpty()) return;

        List<String> ids = new ArrayList<>();
        for (net.minecraft.world.item.Item item : flagged) {
            ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
        }
        Collections.sort(ids);

        LOGGER.info("[shopmod] {} item(s) had a recipe with a non-vanilla secondary input "
                + "(e.g. a Mekanism chemical/infusion machine). Their price includes an automatic "
                + "estimate for that reagent (via reflection + name-based valuation), so it may "
                + "not be perfectly exact, but no manual action is needed. If any of these look "
                + "wrong, they can still be corrected with /shop setprice <item> <buy> <sell>:",
                ids.size());
        for (String id : ids) {
            LOGGER.info("[shopmod]   - {}", id);
        }
    }

    public static void saveConfig() {
        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();
            root.addProperty("auto_generate_missing_prices", autoGenerateMissing);
            root.addProperty("enable_recipe_heuristics", enableRecipeHeuristics);
            root.addProperty("max_recipe_scan_depth", maxRecipeScanDepth);
            root.addProperty("default_buy_margin", defaultBuyMargin);
            root.addProperty("default_sell_margin", defaultSellMargin);
            root.addProperty("modded_processing_cost", moddedProcessingCost);

            JsonObject itemsObj = new JsonObject();
            for (Map.Entry<String, ConfigItemEntry> entry : PRICE_MAP.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("buy_price", entry.getValue().buy_price);
                obj.addProperty("sell_price", entry.getValue().sell_price);
                obj.addProperty("base_cost", entry.getValue().base_cost);
                obj.addProperty("category", entry.getValue().category);
                obj.addProperty("manual", entry.getValue().manual);
                itemsObj.add(entry.getKey(), obj);
            }
            root.add("items", itemsObj);

            try (var writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            LOGGER.info("[shopmod] Saved price config to {} ({} items).", CONFIG_FILE, PRICE_MAP.size());
        } catch (IOException e) {
            LOGGER.error("[shopmod] Failed to write price config to {} - check that the config directory is writable!", CONFIG_FILE, e);
        }
    }

    public static Map<String, ConfigItemEntry> getPriceMap() {
        return Collections.unmodifiableMap(PRICE_MAP);
    }

    public static ConfigItemEntry getEntry(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return PRICE_MAP.getOrDefault(id, new ConfigItemEntry(100, 40, 66, "MISC", false));
    }
}
