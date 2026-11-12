package za.co.neroland.nerofactions.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class FactionsScreenBase<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected int panelWidth = 360;
    protected int panelHeight = 248;

    protected FactionsScreenBase(T menu, Inventory inventory, Component title) {
        //? if >=26.1 {
        super(menu, inventory, title, 360, 248);
        //?} else {
        /*super(menu, inventory, title);
        this.imageWidth = 360;
        this.imageHeight = 248;
        *///?}
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(360, this.width - 8);
        this.panelHeight = Math.min(248, this.height - 8);
        super.init();
        this.leftPos = (this.width - panelWidth) / 2;
        this.topPos = (this.height - panelHeight) / 2;
    }

    protected abstract void drawBody(GuiGraphicsExtractor g);

    private void drawPanel(GuiGraphicsExtractor g) {
        g.fill(leftPos, topPos, leftPos + panelWidth, topPos + panelHeight, 0xFF79928E);
        g.fill(leftPos + 1, topPos + 1, leftPos + panelWidth - 1, topPos + panelHeight - 1, 0xFF18272D);
        g.fill(leftPos + 1, topPos + 1, leftPos + panelWidth - 1, topPos + 25, 0xFF30454C);
        g.text(font, title, leftPos + 9, topPos + 9, 0xFFF1E7CF, false);
        drawBody(g);
    }

    //? if >=26.1 {
    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g);
        super.extractContents(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }
    //?} else {
    /*@Override
    protected void renderBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        drawPanel(g);
    }

    @Override
    protected void renderLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }
    *///?}
}
