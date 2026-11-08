package de.crafterteam.missilewars.map;

import de.crafterteam.missilewars.game.Team;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;

/**
 * Baut die Standard-Arena (Grün bei -Z, Rot bei +Z, dazwischen Void):
 *  - Plattform vor dem Portal mit Glas-Seitenwänden
 *  - Obsidian-Schutzbunker vor dem Portal (Spawn + Schutz für den unteren Portalteil)
 *  - großes Netherportal (21 x 15) im Obsidian-/Crying-Obsidian-Rahmen
 *  - Leuchtsäulen, Stützen unter der Plattform, Deko-Insel unter der Mitte
 */
public final class ArenaGenerator {

    public static final int FLOOR_Y = 50;
    public static final int PORTAL_HALF_W = 10;   // Portal x -10..10 (21 breit = Vanilla-Maximum)
    public static final int PORTAL_TOP = 65;      // Portal y 51..65
    public static final int PLATFORM_HALF_W = 15;
    public static final int PLATFORM_DEPTH = 14;
    public static final int BUNKER_HALF_W = 6;
    public static final int BUNKER_DEPTH = 5;
    public static final int BUNKER_TOP = 55;

    private ArenaGenerator() {
    }

    private static int side(Team t) {
        return t == Team.GREEN ? -1 : 1;
    }

    /** Spawns, Portale und Todeshöhe für die generierte Arena eintragen. */
    public static void applyLayout(MapConfig m, int distance) {
        m.distance(distance);
        m.deathY(FLOOR_Y - 15);
        for (Team t : Team.values()) {
            int s = side(t);
            int zp = s * distance;
            m.portal(t, new MapConfig.Box(-PORTAL_HALF_W, FLOOR_Y + 1, zp, PORTAL_HALF_W, PORTAL_TOP, zp));
            double spawnZ = zp - s * 3 + 0.5;          // im Bunker
            float yaw = t == Team.GREEN ? 0f : 180f;   // Blick zum Gegner
            m.spawn(t, new MapConfig.Spawn(0.5, FLOOR_Y + 1, spawnZ, yaw, 0f));
        }
    }

    public static void build(World w, int distance) {
        for (Team t : Team.values()) {
            int s = side(t);
            int zp = s * distance;
            Material glass = t.pane();

            // Plattform
            for (int x = -PLATFORM_HALF_W; x <= PLATFORM_HALF_W; x++) {
                for (int i = 1; i <= PLATFORM_DEPTH; i++) {
                    int z = zp - s * i;
                    boolean rim = Math.abs(x) == PLATFORM_HALF_W || i == PLATFORM_DEPTH;
                    Material floor;
                    if (rim) floor = Material.POLISHED_BLACKSTONE;
                    else if (x == 0 || (i % 4 == 0 && Math.abs(x) % 4 == 0)) floor = Material.SEA_LANTERN;
                    else floor = (Math.abs(x) + i) % 2 == 0 ? t.floor() : t.floor2();
                    set(w, x, FLOOR_Y, z, floor);
                    set(w, x, FLOOR_Y - 1, z, Material.OBSIDIAN);
                }
            }
            for (int i = 1; i <= PLATFORM_DEPTH; i++) {
                int z = zp - s * i;
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
                    pane(w, -PLATFORM_HALF_W, y, z, glass);
                    pane(w, PLATFORM_HALF_W, y, z, glass);
                }
            }

            // Leuchtsäulen an den vorderen Ecken
            for (int x : new int[]{-PLATFORM_HALF_W, PLATFORM_HALF_W}) {
                int z = zp - s * PLATFORM_DEPTH;
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 6; y++) set(w, x, y, z, y == FLOOR_Y + 6 ? Material.SEA_LANTERN : Material.CRYING_OBSIDIAN);
            }

