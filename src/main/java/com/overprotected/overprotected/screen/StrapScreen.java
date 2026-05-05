package com.overprotected.overprotected.screen;

import com.overprotected.overprotected.OverProtected;
import com.overprotected.overprotected.item.StrapTier;
import com.overprotected.overprotected.menu.StrapMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class StrapScreen extends AbstractContainerScreen<StrapMenu> {

    // One 256×256 texture per tier.
    // Netherite uses a 176×184 panel (extra row), all others use 176×166.
    private static ResourceLocation textureFor(StrapTier tier) {
        String name = tier.name().toLowerCase() + "_strap_gui";
        return ResourceLocation.fromNamespaceAndPath(OverProtected.MODID, "textures/gui/" + name + ".png");
    }

    private final ResourceLocation texture;

    public StrapScreen(StrapMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.texture = textureFor(menu.getStrapTier());
        this.imageWidth = 176;
        this.imageHeight = menu.isTwoRows() ? 158 : 140;
        // "Inventory" label: 12px above the player inventory row (matches vanilla convention).
        this.inventoryLabelY = menu.isTwoRows() ? 64 : 46;
    }

    @Override
    protected void init() {
        super.init();
        // Center the container title horizontally.
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(texture, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}

