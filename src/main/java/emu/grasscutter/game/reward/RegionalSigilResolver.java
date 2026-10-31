package emu.grasscutter.game.reward;

/** Converts the shared native world-area identity into its regional sigil item. */
public final class RegionalSigilResolver {
    private RegionalSigilResolver() {}

    public static int resolve(RewardContext context) {
        return fromCityId(RewardAreaResolver.resolve(context).cityId());
    }

    static int fromCityId(int cityId) {
        // Special cities (99+) are not reliable nation identifiers.
        return switch (cityId) {
            case 1 -> 305;     // Mondstadt
            case 2 -> 307;     // Liyue
            case 3 -> 304;     // Inazuma
            case 4 -> 303;     // Sumeru
            case 5 -> 302;     // Fontaine
            case 6 -> 301;     // Natlan
            case 7 -> 308;     // Nod-Krai
            case 8, 9 -> 306;  // Snezhnaya
            default -> throw new IllegalArgumentException(
                    "No verified regional sigil mapping for city " + cityId);
        };
    }
}