            // Stützen unter der Plattform (Kegel nach unten)
            for (int[] c : new int[][]{{-PLATFORM_HALF_W + 2, 2}, {PLATFORM_HALF_W - 2, 2},
                    {-PLATFORM_HALF_W + 2, PLATFORM_DEPTH - 2}, {PLATFORM_HALF_W - 2, PLATFORM_DEPTH - 2}, {0, PLATFORM_DEPTH / 2}}) {
                int z = zp - s * c[1];
                for (int y = FLOOR_Y - 2; y >= FLOOR_Y - 12; y--) {
                    set(w, c[0], y, z, y < FLOOR_Y - 9 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                }
            }

            // Obsidian-Schutzbunker vor dem Portal (vorne offen)
            for (int i = 1; i <= BUNKER_DEPTH; i++) {
                int z = zp - s * i;
                for (int y = FLOOR_Y + 1; y < BUNKER_TOP; y++) {
                    set(w, -BUNKER_HALF_W, y, z, Material.OBSIDIAN);
                    set(w, BUNKER_HALF_W, y, z, Material.OBSIDIAN);
                }
                for (int x = -BUNKER_HALF_W; x <= BUNKER_HALF_W; x++) {
                    set(w, x, BUNKER_TOP, z, i == BUNKER_DEPTH && Math.abs(x) % 3 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                }
            }
            // Licht im Bunker
            set(w, -BUNKER_HALF_W + 1, BUNKER_TOP - 1, zp - s * 2, Material.SEA_LANTERN);
            set(w, BUNKER_HALF_W - 1, BUNKER_TOP - 1, zp - s * 2, Material.SEA_LANTERN);

            // Portalrahmen: Obsidian mit Crying-Obsidian-Akzenten
            for (int x = -PORTAL_HALF_W - 1; x <= PORTAL_HALF_W + 1; x++) {
                for (int y = FLOOR_Y; y <= PORTAL_TOP + 1; y++) {
                    boolean edge = Math.abs(x) == PORTAL_HALF_W + 1 || y == FLOOR_Y || y == PORTAL_TOP + 1;
                    if (!edge) continue;
                    boolean accent = (Math.abs(x) == PORTAL_HALF_W + 1 && y % 3 == 0) || (y == PORTAL_TOP + 1 && x % 4 == 0);
                    set(w, x, y, zp, accent ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                }
            }
            // Rückwand-Verstärkung seitlich & Boden
            for (int x = -PLATFORM_HALF_W; x <= PLATFORM_HALF_W; x++) {
                set(w, x, FLOOR_Y - 1, zp, Material.OBSIDIAN);
                if (Math.abs(x) > PORTAL_HALF_W + 1) {
                    set(w, x, FLOOR_Y, zp, Material.OBSIDIAN);
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) pane(w, x, y, zp, glass);
                }
            }
            // Leuchtfeuer oben auf dem Portal
            for (int x : new int[]{-PORTAL_HALF_W - 1, PORTAL_HALF_W + 1}) {
                set(w, x, PORTAL_TOP + 2, zp, Material.SEA_LANTERN);
            }

            // Portalblöcke (Achse X, ohne Physik setzen)
            for (int x = -PORTAL_HALF_W; x <= PORTAL_HALF_W; x++) {
                for (int y = FLOOR_Y + 1; y <= PORTAL_TOP; y++) {
                    Block b = w.getBlockAt(x, y, zp);
                    Orientable data = (Orientable) Material.NETHER_PORTAL.createBlockData();
                    data.setAxis(Axis.X);
                    b.setBlockData(data, false);
                }
            }
        }

        // Deko-Insel tief unter der Mitte (stört keine Raketen) + Mittellinie
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 6.3) continue;
                set(w, x, FLOOR_Y - 14, z, d < 2 ? Material.SEA_LANTERN : Material.OBSIDIAN);
                for (int y = 1; y <= (int) (6 - d); y++) set(w, x, FLOOR_Y - 14 - y, z, Material.CRYING_OBSIDIAN);
            }
        }
        for (int x = -PLATFORM_HALF_W; x <= PLATFORM_HALF_W; x++) {
            set(w, x, FLOOR_Y - 13, 0, Material.WHITE_STAINED_GLASS);
        }
    }

    /** Glasscheiben mit Physik setzen, damit sie sich verbinden. */
    private static void pane(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, true);
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
