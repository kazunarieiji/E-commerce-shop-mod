package net.example.shopmod.client;

import net.example.shopmod.network.RequestReadyPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class LoadingScreen extends Screen {
    private int ticks = 0;
    private volatile int progress = 0; // 0-100, updated from ReadyResponsePayload

    public LoadingScreen() {
        super(Component.literal("Loading Shop..."));
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        // Poll server every 10 ticks (0.5 seconds)
        if (ticks % 10 == 0) {
            PacketDistributor.sendToServer(new RequestReadyPayload());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Skip the default renderBackground() call here - it triggers vanilla's menu blur
        // post-process shader, which is what causes the blurry backdrop (and costs a shader
        // pass every frame). A flat dim overlay looks cleaner and is essentially free.
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0101014);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.drawCenteredString(this.font, "Generating shop prices...", centerX, centerY - 30, 0xFFFFFF);

        // Progress bar
        int barWidth = 200;
        int barHeight = 14;
        int barX = centerX - barWidth / 2;
        int barY = centerY - 6;

        // Track
        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF2A2A30);

        // Fill
        int filledWidth = (int) (barWidth * (progress / 100.0));
        if (filledWidth > 0) {
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFF00AA55);
        }

        // Percentage label centered on the bar
        String pctText = progress + "%";
        guiGraphics.drawCenteredString(this.font, pctText, centerX, barY + 3, 0xFFFFFF);

        guiGraphics.drawCenteredString(this.font, "Please wait, this may take a moment.", centerX, barY + barHeight + 12, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
