package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.map.MapConfig;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/** Schilder mit [MissileWars] / <map> → Klick = beitreten, zeigen Live-Status. */
public final class JoinSigns {

    private final MissileWarsPlugin plugin;
    private final File file;
    /** "welt;x;y;z" → Mapname */
    private final Map<String, String> signs = new HashMap<>();

    public JoinSigns(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "signs.yml");
    }

    public static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    public void load() {
        signs.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) signs.put(k.replace('|', ';'), y.getString(k));
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        // ';' ist in YAML-Keys ok, '.' nicht – Koordinaten enthalten keine Punkte
        for (Map.Entry<String, String> e : signs.entrySet()) y.set(e.getKey().replace(';', '|'), e.getValue());
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "signs.yml konnte nicht gespeichert werden", ex);
        }
    }

    public void add(Block b, String map) {
        signs.put(key(b), map);
        save();
    }

    public String mapAt(Block b) {
        return signs.get(key(b));
    }

    public void remove(Block b) {
        if (signs.remove(key(b)) != null) save();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 40L, 40L);
    }

    public void updateAll() {
        for (Map.Entry<String, String> e : signs.entrySet()) {
            String[] p = e.getKey().split(";");
            if (p.length != 4) continue;
            World w = Bukkit.getWorld(p[0]);
            if (w == null) continue;
            int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = new Location(w, x, y, z).getBlock();
            if (!(b.getState() instanceof Sign sign)) continue;
            write(sign.getSide(Side.FRONT), e.getValue());
            sign.setWaxed(true);
            sign.update(false, false);
        }
    }

    private void write(SignSide side, String mapName) {
        MapConfig m = plugin.maps().get(mapName);
        Game g = plugin.games().get(mapName);
        String status;
        String players;
        if (m == null) {
            status = "<bad>Map fehlt";
            players = "";
        } else if (g == null) {
            status = m.ready() && m.complete() ? "<ok>Beitreten" : "<bad>Setup";
            players = "<dim>0 Spieler";
        } else {
            status = switch (g.state()) {
                case WAITING -> "<ok>Wartet";
                case COUNTDOWN -> "<acc>Startet";
                case RUNNING -> "<pri>Läuft";
                case ENDING -> "<bad>Endet";
            };
            players = "<dim>" + g.playerCount() + "/" + plugin.getConfig().getInt("players.max", 16) + " Spieler";
        }
        side.line(0, Msg.mm("<logo>"));
        side.line(1, Msg.mm("<dim>" + Msg.esc(mapName)));
        side.line(2, Msg.mm(status));
        side.line(3, Msg.mm(players));
    }
}
