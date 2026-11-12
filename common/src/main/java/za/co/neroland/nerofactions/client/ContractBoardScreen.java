package za.co.neroland.nerofactions.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import za.co.neroland.nerofactions.menu.ContractBoardMenu;
import za.co.neroland.nerofactions.network.ContractIntent;
import za.co.neroland.nerofactions.network.ContractSnapshot;
import za.co.neroland.nerofactions.platform.Services;

public final class ContractBoardScreen extends FactionsScreenBase<ContractBoardMenu> {
    private int page;
    private final Button[] actions = new Button[3];
    private final Button[] rerolls = new Button[3];
    private int waiting;
    private ContractSnapshot observed;

    public ContractBoardScreen(ContractBoardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(-1))
                .bounds(leftPos + 8, topPos + 29, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(1))
                .bounds(leftPos + panelWidth - 32, topPos + 29, 24, 18).build());
        for (int i = 0; i < 3; i++) {
            final int row = i;
            actions[i] = addRenderableWidget(Button.builder(Component.empty(), b -> act(row, false))
                    .bounds(leftPos + panelWidth - 108, topPos + 57 + i * 45, 100, 18).build());
            rerolls[i] = addRenderableWidget(Button.builder(Component.translatable("gui.nerofactions.contract.reroll"), b -> act(row, true))
                    .bounds(leftPos + panelWidth - 108, topPos + 77 + i * 45, 100, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(leftPos + 8, topPos + panelHeight - 23, panelWidth - 16, 20).build());
    }

    private List<Identifier> factions() {
        return FactionsClientState.contracts().entries().stream().map(ContractSnapshot.Row::faction).distinct().sorted().toList();
    }

    private List<ContractSnapshot.Row> rows() {
        var factions = factions();
        if (factions.isEmpty()) return List.of();
        page = Math.min(page, factions.size() - 1);
        var faction = factions.get(page);
        return FactionsClientState.contracts().entries().stream().filter(e -> e.faction().equals(faction)).toList();
    }

    private void page(int change) {
        int count = factions().size();
        if (count > 0) page = Math.floorMod(page + change, count);
    }

    private void act(int index, boolean reroll) {
        var rows = rows();
        if (index >= rows.size() || waiting > 0) return;
        var row = rows.get(index);
        observed = FactionsClientState.contracts();
        waiting = 100;
        Services.NETWORK.sendToServer(new ContractIntent(menu.containerId, row.faction(), row.id(),
                observed.day(), row.slot(), reroll ? 2 : row.progress() >= row.count() ? 1 : 0));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (observed != FactionsClientState.contracts()) waiting = 0;
        else if (waiting > 0) waiting--;
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g) {
        var snapshot = FactionsClientState.contracts();
        var rows = rows();
        for (int i = 0; i < actions.length; i++) {
            actions[i].visible = i < rows.size();
            rerolls[i].visible = i < rows.size();
            if (i >= rows.size()) continue;
            var row = rows.get(i);
            actions[i].setMessage(Component.translatable(row.claimed() ? "gui.nerofactions.contract.claimed"
                    : row.progress() >= row.count() ? "gui.nerofactions.contract.claim"
                    : row.type().equals("deliver") ? "gui.nerofactions.contract.deliver" : "gui.nerofactions.contract.in_progress"));
            actions[i].active = waiting == 0 && !row.claimed() && (row.type().equals("deliver") || row.progress() >= row.count());
            rerolls[i].active = waiting == 0 && snapshot.rerollAvailable() && !row.claimed() && row.progress() == 0;
            int y = topPos + 56 + i * 45;
            g.fill(leftPos + 8, y - 2, leftPos + panelWidth - 8, y + 40, 0xFF22373D);
            String target = row.target().replace("#", "");
            Identifier id = Identifier.tryParse(target);
            if (id != null) target = id.getPath().replace('_', ' ');
            String task = Component.translatable("gui.nerofactions.contract.type." + row.type()).getString() + ": " + target;
            g.text(font, font.plainSubstrByWidth(task, panelWidth - 130), leftPos + 13, y + 2, 0xFFF1E7CF, false);
            g.text(font, row.progress() + " / " + row.count(), leftPos + 13, y + 14, 0xFFAAD6C5, false);
            g.text(font, Component.translatable("gui.nerofactions.contract.reward", row.reputation(), row.credits()),
                    leftPos + 13, y + 27, 0xFFCCDBD5, false);
        }
        if (rows.isEmpty()) {
            g.text(font, Component.translatable("gui.nerofactions.contract.empty"), leftPos + 8, topPos + 62, 0xFFF1E7CF, false);
        } else {
            var faction = rows.get(0).faction();
            String name = FactionsClientState.standing().factions().stream().filter(f -> f.faction().equals(faction))
                    .map(f -> f.displayName()).findFirst().orElse(faction.getPath().replace('_', ' '));
            g.text(font, font.plainSubstrByWidth(name, panelWidth - 90), leftPos + 42, topPos + 34, 0xFFF1E7CF, false);
        }
        String key = waiting > 0 ? "gui.nerofactions.contract.waiting" : snapshot.feedback();
        if (!key.isEmpty()) g.text(font, font.plainSubstrByWidth(Component.translatable(key).getString(), panelWidth - 16),
                leftPos + 8, topPos + panelHeight - 37, 0xFFFFD997, false);
    }
}
