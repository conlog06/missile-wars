package de.crafterteam.missilewars.stats;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.stats.StatsManager.Stat;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Schwebende Top-10-Listen (Text-Displays), z. B. in der Lobby. */
public final class LeaderboardHolograms {

    public record Holo(String id, String world, double x, double y, double z, Stat stat) {
        Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private final MissileWarsPlugin plugin;
    private final File file;
    private final Map<String, Holo> holos = new LinkedHashMap<>();
    private final Map<String, TextDisplay> entities = new LinkedHashMap<>();

    public LeaderboardHolograms(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
    }

    public void load() {
        despawnAll();
        holos.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Stat stat = Stat.parse(s.getString("stat", "wins"));
            if (stat == null) stat = Stat.WINS;
            holos.put(id, new Holo(id, s.getString("world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), stat));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Holo h : holos.values()) {
            y.set(h.id() + ".world", h.world());
            y.set(h.id() + ".x", h.x());
            y.set(h.id() + ".y", h.y());
            y.set(h.id() + ".z", h.z());
            y.set(h.id() + ".stat", h.stat().key());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "holograms.yml konnte nicht gespeichert werden", e);
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 40L, 20L * 30);
    }

    public String add(Location l, Stat stat) {
        int n = 1;
        while (holos.containsKey("holo" + n)) n++;
        String id = "holo" + n;
        holos.put(id, new Holo(id, l.getWorld().getName(), l.getX(), l.getY() + 2.0, l.getZ(), stat));
        save();
        updateAll();
        return id;
    }

    /** Nächstes Hologramm im Umkreis entfernen. */
    public String removeNear(Location l, double radius) {
        Holo best = null;
        double bestD = radius * radius;
        for (Holo h : holos.values()) {
            Location hl = h.location();
            if (hl == null || !hl.getWorld().equals(l.getWorld())) continue;
            double d = hl.distanceSquared(l);
            if (d <= bestD) {
                bestD = d;
                best = h;
            }
        }
        if (best == null) return null;
        holos.remove(best.id());
        TextDisplay e = entities.remove(best.id());
        if (e != null) e.remove();
        save();
        return best.id();
    }

    public List<Holo> all() {
        return new ArrayList<>(holos.values());
    }

    public void updateAll() {
        for (Holo h : holos.values()) {
            Location l = h.location();
            if (l == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            TextDisplay td = entities.get(h.id());
            if (td == null || !td.isValid()) {
                td = l.getWorld().spawn(l, TextDisplay.class, d -> {
                    d.setBillboard(Display.Billboard.VERTICAL);
                    d.setPersistent(false);
                    d.setShadowed(true);
                    d.setLineWidth(250);
                    d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                });
                entities.put(h.id(), td);
            }
            td.text(Msg.mm(render(h.stat())));
        }
    }

    private String render(Stat stat) {
        StringBuilder sb = new StringBuilder("<logo>\n<acc>Top 10 – " + stat.label() + "\n");
        List<StatsManager.Entry> top = plugin.stats().top(stat, 10);
        if (top.isEmpty()) sb.append("<mut>Noch keine Einträge");
        for (int i = 0; i < top.size(); i++) {
            String c = i == 0 ? "<pri>" : i == 1 ? "<mut>" : i == 2 ? "<#cd7f32>" : "<dim>";
            sb.append("\n").append(c).append(i + 1).append(". <txt>").append(Msg.esc(top.get(i).name))
                    .append(" <dim>» <acc>").append(top.get(i).get(stat));
        }
        return sb.toString();
    }

    public void despawnAll() {
        entities.values().forEach(TextDisplay::remove);
        entities.clear();
    }
}
