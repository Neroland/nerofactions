package za.co.neroland.nerofactions.contracts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.data.ContractState;

class ContractServiceTest {
    private static final UUID PLAYER = new UUID(1, 2);
    private static final UUID OTHER = new UUID(3, 4);
    private static final Identifier FACTION = ContractFixtures.FACTION;
    private static final Set<Identifier> MEMBER = Set.of(FACTION);
    private static final Set<Identifier> FACTIONS = Set.of(FACTION);
    private static final long DAY = 5;

    // ---- authorisation (forged C→S requests) ----

    @Test
    void forgedClaimsForAnotherPlayerSlotDayOrFactionFindNothingAndPayNothing() {
        var state = assigned();
        state.advance(PLAYER, FACTION, 0, 16);
        var rewards = new Rewards();
        assertNull(ContractService.find(view(state, OTHER, DAY), FACTION, 0), "wrong player");
        assertNull(ContractService.find(view(state, PLAYER, DAY), FACTION, 3), "wrong slot");
        assertNull(ContractService.find(view(state, PLAYER, DAY), Identifier.parse("nerofactions:void_cult"), 0),
                "wrong faction");
        assertNull(ContractService.find(ContractService.visible(state, PLAYER, Set.of(), FACTIONS, DAY), FACTION, 0),
                "not a member");
        assertNull(ContractService.find(ContractService.visible(state, PLAYER, MEMBER, Set.of(), DAY), FACTION, 0),
                "faction removed by datapack");
        assertEquals(ContractService.NOT_READY, ContractService.claim(state, PLAYER, null, rewards));
        assertNull(ContractService.find(view(state, PLAYER, DAY + 1), FACTION, 0), "stale day");
        assertEquals(0, rewards.calls.size());
        assertEquals(0, state.completed(PLAYER));
    }

    @Test
    void anEntryFromTheCallersOwnViewIsFound() {
        var state = assigned();
        var entry = ContractService.find(view(state, PLAYER, DAY), FACTION, 1);
        assertNotNull(entry);
        assertEquals(1, entry.slot());
        assertEquals(FACTION, entry.contract().faction());
    }

    // ---- claim ----

    @Test
    void claimPaysEveryRewardOnceAndCannotDoublePay() {
        var state = assigned();
        state.advance(PLAYER, FACTION, 0, 16);
        var rewards = new Rewards();
        var entry = ContractService.find(view(state, PLAYER, DAY), FACTION, 0);
        assertEquals(ContractService.COMPLETED, ContractService.claim(state, PLAYER, entry, rewards));
        assertEquals(List.of("rep:25", "credits:10", "completed:" + entry.contract().id()), rewards.calls);
        // a replayed packet carrying the stale (unclaimed) entry, and a fresh lookup, both pay nothing
        assertEquals(ContractService.NOT_READY, ContractService.claim(state, PLAYER, entry, rewards));
        assertEquals(ContractService.NOT_READY, ContractService.claim(state, PLAYER,
                ContractService.find(view(state, PLAYER, DAY), FACTION, 0), rewards));
        assertEquals(3, rewards.calls.size());
        assertEquals(1, state.completed(PLAYER));
    }

    @Test
    void claimRefusesAnUnfinishedContract() {
        var state = assigned();
        state.advance(PLAYER, FACTION, 0, 15);
        var rewards = new Rewards();
        var entry = ContractService.find(view(state, PLAYER, DAY), FACTION, 0);
        assertEquals(ContractService.NOT_READY, ContractService.claim(state, PLAYER, entry, rewards));
        assertTrue(rewards.calls.isEmpty());
        assertFalse(state.entry(PLAYER, FACTION, 0).claimed());
    }

