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

/**
 * Verwaltet alle Runden. Im Queue-Modus (single-game.enabled) gibt es bis zu queues.max-games Runden
 * gleichzeitig: Spieler landen automatisch in der offenen Lobby; startet sie, öffnet die nächste.
 */
public final class GameManager {

    private final MissileWarsPlugin plugin;
    private final Map<Integer, Game> games = new java.util.LinkedHashMap<>();
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

    /** Runde per Nummer ("2", "#2") oder erste Runde auf dieser Map. */
    public Game get(String key) {
        if (key == null) return null;
        String k = key.startsWith("#") ? key.substring(1) : key;
        try {
            Game g = games.get(Integer.parseInt(k));
            if (g != null) return g;
        } catch (NumberFormatException ignored) {
        }
        for (Game g : games.values()) if (g.map().name().equalsIgnoreCase(key)) return g;
        return null;
    }

    public Collection<Game> all() {
        return new ArrayList<>(games.values());
    }

    private final Map<UUID, String> canRate = new HashMap<>();

    void allowRating(Player p, String map) {
        canRate.put(p.getUniqueId(), map);
    }

    /** Map nach der Runde bewerten (einmal pro Runde). */
    public void rate(Player p, boolean up) {
        String name = canRate.remove(p.getUniqueId());
        MapConfig m = name == null ? null : plugin.maps().get(name);
        if (m == null) {
            de.crafterteam.missilewars.util.Msg.send(p, "<bad>Du kannst gerade keine Map bewerten.");
            return;
        }
        m.rate(up);
        plugin.maps().save(m);
        de.crafterteam.missilewars.util.Msg.send(p, "<ok>Danke! <mut>" + m.name() + " hat jetzt <acc>" + m.ratingPercent() + "% <mut>positive Bewertungen.");
    }

    /** Queue-Modus: automatische Lobbys statt freier Map-Wahl. */
    public boolean singleGame() {
        return plugin.getConfig().getBoolean("single-game.enabled", true);
    }

    /** Wie viele Runden gleichzeitig laufen dürfen (Standard 2). */
    public int maxGames() {
        return Math.max(1, plugin.getConfig().getInt("queues.max-games", 2));
    }

    private static boolean open(Game g) {
        return g.state() == Game.State.WAITING || g.state() == Game.State.COUNTDOWN;
    }

    /** Offene Lobby mit den meisten Spielern, die noch Platz hat. */
    public Game openLobby() {
        Game best = null;
        for (Game g : games.values()) {
            if (!open(g) || g.playerCount() >= g.maxPlayers()) continue;
            if (best == null || g.playerCount() > best.playerCount()) best = g;
        }
        return best;
    }

    /** Runden, die nicht gerade enden. */
    public int activeCount() {
        int n = 0;
        for (Game g : games.values()) if (g.state() != Game.State.ENDING) n++;
        return n;
    }

    /** Die "Haupt"-Runde (offene Lobby bevorzugt) oder null. */
    public Game current() {
        Game o = openLobby();
        if (o != null) return o;
        for (Game g : games.values()) if (g.state() != Game.State.ENDING) return g;
        return null;
    }

    public Game getOrCreate(MapConfig map) throws GameException {
        for (Game g : games.values()) if (g.map() == map && open(g)) return g;
        return create(map);
    }

