package za.co.neroland.nerofactions.integration.quests;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * The NeroQuests integration — which is <b>contract-based, not code-based</b>: both directions
 * already flow through Neroland Core's shared seams, so this class imports no NeroQuests type
 * (there is no NeroQuests public API package; its {@code quest.engine} internals are off-limits)
 * and exists to document the contract in one place and say at startup which half is live.
 *
 * <p><b>Quest completion → faction reputation.</b> NeroQuests ships the datapack reward type
 * {@code neroquests:reputation}, which checks {@code ReputationApi.hasRealProvider()} and then
 * writes through Core's {@code ReputationApi.adjust} — it started working the moment Stage 1
 * bound NeroFactions' provider. Note the contract's shape: that reward calls {@code adjust}
 * <em>directly</em>, so quest rewards bypass NeroFactions' {@code ReputationSources} weights,
 * daily caps and enemy bleed. That is NeroQuests' side of the contract and accepted as designed:
 * quests are the highest-weighted source anyway (weight 1.0), the quest author states the exact
 * amount (which may be negative), and a quest can only complete once — the caps exist to bound
 * grindable sources, which a quest is not.
 *
 * <p><b>Faction progression → quest content.</b> Stage 3's {@code TierCrossings} publishes every
 * tier boundary on Core's {@code ThresholdEvents} bus (channel
 * {@code nerofactions:reputation_tier}, scope = faction id, value = tier ordinal), and
 * NeroQuests' {@code custom_event} objective consumes that bus natively — a quest pack can gate
 * on "reach Member with the Space Guild" with zero code on either side.
 *
 * <p><b>Fallback (and baseline).</b> {@code trigger.InternalTriggers}' combat award runs whether
 * or not NeroQuests is present, so a Core-only server genuinely earns standing; quests layer on
 * top rather than being the only door in. Gather/deliver-style earning is deliberately absent in
 * 0.1.0 — it arrives honestly with Stage 5's trade terminal (the {@code TRADE} source), not as a
 * noisy block-break hook or a command pretending to be gameplay.
 */
public final class QuestsIntegration {

    private QuestsIntegration() {
    }

    /**
     * Logs (once, at info) which half of the contract is live. Called once from
     * {@code Integrations.init()}; there is nothing to register — see the class javadoc.
     */
    public static void init(boolean questsPresent) {
        if (questsPresent) {
            NeroFactionsCommon.LOGGER.info(
                    "[NeroFactions] NeroQuests present - quest rewards can pay reputation "
                            + "(neroquests:reputation via Core's ReputationApi) and quest packs can "
                            + "gate on tier crossings (nerofactions:reputation_tier).");
        } else {
            NeroFactionsCommon.LOGGER.info(
                    "[NeroFactions] NeroQuests absent - standing is earned through the internal "
                            + "combat trigger (and trade, once the trade terminal ships).");
        }
    }
}
