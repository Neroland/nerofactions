package za.co.neroland.nerofactions.content;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * The active set of faction definitions, loaded from datapacks. One JSON per faction under
 * {@code data/<namespace>/nerofactions/factions/<path>.json}; the id is the file's namespace + path
 * without the extension, so a pack overrides a shipped faction simply by shipping the same id.
 *
 * <p>Lifecycle follows the NeroColonies {@code ColonyDefinitions} pattern (itself derived from
 * NeroQuests' {@code QuestDefinitions} and Core's {@code GateDefinitions}) — deliberately
 * <em>not</em> a per-loader reload-listener: definitions are read from the running server's
 * {@link ResourceManager} lazily on first use and cached, keyed on the <em>ResourceManager
 * instance</em> as well as the server. {@code MinecraftServer.reloadResources} replaces that
 * instance wholesale, so {@code /reload} is detected by an identity comparison in pure common code
 * ({@link #refreshIfReloaded}, called from the loaders' tick hooks — the common case is one
 * reference comparison).
 *
 * <p><b>Nothing here ever crashes on bad content.</b> Every malformed file, broken tier ladder,
 * dangling enemy reference, unknown reward item and out-of-band trade multiplier is logged at warn
 * level against its resource id and the offending definition is dropped — or just the offending
 * entry pruned. The same complaints are collected as {@link ValidationIssue}s so
 * {@code /nerofactions reload-check} can show what a pack got wrong without making the operator
 * read the server log.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> everything logged from this class is a resource id or a codec
 * message. No player data reaches this path at all — faction definitions are not player-scoped.
 */
public final class FactionDefinitions {

    private static final String DIRECTORY = "nerofactions/factions";
    private static final String EXTENSION = ".json";

    /** Stands in for "the whole load", which belongs to no single resource. */
    private static final Identifier LOAD_ISSUE_ID =
            Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, "load");

    /** The server whose datapacks produced the current map, or null before the first load. */
    private static MinecraftServer loadedFor;

    /**
     * The resource-manager instance the current map was read from. {@code /reload} replaces the
     * server's whole reloadable-resources object (and with it this one), so an identity change here
     * means "the datapacks were reloaded" — the loader-free reload signal.
     */
    private static ResourceManager loadedFrom;

    /** Incremented on every (re)load, so derived caches can tell when they are stale. */
    private static int generation;

    private static Map<Identifier, FactionDefinition> factions = Map.of();

    /** What the last load complained about, in the order it complained. Replaced wholesale. */
    private static List<ValidationIssue> issues = List.of();

    private FactionDefinitions() {
    }

    // --- lifecycle ----------------------------------------------------------

    /** The factions this server's datapacks define, keyed by id (loads + caches on first use). */
    public static synchronized Map<Identifier, FactionDefinition> factionsForServer(MinecraftServer server) {
        ensureLoaded(server);
        return factions;
    }

    /** The validation problems this server's content produced (loads + caches on first use). */
    public static synchronized List<ValidationIssue> issuesForServer(MinecraftServer server) {
        ensureLoaded(server);
        return issues;
    }

    /**
     * Everything the last load dropped or ignored, empty when the packs are clean. The list is
     * immutable and replaced (never mutated) per load, so a caller may hold on to a snapshot.
     */
    public static List<ValidationIssue> validationIssues() {
        return issues;
    }

    /** Re-reads every faction from {@code server}'s current datapacks. Safe at any time. */
    public static synchronized void reload(MinecraftServer server) {
        loadFrom(server);
    }

    /**
     * Re-reads the definitions if — and only if — {@code server}'s datapacks have been reloaded (or
     * this is a different server) since the last load. Cheap enough to call from a tick hook: the
     * common case is one reference comparison.
     *
     * @return {@code true} if the definitions were re-read
     */
    public static synchronized boolean refreshIfReloaded(MinecraftServer server) {
        if (server == loadedFor && server.getResourceManager() == loadedFrom) {
            return false;
        }
        loadFrom(server);
        return true;
    }

    /** How many times the definitions have been loaded; changes whenever the content may have. */
    public static synchronized int generation() {
        return generation;
    }

    /** Drops the cache so the next access re-reads. Called when a server shuts down. */
    public static synchronized void forgetServer() {
        loadedFor = null;
        loadedFrom = null;
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (server != loadedFor || server.getResourceManager() != loadedFrom) {
            loadFrom(server);
        }
    }

    private static void loadFrom(MinecraftServer server) {
        load(server);
        loadedFor = server;
        loadedFrom = server.getResourceManager();
        generation++;
    }

    // --- accessors ----------------------------------------------------------

    /** The currently loaded factions, keyed by id (empty until a server loads its datapacks). */
    public static Map<Identifier, FactionDefinition> factions() {
        return factions;
    }

    public static Optional<FactionDefinition> faction(Identifier id) {
        return Optional.ofNullable(factions.get(id));
    }

    // --- loading ------------------------------------------------------------

    private static void load(MinecraftServer server) {
        Map<Identifier, FactionDefinition> loaded = Map.of();
        List<ValidationIssue> collected = new ArrayList<>();
        try {
            Map<Identifier, FactionDefinition> parsed = read(server.getResourceManager(), collected);
            loaded = validate(parsed, FactionDefinitions::itemRegistered, collected);
        } catch (RuntimeException e) {
            NeroFactionsCommon.LOGGER.warn(
                    "[NeroFactions] Faction content load failed; no factions are active.", e);
            loaded = Map.of();
            collected.clear();
            // The exception's own message can carry a filesystem path, so only its type is kept for
            // the operator-facing report; the full trace stays in the log line above.
            collected.add(ValidationIssue.dropped(LOAD_ISSUE_ID,
                    "load failed (" + e.getClass().getSimpleName() + "); no factions are active"));
        }
        factions = loaded;
        issues = List.copyOf(collected);
        NeroFactionsCommon.LOGGER.info("[NeroFactions] Loaded {} faction(s){}.",
                factions.size(),
                issues.isEmpty() ? "" : " with " + issues.size() + " validation issue(s)");
    }

    /** Whether an item id is registered in this launch — the loader's real registry check. */
    private static boolean itemRegistered(Identifier id) {
        // ITEM is a defaulted registry (unknown lookups return air), so presence must be asked
        // with containsKey rather than by comparing a lookup result.
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    private static Map<Identifier, FactionDefinition> read(ResourceManager resources,
            List<ValidationIssue> collected) {
        Map<Identifier, FactionDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> file
                : resources.listResources(DIRECTORY, path -> path.getPath().endsWith(EXTENSION))
                        .entrySet()) {
            Identifier definitionId = toDefinitionId(file.getKey());
            if (definitionId == null) {
                continue;
            }
            try (BufferedReader reader = file.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                List<String> errors = new ArrayList<>(1);
                Optional<FactionDefinition> parsed = FactionDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> {
                            NeroFactionsCommon.LOGGER.warn("[NeroFactions] Bad faction {}: {}",
                                    definitionId, error);
                            errors.add(error);
                        });
                parsed.ifPresent(value -> loaded.put(definitionId, value.withId(definitionId)));
                recordParseErrors(collected, definitionId, parsed.isPresent(), errors);
            } catch (Exception e) {
                NeroFactionsCommon.LOGGER.warn("[NeroFactions] Could not read faction {}",
                        definitionId, e);
                collected.add(ValidationIssue.dropped(definitionId,
                        "could not be read (" + e.getClass().getSimpleName() + ")"));
            }
        }
        return loaded;
    }

    /**
     * Turns the codec's complaints into report rows. A codec may complain and still produce a value
     * (a partial decode), so the severity follows whether anything survived rather than whether
     * anything was said.
     */
    private static void recordParseErrors(List<ValidationIssue> collected, Identifier id,
            boolean survived, List<String> errors) {
        if (errors.isEmpty()) {
            if (!survived) {
                collected.add(ValidationIssue.dropped(id, "not a readable faction definition"));
            }
            return;
        }
        String prefix = survived ? "partly bad faction: " : "bad faction: ";
        for (String error : errors) {
            collected.add(survived
                    ? ValidationIssue.ignored(id, prefix + error)
                    : ValidationIssue.dropped(id, prefix + error));
        }
    }

    // --- validation ---------------------------------------------------------

    /**
     * Validates and cleans every parsed definition. Package-private and registry-free on purpose:
     * {@code itemExists} is the item-registry seam, so the plain-JVM unit tests can validate the
     * shipped JSONs without bootstrapping the game.
     *
     * <p>Severity discipline: a faction whose tier ladder is unusable (missing tier, Outsider not
     * at 0, non-monotonic thresholds) or whose display name is blank is <b>DROPPED</b> — standing
     * cannot be resolved against it. Everything else — unknown reward tier names, bad reward
     * entries, dangling or duplicate enemies, unknown trade tiers, out-of-band multipliers — is
     * pruned or clamped and <b>IGNORED</b>-reported, because the faction still works without the
     * offending entry. Asymmetric enemy graphs are deliberate and valid: A listing B as an enemy
     * never requires B to list A.
     */
    static Map<Identifier, FactionDefinition> validate(Map<Identifier, FactionDefinition> parsed,
            Predicate<Identifier> itemExists, List<ValidationIssue> collected) {

        // Pass 1: per-definition structure. Enemies are checked in pass 2, against survivors.
        Map<Identifier, FactionDefinition> accepted = new LinkedHashMap<>();
        for (FactionDefinition faction : parsed.values()) {
            if (faction.displayName().isBlank()) {
                warn(collected, faction.id(), "has a blank display_name", true);
                continue;
            }
            if (faction.theme().isBlank()) {
                warn(collected, faction.id(), "has a blank theme", false);
            }
            FactionDefinition cleaned = validateTiers(faction, collected);
            if (cleaned == null) {
                continue; // dropped, already reported
            }
            cleaned = validateRewards(cleaned, itemExists, collected);
            cleaned = validateTrade(cleaned, collected);
            accepted.put(cleaned.id(), cleaned);
        }

        // Pass 2: prune enemy references that point at nothing (or at the faction itself). A
        // dropped faction is "nothing" here on purpose — bleed against a ghost would be invisible
        // and unexplainable. Duplicates are pruned too so bleed never applies twice.
        Map<Identifier, FactionDefinition> result = new LinkedHashMap<>();
        for (FactionDefinition faction : accepted.values()) {
            Set<Identifier> kept = new LinkedHashSet<>();
            for (Identifier enemy : faction.enemies()) {
                if (enemy.equals(faction.id())) {
                    warn(collected, faction.id(), "lists itself as an enemy (pruned)", false);
                } else if (!accepted.containsKey(enemy)) {
                    warn(collected, faction.id(), "lists unknown enemy " + enemy + " (pruned)", false);
                } else if (!kept.add(enemy)) {
                    warn(collected, faction.id(), "lists enemy " + enemy + " twice (pruned)", false);
                }
            }
            result.put(faction.id(), kept.size() == faction.enemies().size()
                    ? faction
                    : faction.withEnemies(List.copyOf(kept)));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * The tier ladder is the faction's skeleton, so its problems are fatal to the definition:
     * unknown tier names are pruned (IGNORED), but a missing tier, an Outsider threshold other than
     * 0, or thresholds that are not strictly increasing DROP the faction — {@code tierOf} against a
     * broken ladder would misreport standing, which is worse than the faction being absent.
     *
     * @return the cleaned definition, or null if it was dropped (already reported)
     */
    private static FactionDefinition validateTiers(FactionDefinition faction,
            List<ValidationIssue> collected) {
        Map<String, Integer> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : faction.tiers().entrySet()) {
            if (FactionTier.byName(entry.getKey()).isPresent()) {
                cleaned.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            } else {
                warn(collected, faction.id(),
                        "tiers name unknown tier \"" + entry.getKey() + "\" (pruned)", false);
            }
        }
        for (FactionTier tier : FactionTier.values()) {
            if (!cleaned.containsKey(tier.jsonName())) {
                warn(collected, faction.id(),
                        "is missing tier \"" + tier.jsonName() + "\"", true);
                return null;
            }
        }
        if (cleaned.get(FactionTier.OUTSIDER.jsonName()) != 0) {
            warn(collected, faction.id(), "outsider threshold must be 0", true);
            return null;
        }
        int previous = Integer.MIN_VALUE;
        for (FactionTier tier : FactionTier.values()) {
            int threshold = cleaned.get(tier.jsonName());
            if (threshold <= previous) {
                warn(collected, faction.id(), "tier thresholds are not strictly increasing", true);
                return null;
            }
            previous = threshold;
        }
        // Compare by content, not size: keys are lower-cased above, so an odd-case ladder of the
        // same size must still be replaced — threshold() looks tiers up by the lower-case name.
        return cleaned.equals(faction.tiers())
                ? faction
                : faction.withTiers(Collections.unmodifiableMap(cleaned));
    }

    /** Prunes reward tables under unknown tier names and reward entries that cannot be granted. */
    private static FactionDefinition validateRewards(FactionDefinition faction,
            Predicate<Identifier> itemExists, List<ValidationIssue> collected) {
        boolean changed = false;
        Map<String, List<RewardEntry>> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, List<RewardEntry>> table : faction.rewards().entrySet()) {
            if (FactionTier.byName(table.getKey()).isEmpty()) {
                warn(collected, faction.id(),
                        "rewards name unknown tier \"" + table.getKey() + "\" (table pruned)", false);
                changed = true;
                continue;
            }
            // rewardsFor() looks tables up by the lower-case json name, so the key is normalised
            // here the same way validateTiers normalises the ladder's.
            String tierKey = table.getKey().toLowerCase(Locale.ROOT);
            if (!tierKey.equals(table.getKey())) {
                changed = true;
            }
            List<RewardEntry> keptEntries = new ArrayList<>(table.getValue().size());
            for (RewardEntry entry : table.getValue()) {
                Optional<String> problem = entry.problem(itemExists, faction.cosmetics().isPresent());
                if (problem.isPresent()) {
                    warn(collected, faction.id(),
                            table.getKey() + " reward: " + problem.get() + " (pruned)", false);
                    changed = true;
                } else {
                    keptEntries.add(entry);
                }
            }
            cleaned.put(tierKey, List.copyOf(keptEntries));
        }
        return changed ? faction.withRewards(Collections.unmodifiableMap(cleaned)) : faction;
    }

    /** Prunes unknown tier keys in trade tables and clamps multipliers to the sane band. */
    private static FactionDefinition validateTrade(FactionDefinition faction,
            List<ValidationIssue> collected) {
        boolean changed = false;
        List<TradeModifier> cleaned = new ArrayList<>(faction.trade().size());
        for (TradeModifier modifier : faction.trade()) {
            Map<String, Double> byTier = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : modifier.byTier().entrySet()) {
                if (FactionTier.byName(entry.getKey()).isEmpty()) {
                    warn(collected, faction.id(), "trade " + modifier.tag()
                            + " names unknown tier \"" + entry.getKey() + "\" (pruned)", false);
                    changed = true;
                    continue;
                }
                double multiplier = entry.getValue();
                double clamped = Math.max(TradeModifier.MIN_MULTIPLIER,
                        Math.min(TradeModifier.MAX_MULTIPLIER, multiplier));
                if (clamped != multiplier) {
                    warn(collected, faction.id(), "trade " + modifier.tag() + " multiplier "
                            + multiplier + " at \"" + entry.getKey() + "\" clamped to " + clamped, false);
                    changed = true;
                }
                // multiplierFor() looks tiers up by the lower-case json name — normalise like
                // validateTiers does.
                byTier.put(entry.getKey().toLowerCase(Locale.ROOT), clamped);
            }
            if (byTier.equals(modifier.byTier())) {
                cleaned.add(modifier);
            } else {
                cleaned.add(new TradeModifier(modifier.tag(), Collections.unmodifiableMap(byTier)));
                changed = true;
            }
        }
        return changed ? faction.withTrade(List.copyOf(cleaned)) : faction;
    }

    private static void warn(List<ValidationIssue> collected, Identifier id, String detail,
            boolean dropped) {
        NeroFactionsCommon.LOGGER.warn("[NeroFactions] {} {}{}", id, detail,
                dropped ? "; dropped." : ".");
        collected.add(dropped ? ValidationIssue.dropped(id, detail) : ValidationIssue.ignored(id, detail));
    }

    /** {@code <ns>:nerofactions/factions/foo/bar.json} -> {@code <ns>:foo/bar}. */
    private static Identifier toDefinitionId(Identifier file) {
        String path = file.getPath();
        if (!path.startsWith(DIRECTORY + "/") || !path.endsWith(EXTENSION)) {
            return null;
        }
        String trimmed = path.substring(DIRECTORY.length() + 1, path.length() - EXTENSION.length());
        return Identifier.fromNamespaceAndPath(file.getNamespace(), trimmed);
    }
}
