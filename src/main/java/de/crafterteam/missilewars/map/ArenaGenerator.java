package de.crafterteam.missilewars.map;

import de.crafterteam.missilewars.game.Team;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

import java.util.Locale;
import java.util.Random;

/**
 * Generierte Arenen mit Themes. Alle teilen dieselbe Geometrie (Portal 21x15 bei z = ±Abstand,
 * Spawn auf dem Deck/Dach), nur Hülle, Deko und Umgebung unterscheiden sich:
 *  KLASSISCH  – Glas-Boxen in Teamfarbe
 *  PIRATEN    – Galeonen mit Masten, Segeln und Meer
 *  WELTRAUM   – eigene Sci-Fi-Raumschiffe mit Triebwerken, Asteroiden und Mond
 *  EIS        – Eisfestungen mit Türmen und Eisschollen
 *  NETHER     – Netherfestungen mit Basaltsäulen
 * In jeder Basis liegen vor dem Portal zwei zusätzliche Glasschichten.
 */
public final class ArenaGenerator {

    public enum Theme {
        CLASSIC("Klassisch", 6000, Material.GLASS),
        PIRATES("Piratenschiffe", 6000, Material.OAK_BOAT),
        SPACE("Raumschiffe", 18000, Material.END_ROD),
        ICE("Eisfestung", 6000, Material.PACKED_ICE),
        NETHER("Netherfestung", 18000, Material.NETHER_BRICKS);

        private final String displayName;
        private final long time;
        private final Material icon;

        Theme(String displayName, long time, Material icon) {
            this.displayName = displayName;
            this.time = time;
            this.icon = icon;
        }

        public String displayName() { return displayName; }
        public long time() { return time; }
        public Material icon() { return icon; }

