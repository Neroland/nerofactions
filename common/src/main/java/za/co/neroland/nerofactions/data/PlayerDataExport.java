package za.co.neroland.nerofactions.data;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.platform.Services;

/**
 * The DSAR (data-subject access request) export: <b>everything</b> NeroFactions stores for one
 * player, serialised to a stable JSON object for {@code /nerofactions data export} (GDPR Art. 15 /
 * Art. 20; POPIA s23). One section per {@link FactionsStores} entry with an export key — the same
 * list erasure and the retention sweep walk — so a store cannot be erased but left out of the
 * export. Pure serialisation: writes nothing, logs nothing.
 *
 * <p><b>Shape</b> (keys sorted within every map section, so two exports of the same data are
 * byte-identical apart from {@code generated_at}):
 *
 * <pre>{@code
 * {
 *   "mod": "nerofactions",
 *   "mod_version": "0.7.0-beta.1",
 *   "generated_at": "2026-08-08T12:00:00Z",
 *   "reputation": { "standings": { "<faction id>": <int standing>, ... } },
 *   "membership": {
 *     "memberships":       { "<faction id>": <joined-at epoch ms>, ... },
 *     "cooldown_until_ms": <epoch ms, 0 = none>,
 *     "left":              { "<faction id>": <left-at epoch ms, decay pending>, ... },
 *     "accrual":           { "<faction id>": { "<source>": { "day": <day stamp>, "accrued": <int> } } },
 *     "reward_watermarks": { "<faction id>": <highest tier ordinal ever granted>, ... }
 *   },
 *   "contracts":         { ... ContractState.export ... },
 *   "miner_quota":       { ... QuotaState.export ... },
 *   "outposts_visited":  [ ... OutpostVisits.export ... ],
 *   "workbench_access":  { ... WorkbenchAccess.export ... },
 *   "corporate_daily":   { ... CorporateLedger.export ... },
 *   "void_boon_ticks_remaining": <int, 0 = none>
 * }
 * }</pre>
 *
 * <p>A player the mod holds nothing for gets the same envelope with every section empty (and
 * zeros) — an honest "we store nothing about you". The subject's UUID is deliberately <b>not</b>
 * embedded: the export travels through chat components, and the requester already knows whose
 * data they asked for (data minimisation).
 */
public final class PlayerDataExport {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private PlayerDataExport() {
    }

    /** The export for {@code player} from the live server's stores, stamped with the real clock. */
    public static String forPlayer(MinecraftServer server, UUID player) {
        return toJson(FactionsStores.live(server), (state, uuid) -> state.export().apply(server, uuid), player,
                Services.PLATFORM.getModVersion(), System.currentTimeMillis());
    }

    /**
     * The pure core, parameterised for the plain-JVM tests: {@code transients} reads each exported
     * in-memory state (live: the state's own exporter against the server).
     */
    static String toJson(FactionsStores.Snapshot stores,
            BiFunction<FactionsStores.Transient, UUID, ? extends JsonElement> transients, UUID player,
            String modVersion, long generatedAtMs) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", "nerofactions");
        root.addProperty("mod_version", modVersion);
        root.addProperty("generated_at", Instant.ofEpochMilli(generatedAtMs).toString());
        for (FactionsStores.Persistent<?> store : FactionsStores.PERSISTENT) {
            root.add(store.exportKey(), store.exportIn(stores, player));
        }
        for (FactionsStores.Transient state : FactionsStores.TRANSIENT) {
            if (state.exportKey() != null) {
                JsonElement value = transients.apply(state, player);
                root.add(state.exportKey(), value == null ? JsonNull.INSTANCE : value);
            }
        }
        return GSON.toJson(root);
    }

    /** The {@code reputation} section. */
    static JsonObject reputationSection(FactionReputationState reputation, UUID player) {
        JsonObject section = new JsonObject();
        JsonObject standings = new JsonObject();
        sorted(reputation.standingsOf(player)).forEach(standings::addProperty);
        section.add("standings", standings);
        return section;
    }

    /** The {@code membership} section. */
    static JsonObject membershipSection(FactionMembershipState membership, UUID player) {
        FactionMembershipState.ExportView view = membership.exportOf(player);
        JsonObject section = new JsonObject();
        JsonObject memberships = new JsonObject();
        sorted(view.memberships()).forEach(memberships::addProperty);
        section.add("memberships", memberships);
        section.addProperty("cooldown_until_ms", view.cooldownUntil());
        JsonObject left = new JsonObject();
        sorted(view.left()).forEach(left::addProperty);
        section.add("left", left);
        JsonObject accrual = new JsonObject();
        sorted(view.accrual()).forEach((faction, bySource) -> {
            JsonObject sources = new JsonObject();
            sorted(bySource).forEach((source, today) -> {
                JsonObject row = new JsonObject();
                row.addProperty("day", today.dayStamp());
                row.addProperty("accrued", today.accrued());
                sources.add(source, row);
            });
            accrual.add(faction, sources);
        });
        section.add("accrual", accrual);
        JsonObject watermarks = new JsonObject();
        sorted(view.rewarded()).forEach(watermarks::addProperty);
        section.add("reward_watermarks", watermarks);
        return section;
    }

    private static <V> Map<String, V> sorted(Map<String, V> map) {
        return new TreeMap<>(map);
    }
}
