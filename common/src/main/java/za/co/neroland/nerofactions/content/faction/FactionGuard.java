package za.co.neroland.nerofactions.content.faction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
//? if >=1.21.11 {
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
//?} else {
/*import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.reputation.ReputationSources;

public final class FactionGuard extends OutpostResident implements RangedAttackMob {
    private static final int DEFENSE_TICKS = 200;
    private boolean killAccounted;

    public FactionGuard(EntityType<? extends FactionGuard> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return OutpostResident.attributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 4);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new RangedAttackGoal(this, 0.85, 28, 10) {
            @Override public boolean canUse() { return ranged(faction()) && super.canUse(); }
            @Override public boolean canContinueToUse() { return ranged(faction()) && super.canContinueToUse(); }
        });
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
            @Override public boolean canUse() { return !ranged(faction()) && super.canUse(); }
            @Override public boolean canContinueToUse() { return !ranged(faction()) && super.canContinueToUse(); }
        });
    }

    public static boolean ranged(OutpostFaction faction) {
        return switch (faction) {
            case NERO_CORPORATION, SALVAGERS, SPACE_GUILD -> true;
            default -> false;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || bounds() == null || tickCount % 10 != 0) {
            return;
        }
        var server = level().getServer();
        var host = FactionDefinitions.factionsForServer(server).get(faction().factionId());
        if (host == null) {
            setTarget(null);
            setAggressive(false);
            return;
        }
        var envoys = level().getEntitiesOfClass(FactionEnvoy.class, getBoundingBox().inflate(16),
                envoy -> envoy.faction() == faction() && insideOutpost(envoy.blockPosition()));
        LivingEntity nearest = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        boolean opposesCult = Targeting.opposesCult(host);
        for (var player : level().players()) {
            if (!(player instanceof ServerPlayer visitor) || !visitor.isAlive() || visitor.isCreative()
                    || visitor.isSpectator() || !insideOutpost(visitor.blockPosition())) {
                continue;
            }
            double distance = distanceToSqr(visitor);
            boolean cultVested = opposesCult && GearSetBonuses.active(visitor, FactionArmor.VOID_VESTMENTS);
            if (!Targeting.detects(distance, cultVested) || distance >= bestDistance) {
                continue;
            }
            boolean defending = attacked(this, visitor) || envoys.stream().anyMatch(envoy -> attacked(envoy, visitor));
            if (Targeting.shouldTarget(FactionAccess.evaluate(server, visitor.getUUID(), host), defending)) {
                nearest = visitor;
                bestDistance = distance;
            }
        }
        if (getTarget() != nearest) {
            setTarget(nearest);
            setAggressive(nearest != null);
            if (nearest == null) {
                getNavigation().stop();
            }
        }
    }

    /** Pure targeting rules; a nested class so tests can load it without the entity class hierarchy. */
    static final class Targeting {
        private static final int DETECTION_RANGE = 12;
        private static final int CULT_DETECTION_RANGE = 20;

        private Targeting() {
        }

        /** Factions at odds with the Void Cult spot its vestments from further away. */
        static boolean opposesCult(za.co.neroland.nerofactions.content.FactionDefinition host) {
            return host.enemies().contains(OutpostFaction.VOID_CULT.factionId());
        }

        static boolean detects(double distanceSquared, boolean cultVested) {
            int range = cultVested ? CULT_DETECTION_RANGE : DETECTION_RANGE;
            return distanceSquared <= (double) range * range;
        }

        /**
         * Neutral to everyone the host still serves; hostile to the HOSTILE standing band and to enemy-faction
         * members at TRUSTED+ (both refuse service), and to anyone who recently hurt this guard or its Envoy.
         */
        static boolean shouldTarget(FactionAccess access, boolean defending) {
            return access.refusesService() || defending;
        }
    }

    private static boolean attacked(OutpostResident resident, ServerPlayer player) {
        return resident.getLastHurtByMob() == player
                && resident.tickCount - resident.getLastHurtByMobTimestamp() <= DEFENSE_TICKS;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float strength) {
        if (!insideOutpost(target.blockPosition())) {
            return;
        }
        //? if >=1.21 {
        var arrow = new Arrow(level(), this, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        //?} else {
        /*var arrow = new Arrow(level(), this, new ItemStack(Items.ARROW));
        *///?}
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY(0.33) - arrow.getY();
        arrow.shoot(dx, dy + Math.hypot(dx, dz) * 0.12, dz, 1.55F, 6);
        playSound(SoundEvents.SKELETON_SHOOT, 0.8F, 1);
        level().addFreshEntity(arrow);
    }

    @Override
    public void die(DamageSource source) {
        if (!killAccounted) {
            killAccounted = true;
            var server = level().getServer();
            if (server != null && bounds() != null && source.getEntity() instanceof ServerPlayer player) {
                var host = FactionDefinitions.factionsForServer(server).get(faction().factionId());
                if (host != null) {
                    ReputationSources.awardGuardKill(server, player.getUUID(), host);
                }
            }
        }
        super.die(source);
    }
}
