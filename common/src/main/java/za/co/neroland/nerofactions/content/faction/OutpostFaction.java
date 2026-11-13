package za.co.neroland.nerofactions.content.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

public enum OutpostFaction implements StringRepresentable {
    NONE("none"),
    FREE_COLONISTS("free_colonists"),
    MINER_UNION("miner_union"),
    NERO_CORPORATION("nero_corporation"),
    SALVAGERS("salvagers"),
    SPACE_GUILD("space_guild"),
    TERRAFORMING_AUTHORITY("terraforming_authority"),
    VOID_CULT("void_cult");

    public static final Codec<OutpostFaction> CODEC = StringRepresentable.fromEnum(OutpostFaction::values)
            .validate(faction -> faction == NONE
                    ? DataResult.error(() -> "An outpost requires a faction") : DataResult.success(faction));
    private static final Map<String, OutpostFaction> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(OutpostFaction::getSerializedName, Function.identity()));

    private final String name;

    OutpostFaction(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Identifier factionId() {
        return Identifier.fromNamespaceAndPath("nerofactions", name);
    }

    public static OutpostFaction byName(String name) {
        return BY_NAME.getOrDefault(name, NONE);
    }
}