    @Test
    void claimGrantsTheItemRewardAndReportsARefusedCreditDeposit() {
        var state = new ContractState();
        var withItem = new ContractDefinition(Identifier.parse("nerofactions:miner_union/item"), FACTION, "deliver",
                "minecraft:iron_ingot", 1, 5, 40, Optional.of(Identifier.parse("minecraft:diamond")), 2, 0, 4, 1);
        state.ensure(PLAYER, FACTION, 0, 1, DAY, List.of(withItem));
        state.advance(PLAYER, FACTION, 0, 1);
        var rewards = new Rewards();
        rewards.creditsAccepted = false;
        var entry = ContractService.find(view(state, PLAYER, DAY), FACTION, 0);
        assertEquals(ContractService.CREDIT_REFUSED, ContractService.claim(state, PLAYER, entry, rewards));
        assertTrue(rewards.calls.contains("item:minecraft:diamond x2"));
        assertTrue(state.entry(PLAYER, FACTION, 0).claimed(), "reward is reserved even when credits bounce");
    }

    // ---- deliver ----

    @Test
    void deliverTakesOnlyMatchingPristineStacksUpToTheRemainingCount() {
        var state = assigned();
        state.advance(PLAYER, FACTION, 0, 4); // 12 remaining
        var slots = new Slots()
                .add("minecraft:iron_ingot", 5, true)
                .add("minecraft:gold_ingot", 64, true)
                .add("minecraft:iron_ingot", 64, false) // renamed / custom data
                .add("minecraft:iron_ingot", 64, true);
        var entry = ContractService.find(view(state, PLAYER, DAY), FACTION, 0);
        assertEquals(12, ContractService.deliver(state, PLAYER, entry, slots));
        assertArrayEquals(new int[] {0, 64, 64, 57}, slots.counts());
        assertEquals(16, state.entry(PLAYER, FACTION, 0).progress());
        // a completed contract takes nothing more
        assertEquals(0, ContractService.deliver(state, PLAYER, state.entry(PLAYER, FACTION, 0), slots));
        assertArrayEquals(new int[] {0, 64, 64, 57}, slots.counts());
    }

    @Test
    void aRenamedStackIsRefusedWhileAPlainOneIsAccepted() {
        var renamed = new Slots().add("minecraft:iron_ingot", 16, false);
        assertEquals(0, ContractService.collect(renamed, "minecraft:iron_ingot", 16));
        assertEquals(16, renamed.counts()[0]);
        var plain = new Slots().add("minecraft:iron_ingot", 16, true);
        assertEquals(16, ContractService.collect(plain, "minecraft:iron_ingot", 16));
    }

    @Test
    void deliverCountsOnlyWhatTheInventoryActuallyRemoved() {
        var state = assigned();
        var slots = new Slots().add("minecraft:iron_ingot", 10, true);
        slots.removalLimit = 3;
        var entry = ContractService.find(view(state, PLAYER, DAY), FACTION, 0);
        assertEquals(3, ContractService.deliver(state, PLAYER, entry, slots));
        assertEquals(3, state.entry(PLAYER, FACTION, 0).progress());
        slots.removalLimit = 0;
        assertEquals(0, ContractService.deliver(state, PLAYER, state.entry(PLAYER, FACTION, 0), slots));
        assertEquals(3, state.entry(PLAYER, FACTION, 0).progress());
    }

    @Test
    void deliverRefusesClaimedOrNonDeliveryEntries() {
        var state = assigned();
        var slots = new Slots().add("minecraft:iron_ingot", 64, true);
        var entry = state.entry(PLAYER, FACTION, 0);
        var claimed = new ContractState.Entry(0, entry.contract(), 0, true);
        assertEquals(0, ContractService.deliver(state, PLAYER, claimed, slots));
        var kill = new ContractState.Entry(0, withType(entry.contract(), "kill"), 0, false);
        assertEquals(0, ContractService.deliver(state, PLAYER, kill, slots));
        assertEquals(0, ContractService.deliver(state, PLAYER, null, slots));
        assertEquals(64, slots.counts()[0]);
    }

    // ---- reroll ----

