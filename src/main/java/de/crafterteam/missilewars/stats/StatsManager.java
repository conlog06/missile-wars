package de.crafterteam.missilewars.stats;

import de.crafterteam.missilewars.MissileWarsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Dauerhafte Spielerstatistiken in stats.yml. */
public final class StatsManager {

    public enum Stat {
        WINS("Siege"), LOSSES("Niederlagen"), KILLS("Kills"), DEATHS("Tode"),
        GAMES("Runden"), MISSILES("Raketen"), PORTALS("Portale zerstört"), COINS("Coins"),
        ASSISTS("Assists"), INTERCEPTS("Abgefangen"), AIRDROPS("Airdrops"), BEST_STREAK("Beste Serie");

        private final String label;

        Stat(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Stat parse(String s) {
            if (s == null) return null;
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "wins", "siege" -> WINS;
                case "kills" -> KILLS;
                case "deaths", "tode" -> DEATHS;
                case "games", "runden" -> GAMES;
                case "missiles", "raketen" -> MISSILES;
                case "portals", "portale" -> PORTALS;
                case "losses", "niederlagen" -> LOSSES;
                case "coins", "münzen" -> COINS;
                case "assists" -> ASSISTS;
                case "intercepts", "abgefangen" -> INTERCEPTS;
                case "airdrops" -> AIRDROPS;
                case "streak", "serie" -> BEST_STREAK;
                default -> null;
            };
        }
    }

    public static final class Entry {
        public final UUID id;
        public String name;
        public final Map<Stat, Integer> values = new HashMap<>();
        /** Gekaufte Shop-Artikel (Kits, Spuren, Effekte). */
        public final java.util.Set<String> unlocks = new java.util.HashSet<>();
        /** Ausgewählte Kosmetik pro Kategorie, z. B. trail → soul. */
        public final Map<String, String> selected = new HashMap<>();

        Entry(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public int get(Stat s) {
            return values.getOrDefault(s, 0);
        }
    }

    private final MissileWarsPlugin plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private boolean dirty;

    /** null = lokale stats.yml, sonst MySQL/MariaDB. */
    private MySqlStorage sql;
    /** Einträge mit ungespeicherten Änderungen (für MySQL: nur diese werden geschrieben). */
    private final java.util.Set<UUID> changed = new java.util.HashSet<>();

    public StatsManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public String storageName() {
        return sql == null ? "lokal (stats.yml)" : "MySQL";
    }

    // ── (De-)Serialisierung eines Eintrags ───────────────────────────

    private static void write(ConfigurationSection s, Entry e) {
        s.set("name", e.name);
        for (Stat st : Stat.values()) s.set(st.key(), e.get(st));
        s.set("unlocks", new ArrayList<>(e.unlocks));
        for (Map.Entry<String, String> sel : e.selected.entrySet()) s.set("selected." + sel.getKey(), sel.getValue());
    }

    private static Entry read(UUID id, ConfigurationSection s) {
        Entry e = new Entry(id, s.getString("name", "?"));
        for (Stat st : Stat.values()) e.values.put(st, s.getInt(st.key()));
        e.unlocks.addAll(s.getStringList("unlocks"));
        ConfigurationSection sel = s.getConfigurationSection("selected");
        // getKeys(true): Schlüssel mit Punkt (daily.day) liegen verschachtelt – vorher gingen sie beim Neustart verloren
        if (sel != null) for (String k : sel.getKeys(true)) if (!sel.isConfigurationSection(k)) e.selected.put(k, sel.getString(k));
        return e;
    }

    private static String toText(Entry e) {
        YamlConfiguration y = new YamlConfiguration();
        write(y, e);
        return y.saveToString();
    }

    private static Entry fromText(UUID id, String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (org.bukkit.configuration.InvalidConfigurationException ex) {
            return null;
        }
        return read(id, y);
    }

    private void touch(UUID id) {
        changed.add(id);
        dirty = true;
    }

