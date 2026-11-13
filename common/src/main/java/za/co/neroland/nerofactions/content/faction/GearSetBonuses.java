package za.co.neroland.nerofactions.content.faction;

import java.util.EnumSet;

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
    private static final AttributeModifier GUILD_SPEED = new AttributeModifier(GUILD_SPEED_ID, 0.1,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier GUILD_TOUGHNESS = new AttributeModifier(GUILD_TOUGHNESS_ID, -2,
            AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier CORPORATE_ATTACK = new AttributeModifier(CORPORATE_ATTACK_ID, 2,
            AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier AUTHORITY_ATTACK = new AttributeModifier(AUTHORITY_ATTACK_ID, -1,
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

    private GearSetBonuses() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        var activeSets = EnumSet.noneOf(OutpostFaction.class);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var worn = EnumSet.noneOf(FactionArmor.class);
            for (FactionArmor set : FactionArmor.values()) {
                if (active(player, set)) {
                    activeSets.add(set.faction());
                    worn.add(set);
                }
            }
            applyBonuses(player, worn);
        }
        FactionMilestones.fullSets(activeSets);
    }

    private static void applyBonuses(ServerPlayer player, EnumSet<FactionArmor> worn) {
        boolean flight = worn.contains(FactionArmor.GUILD_FLIGHT_SUIT);
        boolean corporate = worn.contains(FactionArmor.CORPORATE_POWER_SUIT);
        boolean authority = worn.contains(FactionArmor.AUTHORITY_ENVIRONMENT_SUIT);
        modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), GUILD_SPEED, flight);
        modifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), GUILD_TOUGHNESS, flight);
        modifier(player.getAttribute(Attributes.ATTACK_DAMAGE), CORPORATE_ATTACK, corporate);
        modifier(player.getAttribute(Attributes.ATTACK_DAMAGE), AUTHORITY_ATTACK, authority);
        if (flight && player.isShiftKeyDown() && !player.onGround() && !player.isInWater() && !player.getAbilities().flying) {
            effect(player, MobEffects.SLOW_FALLING, EFFECT_DURATION);
        }
        if (corporate && player.getHealth() >= player.getMaxHealth()) effect(player, resistance(), EFFECT_DURATION);
        if (authority) {
            effect(player, MobEffects.WATER_BREATHING, EFFECT_DURATION);
            player.removeEffect(MobEffects.POISON);
            player.setTicksFrozen(0);
        }
        if (worn.contains(FactionArmor.COLONIST_FIELD_GEAR) && player.onGround()
                && player.getDeltaMovement().horizontalDistanceSqr() < 0.0001) {
            // Vanilla Regeneration I heals every 50 ticks. Refresh at multiples of 50.
            if (player.tickCount % 50 < 10) effect(player, regeneration(), 50);
        }
        if (worn.contains(FactionArmor.UNION_EXOSUIT)) {
            switch (environment(player)) {
                case UNDERGROUND -> effect(player, haste(), EFFECT_DURATION);
                case DAYLIGHT -> effect(player, slowness(), EFFECT_DURATION);
                case NEUTRAL -> { }
            }
        }
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
        if (source.is(DamageTypeTags.IS_FREEZING) && active(player, FactionArmor.AUTHORITY_ENVIRONMENT_SUIT)) return 0;
        if (active(player, FactionArmor.VOID_VESTMENTS) && player.level().dimensionType().hasSkyLight()
                && player.level().getSkyDarken() < 4 && player.level().canSeeSky(player.blockPosition())) {
            amount *= 1.2F;
        }
        boolean fall = source.is(DamageTypeTags.IS_FALL);
        if (fall && (NavigationBeaconBlock.protects(player) || active(player, FactionArmor.GUILD_FLIGHT_SUIT))) {
            return 0;
        }
        if ((source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) && active(player, FactionArmor.GUILD_FLIGHT_SUIT)) {
            return amount * 1.15F;
        }
        if ((!source.is(DamageTypeTags.IS_EXPLOSION) && !fall) || !active(player, FactionArmor.UNION_EXOSUIT)) {
            return amount;
        }
        return environment(player) == UnionEnvironment.UNDERGROUND ? amount * 0.65F : amount;
    }

    public static float exhaustion(ServerPlayer player, float amount) {
        return active(player, FactionArmor.CORPORATE_POWER_SUIT) ? amount * 1.5F : amount;
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
