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
        GAMES("Runden"), MISSILES("Raketen"), PORTALS("Portale zerstört"), COINS("Coins");

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

    public StatsManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public void load() {
        entries.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Entry e = new Entry(UUID.fromString(key), s.getString("name", "?"));
                for (Stat st : Stat.values()) e.values.put(st, s.getInt(st.key()));
                e.unlocks.addAll(s.getStringList("unlocks"));
                ConfigurationSection sel = s.getConfigurationSection("selected");
                if (sel != null) for (String k : sel.getKeys(false)) e.selected.put(k, sel.getString(k));
                entries.put(e.id, e);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Entry e : entries.values()) {
            y.set(e.id + ".name", e.name);
            for (Stat st : Stat.values()) y.set(e.id + "." + st.key(), e.get(st));
            y.set(e.id + ".unlocks", new ArrayList<>(e.unlocks));
            for (Map.Entry<String, String> sel : e.selected.entrySet()) y.set(e.id + ".selected." + sel.getKey(), sel.getValue());
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "stats.yml konnte nicht gespeichert werden", ex);
        }
    }

    public void add(Player p, Stat stat, int amount) {
        Entry e = entries.computeIfAbsent(p.getUniqueId(), id -> new Entry(id, p.getName()));
        e.name = p.getName();
        e.values.merge(stat, amount, Integer::sum);
        dirty = true;
    }

    public Entry entry(Player p) {
        Entry e = entries.computeIfAbsent(p.getUniqueId(), id -> new Entry(id, p.getName()));
        e.name = p.getName();
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
        dirty = true;
        return true;
    }

    public boolean has(Player p, String unlock) {
        return entry(p).unlocks.contains(unlock);
    }

    public void unlock(Player p, String unlock) {
        entry(p).unlocks.add(unlock);
        dirty = true;
    }

    public String selected(Player p, String category, String def) {
        Entry e = entries.get(p.getUniqueId());
        return e == null ? def : e.selected.getOrDefault(category, def);
    }

    public void select(Player p, String category, String value) {
        entry(p).selected.put(category, value);
        dirty = true;
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