    // ── Laden ────────────────────────────────────────────────────────

    public void load() {
        entries.clear();
        changed.clear();
        if (sql != null) {
            sql.close();
            sql = null;
        }
        if ("mysql".equalsIgnoreCase(plugin.getConfig().getString("storage.type", "local"))) {
            try {
                MySqlStorage s = new MySqlStorage(plugin.getConfig().getConfigurationSection("storage.mysql"));
                s.init();
                boolean importLocal = s.isEmpty() && file.exists();
                sql = s;
                if (importLocal) {
                    // Erster Start mit MySQL: vorhandene stats.yml übernehmen
                    loadLocal();
                    changed.addAll(entries.keySet());
                    dirty = true;
                    saveNow(false);
                    plugin.getLogger().info("stats.yml nach MySQL übernommen (" + entries.size() + " Spieler).");
                } else {
                    for (Map.Entry<UUID, String> r : s.loadAll().entrySet()) {
                        Entry e = fromText(r.getKey(), r.getValue());
                        if (e != null) entries.put(e.id, e);
                    }
                }
                plugin.getLogger().info("Stats-Speicher: MySQL (" + entries.size() + " Spieler).");
                return;
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "MySQL nicht erreichbar – nutze lokale stats.yml: " + ex.getMessage());
                if (sql != null) sql.close();
                sql = null;
            }
        }
        loadLocal();
    }

