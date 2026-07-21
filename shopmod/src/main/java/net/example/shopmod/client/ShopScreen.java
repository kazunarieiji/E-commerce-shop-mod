package net.example.shopmod.client;

import net.example.shopmod.ShopCategory;
import net.example.shopmod.ShopEntry;
import net.example.shopmod.ShopMenu;
import net.example.shopmod.network.OpenCartPayload;
import net.example.shopmod.network.ShopBuyCardPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class ShopScreen extends AbstractContainerScreen<ShopMenu> {

    private static final int ROWS_PER_PAGE = 7;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 52;
    private static final int MAX_CART_PER_ITEM = 999;

    private EditBox searchBox;
    private int page = 0;
    private ShopCategory selectedCategory = ShopCategory.ALL;
    private int sortMode = 0;

    private List<Integer> filteredIndices = new ArrayList<>();
    private final Map<Integer, Integer> localAddCart = new LinkedHashMap<>();

    public ShopScreen(ShopMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 360;
        this.imageHeight = LIST_TOP + ROWS_PER_PAGE * ROW_HEIGHT + 26 + 46;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        searchBox = new EditBox(this.font, left + 10, top + 20, 160, 16, Component.literal("Search Market"));
        searchBox.setHint(Component.literal("Search items..."));
        searchBox.setResponder(text -> {
            page = 0;
            refreshFilter();
            rebuildRowWidgets();
        });
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(Component.literal(getSortLabel()), b -> {
            sortMode = (sortMode + 1) % 3;
            b.setMessage(Component.literal(getSortLabel()));
            refreshFilter();
            rebuildRowWidgets();
        }).bounds(left + 175, top + 19, 85, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cat: " + selectedCategory.name()), b -> {
            ShopCategory[] values = ShopCategory.values();
            selectedCategory = values[(selectedCategory.ordinal() + 1) % values.length];
            b.setMessage(Component.literal("Cat: " + selectedCategory.name()));
            page = 0;
            refreshFilter();
            rebuildRowWidgets();
        }).bounds(left + 265, top + 19, 85, 18).build());

        refreshFilter();
        rebuildRowWidgets();
    }

    private String getSortLabel() {
        if (sortMode == 1) return "Sort: $ Low";
        if (sortMode == 2) return "Sort: $ High";
        return "Sort: A-Z";
    }

    private void refreshFilter() {
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        List<ShopEntry> all = ClientShopData.getEntries();
        filteredIndices = new ArrayList<>();

        for (int i = 0; i < all.size(); i++) {
            ShopEntry entry = all.get(i);
            String name = entry.createStack(1).getHoverName().getString().toLowerCase();
            String mod = entry.modId.toLowerCase();

            boolean matchCategory = selectedCategory == ShopCategory.ALL || entry.category.equalsIgnoreCase(selectedCategory.name());
            boolean matchQuery = query.isEmpty() || name.contains(query) || mod.contains(query);

            if (matchCategory && matchQuery) {
                filteredIndices.add(i);
            }
        }

        if (sortMode == 1) {
            filteredIndices.sort(Comparator.comparingInt(i -> all.get(i).buyPricePerItem));
        } else if (sortMode == 2) {
            filteredIndices.sort((i1, i2) -> Integer.compare(all.get(i2).buyPricePerItem, all.get(i1).buyPricePerItem));
        }
    }

    private int maxPage() {
        return Math.max(0, (filteredIndices.size() - 1) / ROWS_PER_PAGE);
    }

    private void rebuildRowWidgets() {
        this.renderables.removeIf(w -> w instanceof Button && !((Button) w).getMessage().getString().startsWith("Sort") && !((Button) w).getMessage().getString().startsWith("Cat"));
        this.children().removeIf(w -> w instanceof Button && !((Button) w).getMessage().getString().startsWith("Sort") && !((Button) w).getMessage().getString().startsWith("Cat"));

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, filteredIndices.size());

        for (int row = 0; start + row < end; row++) {
            int entryIndex = filteredIndices.get(start + row);
            int rowY = top + LIST_TOP + row * ROW_HEIGHT;
            int qtyControlsX = left + imageWidth - 110;

            this.addRenderableWidget(Button.builder(Component.literal("-"), b ->
                            adjustLocalCart(entryIndex, Screen.hasShiftDown() ? -16 : -1))
                    .bounds(qtyControlsX, rowY + 2, 16, 18).build());

            this.addRenderableWidget(Button.builder(Component.literal("+1"), b ->
                            adjustLocalCart(entryIndex, 1))
                    .bounds(qtyControlsX + 38, rowY + 2, 22, 18).build());

            this.addRenderableWidget(Button.builder(Component.literal("+16"), b ->
                            adjustLocalCart(entryIndex, 16))
                    .bounds(qtyControlsX + 62, rowY + 2, 24, 18).build());

            this.addRenderableWidget(Button.builder(Component.literal("+64"), b ->
                            adjustLocalCart(entryIndex, 64))
                    .bounds(qtyControlsX + 88, rowY + 2, 24, 18).build());
        }

        int pagingY = top + LIST_TOP + ROWS_PER_PAGE * ROW_HEIGHT + 4;

        this.addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
            if (page > 0) { page--; rebuildRowWidgets(); }
        }).bounds(left + 10, pagingY, 60, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            if (page < maxPage()) { page++; rebuildRowWidgets(); }
        }).bounds(left + imageWidth - 70, pagingY, 60, 18).build());

        int barY = top + imageHeight - 46;

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> localAddCart.clear())
                .bounds(left + 10, barY + 20, 50, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Get e-Card"), b ->
                        PacketDistributor.sendToServer(new ShopBuyCardPayload()))
                .bounds(left + 65, barY + 20, 75, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Add to Cart"), b -> addToCart())
                .bounds(left + 145, barY + 20, 80, 18).build());

        int cartSize = ClientCartData.getCart().values().stream().mapToInt(Integer::intValue).sum();
        String cartButtonText = cartSize > 0 ? "🛒 Cart (" + cartSize + ")" : "🛒 Cart";
        this.addRenderableWidget(Button.builder(Component.literal(cartButtonText), b ->
                        PacketDistributor.sendToServer(new OpenCartPayload()))
                .bounds(left + 230, barY + 20, 115, 18).build());
    }

    private void adjustLocalCart(int entryIndex, int delta) {
        int current = localAddCart.getOrDefault(entryIndex, 0);
        int next = Math.max(0, Math.min(MAX_CART_PER_ITEM, current + delta));
        if (next == 0) localAddCart.remove(entryIndex);
        else localAddCart.put(entryIndex, next);
    }

    private void addToCart() {
        if (localAddCart.isEmpty()) return;
        for (Map.Entry<Integer, Integer> e : localAddCart.entrySet()) ClientCartData.addToCart(e.getKey(), e.getValue());
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
        localAddCart.clear();
        rebuildRowWidgets();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xF018181C);
        guiGraphics.fill(left, top, left + imageWidth, top + 18, 0xFF2A2A30);

        String balanceText = "Balance: " + ClientShopData.getBalance() + " Coins";
        int balanceWidth = this.font.width(balanceText);
        guiGraphics.drawString(this.font, balanceText, left + imageWidth - balanceWidth - 8, 5, 0xFFFFD700, false);
        guiGraphics.drawString(this.font, "⚡ Prime Delivery: FREE", left + 8, 5, 0xFF00FF88, false);

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, filteredIndices.size());

        List<ShopEntry> entries = ClientShopData.getEntries();

        for (int row = 0; start + row < end; row++) {
            int entryIndex = filteredIndices.get(start + row);
            if (entryIndex >= entries.size()) continue;
            ShopEntry entry = entries.get(entryIndex);
            int rowY = top + LIST_TOP + row * ROW_HEIGHT;

            if (row % 2 == 0) guiGraphics.fill(left + 6, rowY - 1, left + imageWidth - 6, rowY + ROW_HEIGHT - 3, 0x1AFFFFFF);

            ItemStack display = new ItemStack(entry.item, 1);
            guiGraphics.renderItem(display, left + 8, rowY);
            guiGraphics.renderItemDecorations(this.font, display, left + 8, rowY);

            String displayName = display.getHoverName().getString();
            if (displayName.length() > 18) displayName = displayName.substring(0, 16) + "..";
            guiGraphics.drawString(this.font, displayName, left + 30, rowY + 1, 0xFFFFFF, false);

            String modBadge = "[" + entry.modId + "]";
            guiGraphics.drawString(this.font, modBadge, left + 140, rowY + 1, 0xAA888888, false);

            String priceText = "Buy " + entry.buyPricePerItem + "c | Sell " + entry.sellPricePerItem + "c";
            guiGraphics.drawString(this.font, priceText, left + 30, rowY + 12, 0xFFD0D0D0, false);

            int qty = localAddCart.getOrDefault(entryIndex, 0);
            int qtyControlsX = left + imageWidth - 110;
            String qtyText = String.valueOf(qty);
            int qtyTextWidth = this.font.width(qtyText);
            guiGraphics.drawString(this.font, qtyText, qtyControlsX + 22 - qtyTextWidth / 2, rowY + 6, qty > 0 ? 0xFF00FF88 : 0xAAAAAA, false);
        }

        int pagingY = top + LIST_TOP + ROWS_PER_PAGE * ROW_HEIGHT + 4;
        String pageLabel = "Page " + (page + 1) + " / " + (maxPage() + 1) + "  (" + filteredIndices.size() + " products)";
        int pageLabelWidth = this.font.width(pageLabel);
        guiGraphics.drawString(this.font, pageLabel, left + (imageWidth - pageLabelWidth) / 2, pagingY + 5, 0xAAAAAA, false);

        int barY = top + imageHeight - 46;
        guiGraphics.fill(left, barY, left + imageWidth, top + imageHeight, 0xFF202025);

        if (localAddCart.isEmpty()) {
            guiGraphics.drawString(this.font, "Select item quantities then click 'Add to Cart'", left + 10, barY + 5, 0xAAAAAA, false);
        } else {
            int itemCount = localAddCart.values().stream().mapToInt(Integer::intValue).sum();
            String summary = itemCount + " item" + (itemCount == 1 ? "" : "s") + " ready to add";
            guiGraphics.drawString(this.font, summary, left + 10, barY + 5, 0xFF00FF88, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int start = page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, filteredIndices.size());
        List<ShopEntry> entries = ClientShopData.getEntries();

        for (int row = 0; start + row < end; row++) {
            int entryIndex = filteredIndices.get(start + row);
            if (entryIndex >= entries.size()) continue;
            ShopEntry entry = entries.get(entryIndex);
            int rowY = top + LIST_TOP + row * ROW_HEIGHT;

            if (mouseX >= left + 6 && mouseX <= left + 240 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                List<Component> tooltip = new ArrayList<>();
                ItemStack stack = entry.createStack(1);
                tooltip.add(stack.getHoverName());
                tooltip.add(Component.literal("§7Mod: §f" + entry.modId));
                tooltip.add(Component.literal("§7Category: §e" + entry.category));
                tooltip.add(Component.literal("§aBuy Price: §f" + entry.buyPricePerItem + " Coins"));
                tooltip.add(Component.literal("§cSell Price: §f" + entry.sellPricePerItem + " Coins"));

                int currentQty = localAddCart.getOrDefault(entryIndex, 0);
                if (currentQty > 0) {
                    tooltip.add(Component.literal("§bSelected: §f" + currentQty + " (§6Total Buy: " + ((long) currentQty * entry.buyPricePerItem) + " Coins§f)"));
                }
                guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                break;
            }
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    @Override public boolean isPauseScreen() { return false; }
}
