package za.co.neroland.nerofactions.integration.space;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import za.co.neroland.nerospace.api.NerospaceEnvironment;
import za.co.neroland.nerospace.api.NerospacePlanets;

public final class SpaceIntegration {
    private SpaceIntegration() {
    }

    public static Optional<Component> atmosphere(ServerLevel level, BlockPos position) {
        if (NerospacePlanets.byDimension(level.dimension()).isEmpty()) return Optional.empty();
        var sample = NerospaceEnvironment.at(level, position);
        if (!sample.loaded()) return Optional.empty();
        String atmosphere = sample.atmosphere().name().toLowerCase(Locale.ROOT);
        return Optional.of(Component.translatable("message.nerofactions.survey.atmosphere",
                Component.translatable("message.nerofactions.survey.atmosphere." + atmosphere), sample.oxygen()));
    }
}
