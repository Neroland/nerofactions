package za.co.neroland.nerofactions.forge;

import java.util.EnumMap;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import za.co.neroland.nerofactions.content.faction.terraformingauthority.AtmosphereProcessorBlock.Processor;
import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.ForgeEnergyLookup;

final class ProcessorCapabilities implements ICapabilityProvider {
    private static final Identifier ID = Identifier.parse("nerofactions:processor_energy");
    private final Processor processor;
    private final EnumMap<Direction, LazyOptional<NeroEnergyStorage>> sides = new EnumMap<>(Direction.class);
    private final LazyOptional<NeroEnergyStorage> unsided;

    private ProcessorCapabilities(Processor processor) {
        this.processor = processor;
        this.unsided = LazyOptional.of(() -> new Input(null));
    }

    static void register() {
        //? if >=1.21.6 {
        AttachCapabilitiesEvent.BlockEntities.BUS.addListener(ProcessorCapabilities::attach);
        //?} else {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ProcessorCapabilities::attach);
        *///?}
    }

    //? if >=1.21.6 {
    private static void attach(AttachCapabilitiesEvent.BlockEntities event) {
    //?} else {
    /*private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
    *///?}
        if (event.getObject() instanceof Processor processor) {
            var caps = new ProcessorCapabilities(processor);
            event.addCapability(ID, caps);
            event.addListener(caps::invalidate);
        }
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (capability != ForgeEnergyLookup.ENERGY) return LazyOptional.empty();
        return (side == null ? unsided : sides.computeIfAbsent(side,
                face -> LazyOptional.of(() -> new Input(face)))).cast();
    }

    private void invalidate() {
        unsided.invalidate();
        sides.values().forEach(LazyOptional::invalidate);
    }

    private final class Input implements NeroEnergyStorage {
        private final Direction side;
        Input(Direction side) { this.side = side; }
        @Override public long getAmount() { return processor.getEnergy().getAmount(); }
        @Override public long getCapacity() { return processor.getEnergy().getCapacity(); }
        @Override public long insert(long amount, boolean simulate) {
            var view = processor.sideConfig().energyView(side);
            return view == null ? 0 : view.insert(amount, simulate);
        }
        @Override public long extract(long amount, boolean simulate) { return 0; }
    }
}
