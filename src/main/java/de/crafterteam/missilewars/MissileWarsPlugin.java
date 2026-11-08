package de.crafterteam.missilewars;

import de.crafterteam.missilewars.game.GameItems;
import de.crafterteam.missilewars.game.GameListener;
import de.crafterteam.missilewars.game.GameManager;
import de.crafterteam.missilewars.game.JoinSigns;
import de.crafterteam.missilewars.stats.StatsManager;
import de.crafterteam.missilewars.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class MissileWarsPlugin extends JavaPlugin {

    private MapManager maps;
    private GameManager games;
    private GameItems items;
    private GameListener listener;
    private StatsManager stats;
    private JoinSigns signs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);   // neue Optionen in alte config.yml übernehmen
        saveConfig();
        maps = new MapManager(this);
        games = new GameManager(this);
        items = new GameItems(this);
        stats = new StatsManager(this);
        signs = new JoinSigns(this);
        maps.load();
        stats.load();
        signs.load();
        signs.start();
        games.cleanupLeftovers();
        // Stats regelmäßig sichern
        Bukkit.getScheduler().runTaskTimer(this, stats::save, 20L * 300, 20L * 300);

        listener = new GameListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        MwCommand cmd = new MwCommand(this);
        PluginCommand pc = getCommand("mw");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getLogger().info("MissileWars aktiv – " + maps.all().size() + " Map(s).");
    }

    @Override
    public void onDisable() {
        if (games != null) games.shutdownAll();
        if (stats != null) stats.save();
    }

    public StatsManager stats() { return stats; }
    public JoinSigns signs() { return signs; }
    public MapManager maps() { return maps; }
    public GameManager games() { return games; }
    public GameItems items() { return items; }

    public void reloadAll() {
        reloadConfig();
        maps.load();
        signs.load();
        listener.reloadBreakable();
    }

    /** Lobby oder null (= zurück zum Ausgangsort). */
    public Location lobby() {
        String s = getConfig().getString("lobby", "");
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

    public void setLobby(Location l) {
        getConfig().set("lobby", String.format(Locale.ROOT, "%s;%.2f;%.2f;%.2f;%.1f;%.1f",
                l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        saveConfig();
    }
}
