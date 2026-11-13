package za.co.neroland.nerofactions.content.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import za.co.neroland.nerolandcore.data.compat.ValueInput;
import za.co.neroland.nerolandcore.data.compat.ValueOutput;
*///?}

public abstract class OutpostResident extends PathfinderMob {
    private static final EntityDataAccessor<String> FACTION =
            SynchedEntityData.defineId(OutpostResident.class, EntityDataSerializers.STRING);

    private BlockPos post;
    private BoundingBox bounds;

    protected OutpostResident(EntityType<? extends OutpostResident> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FACTION, OutpostFaction.NONE.getSerializedName());
    }

    public OutpostFaction faction() {
        return OutpostFaction.byName(entityData.get(FACTION));
    }

    final boolean insideOutpost(BlockPos pos) {
        return bounds != null && bounds.isInside(pos);
    }

    final BoundingBox bounds() {
        return bounds;
    }

    @Override
    //? if >=1.21.2 {
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return !friendly(source.getEntity()) && super.hurtServer(level, source, amount);
    }
    //?} else {
    /*public boolean hurt(DamageSource source, float amount) {
        return !friendly(source.getEntity()) && super.hurt(source, amount);
    }
    *///?}

    private boolean friendly(Entity attacker) {
        return attacker instanceof OutpostResident resident && resident.faction() == faction();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        var level = level().getServer().getLevel(level().dimension());
        if (level == null) {
            return;
        }
        if (bounds == null && tickCount % 20 == 0) {
            bind(level);
        }
        if (bounds != null && !insideOutpost(blockPosition())) {
            setTarget(null);
            getNavigation().stop();
            teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
            setDeltaMovement(0, 0, 0);
        }
    }

    private void bind(ServerLevel level) {
        BlockPos origin = post == null ? blockPosition() : post;
        if (!level.hasChunkAt(origin)) {
            return;
        }
        var structure = OutpostStructure.at(level, origin);
        if (structure.getStructure() instanceof OutpostStructure outpost && outpost.faction() == faction()) {
            structure.getPieces().stream().map(piece -> piece.getBoundingBox())
                    .filter(box -> box.isInside(origin)).findFirst().ifPresent(box -> {
                        post = origin.immutable();
                        bounds = box;
                    });
        }
        if (bounds == null && tickCount >= 100) {
            discard();
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    //?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ValueOutput output = ValueOutput.of(nbt, registryAccess());
    *///?}
        output.putString("OutpostFaction", faction().getSerializedName());
        if (post != null) {
            output.store("OutpostPost", BlockPos.CODEC, post);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    //?} else {
    /*@Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ValueInput input = ValueInput.of(nbt, registryAccess());
    *///?}
        entityData.set(FACTION, OutpostFaction.byName(input.getStringOr("OutpostFaction", "none")).getSerializedName());
        post = input.read("OutpostPost", BlockPos.CODEC).orElse(null);
        bounds = null;
    }
}
