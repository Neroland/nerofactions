package za.co.neroland.nerofactions.content.faction;

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
}