    @Test
    void rerollReplacesAnUntouchedEntryOnceWithAnUnusedContract() {
        var state = assigned();
        var before = state.entries(PLAYER).stream().map(e -> e.contract().id()).toList();
        var entry = state.entry(PLAYER, FACTION, 2);
        assertTrue(ContractService.reroll(state, PLAYER, entry, used -> ContractFixtures.pool().stream()
                .filter(c -> !used.contains(c.id())).toList()));
        var replaced = state.entry(PLAYER, FACTION, 2).contract().id();
        assertFalse(before.contains(replaced));
        // token spent
        assertFalse(ContractService.reroll(state, PLAYER, state.entry(PLAYER, FACTION, 1),
                used -> ContractFixtures.pool().stream().filter(c -> !used.contains(c.id())).toList()));
    }

    @Test
    void rerollIsRefusedWithProgressWhenClaimedOrWithoutCandidates() {
        var state = assigned();
        state.advance(PLAYER, FACTION, 0, 1);
        var any = (java.util.function.Function<Set<Identifier>, List<ContractDefinition>>) used ->
                ContractFixtures.pool().stream().filter(c -> !used.contains(c.id())).toList();
        assertFalse(ContractService.reroll(state, PLAYER, state.entry(PLAYER, FACTION, 0), any));
        var claimed = new ContractState.Entry(1, state.entry(PLAYER, FACTION, 1).contract(), 0, true);
        assertFalse(ContractService.reroll(state, PLAYER, claimed, any));
        assertFalse(ContractService.reroll(state, PLAYER, state.entry(PLAYER, FACTION, 1), used -> List.of()));
        assertFalse(ContractService.reroll(state, PLAYER, null, any));
        assertFalse(ContractService.reroll(state, OTHER, state.entry(PLAYER, FACTION, 1), any), "no row, no token");
        assertTrue(state.rerollAvailable(PLAYER), "refusals do not spend the token");
    }

    // ---- craft / kill / visit progress ----

    @Test
    void progressAdvancesOnlyMatchingOpenEntriesOfTheRightTypeAndIsCapped() {
        var state = new ContractState();
        var pool = List.of(
                contract("craft_iron", "craft", "minecraft:iron_ingot", 4),
                contract("kill_zombie", "kill", "minecraft:zombie", 2),
                contract("kill_tag", "kill", "#minecraft:undead", 3));
        state.ensure(PLAYER, FACTION, 0, 1, DAY, pool);
        var zombie = Identifier.parse("minecraft:zombie");
        var view = view(state, PLAYER, DAY);
        // a zombie kill counts for the id contract and the tag contract, never for the craft contract
        assertEquals(2, ContractService.advanceMatching(state, PLAYER, view, "kill",
                target -> ContractService.targetMatches(target, zombie, tag -> tag.getPath().equals("undead")), 1));
        assertEquals(0, ContractService.advanceMatching(state, PLAYER, view(state, PLAYER, DAY), "kill",
                target -> ContractService.targetMatches(target, Identifier.parse("minecraft:cow"), tag -> false), 1));
        assertEquals(4, ContractService.advanceMatching(state, PLAYER, view(state, PLAYER, DAY), "craft",
                target -> target.equals("minecraft:iron_ingot"), 64), "craft progress is capped at the count");
        assertEquals(0, ContractService.advanceMatching(state, PLAYER, view(state, PLAYER, DAY), "craft",
                target -> true, 1), "a ready entry takes no more");
        assertEquals(0, ContractService.advanceMatching(state, PLAYER, view(state, PLAYER, DAY), "craft",
                target -> true, 0));
        for (var entry : state.entries(PLAYER)) assertTrue(entry.progress() <= entry.contract().count());
    }

