package de.crafterteam.missilewars.map;

import de.crafterteam.missilewars.game.Team;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Eine Map. Zwei Arten:
 * GENERATED – Arena wird für jede Runde frisch generiert (kein Welt-Ordner nötig).
 * WORLD     – eigene Welt (Template), wird für jede Runde kopiert.
 *
 * Achse: Die Teams stehen sich entlang der Z-Achse gegenüber.
 */
public final class MapConfig {

    public enum Type { GENERATED, WORLD }

    public record Spawn(double x, double y, double z, float yaw, float pitch) {
        public Location toLocation(World w) {
            return new Location(w, x, y, z, yaw, pitch);
        }

        public static Spawn of(Location l) {
            return new Spawn(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        }
    }

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public double centerX() { return (minX + maxX + 1) / 2.0; }
        public double centerY() { return (minY + maxY + 1) / 2.0; }
        public double centerZ() { return (minZ + maxZ + 1) / 2.0; }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private final String name;
    private Type type;
    private String templateWorld;
    private int distance;
    private boolean ready;
    private int deathY;
    private final Map<Team, Spawn> spawns = new EnumMap<>(Team.class);
    private final Map<Team, Box> portals = new EnumMap<>(Team.class);

    public MapConfig(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String name() { return name; }
    public Type type() { return type; }
    public void type(Type t) { this.type = t; }
    public String templateWorld() { return templateWorld; }
    public void templateWorld(String w) { this.templateWorld = w; }
    public int distance() { return distance; }
    public void distance(int d) { this.distance = d; }
    public boolean ready() { return ready; }
    public void ready(boolean r) { this.ready = r; }
    public int deathY() { return deathY; }
    public void deathY(int y) { this.deathY = y; }
    public Spawn spawn(Team t) { return spawns.get(t); }
    public void spawn(Team t, Spawn s) { spawns.put(t, s); }
    public Box portal(Team t) { return portals.get(t); }
    public void portal(Team t, Box b) { portals.put(t, b); }

    public boolean complete() {
        return spawns.size() == 2 && portals.size() == 2;
    }

    /** +1 oder -1: in welche Z-Richtung die Raketen dieses Teams fliegen. */
    public int forward(Team t) {
        Box own = portals.get(t);
        Box enemy = portals.get(t.other());
        return enemy.centerZ() >= own.centerZ() ? 1 : -1;
    }

    /** Z-Koordinate der Mittellinie. */
    public double midZ() {
        return (portals.get(Team.GREEN).centerZ() + portals.get(Team.RED).centerZ()) / 2.0;
    }

    /** Mittelpunkt zum Zuschauen. */
    public Location spectatorSpot(World w) {
        Box g = portals.get(Team.GREEN);
        Box r = portals.get(Team.RED);
        double x = (g.centerX() + r.centerX()) / 2.0;
        double y = Math.max(g.maxY(), r.maxY()) + 6;
        return new Location(w, x, y, midZ(), 90f, 30f);
    }

    // ── Speichern / Laden ────────────────────────────────────────────

    public void save(YamlConfiguration y) {
        y.set("type", type.name());
        y.set("world", templateWorld);
        y.set("distance", distance);
        y.set("ready", ready);
        y.set("death-y", deathY);
        for (Team t : Team.values()) {
            String p = t.name().toLowerCase();
            Spawn s = spawns.get(t);
            if (s != null) {
                y.set(p + ".spawn.x", s.x());
                y.set(p + ".spawn.y", s.y());
                y.set(p + ".spawn.z", s.z());
                y.set(p + ".spawn.yaw", s.yaw());
                y.set(p + ".spawn.pitch", s.pitch());
            }
            Box b = portals.get(t);
            if (b != null) {
                y.set(p + ".portal", java.util.List.of(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
            }
        }
    }

    public static MapConfig load(String name, YamlConfiguration y) {
        Type type;
        try {
            type = Type.valueOf(y.getString("type", "GENERATED"));
        } catch (IllegalArgumentException e) {
            type = Type.GENERATED;
        }
        MapConfig m = new MapConfig(name, type);
        m.templateWorld = y.getString("world");
        m.distance = y.getInt("distance", 60);
        m.ready = y.getBoolean("ready", false);
        m.deathY = y.getInt("death-y", 30);
        if (type == Type.GENERATED) {
            ArenaGenerator.applyLayout(m, m.distance);
            m.ready = true;
            return m;
        }
        for (Team t : Team.values()) {
            String p = t.name().toLowerCase();
            ConfigurationSection s = y.getConfigurationSection(p + ".spawn");
            if (s != null) {
                m.spawns.put(t, new Spawn(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
            }
            java.util.List<Integer> b = y.getIntegerList(p + ".portal");
            if (b.size() == 6) {
                m.portals.put(t, new Box(b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5)));
            }
        }
        return m;
    }
}
