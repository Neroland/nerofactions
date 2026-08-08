package za.co.neroland.nerofactions.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.platform.PlatformInfo;

/**
 * The soft-integration guard predicates, against a stubbed {@link PlatformInfo}. What these tests
 * establish is that the <b>only</b> decision point for touching a sibling-importing class answers
 * correctly for present/absent; that the NeroEconomy-importing bridge is never <em>initialised</em>
 * on "absent" is structural, not asserted here — {@code Integrations.init()} contains the single
 * reference to {@code EconomyIntegration}, inside the guarded branch, and the JVM only loads a
 * class when bytecode mentioning it executes. (A test asserting the negative would have to observe
 * a classload that, by construction, has no code path reaching it.)
 */
class IntegrationsGuardTest {

    @Test
    void bothAbsentMeansNeitherBridgeIsWanted() {
        PlatformInfo platform = stub(Set.of("minecraft", "nerolandcore"));
        assertFalse(Integrations.questsDetected(platform));
        assertFalse(Integrations.economyDetected(platform));
    }

    @Test
    void eachGuardTracksExactlyItsOwnMod() {
        assertTrue(Integrations.questsDetected(stub(Set.of("neroquests"))));
        assertFalse(Integrations.economyDetected(stub(Set.of("neroquests"))));
        assertTrue(Integrations.economyDetected(stub(Set.of("neroeconomy"))));
        assertFalse(Integrations.questsDetected(stub(Set.of("neroeconomy"))));
    }

    @Test
    void aMissingPlatformFailsClosed() {
        assertFalse(Integrations.questsDetected(null));
        assertFalse(Integrations.economyDetected(null));
    }

    private static PlatformInfo stub(Set<String> loadedMods) {
        return new PlatformInfo() {
            @Override
            public String getPlatformName() {
                return "Test";
            }

            @Override
            public boolean isDevelopmentEnvironment() {
                return true;
            }

            @Override
            public boolean isClient() {
                return false;
            }

            @Override
            public String getModVersion() {
                return "test";
            }

            @Override
            public boolean isModLoaded(String modId) {
                return loadedMods.contains(modId);
            }

            @Override
            public List<String> getLoadedModIds() {
                return List.copyOf(loadedMods);
            }

            @Override
            public Path getConfigDir() {
                return Path.of(".");
            }
        };
    }
}
