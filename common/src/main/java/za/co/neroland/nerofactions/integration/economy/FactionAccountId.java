package za.co.neroland.nerofactions.integration.economy;

import java.util.Optional;
import java.util.regex.Pattern;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * How a faction maps onto its NeroEconomy treasury account. Deliberately <b>free of NeroEconomy
 * imports</b> (plain NeroFactions code, safe to classload and unit-test without NeroEconomy):
 * {@link EconomyIntegration} turns the value computed here into NeroEconomy's
 * {@code ForeignAccountId(kind, value)}.
 *
 * <p>NeroEconomy persists accounts owned by a sibling mod under the <em>caller's mod id</em> as
 * their kind, so the kind is {@code nerofactions} and only NeroFactions can move them. The value
 * is the faction id string (for example {@code nerofactions:space_guild}), which must match
 * {@code [a-z0-9_.:/-]{1,64}}. A faction id is not personal data; it never holds a player name or
 * UUID.
 *
 * <p>This replaces the earlier dormant {@code faction}-kind account record: NeroEconomy refuses
 * that kind, and from 0.3.0 it persists foreign accounts across restarts, so the treasury feature
 * is now shippable.
 */
public final class FactionAccountId {

    /** The account kind: always the claiming mod's id. */
    public static final String KIND = NeroFactionsCommon.MOD_ID;

    private static final Pattern VALUE = Pattern.compile("[a-z0-9_.:/-]{1,64}");

    private FactionAccountId() {
    }

    /**
     * The treasury account value for {@code faction}, or empty when its id cannot be an account
     * value (too long, or a character NeroEconomy does not accept).
     */
    public static Optional<String> valueFor(Identifier faction) {
        if (faction == null) {
            return Optional.empty();
        }
        String value = faction.toString();
        return VALUE.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }
}
