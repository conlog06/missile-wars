package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.map.MapConfig;
import de.crafterteam.missilewars.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Eine laufende Runde pro Map. */
public final class GameManager {

    private final MissileWarsPlugin plugin;
    private final Map<String, Game> games = new HashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private int nextId = 1;

    public GameManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public Game get(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public Game get(World w) {
        for (Game g : games.values()) if (g.world().equals(w)) return g;
        return null;
    }

    public Game get(String mapName) {
        return games.get(mapName);
    }

    public Collection<Game> all() {
        return new ArrayList<>(games.values());
    }

    public Game getOrCreate(MapConfig map) throws GameException {
        Game g = games.get(map.name());
        if (g != null) return g;
        g = Game.create(plugin, map, nextId++);
        games.put(map.name(), g);
        return g;
    }

    void bind(Player p, Game g) {
        byPlayer.put(p.getUniqueId(), g);
    }

    void unbind(Player p) {
        byPlayer.remove(p.getUniqueId());
    }

    void remove(Game g) {
        games.values().remove(g);
        byPlayer.values().removeIf(x -> x == g);
    }

    public void shutdownAll() {
        for (Game g : all()) g.shutdown();
    }

    public boolean isMwWorld(World w) {
        String n = w.getName();
        return n.startsWith(Game.WORLD_PREFIX) || n.startsWith(MapManager.TEMPLATE_PREFIX)
                || plugin.maps().byTemplate(w) != null;
    }

    /** Übrig gebliebene Spielwelten (Absturz) beim Start aufräumen. */
    public void cleanupLeftovers() {
        File[] dirs = Bukkit.getWorldContainer().listFiles(f -> f.isDirectory() && f.getName().startsWith(Game.WORLD_PREFIX));
        if (dirs == null) return;
        for (File d : dirs) {
            if (Bukkit.getWorld(d.getName()) != null) continue;
            plugin.getLogger().info("Lösche alte Spielwelt " + d.getName());
            Game.deleteFolder(d);
        }
    }
}
