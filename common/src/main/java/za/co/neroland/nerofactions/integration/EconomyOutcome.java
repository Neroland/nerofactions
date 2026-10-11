package za.co.neroland.nerofactions.integration;

/**
 * The NeroEconomy-free result of one economy call made through {@link FactionEconomy}: whether
 * it went through, and the translation key to show the player when it did not. Callers outside
 * {@code integration.economy} only ever see this record, never a NeroEconomy type.
 *
 * @param ok             true when the credits moved (or the same request was already applied)
 * @param translationKey the message to show on refusal; NeroEconomy's own
 *                       {@code economy_result.neroeconomy.*} keys or one of ours
 */
public record EconomyOutcome(boolean ok, String translationKey) {

    /** Shown when no economy that can do this is installed or ready. */
    public static final String UNAVAILABLE_KEY = "message.nerofactions.economy.unavailable";

    /** The call cannot be made on this server (no economy, or one too old for it). */
    public static EconomyOutcome unavailable() {
        return new EconomyOutcome(false, UNAVAILABLE_KEY);
    }

    /** A refusal with its message key. */
    public static EconomyOutcome refused(String translationKey) {
        return new EconomyOutcome(false, translationKey);
    }

    /** The call went through. */
    public static EconomyOutcome applied() {
        return new EconomyOutcome(true, null);
    }
}
