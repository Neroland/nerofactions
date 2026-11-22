package za.co.neroland.nerofactions.contracts;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
    static final String NOT_READY = "gui.nerofactions.contract.not_ready";
    static final String COMPLETED = "gui.nerofactions.contract.completed";
    static final String CREDIT_REFUSED = "gui.nerofactions.contract.credit_refused";
    static final String REWARD_FAILED = "gui.nerofactions.contract.reward_failed";

    /** The caller's own inventory slots a delivery may draw from. */
    interface DeliverySlots {
        int size();
        int count(int slot);
        /** True when the stack carries no component changes (no rename, custom data, damage, …). */
        boolean pristine(int slot);
        boolean matches(int slot, String target);
        /** Removes up to {@code amount} and returns how many were actually removed. */
        int remove(int slot, int amount);
    }

    /** Applies a reserved contract's rewards to the claiming player. */
    interface RewardSink {
        void reputation(Identifier faction, int amount);
        boolean credits(long amount);
        void item(Identifier item, int count);
        void completed(Identifier faction, Identifier contract);
    }

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
        return visible(state, player.getUUID(), memberships, definitions.keySet(), day);
    }

    /** The caller's entries for today, limited to factions they belong to that still exist. */
    static List<ContractState.Entry> visible(ContractState state, UUID player, Collection<Identifier> memberships,
            Set<Identifier> factions, long day) {
        state.expireDay(player, day);
        return state.entries(player).stream()
                .filter(e -> memberships.contains(e.contract().faction()) && factions.contains(e.contract().faction()))
                .toList();
    }

    static ContractState.Entry find(List<ContractState.Entry> visible, Identifier faction, int slot) {
        return visible.stream().filter(e -> e.contract().faction().equals(faction) && e.slot() == slot)
                .findFirst().orElse(null);
    }

    /** {@code target} is an id ({@code ns:path}) or a tag ({@code #ns:path}); a malformed target never matches. */
    static boolean targetMatches(String target, Identifier id, Predicate<Identifier> inTag) {
        if (target == null || id == null) return false;
        boolean tag = target.startsWith("#");
        Identifier parsed = Identifier.tryParse(tag ? target.substring(1) : target);
        return parsed != null && (tag ? inTag.test(parsed) : id.equals(parsed));
    }

    public static boolean itemMatches(ItemStack stack, String target) {
        if (stack.isEmpty()) return false;
        return targetMatches(target, BuiltInRegistries.ITEM.getKey(stack.getItem()),
                tag -> stack.is(TagKey.create(Registries.ITEM, tag)));
    }

    private static ContractState.Entry authorised(ServerPlayer player, Identifier faction, int slot) {
        return find(current(player), faction, slot);
    }

    public static int deliver(ServerPlayer player, Identifier faction, int slot) {
        var entry = authorised(player, faction, slot);
        if (entry == null) return 0;
        var inventory = player.getInventory();
        int removed = deliver(ContractState.get(player.level().getServer()), player.getUUID(), entry,
                new InventorySlots(inventory));
        if (removed > 0) inventory.setChanged();
        return removed;
    }

    static int deliver(ContractState state, UUID player, ContractState.Entry entry, DeliverySlots slots) {
        if (entry == null || entry.claimed() || !entry.contract().type().equals("deliver")) return 0;
        int remaining = entry.contract().count() - entry.progress();
        if (remaining <= 0) return 0;
        int removed = collect(slots, entry.contract().target(), remaining);
        if (removed > 0) state.advance(player, entry.contract().faction(), entry.slot(), removed);
        return removed;
    }

    static int collect(DeliverySlots slots, String target, int remaining) {
        int removed = 0;
        for (int i = 0; i < slots.size() && removed < remaining; i++) {
            int count = slots.count(i);
            if (count <= 0 || !slots.pristine(i) || !slots.matches(i, target)) continue;
            int take = Math.min(count, remaining - removed);
            removed += Math.max(0, Math.min(take, slots.remove(i, take)));
        }
        return removed;
    }

    private record InventorySlots(net.minecraft.world.entity.player.Inventory inventory) implements DeliverySlots {
        @Override public int size() { return Math.min(36, inventory.getContainerSize()); }
        @Override public int count(int slot) { return inventory.getItem(slot).getCount(); }
        @Override public boolean pristine(int slot) { return inventory.getItem(slot).getComponentsPatch().isEmpty(); }
        @Override public boolean matches(int slot, String target) { return itemMatches(inventory.getItem(slot), target); }
        @Override public int remove(int slot, int amount) { return inventory.removeItem(slot, amount).getCount(); }
    }

    public static String claim(ServerPlayer player, Identifier faction, int slot) {
        var entry = authorised(player, faction, slot);
        if (entry == null) return NOT_READY;
        var server = player.level().getServer();
        return claim(ContractState.get(server), player.getUUID(), entry, new PlayerRewards(server, player));
    }

    static String claim(ContractState state, UUID player, ContractState.Entry entry, RewardSink rewards) {
        if (entry == null || !entry.ready()) return NOT_READY;
        var contract = state.reserveReward(player, entry.contract().faction(), entry.slot());
        if (contract == null) return NOT_READY;
        try {
            rewards.reputation(contract.faction(), contract.reputation());
            boolean paid = rewards.credits(contract.credits());
            if (contract.item().isPresent()) rewards.item(contract.item().get(), contract.itemCount());
            rewards.completed(contract.faction(), contract.id());
            return paid ? COMPLETED : CREDIT_REFUSED;
        } catch (RuntimeException | LinkageError failure) {
            NeroFactionsCommon.LOGGER.error("[NeroFactions] Contract reward application failed ({}).",
                    failure.getClass().getSimpleName());
            return REWARD_FAILED;
        }
    }

    private record PlayerRewards(MinecraftServer server, ServerPlayer player) implements RewardSink {
        @Override
        public void reputation(Identifier faction, int amount) {
            ReputationSources.award(server, player.getUUID(), faction, ReputationSources.Source.QUEST, amount);
        }

        @Override
        public boolean credits(long amount) {
            return amount == 0 || !CurrencyApi.hasRealProvider()
                    || CurrencyApi.deposit(player.getUUID(), CoreCurrencies.CREDITS, amount, "nerofactions", "contract_reward");
        }

        @Override
        public void item(Identifier item, int count) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(item), count);
            //? if >=26.3 {
            /*player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
            *///?} else {
            player.getInventory().placeItemBackInInventory(stack);
            //?}
        }

        @Override
        public void completed(Identifier faction, Identifier contract) {
            ThresholdEvents.fire(NeroFactionsCommon.MOD_ID, completionEvent(faction));
            za.co.neroland.nerofactions.progression.RecordGates.onContractCompleted(server, player.getUUID(),
                    ContractState.get(server).completed(player.getUUID()));
            za.co.neroland.nerofactions.link.FactionsLinkEvents.onContractCompleted(player.getUUID(), faction, contract);
        }
    }

    static ThresholdEvents.ThresholdCrossing completionEvent(Identifier faction) {
        return new ThresholdEvents.ThresholdCrossing(COMPLETED_CHANNEL, faction.toString(), 1, 1, true);
    }

    public static boolean reroll(ServerPlayer player, Identifier faction, int slot) {
        var entry = authorised(player, faction, slot);
        if (entry == null) return false;
        var server = player.level().getServer();
        var state = ContractState.get(server);
        return reroll(state, player.getUUID(), entry, used -> {
            var definition = FactionDefinitions.factionsForServer(server).get(faction);
            if (definition == null) return List.of();
            int tier = FactionTiers.tierOf(definition,
                    FactionReputationState.get(server).getReputation(player.getUUID(), faction)).ordinal();
            var pool = ContractDefinitions.forServer(server).values().stream().filter(c -> !used.contains(c.id())).toList();
            return DailyContracts.roll(pool, server.overworld().getSeed() ^ (0x4f1bbcdcL + slot),
                    state.day(player.getUUID()), player.getUUID(), faction, tier);
        });
    }

    /** {@code choices} receives the contract ids already assigned today and returns candidates, best first. */
    static boolean reroll(ContractState state, UUID player, ContractState.Entry entry,
            Function<Set<Identifier>, List<ContractDefinition>> choices) {
        if (entry == null || entry.claimed() || entry.progress() != 0 || !state.rerollAvailable(player)) return false;
        Set<Identifier> used = state.entries(player).stream().map(e -> e.contract().id()).collect(Collectors.toSet());
        var options = choices.apply(used);
        return options != null && !options.isEmpty()
                && state.reroll(player, entry.contract().faction(), entry.slot(), options.get(0));
    }

    /** Advances every open entry of {@code type} whose target passes {@code target}; returns the total progress added. */
    static int advanceMatching(ContractState state, UUID player, List<ContractState.Entry> visible, String type,
            Predicate<String> target, int amount) {
        if (amount <= 0) return 0;
        int advanced = 0;
        for (var entry : visible) {
            if (entry.claimed() || entry.ready() || !entry.contract().type().equals(type)
                    || !target.test(entry.contract().target())) continue;
            advanced += state.advance(player, entry.contract().faction(), entry.slot(), amount);
        }
        return advanced;
    }

    public static void crafted(ServerPlayer player, ItemStack stack, int count) {
        if (count <= 0) return;
        var visible = current(player);
        if (visible.isEmpty()) return;
        advanceMatching(ContractState.get(player.level().getServer()), player.getUUID(), visible, "craft",
                target -> itemMatches(stack, target), count);
    }

    public static void killed(ServerPlayer player, LivingEntity victim) {
        var visible = current(player);
        if (visible.isEmpty()) return;
        var type = victim.getType();
        advanceMatching(ContractState.get(player.level().getServer()), player.getUUID(), visible, "kill",
                target -> targetMatches(target, BuiltInRegistries.ENTITY_TYPE.getKey(type),
                        tag -> type.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, tag))), 1);
    }

    public static long worldDay(MinecraftServer server) {
        //? if >=26.1 {
        return Math.floorDiv(server.overworld().getDefaultClockTime(), 24000L);
        //?} else {
        /*return Math.floorDiv(server.overworld().getDayTime(), 24000L);
        *///?}
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 == 0) {
            za.co.neroland.nerofactions.data.CorporateLedger.get(server).expire(worldDay(server));
            za.co.neroland.nerofactions.content.faction.corporation.CorporateMarketNudge.audit(System.currentTimeMillis());
        }
        if (server.overworld().getGameTime() % 40 != 0) return;
        long day = worldDay(server);
        ContractState.get(server).expireAll(day);
        if (!NeroFactionsConfig.CONTRACTS_ENABLED.get()) return;
        for (var player : server.getPlayerList().getPlayers()) {
            var visible = current(player);
            if (visible.isEmpty()) continue;
            var level = server.getLevel(player.level().dimension());
            if (level == null) continue;
            var pos = player.blockPosition();
            // Structure visits are validated as tag targets with count 1, so a match completes the entry.
            advanceMatching(ContractState.get(server), player.getUUID(), visible, "visit_structure", target -> {
                Identifier tag = target.startsWith("#") ? Identifier.tryParse(target.substring(1)) : null;
                return tag != null && level.structureManager().getStructureWithPieceAt(pos,
                        TagKey.create(Registries.STRUCTURE, tag)).isValid();
            }, Integer.MAX_VALUE);
        }
    }
}
