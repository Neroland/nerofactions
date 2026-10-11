package za.co.neroland.nerofactions.reputation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.TradeModifier;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.reputation.ReputationSources.Source;

/**
 * Trade reputation from market sales. When NeroEconomy reports a settled sale, the seller earns
 * {@link Source#TRADE} reputation with each faction they <em>belong to</em> whose speciality
 * matches the sold item. Specialities are the faction's existing data-driven {@code trade} table:
 * a modifier's {@code tag} matches when the item carries that item tag or the id names the item
 * itself (the same rule as faction pricing and the trade terminal), so packs configure this with
 * no new data.
 *
 * <p>The award runs through {@link ReputationSources#award}, so the trade weight, the per-faction
 * daily trade cap and enemy bleed all apply; spamming tiny sales cannot farm standing past the cap.
 *
 * <p><b>Deliberately free of NeroEconomy imports.</b> {@code EconomyIntegration} subscribes to the
 * sale event and hands over plain values only.
 *
 * <p>Privacy (POPIA/GDPR): the seller's UUID is used on the server for this one award and then
 * dropped; the event is never stored, logged or shown to anyone. The buyer is never known.
 */
public final class SaleReputation {

    private SaleReputation() {
    }

    /**
     * One settled sale. Server thread. Never throws (a listener failure must not reach the
     * economy, which would only report and skip it anyway).
     */
    public static void onSale(UUID seller, String itemId, long quantity) {
        try {
            int base = NeroFactionsConfig.SALE_AWARD_BASE.get();
            if (base <= 0 || seller == null || itemId == null || quantity <= 0L) {
                return;
            }
            MinecraftServer server = NeroFactionsCommon.REPUTATION_PROVIDER.boundServer();
            Identifier item = Identifier.tryParse(itemId);
            if (server == null || item == null) {
                return;
            }
            Set<Identifier> memberships = FactionMembership.membershipOf(server, seller);
            if (memberships.isEmpty()) {
                return;
            }
            List<Identifier> matched = matchingFactions(item, itemTags(item), memberships,
                    FactionDefinitions.factionsForServer(server));
            for (Identifier faction : matched) {
                ReputationSources.award(server, seller, faction, Source.TRADE, base);
            }
        } catch (RuntimeException e) {
            NeroFactionsCommon.LOGGER.debug("[NeroFactions] Sale reputation skipped after an error.", e);
        }
    }

    /**
     * The member factions whose speciality matches the item. Pure: reads only its arguments.
     *
     * @param item        the sold item's id
     * @param itemTags    the item tags the item carries
     * @param memberships the factions the seller belongs to
     * @param factions    the loaded faction definitions
     * @return the matching faction ids, in definition order, each at most once
     */
    public static List<Identifier> matchingFactions(Identifier item, Set<Identifier> itemTags,
            Set<Identifier> memberships, Map<Identifier, FactionDefinition> factions) {
        List<Identifier> out = new ArrayList<>();
        if (item == null || memberships == null || factions == null) {
            return out;
        }
        for (FactionDefinition faction : factions.values()) {
            if (!memberships.contains(faction.id())) {
                continue;
            }
            for (TradeModifier modifier : faction.trade()) {
                Identifier speciality = modifier.tag();
                if (speciality != null && (speciality.equals(item)
                        || (itemTags != null && itemTags.contains(speciality)))) {
                    out.add(faction.id());
                    break;
                }
            }
        }
        return out;
    }

    /** The item tags {@code item} carries (empty for an unknown id). */
    private static Set<Identifier> itemTags(Identifier item) {
        return BuiltInRegistries.ITEM.get(item)
                .map(holder -> holder.tags().map(TagKey::location).collect(Collectors.toSet()))
                .orElse(Set.of());
    }
}