        public static Theme parse(String s) {
            if (s == null) return CLASSIC;
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "pirates", "piraten", "schiffe", "ships" -> PIRATES;
                case "space", "weltraum", "raumschiffe", "sterne" -> SPACE;
                case "ice", "eis", "eisfestung" -> ICE;
                case "nether", "netherfestung", "hölle" -> NETHER;
                default -> CLASSIC;
            };
        }
    }

    public static final int BOTTOM = 49;
    public static final int TOP = 67;
    public static final int HALF_W = 16;
    public static final int FRONT = 4;
    public static final int BACK = 2;
    public static final int PORTAL_HALF_W = 10;
    public static final int PORTAL_BOTTOM = 51;
    public static final int PORTAL_TOP = 65;
    public static final int SIGN_X = -34;

    private ArenaGenerator() {
    }

    private static int side(Team t) {
        return t == Team.GREEN ? -1 : 1;
    }

    public static void applyLayout(MapConfig m, int distance) {
        m.distance(distance);
        m.deathY(BOTTOM - 15);
        for (Team t : Team.values()) {
            int s = side(t);
            int zp = s * distance;
            m.portal(t, new MapConfig.Box(-PORTAL_HALF_W, PORTAL_BOTTOM, zp, PORTAL_HALF_W, PORTAL_TOP, zp));
            double spawnZ = zp - s * 2 + 0.5;
            float yaw = t == Team.GREEN ? 0f : 180f;
            m.spawn(t, new MapConfig.Spawn(0.5, TOP + 1, spawnZ, yaw, 0f));
        }
    }

    public static void build(World w, int distance) {
        build(w, distance, Theme.CLASSIC);
    }

    public static void build(World w, int distance, Theme theme) {
        for (Team t : Team.values()) buildBase(w, t, side(t) * distance, theme);
        buildSign(w);
        environment(w, distance, theme);
        for (int x = -HALF_W; x <= HALF_W; x++) set(w, x, BOTTOM - 9, 0, Material.RED_CONCRETE);
    }

    // ── Form der Hülle ───────────────────────────────────────────────

    private static boolean inside(Theme th, int x, int y, int z, int zMin, int zMax) {
        if (y < BOTTOM || y > TOP || z < zMin || z > zMax) return false;
        switch (th) {
            case PIRATES -> {
                int inset = Math.max(0, BOTTOM + 3 - y);           // Kiel unten schmaler
                int taper = Math.max(0, BOTTOM + 6 - y);           // Bug & Heck unten rund
                return Math.abs(x) <= HALF_W - taper && z >= zMin + inset && z <= zMax - inset;
            }
            case SPACE -> {
                int inset = Math.max(0, BOTTOM + 2 - y);
                if (z < zMin + inset || z > zMax - inset || Math.abs(x) > HALF_W) return false;
                int noseStart = HALF_W - 4;                        // Bug läuft unten spitz zu
                return x <= noseStart || y >= BOTTOM + (x - noseStart) * 3;
            }
            default -> {
                return Math.abs(x) <= HALF_W;
            }
        }
    }

    private static void buildBase(World w, Team t, int zp, Theme th) {
        int s = zp < 0 ? -1 : 1;
        int zFront = zp - s * FRONT;
        int zBack = zp + s * BACK;
        int zMin = Math.min(zFront, zBack), zMax = Math.max(zFront, zBack);

        for (int x = -HALF_W; x <= HALF_W; x++) {
            for (int y = BOTTOM; y <= TOP; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (!inside(th, x, y, z, zMin, zMax)) continue;
                    boolean ox = !inside(th, x - 1, y, z, zMin, zMax) || !inside(th, x + 1, y, z, zMin, zMax);
                    boolean oy = !inside(th, x, y - 1, z, zMin, zMax) || !inside(th, x, y + 1, z, zMin, zMax);
                    boolean oz = !inside(th, x, y, z - 1, zMin, zMax) || !inside(th, x, y, z + 1, zMin, zMax);
                    int faces = (ox ? 1 : 0) + (oy ? 1 : 0) + (oz ? 1 : 0);
                    if (faces == 0) {
                        // Innenraum: Portalwand, Schutzglas oder Luft
                        if (z == zp) {
                            boolean inPortal = Math.abs(x) <= PORTAL_HALF_W && y >= PORTAL_BOTTOM && y <= PORTAL_TOP;
                            if (inPortal) portal(w, x, y, z);
                            else {
                                boolean frame = Math.abs(x) == PORTAL_HALF_W + 1 || y == PORTAL_BOTTOM - 1 || y == PORTAL_TOP + 1;
                                set(w, x, y, z, frame && (x + y) % 4 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                            }
                        } else if ((z == zp - s * 2 || z == zp - s * 3)
                                && Math.abs(x) <= PORTAL_HALF_W + 2 && y >= PORTAL_BOTTOM - 1 && y <= PORTAL_TOP + 1) {
                            set(w, x, y, z, t.glass());   // zwei Schutzschichten vor dem Portal
                        }
                        continue;
                    }
                    set(w, x, y, z, shell(th, t, x, y, z, faces, ox, oy, oz));
                }
            }
        }
        // Lichter in den unteren Ecken
        Material light = switch (th) {
            case PIRATES -> Material.OCHRE_FROGLIGHT;
            case NETHER -> Material.SHROOMLIGHT;
            default -> Material.SEA_LANTERN;
        };
        for (int x : new int[]{-HALF_W + 3, HALF_W - 3}) {
            Block b = w.getBlockAt(x, BOTTOM + 3, zFront + s);
            if (b.getType().isAir()) b.setType(light, false);
        }
        switch (th) {
            case PIRATES -> pirateExtras(w, t, zMin, zMax);
            case SPACE -> spaceExtras(w, t, zMin, zMax);
            case ICE -> iceExtras(w, zMin, zMax);
            case NETHER -> netherExtras(w, zMin, zMax);
            default -> {
            }
        }
    }

    private static Material shell(Theme th, Team t, int x, int y, int z, int faces, boolean ox, boolean oy, boolean oz) {
        return switch (th) {
            case CLASSIC -> faces >= 2 ? Material.OBSIDIAN : ox ? Material.WHITE_STAINED_GLASS : t.glass();
            case PIRATES -> {
                if (y == TOP) yield faces >= 2 ? Material.STRIPPED_DARK_OAK_LOG : Material.OAK_PLANKS;
                if (y == TOP - 2) yield t == Team.GREEN ? Material.LIME_WOOL : Material.RED_WOOL;
                if (y == TOP - 1 || y == BOTTOM + 3) yield Material.STRIPPED_SPRUCE_LOG;
                if (oz && y == 60 && Math.floorMod(x, 4) == 0) yield Material.BLACK_STAINED_GLASS;   // Bullaugen
                yield y % 2 == 0 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS;
            }
            case SPACE -> {
                if (y == TOP) yield Math.floorMod(x + z, 2) == 0 ? Material.SMOOTH_STONE : Material.POLISHED_ANDESITE;
                if (faces >= 2) yield Material.GRAY_CONCRETE;
                if (y == TOP - 3) yield t.floor();
                if (oz && y == TOP - 6 && Math.floorMod(x, 2) == 0) yield Material.BLACK_STAINED_GLASS;  // Fensterband
                yield y > (BOTTOM + TOP) / 2 ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE;
            }
            case ICE -> {
                if (faces >= 2) yield Material.BLUE_ICE;
                if (y == TOP) yield Material.SNOW_BLOCK;
                if (y == TOP - 2) yield t.floor();
                yield Math.floorMod(y, 4) == 0 ? Material.BLUE_ICE : Material.PACKED_ICE;
            }
            case NETHER -> {
                if (faces >= 2) yield Material.POLISHED_BLACKSTONE_BRICKS;
                if (y == TOP) yield Material.POLISHED_BLACKSTONE;
                if (y == TOP - 2) yield t == Team.GREEN ? Material.WARPED_WART_BLOCK : Material.NETHER_WART_BLOCK;
                yield Math.floorMod(y, 4) == 0 ? Material.RED_NETHER_BRICKS : Material.NETHER_BRICKS;
            }
        };
    }

    // ── Theme-Details ────────────────────────────────────────────────

    private static void pirateExtras(World w, Team t, int zMin, int zMax) {
        int mid = (zMin + zMax) / 2;
        Material flag = t == Team.GREEN ? Material.LIME_WOOL : Material.RED_WOOL;
        // Reling
        for (int x = -HALF_W; x <= HALF_W; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean edge = Math.abs(x) == HALF_W || z == zMin || z == zMax;
                if (edge && Math.floorMod(x + z, 2) == 0) set(w, x, TOP + 1, z, Material.SPRUCE_SLAB);
            }
        }
        // Masten mit Rahen, Segeln und Flagge
        for (int mx : new int[]{-7, 6}) {
            for (int y = TOP + 1; y <= TOP + 20; y++) set(w, mx, y, mid, Material.OAK_LOG);
            for (int yard : new int[]{TOP + 6, TOP + 14}) {
                for (int z = mid - 4; z <= mid + 4; z++) log(w, mx, yard, z, Material.SPRUCE_LOG, Axis.Z);
            }
            for (int y = TOP + 7; y <= TOP + 13; y++) {
                for (int z = mid - 3; z <= mid + 3; z++) set(w, mx + 1, y, z, Material.WHITE_WOOL);
            }
            for (int y = TOP + 18; y <= TOP + 19; y++) {
                for (int x = mx + 1; x <= mx + 3; x++) set(w, x, y, mid, flag);
            }
        }
        // Kapitänskajüte am Heck
        for (int x = -HALF_W; x <= -11; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean wall = x == -HALF_W || x == -11 || z == zMin || z == zMax;
                for (int y = TOP + 1; y <= TOP + 4; y++) if (wall) set(w, x, y, z, Material.DARK_OAK_PLANKS);
                set(w, x, TOP + 5, z, Material.SPRUCE_PLANKS);
            }
        }
        set(w, -11, TOP + 1, mid, Material.AIR);
        set(w, -11, TOP + 2, mid, Material.AIR);
        set(w, -11, TOP + 3, mid - 2, Material.GLASS_PANE);
        set(w, -11, TOP + 3, mid + 2, Material.GLASS_PANE);
        // Bugspriet
        for (int i = 0; i < 7; i++) log(w, HALF_W + 1 + i, TOP - 1 + i / 2, mid, Material.STRIPPED_SPRUCE_LOG, Axis.X);
    }

    private static void spaceExtras(World w, Team t, int zMin, int zMax) {
        int mid = (zMin + zMax) / 2;
        // zwei Triebwerke am Heck
        for (int ey : new int[]{54, 62}) {
            for (int x = -HALF_W - 6; x <= -HALF_W - 1; x++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Material m;
                        if (x == -HALF_W - 6) m = dy == 0 && dz == 0 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_STAINED_GLASS;
                        else m = Math.abs(dy) + Math.abs(dz) == 2 ? Material.GRAY_CONCRETE : Material.IRON_BLOCK;
                        set(w, x, ey + dy, mid + dz, m);
                    }
                }
            }
        }
        // Cockpit-Kuppel am Bug
        for (int x = HALF_W - 5; x <= HALF_W - 1; x++) {
            for (int z = zMin + 1; z <= zMax - 1; z++) {
                set(w, x, TOP + 1, z, Material.LIGHT_BLUE_STAINED_GLASS);
                if (x < HALF_W - 1 && z > zMin + 1 && z < zMax - 1) set(w, x, TOP + 2, z, Material.LIGHT_BLUE_STAINED_GLASS);
            }
        }
        // Heckflosse
        for (int x = -HALF_W; x <= -12; x++) {
            int h = -11 - x;
            for (int y = TOP + 1; y <= TOP + h; y++) set(w, x, y, mid, y == TOP + h ? t.floor() : Material.WHITE_CONCRETE);
        }
        // Antennen
        set(w, 3, TOP + 1, mid, Material.LIGHTNING_ROD);
        set(w, 3, TOP + 2, mid, Material.END_ROD);
        set(w, -5, TOP + 1, zMin + 1, Material.END_ROD);
    }

    private static void iceExtras(World w, int zMin, int zMax) {
        for (int x = -HALF_W; x <= HALF_W; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean edge = Math.abs(x) == HALF_W || z == zMin || z == zMax;
                if (edge && Math.floorMod(x + z, 2) == 0) set(w, x, TOP + 1, z, Material.SNOW_BLOCK);
            }
        }
        for (int cx : new int[]{-HALF_W, HALF_W}) {
            for (int cz : new int[]{zMin, zMax}) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx * dx + dz * dz > 5) continue;
                        for (int y = BOTTOM - 3; y <= TOP + 6; y++) {
                            set(w, cx + dx, y, cz + dz, y >= TOP + 5 ? Material.PACKED_ICE : Material.SNOW_BLOCK);
                        }
                        if (Math.floorMod(dx + dz, 2) == 0) set(w, cx + dx, TOP + 7, cz + dz, Material.BLUE_ICE);
                    }
                }
            }
        }
    }

    private static void netherExtras(World w, int zMin, int zMax) {
        for (int cx : new int[]{-HALF_W, HALF_W}) {
            for (int cz : new int[]{zMin, zMax}) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        int px = cx + (cx < 0 ? -dx : dx), pz = cz + (cz == zMin ? -dz : dz);
                        for (int y = BOTTOM - 6; y <= TOP + 5; y++) set(w, px, y, pz, Material.POLISHED_BASALT);
                        set(w, px, TOP + 6, pz, Material.SHROOMLIGHT);
                    }
                }
            }
        }
        for (int x = -HALF_W + 2; x <= HALF_W - 2; x += 4) set(w, x, TOP + 1, (zMin + zMax) / 2, Material.NETHER_WART_BLOCK);
    }

    // ── Umgebung ─────────────────────────────────────────────────────

    private static void environment(World w, int distance, Theme th) {
        Random r = new Random(distance * 31L + th.ordinal());
        switch (th) {
            case PIRATES -> {
                // Meer unterhalb der Todeshöhe
                int y = BOTTOM - 18;
                for (int x = -70; x <= 70; x++) {
                    for (int z = -distance - 40; z <= distance + 40; z++) set(w, x, y, z, Material.WATER);
                }
            }
            case SPACE -> {
                sphere(w, 75, 35, 0, 18, Material.END_STONE, r, Material.END_STONE_BRICKS);
                for (int i = 0; i < 14; i++) {
                    int x = (r.nextBoolean() ? 1 : -1) * (40 + r.nextInt(40));
                    int y = 25 + r.nextInt(70);
                    int z = -distance + r.nextInt(distance * 2);
                    sphere(w, x, y, z, 2 + r.nextInt(3), Material.COBBLED_DEEPSLATE, r, Material.TUFF);
                }
            }
            case ICE -> {
                for (int i = 0; i < 18; i++) {
                    int cx = -60 + r.nextInt(120), cz = -distance - 20 + r.nextInt(distance * 2 + 40);
                    int rad = 3 + r.nextInt(5);
                    for (int dx = -rad; dx <= rad; dx++) {
                        for (int dz = -rad; dz <= rad; dz++) {
                            if (dx * dx + dz * dz > rad * rad) continue;
                            set(w, cx + dx, BOTTOM - 22, cz + dz, Material.PACKED_ICE);
                            set(w, cx + dx, BOTTOM - 21, cz + dz, Material.SNOW_BLOCK);
                        }
                    }
                }
            }
            case NETHER -> {
                for (int i = 0; i < 12; i++) {
                    int x = (r.nextBoolean() ? 1 : -1) * (35 + r.nextInt(35));
                    int y = 20 + r.nextInt(30);
                    int z = -distance + r.nextInt(distance * 2);
                    sphere(w, x, y, z, 3 + r.nextInt(4), Material.NETHERRACK, r, Material.GLOWSTONE);
                }
            }
            default -> {
            }
        }
    }

    private static void sphere(World w, int cx, int cy, int cz, int rad, Material main, Random r, Material speckle) {
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rad * rad) continue;
                    set(w, cx + dx, cy + dy, cz + dz, r.nextInt(7) == 0 ? speckle : main);
                }
            }
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
        for (int z = -halfW; z <= halfW; z++) {
            for (int y = bottom; y <= top; y++) {
                boolean border = Math.abs(z) == halfW || y == bottom || y == top;
                set(w, back, y, z, border ? Material.STRIPPED_DARK_OAK_LOG : Material.WHITE_CONCRETE);
            }
            set(w, back, top + 1, z, Material.MOSS_BLOCK);
        }
        word(w, new String[][]{M, I, S, S, I, L, E}, 20, 73, Material.RED_CONCRETE);
        word(w, new String[][]{W, A, R, S}, 11, 64, Material.LIME_CONCRETE);
        for (int z : new int[]{-18, 18}) {
            for (int y = top + 2; y <= top + 18; y++) set(w, back, y, z, Material.IRON_CHAIN);
        }
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

    // ── Helfer ───────────────────────────────────────────────────────

    private static void portal(World w, int x, int y, int z) {
        Orientable data = (Orientable) Material.NETHER_PORTAL.createBlockData();
        data.setAxis(Axis.X);
        w.getBlockAt(x, y, z).setBlockData(data, false);
    }

    private static void log(World w, int x, int y, int z, Material m, Axis axis) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o) o.setAxis(axis);
        w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
