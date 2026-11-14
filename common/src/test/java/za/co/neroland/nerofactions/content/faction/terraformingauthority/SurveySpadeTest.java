package za.co.neroland.nerofactions.content.faction.terraformingauthority;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SurveySpadeTest {
    @Test
    void formatsTemperateAndSubzeroBiomeReadings() {
        assertEquals("0.81", SurveySpade.Reading.temperature(0.8125F));
        assertEquals("-0.50", SurveySpade.Reading.temperature(-0.5F));
    }
}