    private void loadLocal() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Entry e = read(UUID.fromString(key), s);
                entries.put(e.id, e);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** MySQL + BungeeCord: beim Betreten die aktuellen Werte des Spielers holen (könnte auf einem anderen Server gespielt haben). */
    public void refreshPlayer(Player p) {
        if (sql == null) return;
        UUID id = p.getUniqueId();
        MySqlStorage s = sql;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String text;
            try {
                text = s.load(id);
            } catch (Exception ex) {
                plugin.getLogger().warning("Stats von " + p.getName() + " konnten nicht geladen werden: " + ex.getMessage());
                return;
            }
            if (text == null) return;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (changed.contains(id)) return;   // lokale, noch ungespeicherte Änderungen haben Vorrang
                Entry e = fromText(id, text);
                if (e != null) entries.put(id, e);
            });
        });
    }

    /** MySQL: Ranglisten gelegentlich mit Werten anderer Server auffrischen. */
    public void refreshAll() {
        if (sql == null) return;
        MySqlStorage s = sql;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, String> rows;
            try {
                rows = s.loadAll();
            } catch (Exception ex) {
                return;
            }
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                for (Map.Entry<UUID, String> r : rows.entrySet()) {
                    if (changed.contains(r.getKey()) || org.bukkit.Bukkit.getPlayer(r.getKey()) != null) continue;
                    Entry e = fromText(r.getKey(), r.getValue());
                    if (e != null) entries.put(e.id, e);
                }
            });
        });
    }

    // ── Speichern ────────────────────────────────────────────────────

    private boolean saveQueued;
    private final Object writeLock = new Object();

    /** Speichern gebündelt & asynchron (max. alle 5 s), beim Abschalten synchron. */
    public void save() {
        if (!dirty) return;
        if (!plugin.isEnabled()) {
            saveNow(false);
            return;
        }
        if (saveQueued) return;
        saveQueued = true;
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveQueued = false;
            saveNow(true);
        }, 100L);
    }

    private void saveNow(boolean async) {
        if (!dirty) return;
        Runnable write;
        if (sql != null) {
            Map<UUID, String[]> rows = new HashMap<>();
            for (UUID id : changed) {
                Entry e = entries.get(id);
                if (e != null) rows.put(id, new String[]{e.name, toText(e)});
            }
            changed.clear();
            dirty = false;
            MySqlStorage s = sql;
            write = () -> {
                try {
                    s.upsert(rows);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Stats konnten nicht in MySQL gespeichert werden: " + ex.getMessage());
                    if (async) org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        changed.addAll(rows.keySet());
                        dirty = true;
                    });
                }
            };
        } else {
            YamlConfiguration y = new YamlConfiguration();
            for (Entry e : entries.values()) write(y.createSection(e.id.toString()), e);
            String data = y.saveToString();
            changed.clear();
            dirty = false;
            write = () -> {
                synchronized (writeLock) {
                    try {
                        java.nio.file.Path tmp = file.toPath().resolveSibling("stats.yml.tmp");
                        java.nio.file.Files.createDirectories(file.toPath().getParent());
                        java.nio.file.Files.writeString(tmp, data, java.nio.charset.StandardCharsets.UTF_8);
                        java.nio.file.Files.move(tmp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        dirty = true;
                        plugin.getLogger().log(Level.WARNING, "stats.yml konnte nicht gespeichert werden", ex);
                    }
                }
            };
        }
        if (async) org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    /** Beim Abschalten: alles synchron schreiben und Verbindung schließen. */
    public void shutdown() {
        saveNow(false);
        if (sql != null) sql.close();
    }

    public void add(Player p, Stat stat, int amount) {
        Entry e = entries.computeIfAbsent(p.getUniqueId(), id -> new Entry(id, p.getName()));
        e.name = p.getName();
        int levelBefore = stat == Stat.COINS ? 0 : de.crafterteam.missilewars.stats.Progression.level(e);
        e.values.merge(stat, amount, Integer::sum);
        touch(e.id);
        if (stat != Stat.COINS && plugin.progression() != null) {
            plugin.progression().afterStat(p, e, levelBefore);
            if (amount > 0) plugin.progression().questProgress(p, stat, amount);
        }
    }

    /** Höchstwert speichern (z. B. beste Killserie). */
    public void max(Player p, Stat stat, int value) {
        Entry e = entries.computeIfAbsent(p.getUniqueId(), id -> new Entry(id, p.getName()));
        if (value <= e.get(stat)) return;
        e.values.put(stat, value);
        touch(e.id);
        if (plugin.progression() != null) plugin.progression().afterStat(p, e, Progression.level(e));
    }

    public Entry entry(Player p) {
        Entry e = entries.computeIfAbsent(p.getUniqueId(), id -> new Entry(id, p.getName()));
        e.name = p.getName();
        touch(e.id);   // Aufrufer ändern den Eintrag oft direkt (Shop)
        return e;
    }

    public int coins(Player p) {
        return entry(p).get(Stat.COINS);
    }

    /** Coins abziehen; false wenn zu wenig. */
    public boolean spend(Player p, int amount) {
        Entry e = entry(p);
        if (e.get(Stat.COINS) < amount) return false;
        e.values.put(Stat.COINS, e.get(Stat.COINS) - amount);
        touch(e.id);
        return true;
    }

    public boolean has(Player p, String unlock) {
        return entry(p).unlocks.contains(unlock);
    }

    public void unlock(Player p, String unlock) {
        entry(p).unlocks.add(unlock);
        touch(p.getUniqueId());
    }

    public String selected(Player p, String category, String def) {
        Entry e = entries.get(p.getUniqueId());
        return e == null ? def : e.selected.getOrDefault(category, def);
    }

    public void select(Player p, String category, String value) {
        entry(p).selected.put(category, value);
        touch(p.getUniqueId());
    }

    public Entry get(UUID id) {
        return entries.get(id);
    }

    public Entry byName(String name) {
        for (Entry e : entries.values()) if (e.name.equalsIgnoreCase(name)) return e;
        return null;
    }

    public List<Entry> top(Stat stat, int limit) {
        List<Entry> l = new ArrayList<>(entries.values());
        l.removeIf(e -> e.get(stat) <= 0);
        l.sort(Comparator.comparingInt((Entry e) -> e.get(stat)).reversed());
        return l.size() > limit ? l.subList(0, limit) : l;
    }
}
