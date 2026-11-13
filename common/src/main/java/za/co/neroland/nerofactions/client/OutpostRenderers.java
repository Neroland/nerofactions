package za.co.neroland.nerofactions.client;

//? if >=1.21.11 {
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
//?} else {
/*import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.world.entity.monster.AbstractIllager;
*///?}
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
//?}
import net.minecraft.resources.Identifier;
import za.co.neroland.nerofactions.content.faction.FactionEnvoy;
import za.co.neroland.nerofactions.content.faction.FactionGuard;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;

public final class OutpostRenderers {
    private OutpostRenderers() {
    }

    private static Identifier texture(OutpostFaction faction, String role) {
        if (faction == OutpostFaction.NONE) {
            return Identifier.withDefaultNamespace(role.equals("envoy")
                    ? "textures/entity/villager/villager.png" : "textures/entity/illager/pillager.png");
        }
        return Identifier.fromNamespaceAndPath("nerofactions",
                "textures/entity/outpost/" + faction.getSerializedName() + "_" + role + ".png");
    }

    //? if >=1.21.2 {
    public static final class Envoy extends MobRenderer<FactionEnvoy, Envoy.State, VillagerModel> {
        public Envoy(EntityRendererProvider.Context context) {
            super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.45F);
        }

        public static final class State extends VillagerRenderState {
            private OutpostFaction faction = OutpostFaction.NONE;
        }

        @Override public State createRenderState() { return new State(); }

        @Override
        public void extractRenderState(FactionEnvoy envoy, State state, float partialTick) {
            super.extractRenderState(envoy, state, partialTick);
            state.faction = envoy.faction();
        }

        @Override public Identifier getTextureLocation(State state) { return texture(state.faction, "envoy"); }
    }

    public static final class Guard extends MobRenderer<FactionGuard, Guard.State, IllagerModel<Guard.State>> {
        public Guard(EntityRendererProvider.Context context) {
            super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.PILLAGER)), 0.45F);
        }

        public static final class State extends IllagerRenderState {
            private OutpostFaction faction = OutpostFaction.NONE;
        }

        @Override public State createRenderState() { return new State(); }

        @Override
        public void extractRenderState(FactionGuard guard, State state, float partialTick) {
            super.extractRenderState(guard, state, partialTick);
            state.faction = guard.faction();
            state.mainArm = guard.getMainArm();
            state.armPose = guard.isAggressive()
                    ? FactionGuard.ranged(guard.faction()) ? AbstractIllager.IllagerArmPose.BOW_AND_ARROW
                            : AbstractIllager.IllagerArmPose.ATTACKING
                    : AbstractIllager.IllagerArmPose.NEUTRAL;
        }

        @Override public Identifier getTextureLocation(State state) { return texture(state.faction, "guard"); }
    }
    //?} else {
    /*public static final class Envoy extends MobRenderer<FactionEnvoy, VillagerModel<FactionEnvoy>> {
        public Envoy(EntityRendererProvider.Context context) {
            super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.45F);
        }

        @Override public Identifier getTextureLocation(FactionEnvoy envoy) { return texture(envoy.faction(), "envoy"); }
    }

    public static final class Guard extends MobRenderer<FactionGuard, net.minecraft.client.model.HumanoidModel<FactionGuard>> {
        public Guard(EntityRendererProvider.Context context) {
            super(context, new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.45F);
        }

        @Override public Identifier getTextureLocation(FactionGuard guard) {
            if (guard.faction() == OutpostFaction.NONE) {
                return Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");
            }
            return Identifier.fromNamespaceAndPath("nerofactions",
                    "textures/entity/outpost/" + guard.faction().getSerializedName() + "_guard_legacy.png");
        }
    }
    *///?}
}
