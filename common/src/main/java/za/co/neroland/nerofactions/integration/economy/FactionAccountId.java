package za.co.neroland.nerofactions.integration.economy;

import za.co.neroland.neroeconomy.api.AccountId;

/**
 * <b>DORMANT — wired into nothing, on purpose.</b> The ready-made ledger identity for a faction
 * treasury ({@code kind = "faction"}, value = the faction id string), kept here so the faction-
 * wallet feature has a stable seam the moment it becomes shippable.
 *
 * <p>It is not shippable today: NeroEconomy's ledger <em>accepts</em> non-player
 * {@code AccountId} kinds at runtime, but its load path drops them —
 * {@code EconomySavedData.parseAccount} reads {@code if (!"player".equals(kind)) return null;} —
 * so any balance held under this id would silently evaporate on server restart. A wallet that
 * forgets its money violates "genuinely works", so nothing constructs this record until
 * NeroEconomy persists foreign account kinds. Do not wire it into anything before that; when the
 * time comes, delete this paragraph and this class becomes the feature's first line.
 *
 * <p>Like the rest of this package, the class imports NeroEconomy types ({@code compileOnly})
 * and is therefore only safe to classload when {@code isModLoaded("neroeconomy")} — which is
 * trivially satisfied while nothing references it at all.
 *
 * @param factionId the faction's id string (e.g. {@code "nerofactions:space_guild"})
 */
public record FactionAccountId(String factionId) implements AccountId {

    @Override
    public String kind() {
        return "faction";
    }

    @Override
    public String value() {
        return factionId;
    }
}
