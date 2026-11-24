package za.co.neroland.nerofactions.content.faction.voidcult;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.2 {
import net.minecraft.world.item.ToolMaterial;
//?} else {
/*import net.minecraft.world.item.Tiers;
*///?}
//? if <1.21.5 {
/*import net.minecraft.world.item.SwordItem;
*///?}
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

//? if >=1.21.5 {
public final class RiftBlade extends Item {
//?} else {
/*public final class RiftBlade extends SwordItem {
*///?}

    private static final Identifier FACTION = OutpostFaction.VOID_CULT.factionId();
    private static final int BLINDNESS_TICKS = 40;
    private static final int KILL_XP = 2;

    enum HitEffect {
        NONE,
        BLINDNESS,
        SIPHON;

        static HitEffect forHit(FactionDefinition faction, boolean member, int standing,
                boolean lethal, boolean hostileMob) {
            if (!member || !GatedRecipeCheck.standingMet(faction, standing, FactionTier.MEMBER)) {
                return HitEffect.NONE;
            }
            if (!lethal) {
                return HitEffect.BLINDNESS;
            }
            return hostileMob ? HitEffect.SIPHON : HitEffect.NONE;
        }
    }

    public RiftBlade(Item.Properties properties) {
        //? if >=1.21.5 {
        super(properties.sword(ToolMaterial.IRON, 3.0F, -2.4F));
        //?} else if >=1.21.2 {
        /*super(ToolMaterial.IRON, 3.0F, -2.4F, properties);
        *///?} else {
        /*super(Tiers.IRON, properties.attributes(createAttributes(Tiers.IRON, 3, -2.4F)));
        *///?}
    }

    //? if >=1.21 {
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        applyHitEffect(target, attacker);
    }
    //?} else {
    /*@Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean handled = super.hurtEnemy(stack, target, attacker);
        if (handled) {
            applyHitEffect(target, attacker);
        }
        return handled;
    }
    *///?}

    private static void applyHitEffect(LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }
        var server = player.level().getServer();
        if (server == null || !server.isSameThread()) {
            return;
        }
        var playerId = player.getUUID();
        boolean member = FactionMembership.membershipOf(server, playerId).contains(FACTION);
        FactionDefinition faction = FactionDefinitions.factionsForServer(server).get(FACTION);
        int standing = ReputationApi.getReputation(playerId, FACTION);
        switch (HitEffect.forHit(faction, member, standing, target.isDeadOrDying(), target instanceof Enemy)) {
            case NONE -> { }
            case BLINDNESS -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS), player);
            case SIPHON -> player.giveExperiencePoints(KILL_XP);
        }
    }
}
