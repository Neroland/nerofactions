package za.co.neroland.nerofactions.content.faction;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.spaceguild.NavigationBeaconBlock;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.registry.NeroFactionsItems;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

public final class GearSetBonuses {
    private static final Identifier UNION = OutpostFaction.MINER_UNION.factionId();
    private static final int PIECES = 4;
    private static final int EFFECT_DURATION = 12;
    //? if >=1.21.5 {
    private static Holder<MobEffect> haste() { return MobEffects.HASTE; }
    private static Holder<MobEffect> slowness() { return MobEffects.SLOWNESS; }
    //?} else {
    /*private static Holder<MobEffect> haste() { return MobEffects.DIG_SPEED; }
    private static Holder<MobEffect> slowness() { return MobEffects.MOVEMENT_SLOWDOWN; }
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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!activeUnion(player)) {
                continue;
            }
            switch (environment(player)) {
                case UNDERGROUND -> player.addEffect(new MobEffectInstance(haste(), EFFECT_DURATION, 0,
                        true, false, true));
                case DAYLIGHT -> player.addEffect(new MobEffectInstance(slowness(), EFFECT_DURATION, 0,
                        true, false, true));
                case NEUTRAL -> { }
            }
        }
    }

    public static float incomingDamage(ServerPlayer player, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL) && NavigationBeaconBlock.protects(player)) {
            return 0;
        }
        if ((!source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FALL))
                || !activeUnion(player)) {
            return amount;
        }
        return environment(player) == UnionEnvironment.UNDERGROUND ? amount * 0.65F : amount;
    }

    private static boolean activeUnion(ServerPlayer player) {
        int equipped = pieces(player);
        if (!player.isAlive() || player.isSpectator() || equipped != PIECES) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        var id = player.getUUID();
        return eligible(equipped, FactionMembership.membershipOf(server, id).contains(UNION),
                FactionDefinitions.factionsForServer(server).get(UNION), ReputationApi.getReputation(id, UNION));
    }

    static boolean eligible(int equipped, boolean member, FactionDefinition faction, int standing) {
        return equipped == PIECES && member && GatedRecipeCheck.standingMet(faction, standing, FactionTier.TRUSTED);
    }

    private static int pieces(ServerPlayer player) {
        int pieces = 0;
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(NeroFactionsItems.UNION_HELMET.get())) pieces++;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(NeroFactionsItems.UNION_CHESTPLATE.get())) pieces++;
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(NeroFactionsItems.UNION_LEGGINGS.get())) pieces++;
        if (player.getItemBySlot(EquipmentSlot.FEET).is(NeroFactionsItems.UNION_BOOTS.get())) pieces++;
        return pieces;
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
