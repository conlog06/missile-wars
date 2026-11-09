package de.crafterteam.missilewars.map;

import de.crafterteam.missilewars.game.Team;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;

/**
 * Klassische Missile-Wars-Arena (Grün bei -Z, Rot bei +Z, dazwischen Void, Tageshimmel):
 *  - jede Basis ist eine große Box aus Glas in Teamfarbe mit Obsidian-Kanten
 *  - in der Box steht eine Obsidianwand mit dem Netherportal (21 x 15)
 *  - Spawn auf dem Dach der Box
 *  - seitlich hängt an Ketten ein großes "MISSILE WARS"-Schild (außerhalb der Flugbahn)
 */
public final class ArenaGenerator {

    public static final int BOTTOM = 49;          // Boden der Box
    public static final int TOP = 67;             // Dach der Box
    public static final int HALF_W = 16;          // Box x -16..16
    public static final int FRONT = 4;            // Vorderseite liegt 4 Blöcke vor der Portalebene
    public static final int BACK = 2;             // Rückseite 2 Blöcke dahinter
    public static final int PORTAL_HALF_W = 10;   // Portal x -10..10 (21 breit)
    public static final int PORTAL_BOTTOM = 51;
    public static final int PORTAL_TOP = 65;      // 15 hoch
    public static final int SIGN_X = -34;         // Schild seitlich, außerhalb der Raketenbahn

    private ArenaGenerator() {
    }

    private static int side(Team t) {
        return t == Team.GREEN ? -1 : 1;
    }

    /** Spawns, Portale und Todeshöhe für die generierte Arena eintragen. */
    public static void applyLayout(MapConfig m, int distance) {
        m.distance(distance);
        m.deathY(BOTTOM - 15);
        for (Team t : Team.values()) {
            int s = side(t);
            int zp = s * distance;
            m.portal(t, new MapConfig.Box(-PORTAL_HALF_W, PORTAL_BOTTOM, zp, PORTAL_HALF_W, PORTAL_TOP, zp));
            double spawnZ = zp - s * 2 + 0.5;          // auf dem Dach, vor der Portalebene
            float yaw = t == Team.GREEN ? 0f : 180f;   // Blick zum Gegner
            m.spawn(t, new MapConfig.Spawn(0.5, TOP + 1, spawnZ, yaw, 0f));
        }
    }

    public static void build(World w, int distance) {
        for (Team t : Team.values()) buildBase(w, t, side(t) * distance);
        buildSign(w);
        // dünne Mittellinie tief unten (stört keine Raketen)
        for (int x = -HALF_W; x <= HALF_W; x++) set(w, x, BOTTOM - 9, 0, Material.RED_CONCRETE);
    }

    private static void buildBase(World w, Team t, int zp) {
        int s = zp < 0 ? -1 : 1;
        int zFront = zp - s * FRONT;
        int zBack = zp + s * BACK;
        int zMin = Math.min(zFront, zBack), zMax = Math.max(zFront, zBack);
        Material glass = t.glass();

        for (int x = -HALF_W; x <= HALF_W; x++) {
            for (int y = BOTTOM; y <= TOP; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    boolean onX = Math.abs(x) == HALF_W;
                    boolean onY = y == BOTTOM || y == TOP;
                    boolean onZ = z == zMin || z == zMax;
                    int faces = (onX ? 1 : 0) + (onY ? 1 : 0) + (onZ ? 1 : 0);
                    if (faces == 0) continue;                       // Innenraum
                    if (faces >= 2) set(w, x, y, z, Material.OBSIDIAN);          // Kanten
                    else if (onX) set(w, x, y, z, Material.WHITE_STAINED_GLASS); // Stirnseiten
                    else set(w, x, y, z, glass);                                  // Front, Rücken, Dach, Boden
                }
            }
        }

        // Obsidianwand in der Portalebene, darin das Portal
        for (int x = -HALF_W + 1; x <= HALF_W - 1; x++) {
            for (int y = BOTTOM + 1; y <= TOP - 1; y++) {
                boolean inPortal = Math.abs(x) <= PORTAL_HALF_W && y >= PORTAL_BOTTOM && y <= PORTAL_TOP;
                if (inPortal) {
                    Block b = w.getBlockAt(x, y, zp);
                    Orientable data = (Orientable) Material.NETHER_PORTAL.createBlockData();
                    data.setAxis(Axis.X);
                    b.setBlockData(data, false);
                } else {
                    boolean frame = Math.abs(x) == PORTAL_HALF_W + 1 || y == PORTAL_BOTTOM - 1 || y == PORTAL_TOP + 1;
                    set(w, x, y, zp, frame && (x + y) % 4 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                }
            }
        }

        // Licht in den unteren Ecken
        for (int x : new int[]{-HALF_W + 1, HALF_W - 1}) {
            set(w, x, BOTTOM + 1, zFront + s, Material.SEA_LANTERN);
            set(w, x, BOTTOM + 1, zBack - s, Material.SEA_LANTERN);
        }
    }

    // ── "MISSILE WARS"-Schild ────────────────────────────────────────

    private static final String[] M = {"10001", "11011", "10101", "10101", "10001", "10001", "10001"};
    private static final String[] I = {"11111", "00100", "00100", "00100", "00100", "00100", "11111"};
    private static final String[] S = {"01111", "10000", "10000", "01110", "00001", "00001", "11110"};
    private static final String[] L = {"10000", "10000", "10000", "10000", "10000", "10000", "11111"};
    private static final String[] E = {"11111", "10000", "10000", "11110", "10000", "10000", "11111"};
    private static final String[] W = {"10001", "10001", "10001", "10101", "10101", "11011", "10001"};
    private static final String[] A = {"01110", "10001", "10001", "11111", "10001", "10001", "10001"};
    private static final String[] R = {"11110", "10001", "10001", "11110", "10100", "10010", "10001"};

    private static void buildSign(World w) {
        int halfW = 22, bottom = 56, top = 75;
        int back = SIGN_X - 1;
        // Tafel mit Rahmen
        for (int z = -halfW; z <= halfW; z++) {
            for (int y = bottom; y <= top; y++) {
                boolean border = Math.abs(z) == halfW || y == bottom || y == top;
                set(w, back, y, z, border ? Material.STRIPPED_DARK_OAK_LOG : Material.WHITE_CONCRETE);
            }
            set(w, back, top + 1, z, Material.MOSS_BLOCK);            // grünes Dach wie beim Original
        }
        // Schrift: steht auf der Vorderseite (Blick nach -X), läuft also von +Z nach -Z
        word(w, new String[][]{M, I, S, S, I, L, E}, 20, 73, Material.RED_CONCRETE);
        word(w, new String[][]{W, A, R, S}, 11, 64, Material.LIME_CONCRETE);
        // Ketten nach oben
        for (int z : new int[]{-18, 18}) {
            for (int y = top + 2; y <= top + 18; y++) set(w, back, y, z, Material.IRON_CHAIN);
        }
        // Lichter unter der Tafel
        for (int z = -halfW + 4; z <= halfW - 4; z += 8) set(w, back, bottom - 1, z, Material.SEA_LANTERN);
    }

    private static void word(World w, String[][] letters, int zStart, int yTop, Material mat) {
        int col = 0;
        for (String[] glyph : letters) {
            for (int r = 0; r < glyph.length; r++) {
                for (int c = 0; c < glyph[r].length(); c++) {
                    if (glyph[r].charAt(c) == '1') set(w, SIGN_X, yTop - r, zStart - (col + c), mat);
                }
            }
            col += 6;
        }
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
