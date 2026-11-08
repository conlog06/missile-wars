package de.crafterteam.missilewars.missile;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Raketen-Bauplan in lokalen Koordinaten:
 * x = seitlich, y = hoch, z = nach vorne (0 = Heck). Lokale Blickrichtung SOUTH = "nach vorne".
 */
public enum MissileType {

    TOMAHAWK("tomahawk", "Tomahawk", Material.CREEPER_SPAWN_EGG, false,
            "Schnell & zuverlässig. Die Standard-Rakete."),
    JUGGERNAUT("juggernaut", "Juggernaut", Material.GHAST_SPAWN_EGG, false,
            "Langsam, aber riesige Sprengladung."),
    SHIELDBUSTER("shieldbuster", "Shieldbuster", Material.WITCH_SPAWN_EGG, true,
            "Bricht durch Glas-Schilde."),
    LIGHTNING("lightning", "Lightning", Material.OCELOT_SPAWN_EGG, false,
            "Sehr schnell, kleine Ladung."),
    GUARDIAN("guardian", "Guardian", Material.GUARDIAN_SPAWN_EGG, false,
            "Mit Plattform obendrauf – zum Mitfahren.");

    public record Part(int x, int y, int z, Material material, BlockFace facing) {
        public boolean isTnt() {
            return material == Material.TNT;
        }
    }

    private final String id;
    private final String displayName;
    private final Material item;
    private final boolean piercesGlass;
    private final String description;
    private final List<Part> parts;

    MissileType(String id, String displayName, Material item, boolean piercesGlass, String description) {
        this.id = id;
        this.displayName = displayName;
        this.item = item;
        this.piercesGlass = piercesGlass;
        this.description = description;
        this.parts = Collections.unmodifiableList(design(id));
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material item() { return item; }
    public boolean piercesGlass() { return piercesGlass; }
    public String description() { return description; }
    public List<Part> parts() { return parts; }

    public static MissileType byId(String id) {
        if (id == null) return null;
        String s = id.toLowerCase(Locale.ROOT);
        for (MissileType t : values()) if (t.id.equals(s)) return t;
        return null;
    }

    // ── Baupläne ────────────────────────────────────────────────────

    private static List<Part> design(String id) {
        List<Part> p = new ArrayList<>();
        switch (id) {
            case "tomahawk" -> {
                engine(p, 0, 0);
                p.add(b(0, 0, 4, Material.SLIME_BLOCK));
                p.add(b(0, 0, 5, Material.TNT));
                p.add(b(0, 1, 1, Material.SLIME_BLOCK));
                p.add(b(0, 1, 3, Material.TNT));
                p.add(b(0, 1, 4, Material.TNT));
                p.add(b(0, 1, 5, Material.TNT));
            }
            case "juggernaut" -> {
                engine(p, 0, 0);
                for (int x : new int[]{-1, 1}) {
                    for (int z = 0; z <= 3; z++) p.add(b(x, 0, z, Material.SLIME_BLOCK));
                }
                for (int x = -1; x <= 1; x++) {
                    for (int z = 4; z <= 6; z++) p.add(b(x, 0, z, Material.TNT));
                    for (int z = 2; z <= 6; z++) p.add(b(x, 1, z, Material.TNT));
                }
            }
            case "shieldbuster" -> {
                engine(p, 0, 0);
                for (int z = 4; z <= 6; z++) p.add(b(0, 0, z, Material.TNT));
                for (int z = 1; z <= 6; z++) p.add(b(0, 1, z, Material.TNT));
                for (int z = 0; z <= 3; z++) p.add(b(0, 2, z, Material.SLIME_BLOCK));
            }
            case "lightning" -> {
                p.add(f(0, 0, 0, Material.OBSERVER, BlockFace.NORTH));
                p.add(f(0, 0, 1, Material.PISTON, BlockFace.SOUTH));
                p.add(b(0, 0, 2, Material.SLIME_BLOCK));
                p.add(b(0, 0, 3, Material.TNT));
                p.add(b(0, 0, 4, Material.TNT));
            }
            case "guardian" -> {
                engine(p, 0, 0);
                for (int x : new int[]{-1, 1}) {
                    for (int z = 0; z <= 3; z++) p.add(b(x, 0, z, Material.SLIME_BLOCK));
                }
                for (int z = 2; z <= 4; z++) p.add(b(0, 1, z, Material.TNT));
                p.add(b(0, 1, 1, Material.SLIME_BLOCK));
                for (int x = -1; x <= 1; x++) {
                    for (int z = 0; z <= 3; z++) p.add(b(x, 2, z, Material.SLIME_BLOCK));
                }
            }
            default -> throw new IllegalStateException(id);
        }
        return p;
    }

    /** Antrieb: Observer → Schleim → Sticky Piston → Schleim (z 0..3). */
    private static void engine(List<Part> p, int x, int y) {
        p.add(f(x, y, 0, Material.OBSERVER, BlockFace.NORTH));
        p.add(b(x, y, 1, Material.SLIME_BLOCK));
        p.add(f(x, y, 2, Material.STICKY_PISTON, BlockFace.SOUTH));
        p.add(b(x, y, 3, Material.SLIME_BLOCK));
    }

    private static Part b(int x, int y, int z, Material m) {
        return new Part(x, y, z, m, null);
    }

    private static Part f(int x, int y, int z, Material m, BlockFace face) {
        return new Part(x, y, z, m, face);
    }
}
