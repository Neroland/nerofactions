package za.co.neroland.nerofactions.content.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses.Bonus;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses.BonusEnv;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses.DamageEnv;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses.DamageKind;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses.GearSet;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses.UnionEnvironment;

class GearSetBonusesTest {
    private static final FactionDefinition UNION = new FactionDefinition(
            OutpostFaction.MINER_UNION.factionId(), "Miner Union", "test",
            Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500),
            Map.of(), List.of(), List.of(), Optional.empty());

    @Test
    void theExosuitRequiresFourPiecesCurrentMembershipAndTrustedStanding() {
        assertTrue(GearSetBonuses.eligible(4, true, UNION, 1000));
        assertFalse(GearSetBonuses.eligible(3, true, UNION, 2500));
        assertFalse(GearSetBonuses.eligible(4, false, UNION, 2500));
        assertFalse(GearSetBonuses.eligible(4, true, UNION, 999));
        assertFalse(GearSetBonuses.eligible(4, true, null, 2500));
    }

    @Test
    void theBonusAndDaylightPenaltyAreMutuallyExclusive() {
        assertEquals(UnionEnvironment.UNDERGROUND, GearSetBonuses.unionEnvironment(39, true, true, true));
        assertEquals(UnionEnvironment.UNDERGROUND, GearSetBonuses.unionEnvironment(120, false, true, true));
        assertEquals(UnionEnvironment.DAYLIGHT, GearSetBonuses.unionEnvironment(40, true, true, true));
        assertEquals(UnionEnvironment.NEUTRAL, GearSetBonuses.unionEnvironment(60, true, false, true));
        assertEquals(UnionEnvironment.NEUTRAL, GearSetBonuses.unionEnvironment(60, true, true, false));
    }

    @Test
    void salvagerToolsMaySaveWearWhileArmorWearsFaster() {
        assertEquals(0, GearSetBonuses.SalvageWear.adjust(1, false, 0));
        assertEquals(1, GearSetBonuses.SalvageWear.adjust(1, false, 1));
        assertEquals(2, GearSetBonuses.SalvageWear.adjust(1, true, 0));
        assertEquals(1, GearSetBonuses.SalvageWear.adjust(1, true, 1));
        assertEquals(5, GearSetBonuses.SalvageWear.adjust(4, true, 3));
    }

    // ---- eligibility: every set's faction, golden + failure modes ----

    @Test
    void everySetIsOnlyEligibleAtTrustedWithFourPiecesAndCurrentMembership() {
        for (OutpostFaction faction : OutpostFaction.values()) {
            if (faction == OutpostFaction.NONE) continue;
            var definition = ladder(faction);
            assertTrue(GearSetBonuses.eligible(4, true, definition, 1000), faction + " trusted");
            assertTrue(GearSetBonuses.eligible(4, true, definition, 2500), faction + " inner circle");
            assertFalse(GearSetBonuses.eligible(4, true, definition, 400), faction + " member tier");
            assertFalse(GearSetBonuses.eligible(3, true, definition, 2500), faction + " three pieces");
            assertFalse(GearSetBonuses.eligible(4, false, definition, 2500), faction + " not a member");
            assertFalse(GearSetBonuses.eligible(4, true, definition, -500), faction + " hostile");
        }
    }

    // ---- nothing active: no set means no bonus and no trade-off ----

    @Test
    void anIneligibleWearerGetsNoBonusesAndPlainDamage() {
        var none = EnumSet.noneOf(GearSet.class);
        assertTrue(GearSetBonuses.bonuses(none, new BonusEnv(true, true, true, UnionEnvironment.UNDERGROUND)).isEmpty());
        for (DamageKind kind : DamageKind.values()) {
            assertEquals(10F, GearSetBonuses.incomingDamage(none, kind, env(true, UnionEnvironment.UNDERGROUND), 10F),
                    kind.name());
        }
        assertEquals(1F, GearSetBonuses.exhaustion(none, 1F));
    }

    // ---- Miner Union ----

    @Test
    void unionExosuitHastesAndShieldsUndergroundAndSlowsInDaylight() {
        var union = Set.of(GearSet.UNION_EXOSUIT);
        assertEquals(EnumSet.of(Bonus.HASTE), GearSetBonuses.bonuses(union, still(UnionEnvironment.UNDERGROUND)));
        assertEquals(EnumSet.of(Bonus.SLOWNESS), GearSetBonuses.bonuses(union, still(UnionEnvironment.DAYLIGHT)));
        assertTrue(GearSetBonuses.bonuses(union, still(UnionEnvironment.NEUTRAL)).isEmpty());
        assertEquals(6.5F, GearSetBonuses.incomingDamage(union, DamageKind.FALL, env(false, UnionEnvironment.UNDERGROUND), 10F), 1e-4);
        assertEquals(6.5F, GearSetBonuses.incomingDamage(union, DamageKind.EXPLOSION, env(false, UnionEnvironment.UNDERGROUND), 10F), 1e-4);
        assertEquals(10F, GearSetBonuses.incomingDamage(union, DamageKind.FALL, env(false, UnionEnvironment.DAYLIGHT), 10F));
        assertEquals(10F, GearSetBonuses.incomingDamage(union, DamageKind.MELEE, env(false, UnionEnvironment.UNDERGROUND), 10F));
    }

    // ---- Space Guild ----

    @Test
    void guildFlightSuitCancelsFallSpeedsUpAndPaysWithToughnessAndMeleeWeakness() {
        var guild = Set.of(GearSet.GUILD_FLIGHT_SUIT);
        assertEquals(EnumSet.of(Bonus.GUILD_SPEED, Bonus.GUILD_TOUGHNESS_PENALTY),
                GearSetBonuses.bonuses(guild, still(UnionEnvironment.NEUTRAL)));
        assertTrue(GearSetBonuses.bonuses(guild, new BonusEnv(true, false, false, UnionEnvironment.NEUTRAL))
                .contains(Bonus.SLOW_FALLING));
        assertEquals(0F, GearSetBonuses.incomingDamage(guild, DamageKind.FALL, env(false, UnionEnvironment.NEUTRAL), 10F));
        assertEquals(11.5F, GearSetBonuses.incomingDamage(guild, DamageKind.MELEE, env(false, UnionEnvironment.NEUTRAL), 10F), 1e-4);
        assertEquals(10F, GearSetBonuses.incomingDamage(guild, DamageKind.OTHER, env(false, UnionEnvironment.NEUTRAL), 10F));
        assertEquals(0.1, GearSetBonuses.GUILD_SPEED.amount(), 1e-9);
        assertEquals(-2, GearSetBonuses.GUILD_TOUGHNESS.amount(), 1e-9);
    }

    @Test
    void theNavigationBeaconCancelsFallDamageWithoutAnySet() {
        assertEquals(0F, GearSetBonuses.incomingDamage(EnumSet.noneOf(GearSet.class), DamageKind.FALL,
                new DamageEnv(false, true, UnionEnvironment.NEUTRAL), 10F));
    }

    // ---- Void Cult ----

    @Test
    void voidVestmentsTakeExtraDamageOnlyInDaylight() {
        var vestments = Set.of(GearSet.VOID_VESTMENTS);
        assertEquals(12F, GearSetBonuses.incomingDamage(vestments, DamageKind.MELEE, env(true, UnionEnvironment.NEUTRAL), 10F), 1e-4);
        assertEquals(12F, GearSetBonuses.incomingDamage(vestments, DamageKind.OTHER, env(true, UnionEnvironment.NEUTRAL), 10F), 1e-4);
        assertEquals(10F, GearSetBonuses.incomingDamage(vestments, DamageKind.MELEE, env(false, UnionEnvironment.NEUTRAL), 10F));
        assertTrue(GearSetBonuses.bonuses(vestments, still(UnionEnvironment.NEUTRAL)).isEmpty());
    }

    // ---- Nero Corporation ----

    @Test
    void corporateSuitHitsHarderResistsAtFullHealthAndBurnsHunger() {
        var corporate = Set.of(GearSet.CORPORATE_POWER_SUIT);
        assertEquals(EnumSet.of(Bonus.CORPORATE_ATTACK, Bonus.RESISTANCE),
                GearSetBonuses.bonuses(corporate, new BonusEnv(false, true, false, UnionEnvironment.NEUTRAL)));
        assertEquals(EnumSet.of(Bonus.CORPORATE_ATTACK),
                GearSetBonuses.bonuses(corporate, new BonusEnv(false, false, false, UnionEnvironment.NEUTRAL)));
        assertEquals(1.5F, GearSetBonuses.exhaustion(corporate, 1F), 1e-6);
        assertEquals(2, GearSetBonuses.CORPORATE_ATTACK.amount(), 1e-9);
    }

    // ---- Terraforming Authority ----

    @Test
    void authoritySuitBreathesCuresAndIgnoresFreezingAndPoisonButHitsSofter() {
        var authority = Set.of(GearSet.AUTHORITY_ENVIRONMENT_SUIT);
        assertEquals(EnumSet.of(Bonus.AUTHORITY_ATTACK_PENALTY, Bonus.WATER_BREATHING, Bonus.CURE_POISON, Bonus.THAW),
                GearSetBonuses.bonuses(authority, still(UnionEnvironment.NEUTRAL)));
        assertEquals(0F, GearSetBonuses.incomingDamage(authority, DamageKind.FREEZING, env(false, UnionEnvironment.NEUTRAL), 10F));
        assertEquals(0F, GearSetBonuses.incomingDamage(authority, DamageKind.POISON, env(false, UnionEnvironment.NEUTRAL), 1F));
        assertEquals(10F, GearSetBonuses.incomingDamage(authority, DamageKind.MELEE, env(false, UnionEnvironment.NEUTRAL), 10F));
        assertEquals(-1, GearSetBonuses.AUTHORITY_ATTACK.amount(), 1e-9);
    }

    @Test
    void poisonAndFreezingStillHurtWithoutTheAuthorityBonus() {
        for (GearSet other : GearSet.values()) {
            if (other == GearSet.AUTHORITY_ENVIRONMENT_SUIT) continue;
            var worn = Set.of(other);
            assertEquals(1F, GearSetBonuses.incomingDamage(worn, DamageKind.POISON, env(false, UnionEnvironment.NEUTRAL), 1F),
                    other.name());
            assertEquals(1F, GearSetBonuses.incomingDamage(worn, DamageKind.FREEZING, env(false, UnionEnvironment.NEUTRAL), 1F),
                    other.name());
        }
    }

    // ---- Free Colonists ----

    @Test
    void colonistGearRegeneratesOnlyWhileStandingStill() {
        var colonist = Set.of(GearSet.COLONIST_FIELD_GEAR);
        assertEquals(EnumSet.of(Bonus.REGENERATION), GearSetBonuses.bonuses(colonist, still(UnionEnvironment.NEUTRAL)));
        assertTrue(GearSetBonuses.bonuses(colonist, new BonusEnv(false, true, false, UnionEnvironment.NEUTRAL)).isEmpty());
        assertEquals(10F, GearSetBonuses.incomingDamage(colonist, DamageKind.FALL, env(true, UnionEnvironment.UNDERGROUND), 10F));
    }

    // ---- Salvagers ----

    @Test
    void salvagerPatchworkHasNoDamageOrEffectBonus() {
        var salvager = Set.of(GearSet.SALVAGER_PATCHWORK);
        assertTrue(GearSetBonuses.bonuses(salvager, still(UnionEnvironment.UNDERGROUND)).isEmpty());
        for (DamageKind kind : DamageKind.values()) {
            assertEquals(10F, GearSetBonuses.incomingDamage(salvager, kind, env(true, UnionEnvironment.UNDERGROUND), 10F),
                    kind.name());
        }
        assertEquals(1F, GearSetBonuses.exhaustion(salvager, 1F));
    }

    private static BonusEnv still(UnionEnvironment union) {
        return new BonusEnv(false, false, true, union);
    }

    private static DamageEnv env(boolean daylight, UnionEnvironment union) {
        return new DamageEnv(daylight, false, union);
    }

    private static FactionDefinition ladder(OutpostFaction faction) {
        return new FactionDefinition(faction.factionId(), faction.name(), "test",
                Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500),
                Map.of(), List.of(), List.of(), Optional.empty());
    }
}
