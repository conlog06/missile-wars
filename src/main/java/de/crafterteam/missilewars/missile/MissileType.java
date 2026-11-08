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
 * {@link #TEAM_ACCENT} wird beim Spawnen durch die Teamfarbe ersetzt.
 */
public enum MissileType {

    TOMAHAWK("tomahawk", "Tomahawk", Material.CREEPER_SPAWN_EGG, false, -1,
            "Der Allrounder. Schnell, solide Ladung."),
    JUGGERNAUT("juggernaut", "Juggernaut", Material.GHAST_SPAWN_EGG, false, -1,
            "Langsam, aber riesige Sprengladung."),
    SHIELDBUSTER("shieldbuster", "Shieldbuster", Material.WITCH_SPAWN_EGG, true, -1,
            "Bricht durch Glas-Schilde."),
    LIGHTNING("lightning", "Lightning", Material.OCELOT_SPAWN_EGG, false, -1,
            "Extrem schnell, kleine Ladung."),
    GUARDIAN("guardian", "Guardian", Material.GUARDIAN_SPAWN_EGG, false, -1,
            "Mit Plattform obendrauf – zum Mitfahren."),
    GEMINI("gemini", "Gemini", Material.ALLAY_SPAWN_EGG, false, -1,
            "Zwillingsrakete – trifft breit."),
    INTERCEPTOR("interceptor", "Abfangjäger", Material.PHANTOM_SPAWN_EGG, false, 3,
            "Zündet sofort beim Aufprall – holt Raketen vom Himmel.");

    /** Platzhalter für die Teamfarbe (Flossen). */
    public static final Material TEAM_ACCENT = Material.WHITE_TERRACOTTA;

    public record Part(int x, int y, int z, Material material, BlockFace facing) {
        public boolean isTnt() {
            return material == Material.TNT;
        }
    }

    private final String id;
    private final String displayName;
    private final Material item;
    private final boolean piercesGlass;
    private final int fuseOverride;
    private final String description;
    private final List<Part> parts;

    MissileType(String id, String displayName, Material item, boolean piercesGlass, int fuseOverride, String description) {
        this.id = id;
        this.displayName = displayName;
        this.item = item;
        this.piercesGlass = piercesGlass;
        this.fuseOverride = fuseOverride;
        this.description = description;
        this.parts = Collections.unmodifiableList(design(id));
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material item() { return item; }
    public boolean piercesGlass() { return piercesGlass; }
    /** Eigene Zündzeit in Ticks, -1 = Config-Wert. */
    public int fuseOverride() { return fuseOverride; }
    public String description() { return description; }
    public List<Part> parts() { return parts; }

    public long tntCount() {
        return parts.stream().filter(Part::isTnt).count();
    }

    public static MissileType byId(String id) {
        if (id == null) return null;
        String s = id.toLowerCase(Locale.ROOT);
        for (MissileType t : values()) if (t.id.equals(s)) return t;
        return null;
    }

    // ── Baupläne ────────────────────────────────────────────────────

    private static List<Part> design(String id) {
        List<Part> p = new ArrayList<>();
        Material S = Material.SLIME_BLOCK, T = Material.TNT, R = Material.REDSTONE_BLOCK,
                H = Material.HONEY_BLOCK, A = Material.WHITE_TERRACOTTA; // = TEAM_ACCENT (Konstante ist hier noch nicht initialisiert)
        switch (id) {
            case "tomahawk" -> {
                // 1 breit, 2 hoch, 7 lang
                engine(p, 0, 0, 0);
                p.add(b(0, 0, 4, R));
                p.add(b(0, 0, 5, T));
                p.add(b(0, 0, 6, T));
                p.add(b(0, 1, 0, A));
                p.add(b(0, 1, 1, S));
                p.add(b(0, 1, 3, S));
                p.add(b(0, 1, 4, T));
                p.add(b(0, 1, 5, T));
                p.add(b(0, 1, 6, T));
            }
            case "juggernaut" -> {
                // 3 breit, 3 hoch, 8 lang – Antrieb in der Mitte, dicker Sprengkopf
                engine(p, 0, 1, 0);
                for (int x : new int[]{-1, 1}) {
                    p.add(b(x, 1, 0, A));
                    for (int z = 1; z <= 3; z++) p.add(b(x, 1, z, S));
                }
                for (int x = -1; x <= 1; x++) {
                    p.add(b(x, 0, 1, A));
                    p.add(b(x, 0, 3, S));
                    p.add(b(x, 2, 3, S));
                    p.add(b(x, 1, 4, x == 0 ? R : S));
                    for (int z = 5; z <= 7; z++) {
                        p.add(b(x, 1, z, T));
                        if (x == 0 || z == 7) p.add(b(x, 0, z, T));
                    }
                    p.add(b(x, 2, 5, T));
                }
                p.add(b(0, 2, 6, T));
            }
            case "shieldbuster" -> {
                // 1 breit, 3 hoch, 9 lang – Ladung über die ganze Länge (Stufenexplosion)
                engine(p, 0, 0, 0);
                p.add(b(0, 0, 4, R));
                for (int z = 5; z <= 8; z++) p.add(b(0, 0, z, T));
                for (int z = 1; z <= 8; z++) p.add(b(0, 1, z, z % 3 == 0 ? S : T));
                p.add(b(0, 2, 0, A));
                for (int z = 1; z <= 3; z++) p.add(b(0, 2, z, S));
            }
            case "lightning" -> {
                // 1x1, 6 lang
                p.add(f(0, 0, 0, Material.OBSERVER, BlockFace.NORTH));
                p.add(f(0, 0, 1, Material.PISTON, BlockFace.SOUTH));
                p.add(b(0, 0, 2, S));
                p.add(b(0, 0, 3, R));
                p.add(b(0, 0, 4, T));
                p.add(b(0, 0, 5, T));
                p.add(b(0, 1, 0, A));
            }
            case "guardian" -> {
                // 3 breit, Plattform zum Mitfahren mit Geländer-Flossen
                engine(p, 0, 0, 0);
                for (int x : new int[]{-1, 1}) {
                    for (int z = 0; z <= 3; z++) p.add(b(x, 0, z, S));
                }
                p.add(b(0, 1, 1, S));
                p.add(b(0, 1, 2, R));
                for (int z = 3; z <= 5; z++) p.add(b(0, 1, z, T));
                p.add(b(0, 0, 4, T));
                for (int x = -1; x <= 1; x++) {
                    for (int z = 0; z <= 3; z++) p.add(b(x, 2, z, x == 0 ? H : S));
                }
                p.add(b(-1, 3, 0, A));
                p.add(b(1, 3, 0, A));
            }
            case "gemini" -> {
                // zwei schlanke Raketen nebeneinander (x = -1 und +1)
                for (int x : new int[]{-1, 1}) {
                    engine(p, x, 0, 0);
                    p.add(b(x, 0, 4, T));
                    p.add(b(x, 0, 5, T));
                    p.add(b(x, 1, 0, A));
                    p.add(b(x, 1, 3, T));
                }
                p.add(b(0, 0, 1, S));   // Brücke verbindet beide Hälften
                p.add(b(0, 0, 3, S));
            }
            case "interceptor" -> {
                // klein & schnell, zündet sofort
                p.add(f(0, 0, 0, Material.OBSERVER, BlockFace.NORTH));
                p.add(f(0, 0, 1, Material.STICKY_PISTON, BlockFace.SOUTH));
                p.add(b(0, 0, 2, S));
                p.add(b(0, 0, 3, T));
                p.add(b(0, 1, 1, A));
            }
            default -> throw new IllegalStateException(id);
        }
        return p;
    }

    /** Antrieb: Observer → Schleim → Sticky Piston → Schleim (z 0..3). */
    private static void engine(List<Part> p, int x, int y, int z0) {
        p.add(f(x, y, z0, Material.OBSERVER, BlockFace.NORTH));
        p.add(b(x, y, z0 + 1, Material.SLIME_BLOCK));
        p.add(f(x, y, z0 + 2, Material.STICKY_PISTON, BlockFace.SOUTH));
        p.add(b(x, y, z0 + 3, Material.SLIME_BLOCK));
    }

    private static Part b(int x, int y, int z, Material m) {
        return new Part(x, y, z, m, null);
    }

    private static Part f(int x, int y, int z, Material m, BlockFace face) {
        return new Part(x, y, z, m, face);
    }
}