    @Test
    void aStructureVisitCompletesItsEntryAndAnotherPlayersViewIsUntouched() {
        var state = new ContractState();
        state.ensure(PLAYER, FACTION, 0, 1, DAY, List.of(contract("visit", "visit_structure", "#minecraft:village", 1)));
        assertEquals(0, ContractService.advanceMatching(state, OTHER, view(state, OTHER, DAY), "visit_structure",
                target -> true, Integer.MAX_VALUE));
        assertEquals(1, ContractService.advanceMatching(state, PLAYER, view(state, PLAYER, DAY), "visit_structure",
                target -> true, Integer.MAX_VALUE));
        assertTrue(state.entry(PLAYER, FACTION, 0).ready());
    }

    @Test
    void targetsMatchByIdOrTagAndMalformedTargetsNeverMatch() {
        var iron = Identifier.parse("minecraft:iron_ingot");
        assertTrue(ContractService.targetMatches("minecraft:iron_ingot", iron, tag -> false));
        assertFalse(ContractService.targetMatches("minecraft:gold_ingot", iron, tag -> true));
        assertTrue(ContractService.targetMatches("#c:ingots/iron", iron, tag -> tag.toString().equals("c:ingots/iron")));
        assertFalse(ContractService.targetMatches("#c:ingots/gold", iron, tag -> tag.toString().equals("c:ingots/iron")));
        assertFalse(ContractService.targetMatches("Not An Id!", iron, tag -> true));
        assertFalse(ContractService.targetMatches(null, iron, tag -> true));
        assertFalse(ContractService.targetMatches("minecraft:iron_ingot", null, tag -> true));
    }

    // ---- fixtures ----

    private static ContractState assigned() {
        var state = new ContractState();
        state.ensure(PLAYER, FACTION, 0, 123, DAY, ContractFixtures.pool());
        assertEquals(3, state.entries(PLAYER).size());
        return state;
    }

    private static List<ContractState.Entry> view(ContractState state, UUID player, long day) {
        return ContractService.visible(state, player, MEMBER, FACTIONS, day);
    }

    private static ContractDefinition contract(String name, String type, String target, int count) {
        return new ContractDefinition(Identifier.parse("nerofactions:miner_union/" + name), FACTION, type, target,
                count, 10, 0, Optional.empty(), 1, 0, 4, 1);
    }

    private static ContractDefinition withType(ContractDefinition c, String type) {
        return new ContractDefinition(c.id(), c.faction(), type, c.target(), c.count(), c.reputation(), c.credits(),
                c.item(), c.itemCount(), c.minTier(), c.maxTier(), c.weight());
    }

    private static final class Rewards implements ContractService.RewardSink {
        final List<String> calls = new ArrayList<>();
        boolean creditsAccepted = true;

        @Override public void reputation(Identifier faction, int amount) { calls.add("rep:" + amount); }
        @Override public boolean credits(long amount) { calls.add("credits:" + amount); return creditsAccepted; }
        @Override public void item(Identifier item, int count) { calls.add("item:" + item + " x" + count); }
        @Override public void completed(Identifier faction, Identifier contract) { calls.add("completed:" + contract); }
    }

    private static final class Slots implements ContractService.DeliverySlots {
        private final List<String> items = new ArrayList<>();
        private final List<Integer> counts = new ArrayList<>();
        private final List<Boolean> pristine = new ArrayList<>();
        int removalLimit = Integer.MAX_VALUE;

        Slots add(String item, int count, boolean plain) {
            items.add(item);
            counts.add(count);
            pristine.add(plain);
            return this;
        }

        int[] counts() { return counts.stream().mapToInt(Integer::intValue).toArray(); }

        @Override public int size() { return items.size(); }
        @Override public int count(int slot) { return counts.get(slot); }
        @Override public boolean pristine(int slot) { return pristine.get(slot); }
        @Override public boolean matches(int slot, String target) {
            return ContractService.targetMatches(target, Identifier.parse(items.get(slot)), tag -> false);
        }
        @Override public int remove(int slot, int amount) {
            int taken = Math.min(Math.min(amount, removalLimit), counts.get(slot));
            counts.set(slot, counts.get(slot) - taken);
            return taken;
        }
    }
}
