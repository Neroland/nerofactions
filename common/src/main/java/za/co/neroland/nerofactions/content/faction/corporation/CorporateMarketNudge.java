package za.co.neroland.nerofactions.content.faction.corporation;

import java.util.List;
import net.minecraft.resources.Identifier;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.integration.Integrations;

public final class CorporateMarketNudge {
    private static final long WEEK_MS = 604_800_000L;
    private static final List<Identifier> ROTATION = List.of(Identifier.parse("minecraft:iron_ingot"),
            Identifier.parse("minecraft:gold_ingot"), Identifier.parse("minecraft:copper_ingot"));
    private static long loggedWeek = Long.MIN_VALUE;
    private static int loggedPercent = -1;

    private CorporateMarketNudge() {}

    static long week(long millis) { return Math.floorDiv(millis, WEEK_MS); }

    static Identifier commodity(long week) { return ROTATION.get(Math.floorMod(week, ROTATION.size())); }

    public static double multiplier(Identifier item, long millis) {
        return multiplier(item, millis, percent());
    }

    static double multiplier(Identifier item, long millis, int percent) {
        return commodity(week(millis)).equals(item) ? 1 + Math.clamp(percent, 0, 15) / 100.0 : 1;
    }

    private static int percent() {
        return NeroFactionsConfig.CORPORATE_COUNTER_ENABLED.get() ? NeroFactionsConfig.MARKET_NUDGE_CAP.get() : 0;
    }

    public static void audit(long millis) {
        if (!Integrations.economyPresent()) return;
        long week = week(millis);
        int percent = percent();
        if (loggedWeek == week && loggedPercent == percent) return;
        if (percent > 0 || loggedPercent > 0) {
            NeroFactionsCommon.LOGGER.info("[NeroFactions] Corporate weekly market nudge: commodity={}, percent={}.",
                    commodity(week), percent);
        }
        loggedWeek = week;
        loggedPercent = percent;
    }

    public static void clear() {
        loggedWeek = Long.MIN_VALUE;
        loggedPercent = -1;
    }
}
