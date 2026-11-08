package de.crafterteam.missilewars.map;

import de.crafterteam.missilewars.MissileWarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

public final class MapManager {

    public static final String TEMPLATE_PREFIX = "mw_template_";

    private final MissileWarsPlugin plugin;
    private final File folder;
    private final Map<String, MapConfig> maps = new TreeMap<>();

    public MapManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "maps");
    }

    public void load() {
        maps.clear();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Konnte Map-Ordner nicht anlegen: " + folder);
        }
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
                try {
                    maps.put(name, MapConfig.load(name, YamlConfiguration.loadConfiguration(f)));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Map " + name + " konnte nicht geladen werden", e);
                }
            }
        }
        if (maps.isEmpty()) {
            MapConfig std = new MapConfig("standard", MapConfig.Type.GENERATED);
            ArenaGenerator.applyLayout(std, plugin.getConfig().getInt("generator.distance", 60));
            std.ready(true);
            add(std);
            plugin.getLogger().info("Standard-Arena 'standard' angelegt.");
        }
    }

    public Collection<MapConfig> all() {
        return Collections.unmodifiableCollection(maps.values());
    }

    public MapConfig get(String name) {
        return name == null ? null : maps.get(name.toLowerCase(Locale.ROOT));
    }

    public void add(MapConfig m) {
        maps.put(m.name(), m);
        save(m);
    }

    public void save(MapConfig m) {
        YamlConfiguration y = new YamlConfiguration();
        m.save(y);
        try {
            y.save(new File(folder, m.name() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Map " + m.name() + " konnte nicht gespeichert werden", e);
        }
    }

    public void delete(MapConfig m) {
        maps.remove(m.name());
        File f = new File(folder, m.name() + ".yml");
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Konnte " + f + " nicht löschen");
        }
    }

    /** Map, deren Template-Welt das ist (Setup-Modus). */
    public MapConfig byTemplate(World w) {
        for (MapConfig m : maps.values()) {
            if (m.type() == MapConfig.Type.WORLD && w.getName().equals(m.templateWorld())) return m;
        }
        return null;
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[a-z0-9_-]{1,24}");
    }

    public static File worldFolder(String worldName) {
        return new File(Bukkit.getWorldContainer(), worldName);
    }

    /** Neues Paper-26-Format: <hauptwelt>/dimensions/minecraft/<name>. */
    public static java.nio.file.Path dimensionFolder(String worldName) {
        return Bukkit.getServer().getLevelDirectory().resolve("dimensions").resolve("minecraft")
                .resolve(worldName.toLowerCase(java.util.Locale.ENGLISH).replace(' ', '_'));
    }

    /** Ordner einer (nicht geladenen) Welt finden: gemerkter Pfad → neues Format → altes Format. */
    public static java.nio.file.Path findWorldFolder(MapConfig m) {
        if (m.folder() != null && !m.folder().isBlank()) {
            java.nio.file.Path p = java.nio.file.Path.of(m.folder());
            if (java.nio.file.Files.isDirectory(p)) return p;
        }
        java.nio.file.Path dim = dimensionFolder(m.templateWorld());
        if (java.nio.file.Files.isDirectory(dim)) return dim;
        File legacy = worldFolder(m.templateWorld());
        return legacy.isDirectory() ? legacy.toPath() : null;
    }

    /** Ob ein Weltname existiert (neues oder altes Format). */
    public static boolean worldExists(String worldName) {
        return java.nio.file.Files.isDirectory(dimensionFolder(worldName)) || worldFolder(worldName).isDirectory();
    }
}
