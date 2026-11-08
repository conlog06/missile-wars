package de.crafterteam.missilewars.map;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Wartelobby: Spieler warten hier bis zum Start.
 * Standard: generierte, schwebende Lobby-Insel in der Welt "mw_waitlobby".
 * Alternativ ein eigener Punkt (/mw setwaitlobby).
 */
public final class WaitingLobby {

    public static final String WORLD = "mw_waitlobby";
    private static final int Y = 64;
    private static final int R = 16;

    private final MissileWarsPlugin plugin;
    private TextDisplay hologram;

    public WaitingLobby(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("waiting-lobby.enabled", true);
    }

    private boolean custom() {
        String s = plugin.getConfig().getString("waiting-lobby.location", "");
        return s != null && !s.isBlank();
    }

    /** Beim Start: generierte Lobby laden/bauen. */
    public void init() {
        if (!enabled() || custom()) return;
        World w = Bukkit.getWorld(WORLD);
        if (w == null) w = new WorldCreator(WORLD).generator(new VoidGenerator()).createWorld();
        if (w == null) {
            plugin.getLogger().warning("Wartelobby-Welt konnte nicht erstellt werden.");
            return;
        }
        w.setSpawnLocation(0, Y + 1, 0);
        w.setTime(13000);
        w.setStorm(false);
        if (w.getBlockAt(0, Y, 0).getType().isAir()) {
            build(w);
            plugin.getLogger().info("Wartelobby generiert.");
        }
        spawnHologram(w);
    }

    public World world() {
        return custom() ? null : Bukkit.getWorld(WORLD);
    }

    public boolean isLobbyWorld(World w) {
        return w != null && w.getName().equals(WORLD);
    }

    /** Wartepunkt (leicht verteilt, damit nicht alle aufeinander stehen). */
    public Location spawn() {
        if (custom()) {
            Location l = parse(plugin.getConfig().getString("waiting-lobby.location"));
            if (l != null) return l;
        }
        World w = Bukkit.getWorld(WORLD);
        if (w == null) return null;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2);
        double d = 6 + r.nextDouble(3);
        Location l = new Location(w, 0.5 + Math.cos(a) * d, Y + 1, 0.5 + Math.sin(a) * d);
        l.setDirection(new Location(w, 0.5, Y + 1, 0.5).toVector().subtract(l.toVector()));
        return l;
    }

    public int voidY() {
        Location l = spawn();
        return l == null ? -64 : l.getBlockY() - 15;
    }

    public void setLocation(Location l) {
        plugin.getConfig().set("waiting-lobby.location", String.format(java.util.Locale.ROOT, "%s;%.2f;%.2f;%.2f;%.1f;%.1f",
                l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        plugin.saveConfig();
    }

    private static Location parse(String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    p.length > 4 ? Float.parseFloat(p[4]) : 0f, p.length > 5 ? Float.parseFloat(p[5]) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Bauen ────────────────────────────────────────────────────────

    private void build(World w) {
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 0.5) continue;
                Material floor;
                if (d < 3) floor = Material.POLISHED_BLACKSTONE_BRICKS;
                else if (d < 4) floor = Material.ORANGE_GLAZED_TERRACOTTA;
                else if (d > R - 1.2) floor = Material.POLISHED_BLACKSTONE;
                else if (Math.round(d) % 4 == 0) floor = Material.DEEPSLATE_TILES;
                else floor = (x + z) % 2 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_BRICKS;
                set(w, x, Y, z, floor);
                // Unterseite: schwebende Insel (Kegel)
                int depth = (int) Math.max(1, (R - d) * 0.9);
                for (int y = 1; y <= depth; y++) {
                    set(w, x, Y - y, z, y > depth - 2 ? Material.BLACKSTONE : Material.DEEPSLATE);
                }
                // Rand mit Glas-Geländer
                if (d > R - 0.6) set(w, x, Y + 1, z, Material.TINTED_GLASS);
            }
        }
        // Leuchtpunkte im Boden
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            set(w, (int) Math.round(Math.cos(a) * 10), Y, (int) Math.round(Math.sin(a) * 10), Material.SEA_LANTERN);
        }
        // Teamsäulen (Grün bei -Z, Rot bei +Z)
        pillar(w, 0, -13, Material.LIME_STAINED_GLASS, Material.VERDANT_FROGLIGHT);
        pillar(w, 0, 13, Material.RED_STAINED_GLASS, Material.PEARLESCENT_FROGLIGHT);
        pillar(w, -13, 0, Material.ORANGE_STAINED_GLASS, Material.OCHRE_FROGLIGHT);
        pillar(w, 13, 0, Material.ORANGE_STAINED_GLASS, Material.OCHRE_FROGLIGHT);

        // Rakete als Denkmal in der Mitte (Sockel + liegende Tomahawk)
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) set(w, x, Y + 1, z, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        set(w, 0, Y + 2, 0, Material.OBSIDIAN);
        Material[] body = {Material.OBSERVER, Material.SLIME_BLOCK, Material.STICKY_PISTON, Material.SLIME_BLOCK,
                Material.REDSTONE_BLOCK, Material.TNT, Material.TNT};
        for (int i = 0; i < body.length; i++) {
            BlockData data = body[i].createBlockData();
            if (data instanceof Directional d && d.getFaces().contains(BlockFace.UP)) d.setFacing(BlockFace.UP);
            w.getBlockAt(0, Y + 3 + i, 0).setBlockData(data, false);
        }
        set(w, 1, Y + 3, 0, Material.ORANGE_TERRACOTTA);
        set(w, -1, Y + 3, 0, Material.ORANGE_TERRACOTTA);
        set(w, 0, Y + 3, 1, Material.ORANGE_TERRACOTTA);
        set(w, 0, Y + 3, -1, Material.ORANGE_TERRACOTTA);
    }

    private static void pillar(World w, int x, int z, Material glass, Material top) {
        set(w, x, Y + 1, z, Material.POLISHED_BLACKSTONE);
        for (int y = 2; y <= 5; y++) set(w, x, Y + y, z, glass);
        set(w, x, Y + 6, z, top);
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void spawnHologram(World w) {
        if (hologram != null && hologram.isValid()) return;
        Location l = new Location(w, 0.5, Y + 12, 0.5);
        hologram = w.spawn(l, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(2.5f, 2.5f, 2.5f), new AxisAngle4f()));
            d.text(Msg.mm("<logo>\n<mut>Wartelobby\n<dim>Die Runde startet automatisch"));
        });
    }

    public void shutdown() {
        if (hologram != null) hologram.remove();
    }
}
