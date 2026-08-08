package za.co.neroland.nerofactions.content;

import net.minecraft.resources.Identifier;

/**
 * One thing the last faction-content load rejected, alongside the log line it produced.
 *
 * <p>Collected purely so {@code /nerofactions reload-check} can hand back the same picture the
 * server log holds without making anybody read the log. Bad content is <b>never</b> fatal in
 * NeroFactions: a malformed faction file, a dangling enemy reference or a broken tier ladder drops
 * (or prunes) the offending entry and the rest of the pack still loads.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> resource ids and codec messages only — never player data, never a
 * filesystem path (an exception's own message can carry one, so only its type is ever quoted).
 *
 * @param severity whether the whole definition was dropped or only part of it ignored
 * @param id       the definition id the complaint concerns
 * @param detail   a short, human-readable reason
 */
public record ValidationIssue(Severity severity, Identifier id, String detail) {

    /** How badly a definition was affected. */
    public enum Severity {

        /** The definition is not loaded at all. */
        DROPPED,

        /** The definition is loaded, but part of it (an entry, a reference) was skipped. */
        IGNORED
    }

    public static ValidationIssue dropped(Identifier id, String detail) {
        return new ValidationIssue(Severity.DROPPED, id, detail);
    }

    public static ValidationIssue ignored(Identifier id, String detail) {
        return new ValidationIssue(Severity.IGNORED, id, detail);
    }

    /** A one-line, log-safe rendering: {@code [DROPPED] nerofactions:space_guild - bad tiers}. */
    public String describe() {
        return "[" + this.severity + "] " + this.id + " - " + this.detail;
    }
}
