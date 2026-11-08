package de.crafterteam.missilewars.game;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;

/** Kits verändern, welche Items man öfter bekommt, plus kleine Boni. */
public enum Kit {

    STANDARD("Standard", Material.WHITE_BANNER, "<mut>Ausgewogen – keine Vor- und Nachteile.", Map.of()),
    ANGREIFER("Angreifer", Material.TNT, "<mut>Mehr Raketen, weniger Schilde.", Map.of(
            "missiles", 1.5, "shield", 0.5, "platform", 0.6)),
    VERTEIDIGER("Verteidiger", Material.SHIELD, "<mut>Mehr Schilde & Abfangjäger,\n<mut>startet mit einem Schild.", Map.of(
            "shield", 2.2, "interceptor", 2.0, "missiles", 0.75)),
    TECHNIKER("Techniker", Material.BLAZE_ROD, "<mut>Mehr Feuerbälle, Windkugeln,\n<mut>Plattformen & Enderperlen.", Map.of(
            "fireball", 2.0, "windcharge", 2.0, "platform", 2.0, "enderpearl", 2.5, "missiles", 0.85)),
    SPRINTER("Sprinter", Material.FEATHER, "<mut>Dauerhaft Tempo I,\n<mut>etwas weniger Raketen.", Map.of(
            "missiles", 0.85));

    private static final Set<String> MISSILE_IDS = Set.of("tomahawk", "juggernaut", "shieldbuster", "lightning",
            "guardian", "gemini", "hydra", "drill", "arrow", "carrier", "cluster");

    private final String displayName;
    private final Material icon;
    private final String description;
    private final Map<String, Double> multipliers;

    Kit(String displayName, Material icon, String description, Map<String, Double> multipliers) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.multipliers = multipliers;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String description() { return description; }

    /** Gewichts-Faktor für ein Item. "missiles" gilt für alle Angriffsraketen. */
    public double multiplier(String itemId) {
        Double specific = multipliers.get(itemId);
        if (specific != null) return specific;
        if (MISSILE_IDS.contains(itemId)) return multipliers.getOrDefault("missiles", 1.0);
        return 1.0;
    }

    /** Beim Start / Respawn. */
    public void apply(Player p) {
        if (this == SPRINTER) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        }
    }

    /** Einmalig beim Start. */
    public String startItem() {
        return this == VERTEIDIGER ? GameItems.SHIELD : null;
    }
}
