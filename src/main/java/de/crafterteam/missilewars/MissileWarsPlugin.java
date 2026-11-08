package de.crafterteam.missilewars;

import de.crafterteam.missilewars.game.GameItems;
import de.crafterteam.missilewars.game.GameListener;
import de.crafterteam.missilewars.game.GameManager;
import de.crafterteam.missilewars.game.JoinSigns;
import de.crafterteam.missilewars.game.MissilePreview;
import de.crafterteam.missilewars.stats.LeaderboardHolograms;
import de.crafterteam.missilewars.stats.StatsManager;
import de.crafterteam.missilewars.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class MissileWarsPlugin extends JavaPlugin {

    private MapManager maps;
    private GameManager games;
    private GameItems items;
    private GameListener listener;
    private StatsManager stats;
    private JoinSigns signs;
    private MissilePreview preview;
    private LeaderboardHolograms holograms;
    private de.crafterteam.missilewars.map.WaitingLobby waitingLobby;
    private de.crafterteam.missilewars.network.ServerMode serverMode;
    private de.crafterteam.missilewars.rank.NameTags nameTags;

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
        preview = new MissilePreview(this);
        holograms = new LeaderboardHolograms(this);
        waitingLobby = new de.crafterteam.missilewars.map.WaitingLobby(this);
        serverMode = new de.crafterteam.missilewars.network.ServerMode(this);
        nameTags = new de.crafterteam.missilewars.rank.NameTags(this);
        maps.load();
        stats.load();
        signs.load();
        signs.start();
        holograms.load();
        holograms.start();
        games.cleanupLeftovers();
        waitingLobby.init();
        serverMode.init();
        nameTags.init();
        getServer().getPluginManager().registerEvents(serverMode, this);
        getServer().getPluginManager().registerEvents(nameTags, this);
        // Stats regelmäßig sichern
        Bukkit.getScheduler().runTaskTimer(this, stats::save, 20L * 300, 20L * 300);

        listener = new GameListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        // Modernes Paper-Command-System (Brigadier, Lifecycle) statt plugin.yml
        MwCommand cmd = new MwCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("mw", "Missile Wars", List.of("missilewars", "missiles"), cmd));
        getLogger().info("MissileWars aktiv – " + maps.all().size() + " Map(s).");
    }

    @Override
    public void onDisable() {
        if (games != null) games.shutdownAll();
        if (preview != null) preview.hideAll();
        if (holograms != null) holograms.despawnAll();
        if (waitingLobby != null) waitingLobby.shutdown();
        if (stats != null) stats.save();
    }

    public StatsManager stats() { return stats; }
    public JoinSigns signs() { return signs; }
    public MissilePreview preview() { return preview; }
    public LeaderboardHolograms holograms() { return holograms; }
    public de.crafterteam.missilewars.map.WaitingLobby waitingLobby() { return waitingLobby; }
    public de.crafterteam.missilewars.network.ServerMode serverMode() { return serverMode; }
    public de.crafterteam.missilewars.rank.NameTags nameTags() { return nameTags; }
    public MapManager maps() { return maps; }
    public GameManager games() { return games; }
    public GameItems items() { return items; }

    public void reloadAll() {
        reloadConfig();
        maps.load();
        signs.load();
        holograms.load();
        holograms.updateAll();
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