    /** Immer eine neue Runde (eigene Welt) – mehrere Runden auf derselben Map sind erlaubt. */
    public Game create(MapConfig map) throws GameException {
        int id = nextId++;
        Game g = Game.create(plugin, map, id);
        games.put(id, g);
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
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, this::ensureOpenLobby, 40L);
    }

    /** Start-Map für eine neue Lobby: start-map oder zufällig, bevorzugt eine, die gerade nicht läuft. */
    private MapConfig pickMap() {
        String start = plugin.getConfig().getString("single-game.start-map", "random");
        MapConfig map = start == null || start.equalsIgnoreCase("random") ? null : plugin.maps().get(start);
        if (map != null && map.ready() && map.complete()) return map;
        java.util.List<MapConfig> ready = new ArrayList<>(), free = new ArrayList<>();
        for (MapConfig m : plugin.maps().all()) {
            if (!m.ready() || !m.complete()) continue;
            ready.add(m);
            boolean used = false;
            for (Game g : games.values()) if (g.map() == m) used = true;
            if (!used) free.add(m);
        }
        java.util.List<MapConfig> pool = free.isEmpty() ? ready : free;
        return pool.isEmpty() ? null : pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // ── Revanche ─────────────────────────────────────────────────────

    private final Map<UUID, String> rematchOffer = new HashMap<>();
    private final Map<UUID, String> rematchVote = new HashMap<>();

    void offerRematch(Player p, String map) {
        rematchOffer.put(p.getUniqueId(), map);
    }

    /** /mw revanche: Stimme wird in der nächsten Lobby automatisch abgegeben. */
    public void voteRematch(Player p) {
        String map = rematchOffer.remove(p.getUniqueId());
        if (map == null) {
            de.crafterteam.missilewars.util.Msg.send(p, "<bad>Gerade gibt es keine Revanche.");
            return;
        }
        Game g = get(p);
        if (g != null && g.state() != Game.State.ENDING && g.state() != Game.State.RUNNING) {
            if (g.votingOpen()) g.vote(p, map);
            return;
        }
        rematchVote.put(p.getUniqueId(), map);
        de.crafterteam.missilewars.util.Msg.send(p, "<ok>Revanche! <mut>Du stimmst in der nächsten Lobby für <txt>" + map + "<mut>.");
    }

    String takeRematch(Player p) {
        return rematchVote.remove(p.getUniqueId());
    }

    /** Queue-Modus: gibt es keine offene Lobby (und ist noch Platz), wird eine neue geöffnet. */
    public void ensureOpenLobby() {
        if (!plugin.isEnabled() || !singleGame() || openLobby() != null || activeCount() >= maxGames()) return;
        MapConfig map = pickMap();
        if (map == null) return;
        try {
            Game g = create(map);
            de.crafterteam.missilewars.util.Msg.broadcastLobby(plugin, "<ok>Neue Lobby <txt>#" + g.id()
                    + " <mut>ist offen <dim>(" + map.name() + ") <mut>– <acc>/mw lobbys");
            // Spieler ohne Runde (z. B. nach dem Rundenende) direkt hinein
            if (plugin.serverMode().autoJoin()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (get(p) == null && !plugin.staff().isStaff(p)) g.join(p);
                }
            }
        } catch (GameException e) {
            plugin.getLogger().warning("Neue Lobby konnte nicht erstellt werden: " + e.getMessage());
        }
    }

    /** Spieler in eine andere Runde wechseln lassen (Lobby-Auswahl). */
    public void switchTo(Player p, Game target) {
        Game cur = get(p);
        if (cur == target) {
            de.crafterteam.missilewars.util.Msg.send(p, "<mut>Du bist schon in dieser Runde.");
            return;
        }
        if (target.state() == Game.State.ENDING) {
            de.crafterteam.missilewars.util.Msg.send(p, "<bad>Diese Runde endet gerade.");
            return;
        }
        if (cur != null) cur.leave(p, false);
        if (open(target) && target.playerCount() < target.maxPlayers()) target.join(p);
        else target.spectate(p);
    }

    /** Beitreten: bestimmte Map oder automatisch (offene Lobby → neue Lobby → laufende Runde). */
    public void quickJoin(Player p, String mapName, boolean spectate) {
        if (singleGame()) {
            Game g = openLobby();
            if (g == null && activeCount() < maxGames()) {
                MapConfig map = pickMap();
                if (map == null) {
                    de.crafterteam.missilewars.util.Msg.send(p, "<bad>Es ist keine fertige Map vorhanden. <mut>/mw list");
                    return;
                }
                try {
                    g = create(map);
                    if (games.size() > 1) de.crafterteam.missilewars.util.Msg.broadcastLobby(plugin,
                            "<ok>Neue Lobby <txt>#" + g.id() + " <mut>geöffnet <dim>(" + map.name() + ")");
                } catch (GameException e) {
                    de.crafterteam.missilewars.util.Msg.send(p, "<bad>" + de.crafterteam.missilewars.util.Msg.esc(e.getMessage()));
                    return;
                }
            }
            if (g == null) {
                // alle Plätze belegt: laufender Runde beitreten (Late-Join) oder zuschauen
                for (Game x : games.values()) {
                    if (x.state() != Game.State.RUNNING) continue;
                    if (g == null || x.playerCount() < g.playerCount()) g = x;
                }
            }
            if (g == null) {
                de.crafterteam.missilewars.util.Msg.send(p, "<mut>Alle Runden enden gerade – du kommst gleich in die nächste.");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && get(p) == null) quickJoin(p, null, spectate);
                }, 60L);
                return;
            }
            if (spectate) g.spectate(p);
            else g.join(p);
            return;
        }
        MapConfig map = null;
        if (mapName != null) {
            map = plugin.maps().get(mapName);
        } else {
            Game best = openLobby();
            if (best != null) map = best.map();
            if (map == null) {
                String def = plugin.getConfig().getString("server-mode.map", "");
                if (def == null || def.isBlank()) def = plugin.getConfig().getString("default-map", "standard");
                map = plugin.maps().get(def);
            }
            if (map == null) map = pickMap();
        }
        if (map == null) {
            de.crafterteam.missilewars.util.Msg.send(p, "<bad>Gerade ist keine Map frei. <mut>/mw list");
            return;
        }
        try {
            Game g = null;
            for (Game x : games.values()) if (x.map() == map && x.state() != Game.State.ENDING) { g = x; break; }
            if (g == null) g = create(map);
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
