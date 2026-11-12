package za.co.neroland.nerofactions.menu;

public record RegistryIntent(int factionIndex, boolean leave) {
    public static RegistryIntent decode(int button, int factionCount, long now, long lastAction) {
        if (button < 0 || button / 2 >= Math.min(factionCount, za.co.neroland.nerofactions.network.StandingSnapshot.MAX_FACTIONS)
                || (lastAction != Long.MIN_VALUE && now - lastAction < 5)) {
            return null;
        }
        return new RegistryIntent(button / 2, button % 2 != 0);
    }
}
