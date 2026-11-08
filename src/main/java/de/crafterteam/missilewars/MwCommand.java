package de.crafterteam.missilewars;

import de.crafterteam.missilewars.game.Game;
import de.crafterteam.missilewars.game.GameException;
import de.crafterteam.missilewars.game.Team;
import de.crafterteam.missilewars.map.ArenaGenerator;
import de.crafterteam.missilewars.map.MapConfig;
import de.crafterteam.missilewars.map.MapManager;
import de.crafterteam.missilewars.map.VoidGenerator;
import de.crafterteam.missilewars.stats.StatsManager;
import de.crafterteam.missilewars.stats.StatsManager.Stat;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import java.util.Collection;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MwCommand implements BasicCommand {

    private static final String ADMIN = "missilewars.admin";
    private final MissileWarsPlugin plugin;

    public MwCommand(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        handle(source.getSender(), args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        String[] a = args.length == 0 ? new String[]{""} : args;
        return complete(source.getSender(), a);
    }

    public boolean handle(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> join(sender, args.length > 1 ? args[1] : null, false);
            case "spectate", "spec", "zuschauen" -> join(sender, args.length > 1 ? args[1] : null, true);
            case "leave", "verlassen" -> leave(sender);
            case "team" -> team(sender, args);
            case "list", "liste" -> list(sender);
            case "stats" -> stats(sender, args);
            case "top", "leaderboard", "lb" -> top(sender, args);
            case "start" -> start(sender, args);
            case "stop" -> stop(sender, args);
            case "setlobby" -> setLobby(sender);
            case "reload" -> reload(sender);
            case "map" -> map(sender, args);
            case "holo", "hologram" -> holo(sender, args);
            case "vote", "abstimmen" -> vote(sender, args);
            case "menu", "menü" -> { if (sender instanceof Player p) de.crafterteam.missilewars.gui.Menus.main(plugin, p); }
            case "teleporter", "tp" -> {
                if (sender instanceof Player p) {
                    Game g = plugin.games().get(p);
                    if (g != null && g.isSpectator(p)) de.crafterteam.missilewars.gui.Menus.spectator(g, p);
                    else Msg.send(p, "<bad>Nur für Zuschauer.");
                }
            }
            case "setwaitlobby" -> {
                if (admin(sender) && sender instanceof Player p) {
                    plugin.waitingLobby().setLocation(p.getLocation());
                    Msg.send(p, "<ok>Wartelobby gesetzt. <mut>(Leer lassen in der Config = generierte Lobby)");
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        Msg.send(s, "<txt>Befehle:");
        s.sendMessage(Msg.mm(" <acc>/mw join [map]</acc> <mut>– Runde beitreten"));
        s.sendMessage(Msg.mm(" <acc>/mw spectate [map]</acc> <mut>– zuschauen"));
        s.sendMessage(Msg.mm(" <acc>/mw team <grün|rot></acc> <mut>– Team wechseln"));
        s.sendMessage(Msg.mm(" <acc>/mw vote [map]</acc> <mut>– Map-Abstimmung"));
        s.sendMessage(Msg.mm(" <acc>/mw leave</acc> <mut>– Runde verlassen"));
        s.sendMessage(Msg.mm(" <acc>/mw menu</acc> <mut>– Spielmenü"));
        s.sendMessage(Msg.mm(" <acc>/mw teleporter</acc> <mut>– (Zuschauer) zu Spielern teleportieren"));
        s.sendMessage(Msg.mm(" <acc>/mw list</acc> <mut>– Maps & Runden"));
        s.sendMessage(Msg.mm(" <acc>/mw stats [spieler]</acc> <mut>– Statistiken"));
        s.sendMessage(Msg.mm(" <acc>/mw top [wins|kills|raketen|portale]</acc> <mut>– Bestenliste"));
        s.sendMessage(Msg.mm(" <dim>Im Spiel: Chat = Team, <txt>@Nachricht</txt> = an alle"));
        if (s.hasPermission(ADMIN)) {
            s.sendMessage(Msg.mm(" <bad>/mw start [map]</bad> <mut>– sofort starten"));
            s.sendMessage(Msg.mm(" <bad>/mw stop <map></bad> <mut>– Runde beenden"));
            s.sendMessage(Msg.mm(" <bad>/mw setlobby</bad> <mut>– Lobby-Punkt setzen"));
            s.sendMessage(Msg.mm(" <bad>/mw setwaitlobby</bad> <mut>– eigene Wartelobby setzen"));
            s.sendMessage(Msg.mm(" <bad>/mw reload</bad> <mut>– Config & Maps neu laden"));
            s.sendMessage(Msg.mm(" <bad>/mw map</bad> <mut>– Map-Setup (Hilfe)"));
            s.sendMessage(Msg.mm(" <bad>/mw holo add <stat> | remove | list</bad> <mut>– Bestenlisten-Hologramme"));
        }
    }

    // ── Spieler ──────────────────────────────────────────────────────

    private void join(CommandSender s, String mapName, boolean spectate) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "<bad>Nur für Spieler.");
            return;
        }
        if (!p.hasPermission("missilewars.play")) {
            Msg.send(p, "<bad>Keine Berechtigung.");
            return;
        }
        if (plugin.games().get(p) != null) {
            Msg.send(p, "<bad>Du bist schon in einer Runde. <mut>/mw leave");
            return;
        }
        plugin.games().quickJoin(p, mapName, spectate);
    }

    private void leave(CommandSender s) {
        if (!(s instanceof Player p)) return;
        Game g = plugin.games().get(p);
        if (g == null) {
            Msg.send(p, "<bad>Du bist in keiner Runde.");
            return;
        }
        g.leave(p, true);
        if (!plugin.serverMode().handleLeave(p)) Msg.send(p, "Du hast die Runde verlassen.");
    }

    private void vote(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        Game g = plugin.games().get(p);
        if (g == null) {
            Msg.send(p, "<bad>Du bist in keiner Runde.");
            return;
        }
        if (args.length > 1) g.vote(p, args[1].toLowerCase(Locale.ROOT));
        else de.crafterteam.missilewars.game.VoteMenu.open(plugin, g, p);
    }

    private void team(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        Game g = plugin.games().get(p);
        if (g == null) {
            Msg.send(p, "<bad>Du bist in keiner Runde.");
            return;
        }
        Team t = args.length > 1 ? Team.parse(args[1]) : null;
        if (t == null) {
            Msg.send(p, "<bad>/mw team <grün|rot>");
            return;
        }
        g.changeTeam(p, t);
    }

    private void list(CommandSender s) {
        Msg.send(s, "<txt>Maps:");
        for (MapConfig m : plugin.maps().all()) {
            Game g = plugin.games().get(m.name());
            String status = g != null
                    ? "<ok>" + stateName(g.state()) + " <dim>(" + g.playerCount() + " Spieler)"
                    : (m.ready() && m.complete() ? "<mut>frei" : "<bad>im Setup");
            String type = m.type() == MapConfig.Type.GENERATED ? "generiert" : "Welt";
            s.sendMessage(Msg.mm(" <acc>" + m.name() + " <dim>[" + type + "] " + status));
        }
    }

    private void stats(CommandSender s, String[] args) {
        StatsManager.Entry e;
        if (args.length > 1) {
            Player online = Bukkit.getPlayerExact(args[1]);
            e = online != null ? plugin.stats().get(online.getUniqueId()) : plugin.stats().byName(args[1]);
        } else if (s instanceof Player p) {
            e = plugin.stats().get(p.getUniqueId());
        } else {
            Msg.send(s, "<bad>/mw stats <spieler>");
            return;
        }
        if (e == null) {
            Msg.send(s, "<bad>Keine Statistiken gefunden.");
            return;
        }
        int games = e.get(Stat.GAMES), wins = e.get(Stat.WINS), kills = e.get(Stat.KILLS), deaths = e.get(Stat.DEATHS);
        String kd = String.format(Locale.ROOT, "%.2f", deaths == 0 ? kills : kills / (double) deaths);
        String wr = games == 0 ? "0" : String.valueOf(Math.round(wins * 100.0 / games));
        Msg.send(s, "<txt>Statistiken von <acc>" + Msg.esc(e.name));
        s.sendMessage(Msg.mm(" <mut>Runden: <txt>" + games + " <dim>| <mut>Siege: <ok>" + wins
                + " <dim>| <mut>Niederlagen: <bad>" + e.get(Stat.LOSSES) + " <dim>(" + wr + "%)"));
        s.sendMessage(Msg.mm(" <mut>Kills: <ok>" + kills + " <dim>| <mut>Tode: <bad>" + deaths
                + " <dim>| <mut>K/D: <txt>" + kd));
        s.sendMessage(Msg.mm(" <mut>Raketen: <info>" + e.get(Stat.MISSILES)
                + " <dim>| <mut>Portale zerstört: <pri>" + e.get(Stat.PORTALS)));
    }

    private void top(CommandSender s, String[] args) {
        Stat stat = args.length > 1 ? Stat.parse(args[1]) : Stat.WINS;
        if (stat == null) {
            Msg.send(s, "<bad>/mw top <wins|kills|raketen|portale|runden|tode>");
            return;
        }
        List<StatsManager.Entry> top = plugin.stats().top(stat, 10);
        Msg.send(s, "<txt>Top 10 – <acc>" + stat.label());
        if (top.isEmpty()) {
            s.sendMessage(Msg.mm(" <mut>Noch keine Einträge."));
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            String color = i == 0 ? "<pri>" : i == 1 ? "<mut>" : i == 2 ? "<#cd7f32>" : "<dim>";
            s.sendMessage(Msg.mm(" " + color + (i + 1) + ". <txt>" + Msg.esc(top.get(i).name)
                    + " <dim>» <acc>" + top.get(i).get(stat)));
        }
    }

    private void holo(CommandSender s, String[] args) {
        if (!admin(s)) return;
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "add" -> {
                if (!(s instanceof Player p)) return;
                Stat stat = args.length > 2 ? Stat.parse(args[2]) : Stat.WINS;
                if (stat == null) { Msg.send(s, "<bad>/mw holo add <wins|kills|raketen|portale|runden|tode>"); return; }
                String id = plugin.holograms().add(p.getLocation(), stat);
                Msg.send(s, "<ok>Hologramm " + id + " (" + stat.label() + ") erstellt.");
            }
            case "remove" -> {
                if (!(s instanceof Player p)) return;
                String id = plugin.holograms().removeNear(p.getLocation(), 6);
                Msg.send(s, id == null ? "<bad>Kein Hologramm in 6 Blöcken Nähe." : "<ok>Hologramm " + id + " entfernt.");
            }
            case "list" -> {
                Msg.send(s, "<txt>Hologramme:");
                for (var h : plugin.holograms().all()) {
                    s.sendMessage(Msg.mm(" <acc>" + h.id() + " <mut>" + h.stat().label() + " <dim>@ "
                            + Msg.esc(h.world()) + " " + (int) h.x() + " " + (int) h.y() + " " + (int) h.z()));
                }
            }
            default -> Msg.send(s, "<bad>/mw holo add <stat> | remove | list");
        }
    }

    private static String stateName(Game.State st) {
        return switch (st) {
            case WAITING -> "wartet";
            case COUNTDOWN -> "startet gleich";
            case RUNNING -> "läuft";
            case ENDING -> "endet";
        };
    }

    // ── Admin ────────────────────────────────────────────────────────

    private boolean admin(CommandSender s) {
        if (s.hasPermission(ADMIN)) return true;
        Msg.send(s, "<bad>Keine Berechtigung.");
        return false;
    }

    private Game targetGame(CommandSender s, String[] args, int idx) {
        if (args.length > idx) return plugin.games().get(args[idx].toLowerCase(Locale.ROOT));
        if (s instanceof Player p) return plugin.games().get(p);
        return null;
    }

    private void start(CommandSender s, String[] args) {
        if (!admin(s)) return;
        Game g = targetGame(s, args, 1);
        if (g == null) {
            Msg.send(s, "<bad>Keine passende Runde gefunden.");
            return;
        }
        Msg.send(s, g.forceStart() ? "<ok>Runde startet in 5 Sekunden." : "<bad>Runde kann nicht gestartet werden.");
    }

    private void stop(CommandSender s, String[] args) {
        if (!admin(s)) return;
        Game g = targetGame(s, args, 1);
        if (g == null) {
            Msg.send(s, "<bad>Keine passende Runde gefunden.");
            return;
        }
        g.broadcast("<bad>Die Runde wurde von einem Admin beendet.");
        g.shutdown();
        Msg.send(s, "<ok>Runde beendet.");
    }

    private void setLobby(CommandSender s) {
        if (!admin(s) || !(s instanceof Player p)) return;
        plugin.setLobby(p.getLocation());
        Msg.send(p, "<ok>Lobby gesetzt.");
    }

    private void reload(CommandSender s) {
        if (!admin(s)) return;
        plugin.reloadAll();
        Msg.send(s, "<ok>Config & Maps neu geladen.");
    }

    // ── Map-Setup ────────────────────────────────────────────────────

    private void mapHelp(CommandSender s) {
        Msg.send(s, "<txt>Map-Setup:");
        s.sendMessage(Msg.mm(" <acc>/mw map generate <name> [abstand]</acc> <mut>– generierte Arena (keine Welt nötig)"));
        s.sendMessage(Msg.mm(" <acc>/mw map create <name> [arena|leer]</acc> <mut>– neue Welt zum Bauen"));
        s.sendMessage(Msg.mm(" <acc>/mw map import <name> <welt></acc> <mut>– vorhandene Welt nutzen"));
        s.sendMessage(Msg.mm(" <acc>/mw map setspawn <name> <grün|rot></acc> <mut>– Spawn = deine Position"));
        s.sendMessage(Msg.mm(" <acc>/mw map setportal <name> <grün|rot></acc> <mut>– Portal anschauen"));
        s.sendMessage(Msg.mm(" <acc>/mw map setdeathy <name> <y></acc> <mut>– Void-Höhe"));
        s.sendMessage(Msg.mm(" <acc>/mw map tp <name></acc> <mut>– in die Template-Welt"));
        s.sendMessage(Msg.mm(" <acc>/mw map finish <name></acc> <mut>– fertig, Welt entladen"));
        s.sendMessage(Msg.mm(" <acc>/mw map info|delete <name>"));
        s.sendMessage(Msg.mm(" <dim>Teams stehen sich auf der Z-Achse gegenüber."));
    }

    private void map(CommandSender s, String[] args) {
        if (!admin(s)) return;
        if (args.length < 3) {
            if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
                list(s);
                return;
            }
            mapHelp(s);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String name = args[2].toLowerCase(Locale.ROOT);
        MapConfig m = plugin.maps().get(name);

        switch (action) {
            case "generate" -> {
                if (m != null) { Msg.send(s, "<bad>Map existiert schon."); return; }
                if (!MapManager.validName(name)) { Msg.send(s, "<bad>Name: nur a-z, 0-9, _ und -"); return; }
                int dist = args.length > 3 ? parseInt(args[3], 60) : plugin.getConfig().getInt("generator.distance", 60);
                dist = Math.max(25, Math.min(200, dist));
                MapConfig nm = new MapConfig(name, MapConfig.Type.GENERATED);
                ArenaGenerator.applyLayout(nm, dist);
                nm.ready(true);
                plugin.maps().add(nm);
                Msg.send(s, "<ok>Generierte Map '" + name + "' angelegt <mut>(Abstand " + dist + "). /mw join " + name);
            }
            case "create" -> {
                if (!(s instanceof Player p)) return;
                if (m != null) { Msg.send(s, "<bad>Map existiert schon."); return; }
                if (!MapManager.validName(name)) { Msg.send(s, "<bad>Name: nur a-z, 0-9, _ und -"); return; }
                boolean arena = args.length <= 3 || !args[3].equalsIgnoreCase("leer");
                String worldName = MapManager.TEMPLATE_PREFIX + name;
                if (MapManager.worldFolder(worldName).exists() || Bukkit.getWorld(worldName) != null) {
                    Msg.send(s, "<bad>Welt " + worldName + " existiert schon – nimm /mw map import.");
                    return;
                }
                World w = new WorldCreator(worldName).generator(new VoidGenerator()).createWorld();
                if (w == null) { Msg.send(s, "<bad>Welt konnte nicht erstellt werden."); return; }
                MapConfig nm = new MapConfig(name, MapConfig.Type.WORLD);
                nm.templateWorld(worldName);
                if (arena) {
                    int dist = plugin.getConfig().getInt("generator.distance", 60);
                    ArenaGenerator.build(w, dist);
                    ArenaGenerator.applyLayout(nm, dist);
                } else {
                    w.getBlockAt(0, 63, 0).setType(Material.STONE);
                    nm.deathY(0);
                }
                nm.ready(false);
                plugin.maps().add(nm);
                p.teleport(arena ? nm.spawn(Team.GREEN).toLocation(w) : new Location(w, 0.5, 64, 0.5));
                p.setGameMode(GameMode.CREATIVE);
                Msg.send(p, "<ok>Template-Welt erstellt." + (arena
                        ? " <mut>Arena ist vorgebaut – Spawns & Portale sind schon gesetzt. Umbauen und dann <acc>/mw map finish " + name
                        : " <mut>Bauen, dann Spawns/Portale setzen und <acc>/mw map finish " + name));
            }
            case "import" -> {
                if (m != null) { Msg.send(s, "<bad>Map existiert schon."); return; }
                if (!MapManager.validName(name)) { Msg.send(s, "<bad>Name: nur a-z, 0-9, _ und -"); return; }
                if (args.length < 4) { Msg.send(s, "<bad>/mw map import <name> <weltordner>"); return; }
                String worldName = args[3];
                if (Bukkit.getWorlds().getFirst().getName().equals(worldName)) {
                    Msg.send(s, "<bad>Die Hauptwelt geht nicht – kopiere sie in einen eigenen Ordner.");
                    return;
                }
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    if (!MapManager.worldFolder(worldName).isDirectory()) {
                        Msg.send(s, "<bad>Weltordner '" + Msg.esc(worldName) + "' nicht gefunden.");
                        return;
                    }
                    w = new WorldCreator(worldName).generator(new VoidGenerator()).createWorld();
                }
                if (w == null) { Msg.send(s, "<bad>Welt konnte nicht geladen werden."); return; }
                MapConfig nm = new MapConfig(name, MapConfig.Type.WORLD);
                nm.templateWorld(w.getName());
                nm.deathY(w.getMinHeight() + 10);
                plugin.maps().add(nm);
                if (s instanceof Player p) {
                    p.teleport(w.getSpawnLocation());
                    p.setGameMode(GameMode.CREATIVE);
                }
                Msg.send(s, "<ok>Welt importiert. <mut>Jetzt Spawns & Portale setzen, dann /mw map finish " + name);
            }
            case "setspawn" -> {
                if (!(s instanceof Player p) || !checkWorldMap(s, m)) return;
                Team t = args.length > 3 ? Team.parse(args[3]) : null;
                if (t == null) { Msg.send(s, "<bad>/mw map setspawn " + name + " <grün|rot>"); return; }
                if (!p.getWorld().getName().equals(m.templateWorld())) {
                    Msg.send(s, "<bad>Du musst in der Template-Welt sein: /mw map tp " + name);
                    return;
                }
                Location l = p.getLocation();
                l.setX(l.getBlockX() + 0.5);
                l.setZ(l.getBlockZ() + 0.5);
                m.spawn(t, MapConfig.Spawn.of(l));
                m.ready(false);
                plugin.maps().save(m);
                Msg.send(s, "<ok>Spawn für Team " + t.colored() + " <ok>gesetzt.");
            }
            case "setportal" -> {
                if (!(s instanceof Player p) || !checkWorldMap(s, m)) return;
                Team t = args.length > 3 ? Team.parse(args[3]) : null;
                if (t == null) { Msg.send(s, "<bad>/mw map setportal " + name + " <grün|rot>"); return; }
                Block target = p.getTargetBlockExact(30);
                if (target == null || target.getType() != Material.NETHER_PORTAL) {
                    Msg.send(s, "<bad>Schau auf einen Netherportal-Block (max. 30 Blöcke).");
                    return;
                }
                MapConfig.Box box = floodPortal(target);
                if (box == null) { Msg.send(s, "<bad>Portal ist zu groß (max. 5000 Blöcke)."); return; }
                m.portal(t, box);
                m.ready(false);
                plugin.maps().save(m);
                int count = (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1) * (box.maxZ() - box.minZ() + 1);
                Msg.send(s, "<ok>Portal für Team " + t.colored() + " <ok>gesetzt <mut>(~" + count + " Blöcke).");
                if (box.maxZ() != box.minZ()) {
                    Msg.send(s, "<acc>Hinweis: Das Portal sollte quer zur Z-Achse stehen (Teams stehen sich auf Z gegenüber).");
                }
            }
            case "setdeathy" -> {
                if (!checkWorldMap(s, m)) return;
                if (args.length < 4) { Msg.send(s, "<bad>/mw map setdeathy " + name + " <y>"); return; }
                m.deathY(parseInt(args[3], m.deathY()));
                plugin.maps().save(m);
                Msg.send(s, "<ok>Void-Höhe: " + m.deathY());
            }
            case "tp" -> {
                if (!(s instanceof Player p) || !checkWorldMap(s, m)) return;
                World w = Bukkit.getWorld(m.templateWorld());
                if (w == null) {
                    if (plugin.games().get(name) != null) {
                        Msg.send(s, "<bad>Auf der Map läuft gerade eine Runde.");
                        return;
                    }
                    w = new WorldCreator(m.templateWorld()).generator(new VoidGenerator()).createWorld();
                }
                if (w == null) { Msg.send(s, "<bad>Welt konnte nicht geladen werden."); return; }
                m.ready(false);
                plugin.maps().save(m);
                MapConfig.Spawn sp = m.spawn(Team.GREEN);
                p.teleport(sp != null ? sp.toLocation(w) : w.getSpawnLocation());
                p.setGameMode(GameMode.CREATIVE);
                Msg.send(p, "<ok>Setup-Modus. <mut>Danach /mw map finish " + name);
            }
            case "finish" -> {
                if (!checkWorldMap(s, m)) return;
                if (!m.complete()) {
                    Msg.send(s, "<bad>Es fehlen noch: " + missing(m));
                    return;
                }
                World w = Bukkit.getWorld(m.templateWorld());
                if (w != null) {
                    Location out = plugin.lobby();
                    if (out == null || out.getWorld().equals(w)) out = Bukkit.getWorlds().getFirst().getSpawnLocation();
                    for (Player pl : w.getPlayers()) {
                        pl.teleport(out);
                        pl.setGameMode(Bukkit.getDefaultGameMode());
                    }
                    if (!Bukkit.unloadWorld(w, true)) {
                        Msg.send(s, "<bad>Welt konnte nicht entladen werden.");
                        return;
                    }
                }
                m.ready(true);
                plugin.maps().save(m);
                Msg.send(s, "<ok>Map '" + name + "' ist fertig! <mut>/mw join " + name);
            }
            case "info" -> {
                if (m == null) { Msg.send(s, "<bad>Map nicht gefunden."); return; }
                Msg.send(s, "<txt>Map " + name + " <dim>[" + m.type() + "]");
                if (m.type() == MapConfig.Type.WORLD) s.sendMessage(Msg.mm(" <mut>Welt: <txt>" + Msg.esc(String.valueOf(m.templateWorld()))));
                else s.sendMessage(Msg.mm(" <mut>Abstand: <txt>" + m.distance()));
                s.sendMessage(Msg.mm(" <mut>Fertig: " + (m.ready() && m.complete() ? "<ok>ja" : "<bad>nein – " + missing(m))));
                s.sendMessage(Msg.mm(" <mut>Void-Höhe: <txt>" + m.deathY()));
                for (Team t : Team.values()) {
                    MapConfig.Spawn sp = m.spawn(t);
                    MapConfig.Box b = m.portal(t);
                    s.sendMessage(Msg.mm(" " + t.colored() + "<mut>: Spawn " + (sp == null ? "<bad>fehlt" : "<txt>" + (int) sp.x() + " " + (int) sp.y() + " " + (int) sp.z())
                            + "<mut>, Portal " + (b == null ? "<bad>fehlt" : "<txt>" + b.minX() + " " + b.minY() + " " + b.minZ() + " → " + b.maxX() + " " + b.maxY() + " " + b.maxZ())));
                }
            }
            case "delete" -> {
                if (m == null) { Msg.send(s, "<bad>Map nicht gefunden."); return; }
                Game g = plugin.games().get(name);
                if (g != null) g.shutdown();
                plugin.maps().delete(m);
                Msg.send(s, "<ok>Map '" + name + "' gelöscht. <mut>(Ein Template-Weltordner bleibt erhalten.)");
            }
            default -> mapHelp(s);
        }
    }

    private boolean checkWorldMap(CommandSender s, MapConfig m) {
        if (m == null) {
            Msg.send(s, "<bad>Map nicht gefunden.");
            return false;
        }
        if (m.type() != MapConfig.Type.WORLD) {
            Msg.send(s, "<bad>Das geht nur bei Welt-Maps. Generierte Maps: /mw map create <name> arena");
            return false;
        }
        return true;
    }

    private static String missing(MapConfig m) {
        List<String> l = new ArrayList<>();
        for (Team t : Team.values()) {
            if (m.spawn(t) == null) l.add("Spawn " + t.displayName());
            if (m.portal(t) == null) l.add("Portal " + t.displayName());
        }
        if (l.isEmpty() && !m.ready()) l.add("/mw map finish");
        return String.join(", ", l);
    }

    private static MapConfig.Box floodPortal(Block start) {
        Deque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        int minX = start.getX(), minY = start.getY(), minZ = start.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        while (!queue.isEmpty()) {
            Block b = queue.poll();
            minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX());
            minY = Math.min(minY, b.getY()); maxY = Math.max(maxY, b.getY());
            minZ = Math.min(minZ, b.getZ()); maxZ = Math.max(maxZ, b.getZ());
            for (BlockFace f : faces) {
                Block n = b.getRelative(f);
                if (n.getType() == Material.NETHER_PORTAL && seen.add(n)) {
                    if (seen.size() > 5000) return null;
                    queue.add(n);
                }
            }
        }
        return new MapConfig.Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ── Tab ──────────────────────────────────────────────────────────

    public List<String> complete(CommandSender s, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission(ADMIN);
        if (args.length == 1) {
            out.addAll(List.of("join", "spectate", "leave", "team", "vote", "menu", "teleporter", "list", "stats", "top"));
            if (admin) out.addAll(List.of("start", "stop", "setlobby", "setwaitlobby", "reload", "map", "holo"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join", "spectate", "start", "stop", "vote" -> plugin.maps().all().forEach(m -> out.add(m.name()));
                case "team" -> out.addAll(List.of("gruen", "rot"));
                case "top" -> out.addAll(List.of("wins", "kills", "raketen", "portale", "runden", "tode"));
                case "stats" -> Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
                case "holo", "hologram" -> { if (admin) out.addAll(List.of("add", "remove", "list")); }
                case "map" -> {
                    if (admin) out.addAll(List.of("generate", "create", "import", "setspawn", "setportal",
                            "setdeathy", "tp", "finish", "info", "delete", "list"));
                }
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("holo") && args[1].equalsIgnoreCase("add") && admin) {
            out.addAll(List.of("wins", "kills", "raketen", "portale", "runden", "tode"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("map") && admin) {
            if (!List.of("generate", "create", "import").contains(args[1].toLowerCase(Locale.ROOT))) {
                plugin.maps().all().forEach(m -> out.add(m.name()));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("map") && admin) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "setspawn", "setportal" -> out.addAll(List.of("gruen", "rot"));
                case "create" -> out.addAll(List.of("arena", "leer"));
                case "import" -> Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
                default -> {
                }
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
