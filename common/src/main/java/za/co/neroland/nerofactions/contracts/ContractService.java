package za.co.neroland.nerofactions.contracts;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.data.ContractState;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerofactions.reputation.ReputationSources;
import za.co.neroland.nerolandcore.economy.CoreCurrencies;
import za.co.neroland.nerolandcore.economy.CurrencyApi;
import za.co.neroland.nerolandcore.event.ThresholdEvents;

public final class ContractService {
    public static final Identifier COMPLETED_CHANNEL = Identifier.parse("nerofactions:contract_completed");

    private ContractService() {
    }

    public static List<ContractState.Entry> current(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null || !server.isSameThread() || !NeroFactionsConfig.CONTRACTS_ENABLED.get()) return List.of();
        var state = ContractState.get(server);
        var memberships = FactionMembershipState.get(server).membershipsOf(player.getUUID());
        long day = worldDay(server);
        state.expireDay(player.getUUID(), day);
        var definitions = FactionDefinitions.factionsForServer(server);
        var pool = List.copyOf(ContractDefinitions.forServer(server).values());
        for (var faction : memberships) {
            var definition = definitions.get(faction);
            if (definition == null) continue;
            int standing = FactionReputationState.get(server).getReputation(player.getUUID(), faction);
            state.ensure(player.getUUID(), faction, FactionTiers.tierOf(definition, standing).ordinal(),
                    server.overworld().getSeed(), day, pool);
        }
        return state.entries(player.getUUID()).stream()
                .filter(e -> memberships.contains(e.contract().faction()) && definitions.containsKey(e.contract().faction()))
                .toList();
    }

    public static boolean itemMatches(ItemStack stack, String target) {
        if (stack.isEmpty()) return false;
        boolean tag = target.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? target.substring(1) : target);
        if (id == null) return false;
        return tag ? stack.is(TagKey.create(Registries.ITEM, id))
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    private static ContractState.Entry authorised(ServerPlayer player, Identifier faction, int slot) {
        return current(player).stream().filter(e -> e.contract().faction().equals(faction) && e.slot() == slot)
                .findFirst().orElse(null);
    }

    public static int deliver(ServerPlayer player, Identifier faction, int slot) {
        var entry = authorised(player, faction, slot);
        if (entry == null || entry.claimed() || !entry.contract().type().equals("deliver")) return 0;
        int remaining = entry.contract().count() - entry.progress();
        int removed = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()) && removed < remaining; i++) {
            var stack = inventory.getItem(i);
            if (itemMatches(stack, entry.contract().target())) {
                int take = Math.min(stack.getCount(), remaining - removed);
                removed += inventory.removeItem(i, take).getCount();
            }
        }
        if (removed > 0) {
            inventory.setChanged();
            ContractState.get(player.level().getServer()).advance(player.getUUID(), faction, slot, removed);
        }
        return removed;
    }

    public static String claim(ServerPlayer player, Identifier faction, int slot) {
        var entry = authorised(player, faction, slot);
        if (entry == null || !entry.ready()) return "gui.nerofactions.contract.not_ready";
        var server = player.level().getServer();
        var contract = ContractState.get(server).reserveReward(player.getUUID(), faction, slot);
        if (contract == null) return "gui.nerofactions.contract.not_ready";
        try {
            ReputationSources.award(server, player.getUUID(), faction, ReputationSources.Source.QUEST, contract.reputation());
            boolean paid = contract.credits() == 0 || !CurrencyApi.hasRealProvider()
                    || CurrencyApi.deposit(player.getUUID(), CoreCurrencies.CREDITS, contract.credits(), "nerofactions", "contract_reward");
            if (contract.item().isPresent()) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(contract.item().get()), contract.itemCount());
                //? if >=26.3 {
                /*player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
                *///?} else {
                player.getInventory().placeItemBackInInventory(stack);
                //?}
            }
            ThresholdEvents.fire(NeroFactionsCommon.MOD_ID, completionEvent(faction));
            za.co.neroland.nerofactions.link.FactionsLinkEvents.onContractCompleted(player.getUUID(), faction, contract.id());
            return paid ? "gui.nerofactions.contract.completed" : "gui.nerofactions.contract.credit_refused";
        } catch (RuntimeException | LinkageError failure) {
            NeroFactionsCommon.LOGGER.error("[NeroFactions] Contract reward application failed ({}).",
                    failure.getClass().getSimpleName());
            return "gui.nerofactions.contract.reward_failed";
        }
    }

    static ThresholdEvents.ThresholdCrossing completionEvent(Identifier faction) {
        return new ThresholdEvents.ThresholdCrossing(COMPLETED_CHANNEL, faction.toString(), 1, 1, true);
    }

    public static boolean reroll(ServerPlayer player, Identifier faction, int slot) {
        var entry = authorised(player, faction, slot);
        if (entry == null || entry.claimed() || entry.progress() != 0) return false;
        var server = player.level().getServer();
        var state = ContractState.get(server);
        if (!state.rerollAvailable(player.getUUID())) return false;
        var definition = FactionDefinitions.factionsForServer(server).get(faction);
        if (definition == null) return false;
        int tier = FactionTiers.tierOf(definition, FactionReputationState.get(server).getReputation(player.getUUID(), faction)).ordinal();
        var used = state.entries(player.getUUID()).stream().map(e -> e.contract().id()).collect(java.util.stream.Collectors.toSet());
        var pool = ContractDefinitions.forServer(server).values().stream().filter(c -> !used.contains(c.id())).toList();
        var choices = DailyContracts.roll(pool, server.overworld().getSeed() ^ (0x4f1bbcdcL + slot), state.day(player.getUUID()),
                player.getUUID(), faction, tier);
        return !choices.isEmpty() && state.reroll(player.getUUID(), faction, slot, choices.get(0));
    }

    public static void crafted(ServerPlayer player, ItemStack stack, int count) {
        if (count <= 0) return;
        for (var entry : current(player)) {
            if (!entry.claimed() && entry.contract().type().equals("craft") && itemMatches(stack, entry.contract().target())) {
                ContractState.get(player.level().getServer()).advance(player.getUUID(), entry.contract().faction(), entry.slot(), count);
            }
        }
    }

    public static void killed(ServerPlayer player, LivingEntity victim) {
        for (var entry : current(player)) {
            if (entry.claimed() || !entry.contract().type().equals("kill")) continue;
            String target = entry.contract().target();
            boolean tag = target.startsWith("#");
            Identifier id = Identifier.tryParse(tag ? target.substring(1) : target);
            if (id != null && (tag ? victim.getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, id))
                    : BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).equals(id))) {
                ContractState.get(player.level().getServer()).advance(player.getUUID(), entry.contract().faction(), entry.slot(), 1);
            }
        }
    }

    public static long worldDay(MinecraftServer server) {
        //? if >=26.1 {
        return Math.floorDiv(server.overworld().getDefaultClockTime(), 24000L);
        //?} else {
        /*return Math.floorDiv(server.overworld().getDayTime(), 24000L);
        *///?}
    }

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 40 != 0) return;
        long day = worldDay(server);
        ContractState.get(server).expireAll(day);
        if (!NeroFactionsConfig.CONTRACTS_ENABLED.get()) return;
        for (var player : server.getPlayerList().getPlayers()) {
            for (var entry : current(player)) {
                if (entry.claimed() || entry.ready() || !entry.contract().type().equals("visit_structure")) continue;
                var level = server.getLevel(player.level().dimension());
                Identifier tag = Identifier.tryParse(entry.contract().target().substring(1));
                if (level != null && tag != null && level.structureManager().getStructureWithPieceAt(player.blockPosition(),
                        TagKey.create(Registries.STRUCTURE, tag)).isValid()) {
                    ContractState.get(server).advance(player.getUUID(), entry.contract().faction(), entry.slot(), entry.contract().count());
                }
            }
        }
    }
}
