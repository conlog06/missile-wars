package de.crafterteam.missilewars.map;

import de.crafterteam.missilewars.game.Team;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;

/**
 * Baut die Standard-Arena:
 * Grün bei -Z, Rot bei +Z, dazwischen Void. Jede Basis hat eine Plattform
 * und dahinter ein großes Netherportal (21 x 15) im Obsidianrahmen.
 * Wird ein Portalblock zerstört, verliert das Team.
 */
public final class ArenaGenerator {

    public static final int FLOOR_Y = 50;
    public static final int PORTAL_HALF_W = 10;   // Portal x -10..10 (21 breit = Vanilla-Maximum)
    public static final int PORTAL_TOP = 65;      // Portal y 51..65
    public static final int PLATFORM_HALF_W = 15;
    public static final int PLATFORM_DEPTH = 14;

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
            double spawnZ = zp - s * 6 + 0.5;
            float yaw = t == Team.GREEN ? 0f : 180f;   // Blick zum Gegner
            m.spawn(t, new MapConfig.Spawn(0.5, FLOOR_Y + 1, spawnZ, yaw, 0f));
        }
    }

    public static void build(World w, int distance) {
        for (Team t : Team.values()) {
            int s = side(t);
            int zp = s * distance;

            // Plattform vor dem Portal
            for (int x = -PLATFORM_HALF_W; x <= PLATFORM_HALF_W; x++) {
                for (int i = 1; i <= PLATFORM_DEPTH; i++) {
                    int z = zp - s * i;
                    boolean rim = Math.abs(x) == PLATFORM_HALF_W || i == PLATFORM_DEPTH;
                    set(w, x, FLOOR_Y, z, rim ? t.glass() : t.floor());
                    set(w, x, FLOOR_Y - 1, z, Material.OBSIDIAN);
                }
            }

            // Obsidianrahmen
            for (int x = -PORTAL_HALF_W - 1; x <= PORTAL_HALF_W + 1; x++) {
                for (int y = FLOOR_Y; y <= PORTAL_TOP + 1; y++) {
                    boolean edge = Math.abs(x) == PORTAL_HALF_W + 1 || y == FLOOR_Y || y == PORTAL_TOP + 1;
                    if (edge) set(w, x, y, zp, Material.OBSIDIAN);
                }
            }
            // Boden unter dem Rahmen etwas breiter, damit es nicht schwebt
            for (int x = -PLATFORM_HALF_W; x <= PLATFORM_HALF_W; x++) {
                set(w, x, FLOOR_Y - 1, zp, Material.OBSIDIAN);
                if (Math.abs(x) > PORTAL_HALF_W + 1) set(w, x, FLOOR_Y, zp, Material.OBSIDIAN);
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

        // Mittellinie als Markierung tief unten (stört keine Raketen)
        for (int x = -PLATFORM_HALF_W; x <= PLATFORM_HALF_W; x++) {
            set(w, x, FLOOR_Y - 10, 0, Material.WHITE_STAINED_GLASS);
        }
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
