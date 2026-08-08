package za.co.neroland.nerofactions.data;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.platform.Services;

/**
 * The DSAR (data-subject access request) export: <b>everything</b> NeroFactions stores for one
 * player, serialised to a stable JSON object for {@code /nerofactions data export} (GDPR Art. 15 /
 * Art. 20; POPIA s23). Pure serialisation — reads the two stores through their package-private
 * snapshot seams, writes nothing, logs nothing.
 *
 * <p><b>Shape</b> (keys sorted within every section, so two exports of the same data are
 * byte-identical apart from {@code generated_at}):
 *
 * <pre>{@code
 * {
 *   "mod": "nerofactions",
 *   "mod_version": "0.0.1-alpha.1",
 *   "generated_at": "2026-08-08T12:00:00Z",
 *   "reputation": { "standings": { "<faction id>": <int standing>, ... } },
 *   "membership": {
 *     "memberships":       { "<faction id>": <joined-at epoch ms>, ... },
 *     "cooldown_until_ms": <epoch ms, 0 = none>,
 *     "left":              { "<faction id>": <left-at epoch ms, decay pending>, ... },
 *     "accrual":           { "<faction id>": { "<source>": { "day": <day stamp>, "accrued": <int> } } },
 *     "reward_watermarks": { "<faction id>": <highest tier ordinal ever granted>, ... }
 *   }
 * }
 * }</pre>
 *
 * <p>A player the mod holds nothing for gets the same envelope with every section empty (and
 * cooldown 0) — an honest "we store nothing about you". The subject's UUID is deliberately
 * <b>not</b> embedded: the export travels through chat components, and the requester already knows
 * whose data they asked for (data minimisation).
 */
public final class PlayerDataExport {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private PlayerDataExport() {
    }

    /** The export for {@code player} from the live server's stores, stamped with the real clock. */
    public static String forPlayer(MinecraftServer server, UUID player) {
        return toJson(FactionReputationState.get(server), FactionMembershipState.get(server),
                player, Services.PLATFORM.getModVersion(), System.currentTimeMillis());
    }

    /** The pure core, parameterised for the plain-JVM tests. */
    static String toJson(FactionReputationState reputation, FactionMembershipState membership,
            UUID player, String modVersion, long generatedAtMs) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", "nerofactions");
        root.addProperty("mod_version", modVersion);
        root.addProperty("generated_at", Instant.ofEpochMilli(generatedAtMs).toString());

        JsonObject reputationSection = new JsonObject();
        JsonObject standings = new JsonObject();
        sorted(reputation.standingsOf(player)).forEach(standings::addProperty);
        reputationSection.add("standings", standings);
        root.add("reputation", reputationSection);

        FactionMembershipState.ExportView view = membership.exportOf(player);
        JsonObject membershipSection = new JsonObject();
        JsonObject memberships = new JsonObject();
        sorted(view.memberships()).forEach(memberships::addProperty);
        membershipSection.add("memberships", memberships);
        membershipSection.addProperty("cooldown_until_ms", view.cooldownUntil());
        JsonObject left = new JsonObject();
        sorted(view.left()).forEach(left::addProperty);
        membershipSection.add("left", left);
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
        membershipSection.add("accrual", accrual);
        JsonObject watermarks = new JsonObject();
        sorted(view.rewarded()).forEach(watermarks::addProperty);
        membershipSection.add("reward_watermarks", watermarks);
        root.add("membership", membershipSection);

        return GSON.toJson(root);
    }

    private static <V> Map<String, V> sorted(Map<String, V> map) {
        return new TreeMap<>(map);
    }
}
