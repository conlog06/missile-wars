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

    /** Beitreten: bestimmte Map oder automatisch (wartende Runde → Standard-Map → erste fertige). */
    public void quickJoin(Player p, String mapName, boolean spectate) {
        MapConfig map = null;
        if (mapName != null) {
            map = plugin.maps().get(mapName);
        } else {
            Game best = null;
            for (Game g : games.values()) {
                if (g.state() != Game.State.WAITING && g.state() != Game.State.COUNTDOWN) continue;
                if (best == null || g.playerCount() > best.playerCount()) best = g;
            }
            if (best != null) map = best.map();
            if (map == null) {
                String def = plugin.getConfig().getString("server-mode.map", "");
                if (def == null || def.isBlank()) def = plugin.getConfig().getString("default-map", "standard");
                map = plugin.maps().get(def);
                // läuft dort schon eine Runde und Late-Join ist aus? → andere freie Map suchen
                Game running = map == null ? null : games.get(map.name());
                if (running != null && running.state() == Game.State.ENDING) map = null;
            }
            if (map == null) {
                for (MapConfig m : plugin.maps().all()) {
                    if (!m.ready() || !m.complete()) continue;
                    Game g = games.get(m.name());
                    if (g == null || g.state() != Game.State.ENDING) { map = m; break; }
                }
            }
        }
        if (map == null) {
            de.crafterteam.missilewars.util.Msg.send(p, "<bad>Gerade ist keine Map frei. <mut>/mw list");
            return;
        }
        try {
            Game g = getOrCreate(map);
            if (spectate) g.spectate(p);
            else g.join(p);
        } catch (GameException e) {
            de.crafterteam.missilewars.util.Msg.send(p, "<bad>" + de.crafterteam.missilewars.util.Msg.esc(e.getMessage()));
        }
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
        java.util.List<File> all = new java.util.ArrayList<>();
        File[] legacy = Bukkit.getWorldContainer().listFiles(f -> f.isDirectory() && f.getName().startsWith(Game.WORLD_PREFIX));
        if (legacy != null) all.addAll(java.util.List.of(legacy));
        File dimDir = Bukkit.getServer().getLevelDirectory().resolve("dimensions").resolve("minecraft").toFile();
        File[] dims = dimDir.listFiles(f -> f.isDirectory() && f.getName().startsWith(Game.WORLD_PREFIX));
        if (dims != null) all.addAll(java.util.List.of(dims));
        for (File d : all) {
            if (Bukkit.getWorld(d.getName()) != null) continue;
            plugin.getLogger().info("Lösche alte Spielwelt " + d.getName());
            Game.deleteFolder(d);
        }
    }
}
