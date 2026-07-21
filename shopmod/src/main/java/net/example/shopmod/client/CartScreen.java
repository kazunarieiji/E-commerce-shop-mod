package net.example.shopmod.client;

import net.example.shopmod.CartMenu;
import net.example.shopmod.ShopEntry;
import net.example.shopmod.network.OpenShopPayload;
import net.example.shopmod.network.ShopCartActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CartScreen extends AbstractContainerScreen<CartMenu> {

    private static final int ROW_HEIGHT = 22;

    public CartScreen(CartMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 340;
        this.imageHeight = 210;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        refreshCartWidgets();
    }

    private void refreshCartWidgets() {
        this.renderables.removeIf(w -> w instanceof Button);
        this.children().removeIf(w -> w instanceof Button);

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        Map<Integer, Integer> cart = ClientCartData.getCart();
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(cart.entrySet());

        int y = top + 30;
        for (Map.Entry<Integer, Integer> entry : entries) {
            int index = entry.getKey();
            int qtyX = left + imageWidth - 80;
            this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                        int newQty = cart.getOrDefault(index, 0) - (Screen.hasShiftDown() ? 16 : 1);
                        ClientCartData.setItem(index, newQty);
                        refreshCartWidgets();
                    }).bounds(qtyX, y, 16, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                        int newQty = cart.getOrDefault(index, 0) + (Screen.hasShiftDown() ? 16 : 1);
                        ClientCartData.setItem(index, newQty);
                        refreshCartWidgets();
                    }).bounds(qtyX + 46, y, 16, 18).build());
            y += ROW_HEIGHT;
        }

        int barY = top + imageHeight - 36;
        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
                    ClientCartData.clear();
                    refreshCartWidgets();
                }).bounds(left + 10, barY, 50, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Trade-In (Sell)"), b -> checkout(false))
                .bounds(left + 65, barY, 110, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Checkout (Buy)"), b -> checkout(true))
                .bounds(left + 180, barY, 105, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                        PacketDistributor.sendToServer(new OpenShopPayload()))
                .bounds(left + 290, barY, 40, 18).build());
    }

    private void checkout(boolean isBuy) {
        Map<Integer, Integer> cart = ClientCartData.getCart();
        if (cart.isEmpty()) return;

        List<Integer> indices = new ArrayList<>(cart.keySet());
        List<Integer> quantities = new ArrayList<>(cart.values());
        PacketDistributor.sendToServer(new ShopCartActionPayload(indices, quantities, isBuy));
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
        }
        ClientCartData.clear();
        refreshCartWidgets();
    }

    private long getBuyTotal(Map<Integer, Integer> cart) {
        long t = 0;
        List<ShopEntry> entries = ClientShopData.getEntries();
        for (Map.Entry<Integer, Integer> e : cart.entrySet()) {
            if (e.getKey() < entries.size()) {
                t += (long) entries.get(e.getKey()).buyPricePerItem * e.getValue();
            }
        }
        return t;
    }

    private long getSellTotal(Map<Integer, Integer> cart) {
        long t = 0;
        List<ShopEntry> entries = ClientShopData.getEntries();
        for (Map.Entry<Integer, Integer> e : cart.entrySet()) {
            if (e.getKey() < entries.size()) {
                t += (long) entries.get(e.getKey()).sellPricePerItem * e.getValue();
            }
        }
        return t;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xF018181C);
        guiGraphics.fill(left, top, left + imageWidth, top + 18, 0xFF2A2A30);

        String balanceText = "Balance: " + ClientShopData.getBalance() + " Coins";
        guiGraphics.drawString(this.font, balanceText, left + imageWidth - this.font.width(balanceText) - 8, 5, 0xFFFFD700, false);

        Map<Integer, Integer> cart = ClientCartData.getCart();
        if (cart.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Your Shopping Cart is Empty", left + imageWidth / 2, top + 60, 0xAAAAAA);
        } else {
            int y = top + 30;
            List<ShopEntry> entries = ClientShopData.getEntries();

            for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
                if (entry.getKey() >= entries.size()) continue;
                ShopEntry shopEntry = entries.get(entry.getKey());
                ItemStack display = shopEntry.createStack(1);
                guiGraphics.renderItem(display, left + 8, y);
                guiGraphics.renderItemDecorations(this.font, display, left + 8, y);
                guiGraphics.drawString(this.font, display.getHoverName(), left + 30, y + 1, 0xFFFFFF, false);

                String qtyText = "x" + entry.getValue();
                guiGraphics.drawString(this.font, qtyText, left + imageWidth - 56, y + 1, 0xFF00FF88, false);
                y += ROW_HEIGHT;
            }

            long buyTotal = getBuyTotal(cart);
            long sellTotal = getSellTotal(cart);
            String summary = "Subtotal: " + buyTotal + " Coins  |  Trade-In Value: " + sellTotal + " Coins";
            guiGraphics.drawString(this.font, summary, left + 10, top + imageHeight - 48, 0xFFFFFF, false);
        }
    }

    @Override protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 5, 0xFFFFFF, false);
    }

    @Override public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return false; }
}
