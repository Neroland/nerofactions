package za.co.neroland.nerofactions.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import za.co.neroland.nerofactions.menu.RegistryMenu;
import za.co.neroland.nerofactions.network.StandingSnapshot;

public final class RegistryScreen extends FactionsScreenBase<RegistryMenu> {
    private int selected;
    private boolean confirming;
    private Button action;
    private final Button[] factionButtons = new Button[7];

    public RegistryScreen(RegistryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.nerofactions.contract.title"), b ->
                za.co.neroland.nerofactions.platform.Services.NETWORK.sendToServer(
                        new za.co.neroland.nerofactions.network.OpenContractBoard(menu.containerId)))
                .bounds(leftPos + panelWidth - 108, topPos + 4, 100, 18).build());
        for (int i = 0; i < factionButtons.length; i++) {
            final int slot = i;
            factionButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                selected = selected / 7 * 7 + slot;
                confirming = false;
            }).bounds(leftPos + 8, topPos + 31 + i * 17, 104, 17).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> select(-7))
                .bounds(leftPos + 8, topPos + 152, 50, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> select(7))
                .bounds(leftPos + 62, topPos + 152, 50, 18).build());
        action = addRenderableWidget(Button.builder(Component.translatable("gui.nerofactions.join"), b -> act())
                .bounds(leftPos + 8, topPos + panelHeight - 23, (panelWidth - 24) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(leftPos + panelWidth / 2 + 4, topPos + panelHeight - 23, (panelWidth - 24) / 2, 20).build());
    }

    private void select(int delta) {
        int count = FactionsClientState.standing().factions().size();
        if (count > 0) selected = Math.floorMod(selected + delta, count);
        confirming = false;
    }

    private StandingSnapshot.Row row() {
        var rows = FactionsClientState.standing().factions();
        if (rows.isEmpty()) return null;
        selected = Math.min(selected, rows.size() - 1);
        return rows.get(selected);
    }

    private void act() {
        var row = row();
        if (row == null || minecraft == null || minecraft.gameMode == null) return;
        if (!confirming) {
            confirming = true;
            return;
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected * 2 + (row.member() ? 1 : 0));
        confirming = false;
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g) {
        var row = row();
        var catalogue = FactionsClientState.standing().factions();
        for (int i = 0; i < factionButtons.length; i++) {
            int index = selected / 7 * 7 + i;
            factionButtons[i].visible = index < catalogue.size();
            if (index < catalogue.size()) {
                factionButtons[i].setMessage(Component.literal(font.plainSubstrByWidth(catalogue.get(index).displayName(), 96)));
                factionButtons[i].setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal(catalogue.get(index).displayName() + "\n" + catalogue.get(index).theme())));
                factionButtons[i].active = index != selected;
            }
        }
        action.active = row != null;
        if (row == null) {
            g.text(font, Component.translatable("gui.nerofactions.loading"), leftPos + 8, topPos + 67, 0xFFF1E7CF, false);
            return;
        }
        action.setMessage(Component.translatable(confirming ? "gui.nerofactions.confirm"
                : row.member() ? "gui.nerofactions.leave" : "gui.nerofactions.join"));
        int x = leftPos + 122;
        int w = panelWidth - 132;
        int color = row.color().matches("#[0-9a-fA-F]{6}") ? 0xFF000000 | Integer.parseInt(row.color().substring(1), 16) : 0xFF90B4A0;
        var iconId = net.minecraft.resources.Identifier.tryParse(row.icon());
        if (iconId != null) {
            var icon = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(iconId));
            //? if >=26.1 {
            g.item(icon, x, topPos + 29);
            //?} else {
            /*g.renderItem(icon, x, topPos + 29);
            *///?}
        }
        g.text(font, font.plainSubstrByWidth(row.displayName(), w - 20), x + 20, topPos + 32, color, false);
        g.text(font, font.plainSubstrByWidth(row.motto(), w), x, topPos + 45, 0xFFF1E7CF, false);
        wrapped(g, row.descriptionKey().isEmpty() ? Component.literal(row.theme()) : Component.translatable(row.descriptionKey()), x, topPos + 58, w, 4);
        wrapped(g, Component.translatable("gui.nerofactions.enemies", row.enemies().isEmpty() ? "—" : row.enemies()), x, topPos + 97, w, 2);
        g.text(font, Component.translatable("gui.nerofactions.standing", row.standing()), x, topPos + 119, 0xFFCCDBD5, false);
        Component tier = Component.translatable(row.hostile() ? "gui.nerofactions.hostile"
                : "tier.nerofactions." + new String[]{"outsider", "associate", "member", "trusted", "inner_circle"}[row.tier()]);
        g.text(font, Component.translatable("gui.nerofactions.tier_progress", tier, row.standing(), row.nextThreshold()),
                x, topPos + 131, row.hostile() ? 0xFFFFB6A0 : color, false);
        double progress = row.tier() == 4 ? 1.0 : Math.max(0.0, Math.min(1.0,
                ((double) row.standing() - row.threshold()) / Math.max(1.0, (double) row.nextThreshold() - row.threshold())));
        g.fill(x, topPos + 143, x + w, topPos + 147, 0xFF30454C);
        g.fill(x, topPos + 143, x + (int) (w * progress), topPos + 147, color);
        wrapped(g, Component.translatable("gui.nerofactions.next_rewards", row.unlocks().isEmpty() ? "—" : row.unlocks()), x, topPos + 151, w, 2);
        var snapshot = FactionsClientState.standing();
        g.text(font, Component.translatable("gui.nerofactions.leave_cost", snapshot.switchPenaltyPoints(),
                snapshot.joinCooldownMinutes()), leftPos + 8, topPos + panelHeight - 57, 0xFFFFD997, false);
        long seconds = Math.max(0L, (snapshot.cooldownUntil() - System.currentTimeMillis()) / 1000L);
        if (seconds > 0) {
            g.text(font, Component.translatable("gui.nerofactions.cooldown", seconds),
                    leftPos + 8, topPos + panelHeight - 44, 0xFFFFB6A0, false);
        }
        if (confirming) {
            g.text(font, Component.translatable("gui.nerofactions.confirm_hint"), leftPos + 8, topPos + panelHeight - 33, 0xFFFFD997, false);
        } else if (!menu.feedbackKey().isEmpty()) {
            var rows = FactionsClientState.standing().factions();
            int index = menu.feedbackFactionIndex();
            String name = index >= 0 && index < rows.size() ? rows.get(index).displayName() : "";
            String result = Component.translatable(menu.feedbackKey(), name).getString();
            g.text(font, font.plainSubstrByWidth(result, panelWidth - 16), leftPos + 8, topPos + panelHeight - 33, 0xFFF1E7CF, false);
        }
    }

    private void wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int maxLines) {
        var lines = font.split(text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            g.text(font, lines.get(i), x, y + i * 9, 0xFFCCDBD5, false);
        }
    }
}
