package de.crafterteam.missilewars.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Locale;

public enum Team {

    GREEN("Grün", "green", NamedTextColor.GREEN, Material.LIME_CONCRETE, Material.LIME_STAINED_GLASS, Color.LIME),
    RED("Rot", "red", NamedTextColor.RED, Material.RED_CONCRETE, Material.RED_STAINED_GLASS, Color.RED);

    private final String displayName;
    private final String tag;
    private final NamedTextColor color;
    private final Material floor;
    private final Material glass;
    private final Color armorColor;

    Team(String displayName, String tag, NamedTextColor color, Material floor, Material glass, Color armorColor) {
        this.displayName = displayName;
        this.tag = tag;
        this.color = color;
        this.floor = floor;
        this.glass = glass;
        this.armorColor = armorColor;
    }

    public String displayName() {
        return displayName;
    }

    /** MiniMessage-Farbtag, z. B. "green". */
    public String tag() {
        return tag;
    }

    /** Farbiger Teamname als MiniMessage. */
    public String colored() {
        return "<" + tag + ">" + displayName + "</" + tag + ">";
    }

    public NamedTextColor color() {
        return color;
    }

    public Material floor() {
        return floor;
    }

    public Material glass() {
        return glass;
    }

    /** Teamfarbe für Raketen-Flossen. */
    public Material accent() {
        return this == GREEN ? Material.LIME_TERRACOTTA : Material.RED_TERRACOTTA;
    }

    public Color armorColor() {
        return armorColor;
    }

    public Team other() {
        return this == GREEN ? RED : GREEN;
    }

    public static Team parse(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "gruen", "grün", "green", "g", "lime" -> GREEN;
            case "rot", "red", "r" -> RED;
            default -> null;
        };
    }
}
