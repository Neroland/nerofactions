package za.co.neroland.nerofactions.trigger;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.reputation.ReputationSources;

/**
 * NeroFactions' own gameplay reputation triggers — the guarantee that a <b>Core-only server</b>
 * (no NeroQuests, no NeroEconomy) can genuinely earn standing. Loader event hooks (the three
 * {@code *FactionsEvents} classes) translate their entity-death events into {@link #entityKilled};
 * everything here routes through {@link ReputationSources#award}, so the combat weight, the
 * per-faction daily cap and enemy bleed all apply exactly as they do to every other source.
 *
 * <p><b>0.1.0 ships exactly one trigger: COMBAT.</b> A player-credited kill of a hostile monster
 * ({@link MobCategory#MONSTER}) awards {@code combatAwardBase} (config; {@code 0} disables) with
 * <em>each faction the player is currently a member of</em> — membership, not mere standing, is
 * the rule: you fight in your colours, and a bystander with incidental reputation earns nothing.
 * Gather/deliver-style earning is deliberately <b>not</b> faked here (no block-break hook — far
 * too noisy — and no command pretending to be gameplay); it arrives honestly with Stage 5's trade
 * terminal as the {@code TRADE} source. This trigger stays active when NeroQuests is present:
 * quests layer on top of baseline earning, they do not replace it, and the daily combat cap keeps
 * the sum bounded.
 *
 * <p>Kill credit follows NeroQuests' resolution exactly: the direct attacker when that is a
 * player, otherwise whoever the game credits with the kill (arrows, pets, TNT); a death with no
 * player behind it credits nobody.
 */
public final class InternalTriggers {

    private InternalTriggers() {
    }

    /** {@code victim} died. Loader-neutral entry point for the per-loader death hooks. */
    public static void entityKilled(LivingEntity victim, DamageSource source) {
        if (victim == null || victim.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        ServerPlayer killer = creditedPlayer(victim, source);
        if (killer == null) {
            return;
        }
        MinecraftServer server = killer.level().getServer();
        if (server == null) {
            return;
        }
        int base = NeroFactionsConfig.COMBAT_AWARD_BASE.get();
        Set<Identifier> memberships =
                FactionMembershipState.get(server).membershipsOf(killer.getUUID());
        for (Identifier faction : combatAwardTargets(base, memberships)) {
            ReputationSources.award(server, killer.getUUID(), faction,
                    ReputationSources.Source.COMBAT, base);
        }
    }

    /**
     * The deterministic core, split out for the plain-JVM tests: which factions a combat award
     * goes to. Member factions only (in membership order), and a configured base of {@code 0} (or
     * less) is the documented off switch — no target list, no award, no cap bookkeeping.
     */
    static List<Identifier> combatAwardTargets(int configuredBase, Set<Identifier> memberships) {
        if (configuredBase <= 0 || memberships == null || memberships.isEmpty()) {
            return List.of();
        }
        return List.copyOf(memberships);
    }

    /** The player a kill is credited to, or {@code null} if no player was behind it. */
    private static ServerPlayer creditedPlayer(LivingEntity victim, DamageSource source) {
        if (source != null && source.getEntity() instanceof ServerPlayer direct) {
            return direct;
        }
        return victim.getKillCredit() instanceof ServerPlayer credited ? credited : null;
    }
}
