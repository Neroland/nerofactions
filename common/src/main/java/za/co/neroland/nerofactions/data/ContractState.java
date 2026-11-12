package za.co.neroland.nerofactions.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.contracts.ContractDefinition;
import za.co.neroland.nerofactions.contracts.DailyContracts;
import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

public final class ContractState extends SavedData {
    public static final Identifier ID = Identifier.parse("nerofactions:contracts");

    public record Entry(int slot, ContractDefinition contract, int progress, boolean claimed) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.intRange(0, 2).fieldOf("slot").forGetter(Entry::slot),
                ContractDefinition.CODEC.fieldOf("contract").forGetter(Entry::contract),
                Codec.intRange(0, 4096).fieldOf("progress").forGetter(Entry::progress),
                Codec.BOOL.fieldOf("claimed").forGetter(Entry::claimed)
        ).apply(inst, Entry::new));

        public boolean ready() { return progress >= contract.count() && !claimed; }
    }

    private record Row(String player, long day, List<Entry> entries, boolean rerolled, long completed) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("player").forGetter(Row::player),
                Codec.LONG.fieldOf("day").forGetter(Row::day),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Row::entries),
                Codec.BOOL.optionalFieldOf("rerolled", false).forGetter(Row::rerolled),
                Codec.LONG.validate(value -> value >= 0 ? com.mojang.serialization.DataResult.success(value)
                        : com.mojang.serialization.DataResult.error(() -> "negative completion count")).optionalFieldOf("completed", 0L).forGetter(Row::completed)
        ).apply(inst, Row::new));

        Row { entries = List.copyOf(entries); }
    }

    public static final Codec<ContractState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Row.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(ContractState::rows)
    ).apply(inst, ContractState::load));
    public static final SavedDataType<ContractState> TYPE = SavedDataCompat.type(ID, ContractState::new, CODEC);
    private final Map<UUID, Row> players = new LinkedHashMap<>();
    private long lastExpiryDay = Long.MIN_VALUE;

    public static ContractState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, ContractState::new, ID.toString());
    }

    public static void eraseFor(MinecraftServer server, UUID player) {
        var state = get(server);
        state.forget(player);
        SavedDataRecovery.backupNow(server.overworld(), TYPE, state, ID.toString());
    }

    public synchronized List<Entry> ensure(UUID player, Identifier faction, int tier,
            long seed, long day, List<ContractDefinition> pool) {
        Row row = players.get(player);
        if (row == null || day > row.day()) {
            row = new Row(player.toString(), day, List.of(), false, row == null ? 0L : row.completed());
            players.put(player, row);
            setDirty();
        }
        var existing = row.entries().stream().filter(e -> e.contract().faction().equals(faction)).toList();
        if (!existing.isEmpty()) return existing;
        var selected = DailyContracts.roll(pool, seed, row.day(), player, faction, tier);
        if (selected.isEmpty()) return List.of();
        if (row.entries().size() + selected.size() > 192) return List.of();
        var entries = new ArrayList<>(row.entries());
        for (int i = 0; i < selected.size(); i++) entries.add(new Entry(i, selected.get(i), 0, false));
        players.put(player, new Row(row.player(), row.day(), entries, row.rerolled(), row.completed()));
        setDirty();
        return entries.stream().filter(e -> e.contract().faction().equals(faction)).toList();
    }

    public synchronized void expireDay(UUID player, long day) {
        Row row = players.get(player);
        if (row != null && day > row.day()) {
            players.put(player, new Row(row.player(), day, List.of(), false, row.completed()));
            setDirty();
        }
    }

    public synchronized void expireAll(long day) {
        if (day <= lastExpiryDay) return;
        lastExpiryDay = day;
        for (UUID player : List.copyOf(players.keySet())) expireDay(player, day);
    }

    public synchronized List<Entry> entries(UUID player) {
        Row row = players.get(player);
        return row == null ? List.of() : row.entries();
    }

    public synchronized Entry entry(UUID player, Identifier faction, int slot) {
        return entries(player).stream().filter(e -> e.slot() == slot && e.contract().faction().equals(faction))
                .findFirst().orElse(null);
    }

    public synchronized int advance(UUID player, Identifier faction, int slot, int amount) {
        Entry entry = entry(player, faction, slot);
        if (entry == null || entry.claimed() || amount <= 0) return 0;
        int progress = (int) Math.min(entry.contract().count(), (long) entry.progress() + amount);
        replace(player, entry, new Entry(slot, entry.contract(), progress, false), false);
        return progress - entry.progress();
    }

    public synchronized ContractDefinition reserveReward(UUID player, Identifier faction, int slot) {
        Entry entry = entry(player, faction, slot);
        if (entry == null || !entry.ready()) return null;
        replace(player, entry, new Entry(slot, entry.contract(), entry.progress(), true), true);
        return entry.contract();
    }

    private void replace(UUID player, Entry before, Entry after, boolean completed) {
        Row row = players.get(player);
        var entries = new ArrayList<>(row.entries());
        entries.set(entries.indexOf(before), after);
        long count = row.completed() == Long.MAX_VALUE ? Long.MAX_VALUE : row.completed() + (completed ? 1 : 0);
        players.put(player, new Row(row.player(), row.day(), entries, row.rerolled(), count));
        setDirty();
    }

    public synchronized boolean reroll(UUID player, Identifier faction, int slot, ContractDefinition replacement) {
        Row row = players.get(player);
        Entry before = entry(player, faction, slot);
        if (replacement == null || row == null || row.rerolled() || before == null || before.claimed() || before.progress() != 0
                || !replacement.faction().equals(faction) || row.entries().stream().anyMatch(e -> e.contract().id().equals(replacement.id()))) {
            return false;
        }
        var entries = new ArrayList<>(row.entries());
        entries.set(entries.indexOf(before), new Entry(slot, replacement, 0, false));
        players.put(player, new Row(row.player(), row.day(), entries, true, row.completed()));
        setDirty();
        return true;
    }

    public synchronized long day(UUID player) { return players.containsKey(player) ? players.get(player).day() : Long.MIN_VALUE; }
    public synchronized long completed(UUID player) { return players.containsKey(player) ? players.get(player).completed() : 0L; }
    public synchronized boolean rerollAvailable(UUID player) { return players.containsKey(player) && !players.get(player).rerolled(); }
    public synchronized Set<UUID> knownPlayers() { return Set.copyOf(players.keySet()); }
    public synchronized void forget(UUID player) { if (players.remove(player) != null) setDirty(); }

    public synchronized com.google.gson.JsonObject export(UUID player) {
        var result = new com.google.gson.JsonObject();
        var entries = new com.google.gson.JsonArray();
        entries(player).stream().sorted(java.util.Comparator.comparing((Entry e) -> e.contract().faction())
                .thenComparingInt(Entry::slot)).forEach(entry -> entries.add(
                        Entry.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entry).getOrThrow()));
        result.add("entries", entries);
        result.addProperty("completed_count", completed(player));
        result.addProperty("reroll_available", rerollAvailable(player));
        if (players.containsKey(player)) result.addProperty("day", day(player));
        return result;
    }
    private synchronized List<Row> rows() { return List.copyOf(players.values()); }

    private static ContractState load(List<Row> rows) {
        var state = new ContractState();
        int rejected = 0;
        for (Row row : rows) {
            try {
                UUID player = UUID.fromString(row.player());
                if (row.entries().size() > 192 || row.entries().stream().anyMatch(e -> e.contract().problem().isPresent()
                        || e.progress() > e.contract().count())) {
                    rejected++;
                    continue;
                }
                long unique = row.entries().stream().map(e -> e.contract().faction() + "/" + e.slot()).distinct().count();
                if (unique != row.entries().size() || row.entries().stream().map(e -> e.contract().id()).distinct().count() != unique) {
                    rejected++;
                    continue;
                }
                state.players.put(player, row);
            } catch (IllegalArgumentException malformed) {
                rejected++;
            }
        }
        if (rejected > 0) NeroFactionsCommon.LOGGER.warn("[NeroFactions] Ignored {} invalid contract-state rows.", rejected);
        return state;
    }
}
