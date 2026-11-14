package za.co.neroland.nerofactions.content.faction;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.spaceguild.NavigationBeaconBlock;
import za.co.neroland.nerofactions.content.faction.voidcult.VoidAltarBlock;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.progression.FactionMilestones;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

public final class GearSetBonuses {
    private static final Identifier GUILD_SPEED_ID = Identifier.parse("nerofactions:guild_flight_speed");
    private static final Identifier GUILD_TOUGHNESS_ID = Identifier.parse("nerofactions:guild_flight_toughness");
    private static final Identifier CORPORATE_ATTACK_ID = Identifier.parse("nerofactions:corporate_attack");
    private static final Identifier AUTHORITY_ATTACK_ID = Identifier.parse("nerofactions:authority_attack");
    static final AttributeModifier GUILD_SPEED = new AttributeModifier(GUILD_SPEED_ID, 0.1,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    static final AttributeModifier GUILD_TOUGHNESS = new AttributeModifier(GUILD_TOUGHNESS_ID, -2,
            AttributeModifier.Operation.ADD_VALUE);
    static final AttributeModifier CORPORATE_ATTACK = new AttributeModifier(CORPORATE_ATTACK_ID, 2,
            AttributeModifier.Operation.ADD_VALUE);
    static final AttributeModifier AUTHORITY_ATTACK = new AttributeModifier(AUTHORITY_ATTACK_ID, -1,
            AttributeModifier.Operation.ADD_VALUE);
    private static final int PIECES = 4;
    private static final int EFFECT_DURATION = 12;
    //? if >=1.21.5 {
    private static Holder<MobEffect> haste() { return MobEffects.HASTE; }
    private static Holder<MobEffect> slowness() { return MobEffects.SLOWNESS; }
    private static Holder<MobEffect> resistance() { return MobEffects.RESISTANCE; }
    private static Holder<MobEffect> regeneration() { return MobEffects.REGENERATION; }
    //?} else {
    /*private static Holder<MobEffect> haste() { return MobEffects.DIG_SPEED; }
    private static Holder<MobEffect> slowness() { return MobEffects.MOVEMENT_SLOWDOWN; }
    private static Holder<MobEffect> resistance() { return MobEffects.DAMAGE_RESISTANCE; }
    private static Holder<MobEffect> regeneration() { return MobEffects.REGENERATION; }
    *///?}

    enum UnionEnvironment {
        UNDERGROUND,
        DAYLIGHT,
        NEUTRAL
    }

    /** Mirror of {@link FactionArmor} that tests can use without bootstrapping the armour registries. */
    enum GearSet {
        UNION_EXOSUIT,
        GUILD_FLIGHT_SUIT,
        VOID_VESTMENTS,
        CORPORATE_POWER_SUIT,
        AUTHORITY_ENVIRONMENT_SUIT,
        COLONIST_FIELD_GEAR,
        SALVAGER_PATCHWORK
    }

    /** Effects and modifiers the per-tick pass keeps applied. */
    enum Bonus {
        GUILD_SPEED,
        GUILD_TOUGHNESS_PENALTY,
        CORPORATE_ATTACK,
        AUTHORITY_ATTACK_PENALTY,
        SLOW_FALLING,
        RESISTANCE,
        WATER_BREATHING,
        CURE_POISON,
        THAW,
        REGENERATION,
        HASTE,
        SLOWNESS
    }

    /** What the player is doing this pass; {@code union} only matters while the exosuit is active. */
    record BonusEnv(boolean airborneSneak, boolean fullHealth, boolean standingStill, UnionEnvironment union) {
    }

    enum DamageKind {
        FREEZING,
        /** Magic damage taken while the Poison effect is on — what the Poison effect deals. */
        POISON,
        FALL,
        EXPLOSION,
        MELEE,
        OTHER
    }

    /** {@code daylightExposed}: sky-lit dimension, daytime, open sky above. */
    record DamageEnv(boolean daylightExposed, boolean beaconProtected, UnionEnvironment union) {
    }

    private GearSetBonuses() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        var activeSets = EnumSet.noneOf(OutpostFaction.class);
        int tick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var worn = EnumSet.noneOf(GearSet.class);
            for (FactionArmor set : FactionArmor.values()) {
                if (active(player, set)) {
                    activeSets.add(set.faction());
                    worn.add(gearSet(set));
                }
            }
            applyBonuses(player, worn, tick);
        }
        FactionMilestones.fullSets(activeSets);
    }

    static GearSet gearSet(FactionArmor armor) {
        return switch (armor) {
            case UNION_EXOSUIT -> GearSet.UNION_EXOSUIT;
            case GUILD_FLIGHT_SUIT -> GearSet.GUILD_FLIGHT_SUIT;
            case VOID_VESTMENTS -> GearSet.VOID_VESTMENTS;
            case CORPORATE_POWER_SUIT -> GearSet.CORPORATE_POWER_SUIT;
            case AUTHORITY_ENVIRONMENT_SUIT -> GearSet.AUTHORITY_ENVIRONMENT_SUIT;
            case COLONIST_FIELD_GEAR -> GearSet.COLONIST_FIELD_GEAR;
            case SALVAGER_PATCHWORK -> GearSet.SALVAGER_PATCHWORK;
        };
    }

    private static EnumSet<GearSet> worn(ServerPlayer player) {
        var worn = EnumSet.noneOf(GearSet.class);
        for (FactionArmor set : FactionArmor.values()) {
            if (active(player, set)) worn.add(gearSet(set));
        }
        return worn;
    }

    private static void applyBonuses(ServerPlayer player, Set<GearSet> worn, int tick) {
        var env = new BonusEnv(
                player.isShiftKeyDown() && !player.onGround() && !player.isInWater() && !player.getAbilities().flying,
                player.getHealth() >= player.getMaxHealth(),
                tick % 50 == 0 && player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 0.0001,
                worn.contains(GearSet.UNION_EXOSUIT) ? environment(player) : UnionEnvironment.NEUTRAL);
        var bonuses = bonuses(worn, env);
        modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), GUILD_SPEED, bonuses.contains(Bonus.GUILD_SPEED));
        modifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), GUILD_TOUGHNESS,
                bonuses.contains(Bonus.GUILD_TOUGHNESS_PENALTY));
        modifier(player.getAttribute(Attributes.ATTACK_DAMAGE), CORPORATE_ATTACK, bonuses.contains(Bonus.CORPORATE_ATTACK));
        modifier(player.getAttribute(Attributes.ATTACK_DAMAGE), AUTHORITY_ATTACK,
                bonuses.contains(Bonus.AUTHORITY_ATTACK_PENALTY));
        if (bonuses.contains(Bonus.SLOW_FALLING)) effect(player, MobEffects.SLOW_FALLING, EFFECT_DURATION);
        if (bonuses.contains(Bonus.RESISTANCE)) effect(player, resistance(), EFFECT_DURATION);
        if (bonuses.contains(Bonus.WATER_BREATHING)) effect(player, MobEffects.WATER_BREATHING, EFFECT_DURATION);
        if (bonuses.contains(Bonus.CURE_POISON)) player.removeEffect(MobEffects.POISON);
        if (bonuses.contains(Bonus.THAW)) player.setTicksFrozen(0);
        if (bonuses.contains(Bonus.REGENERATION)) effect(player, regeneration(), 50);
        if (bonuses.contains(Bonus.HASTE)) effect(player, haste(), EFFECT_DURATION);
        if (bonuses.contains(Bonus.SLOWNESS)) effect(player, slowness(), EFFECT_DURATION);
    }

    static EnumSet<Bonus> bonuses(Set<GearSet> worn, BonusEnv env) {
        var bonuses = EnumSet.noneOf(Bonus.class);
        if (worn.contains(GearSet.GUILD_FLIGHT_SUIT)) {
            bonuses.add(Bonus.GUILD_SPEED);
            bonuses.add(Bonus.GUILD_TOUGHNESS_PENALTY);
            if (env.airborneSneak()) bonuses.add(Bonus.SLOW_FALLING);
        }
        if (worn.contains(GearSet.CORPORATE_POWER_SUIT)) {
            bonuses.add(Bonus.CORPORATE_ATTACK);
            if (env.fullHealth()) bonuses.add(Bonus.RESISTANCE);
        }
        if (worn.contains(GearSet.AUTHORITY_ENVIRONMENT_SUIT)) {
            bonuses.add(Bonus.AUTHORITY_ATTACK_PENALTY);
            bonuses.add(Bonus.WATER_BREATHING);
            bonuses.add(Bonus.CURE_POISON);
            bonuses.add(Bonus.THAW);
        }
        if (worn.contains(GearSet.COLONIST_FIELD_GEAR) && env.standingStill()) bonuses.add(Bonus.REGENERATION);
        if (worn.contains(GearSet.UNION_EXOSUIT)) {
            switch (env.union()) {
                case UNDERGROUND -> bonuses.add(Bonus.HASTE);
                case DAYLIGHT -> bonuses.add(Bonus.SLOWNESS);
                case NEUTRAL -> { }
            }
        }
        return bonuses;
    }

    private static void modifier(net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
            AttributeModifier modifier, boolean active) {
        if (active) {
            if (!attribute.hasModifier(modifier.id())) attribute.addTransientModifier(modifier);
        } else {
            attribute.removeModifier(modifier.id());
        }
    }

    private static void effect(ServerPlayer player, Holder<MobEffect> effect, int duration) {
        player.addEffect(new MobEffectInstance(effect, duration, 0, true, false, true));
    }

    public static float incomingDamage(ServerPlayer player, DamageSource source, float amount) {
        if (source.is(DamageTypes.WITHER) && VoidAltarBlock.hasBoon(player)) return 0;
        DamageKind kind = damageKind(player, source);
        var worn = worn(player);
        boolean beacon = kind == DamageKind.FALL && NavigationBeaconBlock.protects(player);
        if (worn.isEmpty() && !beacon) return amount;
        var level = player.level();
        boolean daylight = worn.contains(GearSet.VOID_VESTMENTS) && level.dimensionType().hasSkyLight()
                && level.getSkyDarken() < 4 && level.canSeeSky(player.blockPosition());
        var union = worn.contains(GearSet.UNION_EXOSUIT) ? environment(player) : UnionEnvironment.NEUTRAL;
        return incomingDamage(worn, kind, new DamageEnv(daylight, beacon, union), amount);
    }

    private static DamageKind damageKind(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FREEZING)) return DamageKind.FREEZING;
        if (source.is(DamageTypeTags.IS_FALL)) return DamageKind.FALL;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return DamageKind.EXPLOSION;
        if (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return DamageKind.MELEE;
        if (source.is(DamageTypes.MAGIC) && player.hasEffect(MobEffects.POISON)) return DamageKind.POISON;
        return DamageKind.OTHER;
    }

    static float incomingDamage(Set<GearSet> worn, DamageKind kind, DamageEnv env, float amount) {
        boolean authority = worn.contains(GearSet.AUTHORITY_ENVIRONMENT_SUIT);
        if (authority && (kind == DamageKind.FREEZING || kind == DamageKind.POISON)) return 0;
        if (worn.contains(GearSet.VOID_VESTMENTS) && env.daylightExposed()) amount *= 1.2F;
        if (kind == DamageKind.FALL && (env.beaconProtected() || worn.contains(GearSet.GUILD_FLIGHT_SUIT))) return 0;
        if (kind == DamageKind.MELEE && worn.contains(GearSet.GUILD_FLIGHT_SUIT)) return amount * 1.15F;
        if ((kind == DamageKind.FALL || kind == DamageKind.EXPLOSION) && worn.contains(GearSet.UNION_EXOSUIT)
                && env.union() == UnionEnvironment.UNDERGROUND) {
            return amount * 0.65F;
        }
        return amount;
    }

    public static float exhaustion(ServerPlayer player, float amount) {
        return exhaustion(active(player, FactionArmor.CORPORATE_POWER_SUIT)
                ? EnumSet.of(GearSet.CORPORATE_POWER_SUIT) : EnumSet.noneOf(GearSet.class), amount);
    }

    static float exhaustion(Set<GearSet> worn, float amount) {
        return worn.contains(GearSet.CORPORATE_POWER_SUIT) ? amount * 1.5F : amount;
    }

    public static int salvageWear(LivingEntity user, ItemStack stack, int amount) {
        if (amount <= 0 || !(user instanceof ServerPlayer player)
                || !active(player, FactionArmor.SALVAGER_PATCHWORK)) return amount;
        return SalvageWear.adjust(amount, FactionArmor.SALVAGER_PATCHWORK.includes(stack),
                player.getRandom().nextInt(4));
    }

    static final class SalvageWear {
        static int adjust(int amount, boolean armor, int roll) {
            if (armor) return amount + amount / 4 + (roll < amount % 4 ? 1 : 0);
            return roll == 0 ? 0 : amount;
        }
    }

    public static boolean active(ServerPlayer player, FactionArmor set) {
        int equipped = set.equipped(player);
        if (!player.isAlive() || player.isSpectator() || equipped != PIECES) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        var id = player.getUUID();
        Identifier faction = set.factionId();
        return eligible(equipped, FactionMembership.membershipOf(server, id).contains(faction),
                FactionDefinitions.factionsForServer(server).get(faction), ReputationApi.getReputation(id, faction));
    }

    static boolean eligible(int equipped, boolean member, FactionDefinition faction, int standing) {
        return equipped == PIECES && member && GatedRecipeCheck.standingMet(faction, standing, FactionTier.TRUSTED);
    }

    private static UnionEnvironment environment(ServerPlayer player) {
        var level = player.level();
        return unionEnvironment(player.getBlockY(), level.dimensionType().hasSkyLight(),
                level.getSkyDarken() < 4, level.canSeeSky(player.blockPosition()));
    }

    static UnionEnvironment unionEnvironment(int y, boolean skylight, boolean day, boolean seesSky) {
        if (y < 40 || !skylight) {
            return UnionEnvironment.UNDERGROUND;
        }
        return day && seesSky ? UnionEnvironment.DAYLIGHT : UnionEnvironment.NEUTRAL;
    }
}
