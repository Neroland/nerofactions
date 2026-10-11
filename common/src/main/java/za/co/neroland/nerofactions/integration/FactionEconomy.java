package za.co.neroland.nerofactions.integration;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.integration.economy.EconomyIntegration;
import za.co.neroland.nerolandcore.economy.CoreCurrencies;
import za.co.neroland.nerolandcore.economy.CurrencyApi;

/**
 * The NeroEconomy-free façade the rest of NeroFactions (commands, the trade terminal) calls for
 * credits: faction treasuries, dues, payouts and the terminal's credit counter. It imports no
 * NeroEconomy type; it reaches {@link EconomyIntegration} only after {@link Integrations} has
 * confirmed, once at init, that NeroEconomy is loaded <em>and</em> its sibling API (version 1+)
 * linked and was claimed. With NeroEconomy absent that branch never executes, so the bridge class
 * is never loaded.
 *
 * <p><b>Fallback.</b> Treasuries need NeroEconomy's sibling API (foreign accounts), so they are
 * simply unavailable without it. The terminal's credit counter falls back to Neroland Core's
 * {@link CurrencyApi} (checking its boolean result) when some economy provides credits but the
 * sibling API is missing or older than version 1.
 *
 * <p>Privacy (POPIA/GDPR): nothing here logs or stores a player id; the UUID passed in is the one
 * the caller already holds, used on the server for that one call.
 */
public final class FactionEconomy {

    private FactionEconomy() {
    }

    /** Whether faction treasuries (dues, payouts, balances) work on this server. */
    public static boolean treasuriesAvailable() {
        return Integrations.economySiblingApiReady();
    }

    /** Whether the trade terminal can sell for credits at all. */
    public static boolean creditsAvailable() {
        return Integrations.economySiblingApiReady() || CurrencyApi.hasRealProvider();
    }

    /** A member pays {@code amount} credits into {@code faction}'s treasury (reason {@code dues}). */
    public static EconomyOutcome payDues(MinecraftServer server, UUID member, Identifier faction, long amount) {
        if (!treasuriesAvailable()) {
            return EconomyOutcome.unavailable();
        }
        return EconomyIntegration.payDues(server, member, faction, amount);
    }

    /** {@code faction}'s treasury pays {@code amount} credits to {@code payee} (reason {@code treasury_payout}). */
    public static EconomyOutcome payout(MinecraftServer server, Identifier faction, UUID payee, long amount) {
        if (!treasuriesAvailable()) {
            return EconomyOutcome.unavailable();
        }
        return EconomyIntegration.payout(server, faction, payee, amount);
    }

    /** What {@code faction}'s treasury holds, or -1 when treasuries are unavailable. */
    public static long treasuryBalance(MinecraftServer server, Identifier faction) {
        if (!treasuriesAvailable()) {
            return -1L;
        }
        return EconomyIntegration.treasuryBalance(server, faction);
    }

    /**
     * Charges {@code price} credits for a terminal purchase (reason {@code terminal_purchase}).
     * The caller delivers the goods only when the outcome is {@link EconomyOutcome#ok()}.
     */
    public static EconomyOutcome chargeTerminal(MinecraftServer server, UUID buyer, long price) {
        if (Integrations.economySiblingApiReady()) {
            return EconomyIntegration.chargeTerminal(server, buyer, price);
        }
        if (CurrencyApi.hasRealProvider()) {
            return CurrencyApi.withdraw(buyer, CoreCurrencies.CREDITS, price)
                    ? EconomyOutcome.applied()
                    : EconomyOutcome.refused("message.nerofactions.terminal.credits_refused");
        }
        return EconomyOutcome.unavailable();
    }
}
