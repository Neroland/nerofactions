package za.co.neroland.nerofactions.content.faction.corporation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class CorporateMarketNudgeTest {
    @Test
    void onlyTheSelectedCommodityChangesAndTheIncreaseIsCapped() {
        var iron = Identifier.parse("minecraft:iron_ingot");
        var gold = Identifier.parse("minecraft:gold_ingot");
        assertEquals(1.05, CorporateMarketNudge.multiplier(iron, 0, 5));
        assertEquals(1.15, CorporateMarketNudge.multiplier(iron, 604_799_999, 100));
        assertEquals(1, CorporateMarketNudge.multiplier(gold, 604_799_999, 15));
        assertEquals(1.05, CorporateMarketNudge.multiplier(gold, 604_800_000, 5));
        assertEquals(1, CorporateMarketNudge.multiplier(iron, 604_800_000, 5));
        assertEquals(1, CorporateMarketNudge.multiplier(gold, 604_800_000, 0));
    }
}
