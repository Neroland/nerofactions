package za.co.neroland.nerofactions.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * The faction's cosmetic identity — a banner pattern and an armour-trim material. Both are
 * <em>forward references</em>: at this stage they are validated only as well-formed identifiers
 * (the codec guarantees that), and the content they point at ships in a later stage. Cosmetic
 * reward entries ({@link RewardEntry#TYPE_COSMETIC}) reference this block.
 *
 * @param bannerPattern the banner-pattern id this faction will use
 * @param trimMaterial  the armour-trim material id this faction will use
 */
public record Cosmetics(Identifier bannerPattern, Identifier trimMaterial) {

    public static final Codec<Cosmetics> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("banner_pattern").forGetter(Cosmetics::bannerPattern),
            Identifier.CODEC.fieldOf("trim_material").forGetter(Cosmetics::trimMaterial)
    ).apply(inst, Cosmetics::new));
}
