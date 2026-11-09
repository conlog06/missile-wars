package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.map.ArenaGenerator;
import de.crafterteam.missilewars.map.MapConfig;
import de.crafterteam.missilewars.map.MapManager;
import de.crafterteam.missilewars.map.VoidGenerator;
import de.crafterteam.missilewars.missile.Missile;
import de.crafterteam.missilewars.missile.MissileType;
import de.crafterteam.missilewars.util.Msg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import de.crafterteam.missilewars.stats.StatsManager.Stat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class Game {

    public enum State { WAITING, COUNTDOWN, RUNNING, ENDING }

    public static final String WORLD_PREFIX = "mw_game_";

    private final MissileWarsPlugin plugin;
    private final MapConfig map;
    private final World world;

    private State state = State.WAITING;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final List<Missile> missiles = new ArrayList<>();
    private final Map<Team, List<Block>> portalBlocks = new EnumMap<>(Team.class);
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();

    private int countdown;
    private int itemTimer;
    private int seconds;
    private long tickCounter;
    private BukkitTask secondTask;
    private BukkitTask tickTask;

    private final Scoreboard board;
    private final Objective sidebar;
    private int lastLines;

    // Rundenstatistik (für MVP & Auswertung)
    private final Map<UUID, int[]> round = new HashMap<>(); // [kills, deaths, missiles]
    private final Map<UUID, String> names = new HashMap<>();
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private boolean suddenDeath;
    private final Map<Team, Long> lastWarning = new EnumMap<>(Team.class);
    private final Map<Team, UUID> lastPortalHitter = new EnumMap<>(Team.class);
    private final Map<Team, Integer> teamPoints = new EnumMap<>(Team.class);
    private final Map<Team, Map<TeamUpgrade, Integer>> upgrades = new EnumMap<>(Team.class);
    private final Map<Team, Map<Block, BlockData>> baseGlass = new EnumMap<>(Team.class);
    private final Map<Block, Team> portalOwner = new HashMap<>();
    private final Map<Block, BlockData> portalData = new HashMap<>();
    private final Map<Team, List<Block>> destroyOrder = new EnumMap<>(Team.class);
    private final Map<UUID, Integer> roundPortalDmg = new HashMap<>();
    private final Map<UUID, Integer> roundGlass = new HashMap<>();
    private final Map<UUID, Integer> roundIntercepts = new HashMap<>();
    private final Map<Team, Integer> portalHp = new EnumMap<>(Team.class);
    private final Map<Team, Integer> lastPortalLeft = new EnumMap<>(Team.class);
    private final Map<Team, TextDisplay> portalHolograms = new EnumMap<>(Team.class);
    private final Map<UUID, String> votes = new HashMap<>();
    private boolean votingDone;

    private Game(MissileWarsPlugin plugin, MapConfig map, World world) {
        this.plugin = plugin;
        this.map = map;
        this.world = world;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.sidebar = board.registerNewObjective("mw", Criteria.DUMMY,
                Msg.mm("<logo>"));
        this.sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.sidebar.numberFormat(NumberFormat.blank());
        for (Team t : Team.values()) {
            org.bukkit.scoreboard.Team st = board.registerNewTeam("mw_" + t.name().toLowerCase());
            st.color(t.color());
            st.setAllowFriendlyFire(false);
            st.prefix(Msg.mm("<" + t.tag() + ">[" + t.displayName() + "] "));
        }
    }

    // ── Erstellen ────────────────────────────────────────────────────

    public static Game create(MissileWarsPlugin plugin, MapConfig map, int id) throws GameException {
        if (!map.complete()) throw new GameException("Map '" + map.name() + "' ist nicht fertig eingerichtet.");
        String worldName = WORLD_PREFIX + map.name() + "_" + id;
        World w;
        if (map.type() == MapConfig.Type.GENERATED) {
            w = new WorldCreator(worldName).generator(new VoidGenerator()).createWorld();
            if (w == null) throw new GameException("Welt konnte nicht erstellt werden.");
            ArenaGenerator.build(w, map.distance(), map.theme());
        } else {
            if (!map.ready()) throw new GameException("Map '" + map.name() + "' ist im Setup – erst /mw map finish " + map.name());
            if (Bukkit.getWorld(map.templateWorld()) != null)
                throw new GameException("Template-Welt ist noch geladen – /mw map finish " + map.name());
            Path src = MapManager.findWorldFolder(map);
            if (src == null) throw new GameException("Template-Ordner fehlt: " + map.templateWorld()
                    + " (gesucht in world/dimensions/minecraft/ und im Serverordner)");
            // Neues Format → Kopie als Geschwister-Dimension; altes Format → in den Serverordner (Paper migriert beim Laden)
            boolean newFormat = !src.toAbsolutePath().normalize().equals(MapManager.worldFolder(map.templateWorld()).toPath().toAbsolutePath().normalize());
            Path dst = newFormat ? src.getParent().resolve(worldName) : MapManager.worldFolder(worldName).toPath();
            try {
                deleteFolder(dst.toFile());
                copyWorld(src, dst);
            } catch (IOException e) {
                throw new GameException("Map konnte nicht kopiert werden: " + e.getMessage());
            }
            w = new WorldCreator(worldName).generator(new VoidGenerator()).createWorld();
            if (w == null) throw new GameException("Welt konnte nicht geladen werden.");
        }
        w.setAutoSave(false);
        w.setDifficulty(Difficulty.NORMAL);
        w.setTime(map.type() == MapConfig.Type.GENERATED ? map.theme().time() : 6000);
        w.setStorm(false);
        w.setThundering(false);
        MapConfig.Spawn gs = map.spawn(Team.GREEN);
        w.setSpawnLocation((int) gs.x(), (int) gs.y(), (int) gs.z());

        Game g = new Game(plugin, map, w);
        if (!g.scanPortals()) {
            g.destroyWorld();
            throw new GameException("Keine Portalblöcke in den Portal-Bereichen gefunden.");
        }
        g.spawnPortalHolograms();
        g.startTasks();
        return g;
    }

    private boolean scanPortals() {
        for (Team t : Team.values()) {
            MapConfig.Box b = map.portal(t);
            List<Block> list = new ArrayList<>();
            for (int x = b.minX(); x <= b.maxX(); x++)
                for (int y = b.minY(); y <= b.maxY(); y++)
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        Block bl = world.getBlockAt(x, y, z);
                        if (bl.getType() == Material.NETHER_PORTAL) list.add(bl);
                    }
            if (list.isEmpty()) return false;
            portalBlocks.put(t, list);
            destroyOrder.put(t, new ArrayList<>());
            for (Block bl : list) {
                portalOwner.put(bl, t);
                portalData.put(bl, bl.getBlockData().clone());
            }
            // Basis-Glas merken (für Teampunkte & Glas-Reparatur)
            Map<Block, BlockData> glass = new HashMap<>();
            boolean ax = map.axisX();
            int latMin = map.lateralMin(b) - 8, latMax = map.lateralMax(b) + 8;
            int along = (int) Math.floor(map.along(b));
            for (int a = along - 22; a <= along + 22; a++) {
                for (int l = latMin; l <= latMax; l++) {
                    for (int y = b.minY() - 5; y <= b.maxY() + 5; y++) {
                        Block bl = ax ? world.getBlockAt(a, y, l) : world.getBlockAt(l, y, a);
                        if (bl.getType().name().contains("GLASS")) glass.put(bl, bl.getBlockData().clone());
                    }
                }
            }
            baseGlass.put(t, glass);
        }
        return true;
    }

    // ── Teampunkte & Upgrades ────────────────────────────────────────

    public int teamPoints(Team t) {
        return teamPoints.getOrDefault(t, 0);
    }

    public void addTeamPoints(Team t, int n) {
        if (t != null && n > 0) teamPoints.merge(t, n, Integer::sum);
    }

    public org.bukkit.configuration.file.FileConfiguration config() {
        return plugin.getConfig();
    }

    public int upgradeLevel(Team t, TeamUpgrade u) {
        return upgrades.computeIfAbsent(t, k -> new EnumMap<>(TeamUpgrade.class)).getOrDefault(u, 0);
    }

    public boolean buyUpgrade(Player p, TeamUpgrade u) {
        Team t = team(p);
        if (t == null || state != State.RUNNING) return false;
        int lvl = upgradeLevel(t, u);
        int cost = u.nextCost(plugin.getConfig(), lvl);
        if (cost < 0) {
            Msg.send(p, u.repeatable() ? "<bad>Keine " + u.displayName() + " mehr übrig in dieser Runde." : "<ok>Schon voll ausgebaut.");
            return false;
        }
        if (teamPoints(t) < cost) {
            Msg.send(p, "<bad>Zu wenig Teampunkte (" + teamPoints(t) + "/" + cost + ").");
            return false;
        }
        teamPoints.merge(t, -cost, Integer::sum);
        upgrades.get(t).put(u, lvl + 1);
        switch (u) {
            case ARMOR -> { for (Player m : teamPlayers(t)) applyArmor(m, t); }
            case GLASS_REPAIR -> {
                // nur ein Teil, und das Glas direkt am Portal zuerst
                MapConfig.Box pb = map.portal(t);
                double along = map.along(pb);
                List<Map.Entry<Block, BlockData>> missing = new ArrayList<>();
                for (Map.Entry<Block, BlockData> e : baseGlass.get(t).entrySet()) if (e.getKey().getType().isAir()) missing.add(e);
                missing.sort(java.util.Comparator.comparingDouble(e -> Math.abs((map.axisX() ? e.getKey().getX() : e.getKey().getZ()) - along)));
                int n = (int) Math.ceil(missing.size() * u.repairPercent(plugin.getConfig()) / 100.0);
                for (int i = 0; i < n; i++) missing.get(i).getKey().setBlockData(missing.get(i).getValue(), false);
                Msg.send(p, "<ok>" + n + " von " + missing.size() + " fehlenden Glasblöcken repariert.");
            }
            case PORTAL_REPAIR -> {
                List<Block> order = destroyOrder.get(t);
                int max = Math.max(1, portalBlocks.get(t).size() * u.repairPercent(plugin.getConfig()) / 100);
                int n = 0;
                for (int i = order.size() - 1; i >= 0 && n < max; i--) {
                    Block b = order.get(i);
                    if (!b.getType().isAir()) continue;
                    b.setBlockData(portalData.get(b), false);
                    order.remove(i);
                    n++;
                }
                Msg.send(p, "<ok>" + n + " Portalblöcke repariert.");
            }
            default -> {
            }
        }
        for (Player m : teamPlayers(t)) {
            Msg.send(m, "<acc>⬆ <txt>" + Msg.esc(p.getName()) + " <mut>hat <pri>" + u.displayName()
                    + (u.repeatable() ? "" : " " + (lvl + 1)) + " <mut>gekauft.");
            m.playSound(m.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.4f);
        }
        updateBoard();
        return true;
    }

    private void applyArmor(Player p, Team t) {
        int lvl = upgradeLevel(t, TeamUpgrade.ARMOR);
        if (lvl <= 0) return;
        for (ItemStack it : p.getInventory().getArmorContents()) {
            if (it == null || it.getType().isAir()) continue;
            it.editMeta(m -> m.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, lvl, true));
        }
    }

    /** Explosion: Portal-Treffer protokollieren, Glas der Gegner-Basis → Teampunkte. */
    public void onExplosion(Player src, java.util.Collection<Block> blocks) {
        if (state != State.RUNNING) return;
        Team st = src == null ? null : team(src);
        for (Block b : blocks) {
            if (b.getType() == Material.NETHER_PORTAL) {
                Team owner = portalOwner.get(b);
                if (owner == null) continue;
                destroyOrder.get(owner).add(b);
                if (st != null && st != owner) roundPortalDmg.merge(src.getUniqueId(), 1, Integer::sum);
            } else if (st != null && b.getType().name().contains("GLASS")) {
                Team other = st.other();
                if (baseGlass.containsKey(other) && baseGlass.get(other).containsKey(b)) {
                    addTeamPoints(st, 1);
                    roundGlass.merge(src.getUniqueId(), 1, Integer::sum);
                }
            }
        }
    }

    private static void copyWorld(Path src, Path dst) throws IOException {
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                String rel = src.relativize(p).toString().replace('\\', '/');
                // Welt-IDs nicht mitkopieren (sonst "doppelte Welt"): alt uid.dat, neu data/paper/metadata.dat
                if (n.equals("uid.dat") || n.equals("session.lock") || rel.endsWith("data/paper/metadata.dat")) continue;
                Path target = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
    }

    private void startTasks() {
        secondTask = Bukkit.getScheduler().runTaskTimer(plugin, this::everySecond, 20L, 20L);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::everyTick, 1L, 1L);
        updateBoard();
    }

    // ── Getter ───────────────────────────────────────────────────────

    public MapConfig map() { return map; }
    public World world() { return world; }
    public State state() { return state; }
    public Team team(Player p) { return teams.get(p.getUniqueId()); }
    public boolean isSpectator(Player p) { return spectators.contains(p.getUniqueId()); }
    public int playerCount() { return teams.size(); }

    public boolean contains(Player p) {
        return teams.containsKey(p.getUniqueId()) || spectators.contains(p.getUniqueId());
    }

    private int min() { return Math.max(1, plugin.getConfig().getInt("players.min", 2)); }
    private int max() { return plugin.getConfig().getInt("players.max", 16); }

    private List<Player> online(Set<UUID> ids) {
        List<Player> l = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) l.add(p);
        }
        return l;
    }

    public List<Player> players() { return online(teams.keySet()); }

    public List<Player> everyone() {
        List<Player> l = players();
        l.addAll(online(spectators));
        return l;
    }

    public List<Player> teamPlayers(Team t) {
        List<Player> l = new ArrayList<>();
        for (Map.Entry<UUID, Team> e : teams.entrySet()) {
            if (e.getValue() != t) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) l.add(p);
        }
        return l;
    }

    public void broadcast(String mini) {
        Component c = Msg.prefixed(mini);
        for (Player p : everyone()) p.sendMessage(c);
    }

    // ── Beitreten / Verlassen ────────────────────────────────────────

    public void join(Player p) {
        if (state == State.ENDING) {
            Msg.send(p, "<bad>Die Runde endet gleich – versuch es in ein paar Sekunden nochmal.");
            return;
        }
        boolean running = state == State.RUNNING;
        if (running && !plugin.getConfig().getBoolean("players.allow-late-join", true)) {
            spectate(p);
            return;
        }
        if (teams.size() >= max()) {
            Msg.send(p, "<bad>Die Runde ist voll. Du schaust zu.");
            spectate(p);
            return;
        }
        snapshots.putIfAbsent(p.getUniqueId(), PlayerSnapshot.capture(p));
        Team t = smallerTeam();
        teams.put(p.getUniqueId(), t);
        plugin.games().bind(p, this);
        setBoardTeam(p, t);
        PlayerSnapshot.reset(p);
        p.setScoreboard(board);
        p.teleport(running ? spawnLoc(t) : waitSpot(t));

        names.put(p.getUniqueId(), p.getName());
        if (running) {
            p.setGameMode(GameMode.SURVIVAL);
            plugin.items().giveKit(p, t);
            applyArmor(p, t);
            applyCombat(p);
            giveCompass(p, t);
            kit(p).apply(p);
            touch(p);
            giveItem(p);
            p.showBossBar(bossBar);
            plugin.stats().add(p, Stat.GAMES, 1);
            broadcast(Msg.esc(p.getName()) + " ist Team " + t.colored() + " <mut>beigetreten.");
        } else {
            p.setGameMode(GameMode.ADVENTURE);
            giveLobbyItems(p, t);
            broadcast(Msg.esc(p.getName()) + " ist beigetreten. <dim>(" + teams.size() + "/" + max() + ")");
            p.sendMessage(Msg.prefixed("Du bist in Team " + t.colored() + "<mut>. Wechseln: ")
                    .append(Msg.mm("<ok><u>[Grün]").clickEvent(ClickEvent.runCommand("/mw team gruen")))
                    .append(Component.space())
                    .append(Msg.mm("<bad><u>[Rot]").clickEvent(ClickEvent.runCommand("/mw team rot"))));
            checkStart();
        }
        updateBoard();
    }

    public void spectate(Player p) {
        snapshots.putIfAbsent(p.getUniqueId(), PlayerSnapshot.capture(p));
        teams.remove(p.getUniqueId());
        ready.remove(p.getUniqueId());
        respawning.remove(p.getUniqueId());
        spectators.add(p.getUniqueId());
        plugin.games().bind(p, this);
        PlayerSnapshot.reset(p);
        p.setScoreboard(board);
        setBoardTeam(p, null);
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(map.spectatorSpot(world));
        plugin.items().giveSpectatorItems(p);
        Msg.send(p, "Du schaust jetzt zu. <dim>/mw leave zum Verlassen");
        updateBoard();
    }

    public void changeTeam(Player p, Team t) {
        Team cur = teams.get(p.getUniqueId());
        if (cur == null) {
            Msg.send(p, "<bad>Du bist in keinem Team.");
            return;
        }
        if (state != State.WAITING && state != State.COUNTDOWN) {
            Msg.send(p, "<bad>Teamwechsel geht nur vor dem Start.");
            return;
        }
        if (cur == t) {
            Msg.send(p, "Du bist schon in Team " + t.colored() + "<mut>.");
            return;
        }
        if (teamPlayers(t).size() > teamPlayers(cur).size() - 1) {
            Msg.send(p, "<bad>Team " + t.colored() + " <bad>hat schon genug Spieler.");
            return;
        }
        teams.put(p.getUniqueId(), t);
        setBoardTeam(p, t);
        if (!plugin.waitingLobby().enabled()) p.teleport(waitSpot(t));
        giveLobbyItems(p, t);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        Msg.send(p, "Du bist jetzt in Team " + t.colored() + "<mut>.");
        updateBoard();
    }

    /** Spieler verlässt das Spiel (Befehl, Quit, Weltwechsel). */
    public void leave(Player p, boolean teleportBack) {
        UUID id = p.getUniqueId();
        Team t = teams.remove(id);
        spectators.remove(id);
        ready.remove(id);
        respawning.remove(id);
        afkDeadline.remove(id);
        layerVotes.remove(id);
        plugin.games().unbind(p);
        plugin.preview().hide(p);
        p.hideBossBar(bossBar);
        if (t != null && state == State.RUNNING) plugin.stats().add(p, Stat.LOSSES, 1);
        for (org.bukkit.scoreboard.Team st : board.getTeams()) st.removeEntry(p.getName());
        PlayerSnapshot snap = snapshots.remove(id);
        if (snap != null && teleportBack) snap.restore(p, plugin.lobby());
        else if (snap != null) {
            // Spieler hat die Welt selbst verlassen: nur Inventar/Modus zurück, nicht teleportieren
            snap.restore(p, p.getLocation());
        }

        if (t != null && state != State.ENDING) {
            broadcast(Msg.esc(p.getName()) + " hat die Runde verlassen.");
        }
        if (teams.isEmpty() && state != State.ENDING) {
            shutdown();
            return;
        }
        if (state == State.RUNNING && t != null && teamPlayers(t).isEmpty()) {
            end(t.other(), "Team " + t.colored() + " <mut>hat keine Spieler mehr.");
            return;
        }
        if (state == State.COUNTDOWN && teams.size() < min()) {
            state = State.WAITING;
            readyTriggered = false;
            broadcast("<bad>Zu wenige Spieler – Countdown abgebrochen.");
        }
        updateBoard();
    }

    private Team smallerTeam() {
        int g = teamPlayers(Team.GREEN).size();
        int r = teamPlayers(Team.RED).size();
        if (g == r) return Math.random() < 0.5 ? Team.GREEN : Team.RED;
        return g < r ? Team.GREEN : Team.RED;
    }

    private void setBoardTeam(Player p, Team t) {
        plugin.nameTags().applyGame(board, p, t);
    }

    // ── Map-Voting ───────────────────────────────────────────────────

    public boolean votingOpen() {
        return !votingDone && (state == State.WAITING || state == State.COUNTDOWN) && plugin.getConfig().getBoolean("voting.enabled", true);
    }

    /** Maps, über die abgestimmt werden kann (fertig & ohne laufende Runde, plus die aktuelle). */
    public List<MapConfig> votableMaps() {
        List<MapConfig> l = new ArrayList<>();
        for (MapConfig m : plugin.maps().all()) {
            if (!m.ready() || !m.complete()) continue;
            Game other = plugin.games().get(m.name());
            if (other != null && other != this) continue;
            l.add(m);
        }
        return l;
    }

    public int votesFor(String mapName) {
        int n = 0;
        for (String v : votes.values()) if (v.equals(mapName)) n++;
        return n;
    }

    public void vote(Player p, String mapName) {
        if (!votingOpen()) {
            Msg.send(p, "<bad>Die Abstimmung ist vorbei.");
            return;
        }
        MapConfig m = plugin.maps().get(mapName);
        if (m == null || !votableMaps().contains(m)) {
            Msg.send(p, "<bad>Für diese Map kann nicht abgestimmt werden.");
            return;
        }
        votes.put(p.getUniqueId(), m.name());
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
        broadcast(Msg.esc(p.getName()) + " stimmt für <acc>" + m.name() + " <dim>(" + votesFor(m.name()) + " Stimmen)");
        updateBoard();
    }

    /** Team-Spawn; bei generierten Maps passend zur gewählten Glasdicke. */
    public Location spawnLoc(Team t) {
        Location l = map.spawn(t).toLocation(world);
        if (map.type() == MapConfig.Type.GENERATED && layers != ArenaGenerator.DEFAULT_LAYERS) {
            int zp = map.portal(t).minZ();
            int sgn = zp < 0 ? -1 : 1;
            l.setZ(zp - sgn * ArenaGenerator.spawnOffset(layers) + 0.5);
        }
        return l;
    }

    // ── Glasdicke-Abstimmung ─────────────────────────────────────────

    public List<Integer> layerOptions() {
        List<Integer> out = new ArrayList<>();
        if (map.type() != MapConfig.Type.GENERATED || !plugin.getConfig().getBoolean("glass-vote.enabled", true)) return out;
        for (int n : plugin.getConfig().getIntegerList("glass-vote.options")) {
            if (n >= 1 && n <= ArenaGenerator.MAX_LAYERS && ArenaGenerator.front(n) < map.distance() - 8) out.add(n);
        }
        return out;
    }

    public int layers() {
        return layers;
    }

    public int layerVotes(int n) {
        int c = 0;
        for (int v : layerVotes.values()) if (v == n) c++;
        return c;
    }

    public void voteLayers(Player p, int n) {
        if (state != State.WAITING && state != State.COUNTDOWN) {
            Msg.send(p, "<bad>Die Abstimmung ist vorbei.");
            return;
        }
        if (!layerOptions().contains(n)) return;
        layerVotes.put(p.getUniqueId(), n);
        broadcast(Msg.esc(p.getName()) + " stimmt für <acc>" + n + " Blöcke Glas <dim>(" + layerVotes(n) + " Stimmen)");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    /** Beim Start: Glasdicke auswerten, Basen bei Bedarf neu bauen. */
    private void applyLayerVote() {
        if (layerVotes.isEmpty() || layerOptions().isEmpty()) return;
        int best = layers, bestVotes = layerVotes(layers);
        for (int n : layerOptions()) {
            int v = layerVotes(n);
            if (v > bestVotes) { best = n; bestVotes = v; }
        }
        if (best == layers) return;
        layers = best;
        ArenaGenerator.rebuildBases(world, map.distance(), map.theme(), layers);
        portalBlocks.clear();
        portalOwner.clear();
        portalData.clear();
        scanPortals();
        broadcast("<acc>Abstimmung: <txt>" + layers + " Blöcke Schutzglas <mut>vor jedem Portal.");
    }

    /** Wo Spieler vor dem Start warten: Wartelobby oder Team-Spawn. */
    public Location waitSpot(Team t) {
        if (plugin.waitingLobby().enabled()) {
            Location l = plugin.waitingLobby().spawn();
            if (l != null) return l;
        }
        return spawnLoc(t == null ? Team.GREEN : t);
    }

    /** Darf ein Spieler dieser Runde in dieser Welt sein? */
    public boolean allowsWorld(World w) {
        if (w.equals(world)) return true;
        if (state == State.RUNNING) return false;
        Location l = plugin.waitingLobby().enabled() ? plugin.waitingLobby().spawn() : null;
        return l != null && l.getWorld().equals(w);
    }

    private void giveLobbyItems(Player p, Team t) {
        plugin.items().giveLobbyItems(p, t, votingOpen() && votableMaps().size() > 1, ready.contains(p.getUniqueId()));
    }

    private void resolveVote() {
        votingDone = true;
        if (votes.isEmpty()) return;
        String best = map.name();
        int bestVotes = votesFor(best);
        for (MapConfig m : votableMaps()) {
            int v = votesFor(m.name());
            if (v > bestVotes) {
                best = m.name();
                bestVotes = v;
            }
        }
        if (best.equals(map.name())) {
            broadcast("<pri>Abstimmung: <txt>" + map.name() + " <mut>bleibt. <dim>(" + bestVotes + " Stimmen)");
            return;
        }
        MapConfig target = plugin.maps().get(best);
        if (target == null || plugin.games().get(best) != null) return;
        broadcast("<pri>Abstimmung: <txt>" + best + " <mut>gewinnt mit " + bestVotes + " Stimmen – Arena wird gewechselt …");
        try {
            Game ng = plugin.games().getOrCreate(target, true);
            transferTo(ng);
        } catch (GameException e) {
            broadcast("<bad>Map-Wechsel fehlgeschlagen: " + Msg.esc(e.getMessage()));
        }
    }

    /** Alle Spieler (mit gespeicherten Inventaren) in eine neue Runde umziehen, diese Runde auflösen. */
    private void transferTo(Game ng) {
        for (Map.Entry<UUID, Team> e : new ArrayList<>(teams.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            plugin.games().unbind(p);
            p.hideBossBar(bossBar);
            ng.adopt(p, e.getValue(), snapshots.remove(e.getKey()), false);
        }
        for (UUID id : new ArrayList<>(spectators)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.games().unbind(p);
            ng.adopt(p, null, snapshots.remove(id), true);
        }
        teams.clear();
        spectators.clear();
        transferring = true;
        ng.kits.putAll(kits);
        ng.boosts.putAll(boosts);
        ng.votingDone = true;
        ng.state = State.COUNTDOWN;
        ng.countdown = Math.max(countdown, 5);
        ng.updateBoard();
        shutdown();
    }

    private void adopt(Player p, Team t, PlayerSnapshot snap, boolean spectator) {
        if (snap != null) snapshots.put(p.getUniqueId(), snap);
        names.put(p.getUniqueId(), p.getName());
        plugin.games().bind(p, this);
        p.setScoreboard(board);
        if (spectator || t == null) {
            spectators.add(p.getUniqueId());
            setBoardTeam(p, null);
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(map.spectatorSpot(world));
            return;
        }
        teams.put(p.getUniqueId(), t);
        setBoardTeam(p, t);
        if (!allowsWorld(p.getWorld())) p.teleport(waitSpot(t));
        giveLobbyItems(p, t);
    }

    // ── Ablauf ───────────────────────────────────────────────────────

    /** Bei wenigen Spielern startet die Runde nur, wenn alle "Bereit" drücken. */
    private boolean readyRequired() {
        return !forcedStart && teams.size() <= plugin.getConfig().getInt("ready.required-up-to", 2);
    }

    private void checkStart() {
        if (state == State.WAITING && teams.size() >= min() && readyRequired()) {
            if (readyTimer < 0) {
                readyTimer = 0;   // AFK-Check aktiv
                broadcast("<acc>Die Runde startet, sobald alle <ok><bold>Bereit</bold> <acc>gedrückt haben. "
                        + "<mut>AFK-Check: Wer " + plugin.getConfig().getInt("ready.timeout", 60) + " s nicht bereit ist, fliegt raus.");
            }
            return;
        }
        if (state == State.WAITING && teams.size() >= min()) {
            state = State.COUNTDOWN;
            countdown = plugin.getConfig().getInt("countdown.normal", 20);
        }
        if (state == State.COUNTDOWN && teams.size() >= max()) {
            countdown = Math.min(countdown, plugin.getConfig().getInt("countdown.full", 10));
        }
    }

    /** Admin-Start, ignoriert Mindestspielerzahl. */
    public boolean forceStart() {
        if (state != State.WAITING && state != State.COUNTDOWN) return false;
        if (teams.isEmpty()) return false;
        forcedStart = true;
        readyTimer = -1;
        state = State.COUNTDOWN;
        countdown = Math.min(countdown <= 0 ? 5 : countdown, 5);
        return true;
    }

    private void everySecond() {
        switch (state) {
            case WAITING -> {
                checkStart();
                tickReadyTimer();
            }
            case COUNTDOWN -> {
                checkReady();
                if (readyRequired() && readyCount() < teams.size()) {
                    state = State.WAITING;
                    readyTriggered = false;
                    checkStart();
                    break;
                }
                if (teams.size() < min() && countdown > 5) {
                    state = State.WAITING;
                    readyTriggered = false;
            broadcast("<bad>Zu wenige Spieler – Countdown abgebrochen.");
                    break;
                }
                if (!votingDone && countdown <= plugin.getConfig().getInt("voting.end-at", 10)) {
                    resolveVote();
                    if (shutDown) return;
                }
                if (countdown <= 0) {
                    start();
                    break;
                }
                if (countdown <= 5 || countdown % 10 == 0) {
                    for (Player p : players()) {
                        p.showTitle(Title.title(Msg.mm("<acc>" + countdown), Component.empty(),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    }
                }
                countdown--;
            }
            case RUNNING -> {
                seconds++;
                int sdAfter = plugin.getConfig().getInt("sudden-death.after-minutes", 8) * 60;
                if (!suddenDeath && sdAfter > 0 && seconds >= sdAfter) {
                    suddenDeath = true;
                    itemTimer = Math.min(itemTimer, interval());
                    broadcast("<bad><bold>SUDDEN DEATH!</bold> <bad>Items kommen jetzt doppelt so schnell.");
                    for (Player p : everyone()) {
                        p.showTitle(Title.title(Msg.mm("<bad><bold>Sudden Death"), Msg.mm("<bad>Items x2"),
                                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);
                    }
                }
                if (--itemTimer <= 0) {
                    itemTimer = interval();
                    for (Player p : players()) {
                        if (respawning.contains(p.getUniqueId())) continue;
                        giveItem(p);
                        int sup = upgradeLevel(team(p), TeamUpgrade.SUPPLY);
                        if (sup > 0 && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < sup * 20) giveItem(p);
                    }
                }
                tickAirdrops();
                tickEvents();
                if (seconds % 60 == 0) world.setTime(map.type() == MapConfig.Type.GENERATED ? map.theme().time() : 6000);
                checkAfk();
                if (state != State.RUNNING) return;
                checkTimeLimit();
                if (state != State.RUNNING) return;
                updateBossBar();
            }
            default -> {
            }
        }
        updateBoard();
        updateTab();
    }

    private void updateTab() {
        String line = switch (state) {
            case WAITING -> "<mut>Warte auf Spieler <dim>(" + teams.size() + "/" + min() + ")";
            case COUNTDOWN -> "<mut>Start in <acc>" + Math.max(0, countdown) + "s";
            case RUNNING -> "<mut>Zeit <txt>" + formatTime(seconds) + (suddenDeath ? " <bad><bold>SUDDEN DEATH" : "");
            case ENDING -> "<pri>Runde beendet";
        };
        String footer = Team.GREEN.colored() + " <txt>" + teamPlayers(Team.GREEN).size() + " <dim>vs <txt>"
                + teamPlayers(Team.RED).size() + " " + Team.RED.colored() + "\n<mut>Map: <txt>" + Msg.esc(map.name());
        for (Player p : everyone()) plugin.nameTags().header(p, line, footer);
    }

    private int interval() {
        int base = Math.max(1, plugin.getConfig().getInt("item-interval", 15));
        if (suddenDeath) base = Math.max(1, (int) Math.round(base * plugin.getConfig().getDouble("sudden-death.item-factor", 0.5)));
        if ("fast_items".equals(activeEvent)) base = Math.max(1, base / 2);
        return base;
    }

    private void updateBossBar() {
        int iv = interval();
        bossBar.progress(Math.max(0f, Math.min(1f, itemTimer / (float) iv)));
        bossBar.color(suddenDeath ? BossBar.Color.RED : BossBar.Color.YELLOW);
        String prefix = suddenDeath ? "<bad><bold>SUDDEN DEATH</bold> <dim>| " : "";
        if (activeEvent != null) prefix += "<info>⚡ " + eventName(activeEvent) + " <dim>(" + eventLeft + "s) | ";
        bossBar.name(Msg.mm(prefix + "<mut>Nächstes Item in <acc>" + itemTimer + "s"));
    }

    /** Teams vor dem Start ausgleichen (Unterschied max. 1). */
    private void autoBalance() {
        while (true) {
            List<Player> g = teamPlayers(Team.GREEN);
            List<Player> r = teamPlayers(Team.RED);
            if (Math.abs(g.size() - r.size()) <= 1) return;
            List<Player> bigger = g.size() > r.size() ? g : r;
            Team target = g.size() > r.size() ? Team.RED : Team.GREEN;
            Player moved = bigger.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(bigger.size()));
            teams.put(moved.getUniqueId(), target);
            setBoardTeam(moved, target);
            Msg.send(moved, "<acc>Teams ausgeglichen – du bist jetzt in Team " + target.colored() + "<acc>.");
        }
    }

    private void applyCombat(Player p) {
        if (plugin.getConfig().getBoolean("combat.old-pvp", false)) PlayerSnapshot.setAttackSpeed(p, 1024.0);
    }

    private int[] round(UUID id) {
        return round.computeIfAbsent(id, k -> new int[3]);
    }

    private void start() {
        applyLayerVote();
        if (plugin.getConfig().getBoolean("auto-balance", true)) autoBalance();
        state = State.RUNNING;
        seconds = 0;
        suddenDeath = false;
        itemTimer = interval();
        updateBossBar();
        int startItems = plugin.getConfig().getInt("start-items", 1);
        for (Player p : players()) {
            Team t = team(p);
            p.closeInventory();
            p.showBossBar(bossBar);
            plugin.stats().add(p, Stat.GAMES, 1);
            PlayerSnapshot.reset(p);
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(spawnLoc(t));
            plugin.items().giveKit(p, t);
            applyArmor(p, t);
            applyCombat(p);
            giveCompass(p, t);
            kit(p).apply(p);
            touch(p);
            if (kit(p).startItem() != null) p.getInventory().addItem(plugin.items().create(kit(p).startItem()));
            giveBoosts(p);
            for (int i = 0; i < startItems; i++) giveItem(p);
            p.showTitle(Title.title(Msg.mm("<ok><bold>Los!"),
                    Msg.mm("<mut>Zerstöre das Portal von Team " + t.other().colored()),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        }
        broadcast("<ok>Die Runde startet! <mut>Alle " + interval() + "s gibt es ein neues Item.");
    }

    private void giveItem(Player p) {
        String id = plugin.items().roll(p, kit(p));
        if (id == null) return;
        ItemStack it = plugin.items().create(id);
        if (it == null) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        if (left.isEmpty()) p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
    }

    private void everyTick() {
        tickCounter++;
        // Raketen
        Iterator<Missile> it = missiles.iterator();
        while (it.hasNext()) {
            Missile m = it.next();
            Missile.Result r = m.tick();
            if (r != Missile.Result.FLYING) {
                if (r == Missile.Result.EXPLODED && m.owner() != null && m.owner().isOnline()) {
                    m.owner().sendActionBar(Msg.mm("<pri>💥 " + m.type().displayName() + " eingeschlagen!"));
                }
                if (r == Missile.Result.STOPPED && m.owner() != null && m.owner().isOnline()) {
                    m.owner().sendActionBar(Msg.mm("<mut>Deine " + m.type().displayName() + " wurde gestoppt."));
                }
                if (r == Missile.Result.STOPPED && m.interceptedBy() != null) {
                    Player by = Bukkit.getPlayer(m.interceptedBy());
                    if (by != null && team(by) != null) {
                        plugin.stats().add(by, Stat.INTERCEPTS, 1);
                        roundIntercepts.merge(by.getUniqueId(), 1, Integer::sum);
                        addTeamPoints(team(by), 5);
                        plugin.shop().reward(by, "intercept", "Rakete abgefangen");
                        by.showTitle(Title.title(Component.empty(), Msg.mm("<info>✈ " + m.type().displayName() + " abgefangen!"),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
                    }
                }
                if (r == Missile.Result.EXPLODED && m.owner() != null) {
                    Team defender = map.forward(Team.GREEN) == m.dir() ? Team.RED : Team.GREEN;
                    if (Math.abs(map.along(map.portal(defender)) - m.frontAlong()) <= 10) {
                        lastPortalHitter.put(defender, m.owner().getUniqueId());
                    }
                }
                it.remove();
                continue;
            }
            if (!m.warned()) checkWarning(m);
        }
        // Void
        if (tickCounter % 5 == 0) {
            for (Player p : players()) {
                if (respawning.contains(p.getUniqueId())) continue;
                if (p.getLocation().getY() < map.deathY()) {
                    if (state == State.RUNNING) die(p, "ist ins Void gefallen", true);
                    else if (p.getWorld().equals(world)) p.teleport(waitSpot(team(p)));
                } else if (state != State.RUNNING && plugin.waitingLobby().enabled()
                        && !p.getWorld().equals(world) && p.getLocation().getY() < plugin.waitingLobby().voidY()) {
                    p.teleport(waitSpot(team(p)));
                }
            }
        }
        // Portale
        if (state == State.RUNNING && tickCounter % 10 == 0) {
            for (Team t : Team.values()) {
                int left = portalLeft(t);
                portalHp.put(t, hp(t, left));
                if (isPortalLost(t, left)) {
                    String by = "";
                    UUID hitter = lastPortalHitter.get(t);
                    Player hp = hitter == null ? null : Bukkit.getPlayer(hitter);
                    if (hp != null && team(hp) == t.other()) {
                        plugin.stats().add(hp, Stat.PORTALS, 1);
                        plugin.shop().reward(hp, "portal", "Portal zerstört");
                        by = " <dim>(von <txt>" + Msg.esc(hp.getName()) + "<dim>)";
                    }
                    updatePortalHolograms();
                    end(t.other(), "Das Portal von Team " + t.colored() + " <mut>wurde zerstört!" + by);
                    return;
                }
                Integer before = lastPortalLeft.put(t, left);
                if (before != null && left < before) {
                    for (Player pl : teamPlayers(t)) {
                        pl.showTitle(Title.title(Component.empty(), Msg.mm("<bad>Euer Portal wird getroffen! <txt>" + portalHp.get(t) + "%"),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(300))));
                        pl.playSound(pl.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
                    }
                }
            }
            updatePortalHolograms();
        }
        // Raketen-Vorschau
        if (state == State.RUNNING && tickCounter % 3 == 0) updatePreviews();
    }

    // ── Portal-HP & Hologramme ───────────────────────────────────────

    private int portalLeft(Team t) {
        int n = 0;
        for (Block b : portalBlocks.get(t)) if (b.getType() == Material.NETHER_PORTAL) n++;
        return n;
    }

    /** Verlust-Schwelle in Prozent zerstörter Blöcke; 0 = klassisch (ein Block reicht). */
    private double lossPercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getDouble("portal.loss-percent", 0)));
    }

    private boolean isPortalLost(Team t, int left) {
        int total = portalBlocks.get(t).size();
        double lp = lossPercent();
        if (lp <= 0) return left < total;
        return (total - left) * 100.0 / total >= lp;
    }

    private int hp(Team t, int left) {
        int total = portalBlocks.get(t).size();
        double lp = lossPercent();
        if (lp <= 0) return left < total ? 0 : 100;
        double destroyedPct = (total - left) * 100.0 / total;
        return (int) Math.max(0, Math.round(100 - destroyedPct * 100 / lp));
    }

    private void spawnPortalHolograms() {
        for (Team t : Team.values()) {
            MapConfig.Box b = map.portal(t);
            Location loc = new Location(world, b.centerX(), b.maxY() + 2.5, b.centerZ());
            TextDisplay td = world.spawn(loc, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setLineWidth(220);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
            });
            portalHolograms.put(t, td);
            portalHp.put(t, 100);
        }
        updatePortalHolograms();
    }

    private void updatePortalHolograms() {
        for (Team t : Team.values()) {
            TextDisplay td = portalHolograms.get(t);
            if (td == null || !td.isValid()) continue;
            int hp = portalHp.getOrDefault(t, 100);
            int filled = Math.round(hp / 5f);
            String color = hp > 60 ? "green" : hp > 25 ? "yellow" : "red";
            String bar = "<" + color + ">" + "|".repeat(filled) + "<dim>" + "|".repeat(20 - filled);
            String mode = lossPercent() <= 0 ? "<mut>1 Treffer = Niederlage" : bar + " <txt>" + hp + "%";
            td.text(Msg.mm("<bold>" + t.colored() + "</bold>\n<mut>Portal\n" + mode));
        }
    }

    // ── Raketen-Vorschau ─────────────────────────────────────────────

    /** Startblock (Heck) einer Rakete aus Blickrichtung bzw. angeklicktem Block. */
    public Block missileOrigin(Player p, Block clicked, BlockFace face) {
        Block origin;
        if (clicked != null && face != null) origin = clicked.getRelative(face);
        else {
            Block target = p.getTargetBlockExact(5);
            BlockFace f = p.getTargetBlockFace(5);
            if (target != null && f != null) origin = target.getRelative(f);
            else {
                Team t = team(p);
                int step = (t == null ? 1 : map.forward(t)) * 2;
                origin = map.axisX() ? p.getLocation().getBlock().getRelative(step, 0, 0) : p.getLocation().getBlock().getRelative(0, 0, step);
            }
        }
        // Schleichen = Rakete tiefer setzen
        if (p.isSneaking()) origin = origin.getRelative(0, -Math.max(1, plugin.getConfig().getInt("missile.sneak-offset", 5)), 0);
        return origin;
    }

    private void updatePreviews() {
        if (!plugin.getConfig().getBoolean("missile.preview", true)) return;
        for (Player p : players()) {
            MissileType type = MissileType.byId(plugin.items().idOf(p.getInventory().getItemInMainHand()));
            Team t = team(p);
            if (type == null || t == null || p.getGameMode() == GameMode.SPECTATOR) {
                plugin.preview().hide(p);
                continue;
            }
            Placement pl = placement(t, type, missileOrigin(p, null, null));
            plugin.preview().show(p, world, pl.positions(), pl.error() == null);
        }
    }

    /** Killstreaks & First Blood. */
    private void onKill(Player k) {
        if (!firstBlood) {
            firstBlood = true;
            broadcast("<bad><bold>FIRST BLOOD!</bold> <txt>" + Msg.esc(k.getName()) + " <mut>hat den ersten Kill.");
            plugin.shop().reward(k, "first-blood", "First Blood");
            for (Player p : everyone()) p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.4f);
        }
        int streak = streaks.merge(k.getUniqueId(), 1, Integer::sum);
        plugin.stats().max(k, Stat.BEST_STREAK, streak);
        if (streak >= 3 && plugin.getConfig().getBoolean("bounty.enabled", true)) {
            int amount = plugin.getConfig().getInt("bounty.base", 25) + (streak - 3) * plugin.getConfig().getInt("bounty.per-kill", 10);
            bounties.put(k.getUniqueId(), amount);
            k.setGlowing(true);
            broadcast("<acc>🎯 Kopfgeld auf <txt>" + Msg.esc(k.getName()) + "<mut>: <acc>" + amount + " Coins<mut>! (leuchtet)");
        }
        if (!plugin.getConfig().getBoolean("killstreaks.enabled", true)) return;
        String title = switch (streak) {
            case 3 -> "<acc>Killing Spree";
            case 5 -> "<pri>Rampage";
            case 7 -> "<bad>Unaufhaltsam";
            case 10 -> "<gradient:#FF2E63:#FFC145>GODLIKE</gradient>";
            default -> null;
        };
        if (title == null) return;
        broadcast("<txt>" + Msg.esc(k.getName()) + " <mut>ist auf einer <bold>" + title + "</bold> <dim>(" + streak + " Kills)");
        k.showTitle(Title.title(Msg.mm("<bold>" + title), Msg.mm("<mut>" + streak + " Kills in Folge – Bonus-Item!"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400))));
        k.playSound(k.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        plugin.shop().reward(k, "streak", "Killstreak");
        // Belohnung: Bonus-Item (ab 5er-Serie zusätzlich eine Rakete)
        giveItem(k);
        if (streak >= 5) {
            ItemStack bonus = plugin.items().create(streak >= 7 ? "juggernaut" : "tomahawk");
            if (bonus != null) k.getInventory().addItem(bonus);
        }
    }

    /** Verteidiger warnen, wenn eine Rakete nah ans eigene Portal kommt. */
    private void checkWarning(Missile m) {
        int dist = plugin.getConfig().getInt("warning.distance", 20);
        if (dist <= 0) {
            m.markWarned();
            return;
        }
        Team shooter = map.forward(Team.GREEN) == m.dir() ? Team.GREEN : Team.RED;
        Team defender = shooter.other();
        MapConfig.Box portal = map.portal(defender);
        if (Math.abs(map.along(portal) - m.frontAlong()) > dist) return;
        // nur warnen, wenn die Rakete grob aufs Portal zielt
        if (m.centerLateral() < map.lateralMin(portal) - 6 || m.centerLateral() > map.lateralMax(portal) + 7) return;
        m.markWarned();
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(defender);
        if (last != null && now - last < 2500) return; // nicht spammen
        lastWarning.put(defender, now);
        // Rechts aus Sicht des Verteidigers (Blick zum Gegner): Z-Achse → -X bei +Z; X-Achse → +Z bei +X
        int fd = map.forward(defender);
        double rel = (m.centerLateral() - map.lateralCenter(portal)) * (map.axisX() ? fd : -fd);
        String side = rel > 3 ? "rechts" : rel < -3 ? "links" : "mittig";
        for (Player p : teamPlayers(defender)) {
            p.showTitle(Title.title(Msg.mm("<bad><bold>⚠ Rakete!"),
                    Msg.mm("<mut>" + m.type().displayName() + " im Anflug <dim>(" + side + ")"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
        }
    }

    // ── Tod ──────────────────────────────────────────────────────────

    public void recordAttack(Player victim, Player attacker) {
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttackTime.put(victim.getUniqueId(), System.currentTimeMillis());
        damageLog.computeIfAbsent(victim.getUniqueId(), k -> new HashMap<>()).put(attacker.getUniqueId(), System.currentTimeMillis());
    }

    /** "Tod" ohne Todesbildschirm: Nachricht, zurück zum Spawn, heilen. */
    public void die(Player p, String reason, boolean voidDeath) {
        Team t = team(p);
        if (t == null) return;
        String killer = null;
        Player killerP = null;
        Long when = lastAttackTime.get(p.getUniqueId());
        if (when != null && System.currentTimeMillis() - when < 10_000) {
            Player k = Bukkit.getPlayer(lastAttacker.get(p.getUniqueId()));
            if (k != null && !k.equals(p) && team(k) != null) {
                killer = "<" + team(k).tag() + ">" + Msg.esc(k.getName()) + "</" + team(k).tag() + ">";
                round(k.getUniqueId())[0]++;
                plugin.stats().add(k, Stat.KILLS, 1);
                k.playSound(k.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                killerP = k;
                plugin.shop().reward(k, "kill", "Kill");
                addTeamPoints(team(k), 3);
                // Rache
                if (p.getUniqueId().equals(lastKilledBy.get(k.getUniqueId()))) {
                    lastKilledBy.remove(k.getUniqueId());
                    broadcast("<pri><bold>RACHE!</bold> <txt>" + Msg.esc(k.getName()) + " <mut>rächt sich an <txt>" + Msg.esc(p.getName()) + "<mut>.");
                    plugin.shop().reward(k, "revenge", "Rache");
                }
                lastKilledBy.put(p.getUniqueId(), k.getUniqueId());
                // Kopfgeld
                Integer bounty = bounties.remove(p.getUniqueId());
                if (bounty != null) {
                    plugin.shop().give(k, bounty, "Kopfgeld");
                    broadcast("<acc>💰 <txt>" + Msg.esc(k.getName()) + " <mut>kassiert das Kopfgeld von <acc>" + bounty
                            + " Coins <mut>auf <txt>" + Msg.esc(p.getName()) + "<mut>!");
                }
                onKill(k);
            }
        }
        // Assists: alle anderen Gegner, die in den letzten 10 s Schaden gemacht haben
        Map<UUID, Long> log = damageLog.remove(p.getUniqueId());
        if (log != null) {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> d : log.entrySet()) {
                if (now - d.getValue() > 10_000) continue;
                Player a = Bukkit.getPlayer(d.getKey());
                if (a == null || a.equals(killerP) || a.equals(p) || team(a) == null || team(a) == t) continue;
                roundAssists.merge(a.getUniqueId(), 1, Integer::sum);
                plugin.stats().add(a, Stat.ASSISTS, 1);
                plugin.shop().reward(a, "assist", "Assist");
            }
        }
        if (bounties.remove(p.getUniqueId()) != null) broadcast("<mut>Das Kopfgeld auf <txt>" + Msg.esc(p.getName()) + " <mut>verfällt.");
        p.setGlowing(false);
        lastAttacker.remove(p.getUniqueId());
        lastAttackTime.remove(p.getUniqueId());
        round(p.getUniqueId())[1]++;
        plugin.stats().add(p, Stat.DEATHS, 1);
        Integer lost = streaks.remove(p.getUniqueId());
        if (lost != null && lost >= 3) broadcast("<mut>Die Serie von <txt>" + Msg.esc(p.getName()) + " <mut>(" + lost + " Kills) ist beendet.");

        String name = "<" + t.tag() + ">" + Msg.esc(p.getName()) + "</" + t.tag() + ">";
        if (killer != null) {
            broadcast(name + " <mut>wurde von " + killer + " <mut>" + (voidDeath ? "ins Void befördert." : "getötet."));
        } else {
            broadcast(name + " <mut>" + reason + ".");
        }

        // Kill-Effekt am Todesort
        Location at = p.getLocation().add(0, 1, 0);
        plugin.shop().playKillEffect(killerP, at, t.armorColor());
        world.playSound(at, Sound.ENTITY_PLAYER_DEATH, 0.8f, 1f);

        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setVelocity(new org.bukkit.util.Vector());
        p.setHealth(PlayerSnapshot.maxHealth(p));
        p.setFoodLevel(20);
        int delay = plugin.getConfig().getInt("respawn-delay", 3);
        if (delay <= 0) {
            respawn(p);
            return;
        }
        // kurze Zuschauerphase mit Countdown
        respawning.add(p.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(spawnLoc(t).add(0, 6, 0));
        for (int i = 0; i < delay; i++) {
            final int left = delay - i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!respawning.contains(p.getUniqueId()) || !p.isOnline()) return;
                p.showTitle(Title.title(Msg.mm("<bad><bold>Gestorben"), Msg.mm("<mut>Respawn in <acc>" + left),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
            }, 20L * i);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!respawning.remove(p.getUniqueId()) || !p.isOnline() || team(p) == null || state != State.RUNNING) return;
            respawn(p);
        }, 20L * delay);
    }

    private void respawn(Player p) {
        Team t = team(p);
        if (t == null) return;
        p.setGameMode(GameMode.SURVIVAL);
        p.setFallDistance(0f);
        p.setFireTicks(0);
        p.setHealth(PlayerSnapshot.maxHealth(p));
        p.teleport(spawnLoc(t));
        p.setNoDamageTicks(40);
        kit(p).apply(p);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
    }

    public boolean isRespawning(Player p) {
        return respawning.contains(p.getUniqueId());
    }

    // ── Kits, Bereit, AFK ────────────────────────────────────────────

    public Kit kit(Player p) {
        return kits.getOrDefault(p.getUniqueId(), Kit.STANDARD);
    }

    public void setKit(Player p, Kit k) {
        if (state == State.RUNNING || state == State.ENDING) {
            Msg.send(p, "<bad>Kits wählt man vor dem Start.");
            return;
        }
        if (!plugin.shop().ownsKit(p, k)) {
            Msg.send(p, "<bad>Dieses Kit ist noch gesperrt. <mut>Freischalten im Shop für <acc>"
                    + de.crafterteam.missilewars.shop.Shop.KIT_PRICES.get(k) + " Coins<mut>.");
            return;
        }
        kits.put(p.getUniqueId(), k);
        Msg.send(p, "Kit <acc>" + k.displayName() + " <mut>ausgewählt.");
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1f, 1.2f);
    }

    public void toggleReady(Player p) {
        if (state != State.WAITING && state != State.COUNTDOWN) return;
        Team t = team(p);
        if (t == null) return;
        if (!ready.remove(p.getUniqueId())) {
            // (Bereit melden)
            ready.add(p.getUniqueId());
            broadcast("<txt>" + Msg.esc(p.getName()) + " <ok>ist bereit <dim>(" + readyCount() + "/" + teams.size() + ")");
        } else {
            broadcast("<txt>" + Msg.esc(p.getName()) + " <mut>ist nicht mehr bereit.");
        }
        giveLobbyItems(p, t);
        checkReady();
        updateBoard();
    }

    private int readyCount() {
        int n = 0;
        for (UUID id : teams.keySet()) if (ready.contains(id)) n++;
        return n;
    }

    /** Alle bereit (und genug Spieler) → Countdown auf 5 s verkürzen. */
    private void checkReady() {
        if (teams.size() < min() || readyCount() < teams.size()) return;
        if (readyTriggered) return;            // nur einmal verkürzen (vorher: Endlosschleife bei 0)
        readyTriggered = true;
        readyTimer = -1;
        if (state == State.WAITING) {
            state = State.COUNTDOWN;
            countdown = 5;
        } else if (countdown > 5) {
            countdown = 5;
        }
        broadcast("<ok>Alle sind bereit – es geht gleich los!");
    }

    /** AFK-Check vor dem Start: der Timer startet die Runde nicht, er entfernt nur, wer nicht bereit ist. */
    private void tickReadyTimer() {
        if (readyTimer < 0) return;
        if (teams.size() < min() || !readyRequired()) {
            readyTimer = -1;
            afkDeadline.clear();
            return;
        }
        long now = System.currentTimeMillis();
        long timeout = plugin.getConfig().getInt("ready.timeout", 60) * 1000L;
        String action = plugin.getConfig().getString("ready.timeout-action", "leave");
        for (Player p : new ArrayList<>(players())) {
            UUID id = p.getUniqueId();
            if (ready.contains(id)) {
                afkDeadline.remove(id);
                p.sendActionBar(Msg.mm("<ok>✔ Bereit <dim>| <mut>warte auf <txt>" + (teams.size() - readyCount()) + " <mut>Spieler"));
                continue;
            }
            long deadline = afkDeadline.computeIfAbsent(id, k -> now + timeout);
            int left = (int) Math.ceil((deadline - now) / 1000.0);
            p.sendActionBar(Msg.mm("<acc>Drück <ok><bold>Bereit</bold> <dim>(grüner Farbstoff) <dim>| <mut>AFK-Check in <bad>" + Math.max(0, left) + " s"));
            if (left == 30 || left == 10 || (left <= 5 && left > 0)) {
                p.showTitle(Title.title(Msg.mm("<acc>Bereit?"), Msg.mm("<mut>Noch <bad>" + left + " s <mut>– sonst gilt du als AFK"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200))));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.7f);
            }
            if (left > 0) continue;
            afkDeadline.remove(id);
            Msg.send(p, "<bad>Du warst AFK (nicht bereit) und wurdest aus der Runde entfernt. <mut>Mit /mw join kommst du zurück.");
            leave(p, true);
            if ("kick".equalsIgnoreCase(action)) p.kick(Msg.mm("<logo>\n<bad>AFK – du warst nicht bereit."));
            else if ("lobby-server".equalsIgnoreCase(action)) plugin.serverMode().toLobbyServer(p);
            if (shutDown) return;
        }
    }

    public boolean hasBoost(Player p, String id) {
        Set<String> b = boosts.get(p.getUniqueId());
        return b != null && b.contains(id);
    }

    public void addBoost(Player p, String id) {
        boosts.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(id);
    }

    private void giveBoosts(Player p) {
        Set<String> b = boosts.remove(p.getUniqueId());
        if (b == null) return;
        for (String id : b) {
            de.crafterteam.missilewars.shop.Shop.Boost boost = de.crafterteam.missilewars.shop.Shop.BOOSTS.get(id);
            if (boost == null) continue;
            for (int i = 0; i < boost.amount(); i++) {
                ItemStack it = plugin.items().create(boost.item());
                if (it != null) p.getInventory().addItem(it);
            }
        }
        Msg.send(p, "<ok>Deine Boosts sind im Inventar.");
    }

    public void touch(Player p) {
        lastActive.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private void checkAfk() {
        int limit = plugin.getConfig().getInt("afk-seconds", 90);
        if (limit <= 0) return;
        long now = System.currentTimeMillis();
        for (Player p : players()) {
            Long last = lastActive.get(p.getUniqueId());
            if (last == null) {
                touch(p);
                continue;
            }
            if (now - last > limit * 1000L && !respawning.contains(p.getUniqueId())) {
                broadcast("<txt>" + Msg.esc(p.getName()) + " <mut>war zu lange AFK und schaut jetzt zu.");
                Msg.send(p, "<acc>Du warst AFK. <mut>/mw leave zum Verlassen oder warte auf die nächste Runde.");
                Team t = team(p);
                spectate(p);
                if (t != null && state == State.RUNNING && teamPlayers(t).isEmpty()) {
                    end(t.other(), "Team " + t.colored() + " <mut>hat keine aktiven Spieler mehr.");
                    return;
                }
            }
        }
    }

    /** Zeitlimit: Team mit intakterem Portal gewinnt, dann mehr Kills, sonst Zufall. */
    private void checkTimeLimit() {
        int limit = plugin.getConfig().getInt("time-limit-minutes", 15);
        if (limit <= 0) return;
        int left = limit * 60 - seconds;
        if (left == 60 || left == 30 || left == 10) broadcast("<acc>Noch " + left + " Sekunden bis zum Zeitlimit!");
        if (left > 0) return;
        double g = portalLeft(Team.GREEN) / (double) portalBlocks.get(Team.GREEN).size();
        double r = portalLeft(Team.RED) / (double) portalBlocks.get(Team.RED).size();
        Team winner;
        String why;
        if (Math.abs(g - r) > 1e-9) {
            winner = g > r ? Team.GREEN : Team.RED;
            why = "intakteres Portal";
        } else {
            int kg = 0, kr = 0;
            for (Map.Entry<UUID, int[]> e : round.entrySet()) {
                Team t = teams.get(e.getKey());
                if (t == Team.GREEN) kg += e.getValue()[0];
                else if (t == Team.RED) kr += e.getValue()[0];
            }
            winner = kg == kr ? (Math.random() < 0.5 ? Team.GREEN : Team.RED) : (kg > kr ? Team.GREEN : Team.RED);
            why = kg == kr ? "Münzwurf" : "mehr Kills";
        }
        end(winner, "<acc>Zeitlimit erreicht! <mut>Entscheidung: " + why + ".");
    }

    /** Kompass zeigt auf das gegnerische Portal. */
    private void giveCompass(Player p, Team t) {
        if (!plugin.getConfig().getBoolean("team-compass", true)) return;
        MapConfig.Box b = map.portal(t.other());
        p.setCompassTarget(new Location(world, b.centerX(), b.centerY(), b.centerZ()));
        p.getInventory().setItem(7, de.crafterteam.missilewars.gui.Gui.item(Material.COMPASS,
                "<bad><bold>Gegner-Portal", "<mut>Zeigt zum Portal von Team " + t.other().colored()));
    }

    // ── Items benutzen ───────────────────────────────────────────────

    /** Berechnete Platzierung einer Rakete; error != null = nicht erlaubt. */
    public record Placement(Map<Missile.Pos, Material> parts, Map<Missile.Pos, BlockData> datas, String error) {
        public List<Missile.Pos> positions() {
            return new ArrayList<>(parts.keySet());
        }
    }

    /** Wo würde die Rakete landen, und ist das erlaubt? (für Spawn & Vorschau) */
    public Placement placement(Team t, MissileType type, Block origin) {
        int dir = map.forward(t);
        boolean ax = map.axisX();
        double mid = map.midAlong();
        double ownPortal = map.along(map.portal(t));
        boolean ownHalf = plugin.getConfig().getBoolean("missile.own-half-only", true);
        Map<Missile.Pos, Material> parts = new LinkedHashMap<>();
        Map<Missile.Pos, BlockData> datas = new LinkedHashMap<>();
        String error = null;
        for (MissileType.Part part : type.parts()) {
            // lokal (x = seitlich, z = vorne) → Welt; Drehung, keine Spiegelung
            int wx = ax ? origin.getX() + dir * part.z() : origin.getX() + dir * part.x();
            int wy = origin.getY() + part.y();
            int wz = ax ? origin.getZ() - dir * part.x() : origin.getZ() + dir * part.z();
            int wa = ax ? wx : wz;
            Missile.Pos pos = new Missile.Pos(wx, wy, wz);
            Material mat = part.material() == MissileType.TEAM_ACCENT ? t.accent() : part.material();
            parts.put(pos, mat);
            if (error != null) continue;
            if (wy < world.getMinHeight() || wy >= world.getMaxHeight()) {
                error = "Hier ist kein Platz für die Rakete.";
                continue;
            }
            Block b = world.getBlockAt(wx, wy, wz);
            if (!b.getType().isAir() && !b.isLiquid()) {
                error = "Hier ist kein Platz für die Rakete.";
            } else if (ownHalf && dir * (wa + 0.5 - mid) > 0) {
                error = "Raketen nur auf deiner Hälfte spawnen!";
            } else if (dir * (wa + 0.5 - ownPortal) <= 0) {
                error = "Nicht hinter dem eigenen Portal!";
            }
            BlockData data = mat.createBlockData();
            if (part.facing() != null && data instanceof Directional d) {
                BlockFace fwd = ax ? (dir > 0 ? BlockFace.EAST : BlockFace.WEST) : (dir > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
                BlockFace f = switch (part.facing()) {
                    case SOUTH -> fwd;
                    case NORTH -> fwd.getOppositeFace();
                    default -> part.facing();
                };
                if (d.getFaces().contains(f)) d.setFacing(f);
            }
            datas.put(pos, data);
        }
        return new Placement(parts, datas, error);
    }

    /**
     * Rakete spawnen. origin = Block, an dem das Heck mittig sitzt.
     * @return true wenn gespawnt (Item verbrauchen)
     */
    public boolean spawnMissile(Player p, MissileType type, Block origin) {
        Team t = team(p);
        if (t == null || state != State.RUNNING) return false;
        int dir = map.forward(t);
        int maxActive = plugin.getConfig().getInt("missile.max-active-per-team", 15);
        if (maxActive > 0) {
            int active = 0;
            for (Missile m : missiles) if (m.dir() == dir) active++;
            if (active >= maxActive) {
                Msg.send(p, "<bad>Dein Team hat schon " + active + " Raketen in der Luft – warte kurz.");
                return false;
            }
        }
        Placement pl = placement(t, type, origin);
        if (pl.error() != null) {
            Msg.send(p, "<bad>" + pl.error());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return false;
        }
        Map<Missile.Pos, Material> parts = pl.parts();
        for (Map.Entry<Missile.Pos, BlockData> e : pl.datas().entrySet()) {
            Missile.Pos pos = e.getKey();
            world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(e.getValue(), false);
        }
        plugin.preview().hide(p);

        double removeBeyond = map.along(map.portal(t.other())) + dir * 12;
        int speed = plugin.getConfig().getInt("missile.speed." + type.id(), 4);
        if ("turbo".equals(activeEvent)) speed = Math.max(1, speed / 2);
        speed = Math.max(1, speed - upgradeLevel(t, TeamUpgrade.TURBO));
        Missile.Settings settings = new Missile.Settings() {
            public int fuseTicks() { return plugin.getConfig().getInt("missile.fuse-ticks", 60); }
            public int maxSteps() { return plugin.getConfig().getInt("missile.max-steps", 260); }
            public int pierceGlassMax() { return plugin.getConfig().getInt("missile.pierce-glass-max", 12); }
            public double removeBeyond() { return removeBeyond; }
            public boolean pierceable(Block b) { return isPlaced(b); }
        };
        missiles.add(new Missile(world, type, map.axisX(), dir, speed, p, parts, settings, plugin.shop().trail(p)));
        plugin.shop().reward(p, "missile", "Rakete");
        round(p.getUniqueId())[2]++;
        plugin.stats().add(p, Stat.MISSILES, 1);
        world.playSound(origin.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1f, 0.7f);
        return true;
    }

    /** Rettungsplattform: 5x5 Schleim unter dem Spieler, verschwindet nach 8 s. */
    public boolean deployPlatform(Player p, Team team) {
        if (state != State.RUNNING) return false;
        Block center = p.getLocation().getBlock().getRelative(0, -2, 0);
        List<Block> placed = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                Block b = center.getRelative(dx, 0, dz);
                if (b.getType().isAir()) {
                    b.setType(Math.abs(dx) == 2 || Math.abs(dz) == 2 ? team.glass() : Material.SLIME_BLOCK, false);
                    placed.add(b);
                    this.placed.add(b);
                }
            }
        }
        if (placed.isEmpty()) {
            Msg.send(p, "<bad>Unter dir ist kein Platz.");
            return false;
        }
        p.setFallDistance(0f);
        Vector v = p.getVelocity();
        if (v.getY() < 0) p.setVelocity(v.setY(0));
        world.playSound(p.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.8f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Block b : placed) {
                this.placed.remove(b);
                if (b.getType() == Material.SLIME_BLOCK || b.getType() == team.glass()) b.setType(Material.AIR, false);
            }
        }, 160L);
        return true;
    }

    /** Glasschild senkrecht zur Flugachse aufklappen. */
    public void deployShield(Location center, Team team) {
        if (state != State.RUNNING) return;
        int r = Math.max(1, plugin.getConfig().getInt("shield.radius", 3)) + upgradeLevel(team, TeamUpgrade.SHIELDS);
        Block c = center.getBlock();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy > r * r + 1) continue;
                Block b = map.axisX() ? c.getRelative(0, dy, dx) : c.getRelative(dx, dy, 0);
                if (b.getType().isAir()) {
                    b.setType(team.glass(), false);
                    placed.add(b);
                }
            }
        }
        world.playSound(center, Sound.BLOCK_GLASS_PLACE, 1f, 1f);
    }

    // ── Spieler-Blöcke, Abfangen ─────────────────────────────────────

    /** Von Schild/Plattform gesetzt (darf abgebaut werden, im Gegensatz zum Arena-Glas). */
    public boolean isPlaced(Block b) {
        return placed.contains(b);
    }

    public void unmarkPlaced(Block b) {
        placed.remove(b);
    }

    /** Team, dem eine Rakete gehört (aus der Flugrichtung). */
    private Team missileTeam(Missile m) {
        return map.forward(Team.GREEN) == m.dir() ? Team.GREEN : Team.RED;
    }

    /** Explosion oder Abbau durch "by": trifft es den Antrieb einer gegnerischen Rakete? */
    public void creditIntercept(Player by, java.util.Collection<Block> blocks) {
        if (by == null || state != State.RUNNING) return;
        Team bt = team(by);
        if (bt == null) return;
        for (Missile m : missiles) {
            if (missileTeam(m) != bt && m.hitsEngine(blocks)) m.interceptedBy(by.getUniqueId());
        }
    }

    public void forceTeam(Player p, Team t) {
        if (!teams.containsKey(p.getUniqueId())) {
            spectators.remove(p.getUniqueId());
        }
        teams.put(p.getUniqueId(), t);
        setBoardTeam(p, t);
        if (state == State.RUNNING) {
            p.setGameMode(GameMode.SURVIVAL);
            PlayerSnapshot.reset(p);
            plugin.items().giveKit(p, t);
            applyArmor(p, t);
            giveCompass(p, t);
            p.teleport(spawnLoc(t));
        } else {
            giveLobbyItems(p, t);
        }
        Msg.send(p, "<acc>Ein Teammitglied hat dich in Team " + t.colored() + " <acc>gesetzt.");
        updateBoard();
    }

    // ── VIP-Start ────────────────────────────────────────────────────

    public void vipStart(Player p) {
        if (state != State.WAITING && state != State.COUNTDOWN) return;
        if (!p.hasPermission("missilewars.start")) {
            Msg.send(p, "<bad>Nur für VIP/Premium.");
            return;
        }
        int need = Math.max(2, plugin.getConfig().getInt("vip.start-min-players", 2));
        if (teams.size() < need) {
            Msg.send(p, "<bad>Es müssen mindestens " + need + " Spieler da sein.");
            return;
        }
        if (vipStartUsed) {
            Msg.send(p, "<bad>Der Start-Block wurde in dieser Runde schon benutzt.");
            return;
        }
        vipStartUsed = true;
        forcedStart = true;
        readyTimer = -1;
        int to = plugin.getConfig().getInt("vip.start-countdown", 10);
        state = State.COUNTDOWN;
        if (countdown <= 0 || countdown > to) countdown = to;
        broadcast("<gradient:#FFC145:#FF6B35><bold>VIP</bold></gradient> <txt>" + Msg.esc(p.getName()) + " <mut>hat die Runde gestartet! <dim>(" + countdown + " s)");
        for (Player o : everyone()) o.playSound(o.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        updateBoard();
    }

    // ── Runden-Shop ──────────────────────────────────────────────────

    public int purchasesLeft(Player p) {
        return Math.max(0, plugin.getConfig().getInt("shop.round-limit", 10) - purchases.getOrDefault(p.getUniqueId(), 0));
    }

    public void countPurchase(Player p) {
        purchases.merge(p.getUniqueId(), 1, Integer::sum);
    }

    // ── Airdrops ─────────────────────────────────────────────────────

    private void tickAirdrops() {
        int interval = plugin.getConfig().getInt("airdrop.interval", 90);
        if (interval <= 0) return;
        if (airdropTimer < 0) airdropTimer = interval;
        for (Block b : new ArrayList<>(airdrops)) {
            if (b.getType() != Material.CHEST) {
                airdrops.remove(b);
                continue;
            }
            world.spawnParticle(org.bukkit.Particle.END_ROD, b.getLocation().add(0.5, 1.4, 0.5), 3, 0.15, 0.6, 0.15, 0.01);
        }
        if (--airdropTimer > 0) return;
        airdropTimer = interval;
        spawnAirdrop(airdropCount++ % 2 == 0 ? Team.GREEN : Team.RED);
    }

    private void spawnAirdrop(Team t) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        Location spawn = spawnLoc(t);
        int lat = r.nextInt(-6, 7);
        Block col = map.axisX() ? spawn.getBlock().getRelative(0, 0, lat) : spawn.getBlock().getRelative(lat, 0, 0);
        Block target = null;
        for (int dy = 3; dy >= -6; dy--) {
            Block b = col.getRelative(0, dy, 0);
            if (b.getType().isSolid() && b.getRelative(0, 1, 0).getType().isAir() && b.getRelative(0, 2, 0).getType().isAir()) {
                target = b.getRelative(0, 1, 0);
                break;
            }
        }
        if (target == null) target = spawn.getBlock();
        final Block land = target;
        Location start = land.getLocation().add(0, 22, 0);
        org.bukkit.entity.BlockDisplay crate = world.spawn(start, org.bukkit.entity.BlockDisplay.class, d -> {
            d.setBlock(Material.CHEST.createBlockData());
            d.setPersistent(false);
            d.setGlowing(true);
            d.setGlowColorOverride(org.bukkit.Color.fromRGB(0xFFC145));
            d.setTeleportDuration(2);
        });
        broadcast("<acc>📦 Ein Airdrop fällt bei Team " + t.colored() + "<acc>! <mut>Wer ihn öffnet, kriegt die Beute.");
        for (Player p : everyone()) p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.3f, 1.6f);
        for (int i = 1; i <= 22; i++) {
            final int step = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!crate.isValid() || shutDown) return;
                Location l = start.clone().subtract(0, step, 0);
                crate.teleport(l);
                world.spawnParticle(org.bukkit.Particle.CLOUD, l.clone().add(0.5, 1.2, 0.5), 3, 0.3, 0.1, 0.3, 0.01);
                if (step == 22) {
                    crate.remove();
                    if (land.getType().isAir()) {
                        land.setType(Material.CHEST, false);
                        airdrops.add(land);
                        world.playSound(land.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
                        world.spawnParticle(org.bukkit.Particle.FIREWORK, land.getLocation().add(0.5, 0.5, 0.5), 30, 0.4, 0.4, 0.4, 0.1);
                    }
                }
            }, i * 2L);
        }
    }

    public boolean isAirdrop(Block b) {
        return airdrops.contains(b);
    }

    public void openAirdrop(Player p, Block b) {
        if (!airdrops.remove(b) || team(p) == null || state != State.RUNNING) return;
        b.setType(Material.AIR, false);
        java.util.List<String> rare = java.util.List.of("juggernaut", "hydra", "cluster", "shieldbuster", "enderpearl", "platform");
        String bonus = rare.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(rare.size()));
        ItemStack it = plugin.items().create(bonus);
        if (it != null) p.getInventory().addItem(it);
        giveItem(p);
        giveItem(p);
        plugin.stats().add(p, Stat.AIRDROPS, 1);
        addTeamPoints(team(p), 5);
        plugin.shop().reward(p, "airdrop", "Airdrop");
        world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, b.getLocation().add(0.5, 0.8, 0.5), 40, 0.4, 0.5, 0.4, 0.3);
        world.playSound(b.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        broadcast("<txt>" + Msg.esc(p.getName()) + " <mut>hat den Airdrop geschnappt!");
    }

    // ── Zufalls-Events ───────────────────────────────────────────────

    private static final java.util.List<String> EVENTS = java.util.List.of("missile_rain", "double_coins", "turbo", "low_gravity", "fast_items");

    private static String eventName(String id) {
        return switch (id) {
            case "missile_rain" -> "Raketenregen";
            case "double_coins" -> "Doppelte Coins";
            case "turbo" -> "Turbo-Raketen";
            case "low_gravity" -> "Mondschwerkraft";
            case "fast_items" -> "Item-Flut";
            default -> id;
        };
    }

    public double coinMultiplier() {
        return "double_coins".equals(activeEvent) ? 2.0 : 1.0;
    }

    private void tickEvents() {
        int interval = plugin.getConfig().getInt("events.interval", 150);
        if (interval <= 0) return;
        if (activeEvent != null && --eventLeft <= 0) {
            broadcast("<mut>Event <info>" + eventName(activeEvent) + " <mut>ist vorbei.");
            activeEvent = null;
        }
        if (eventTimer < 0) eventTimer = interval;
        if (--eventTimer > 0 || activeEvent != null) return;
        eventTimer = interval;
        String id = EVENTS.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(EVENTS.size()));
        String desc;
        switch (id) {
            case "missile_rain" -> {
                desc = "Jeder bekommt eine Rakete!";
                java.util.List<String> m = java.util.List.of("tomahawk", "lightning", "gemini", "hydra", "drill", "cluster", "arrow");
                for (Player p : players()) {
                    ItemStack it = plugin.items().create(m.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(m.size())));
                    if (it != null) p.getInventory().addItem(it);
                }
                eventLeft = 1;
            }
            case "double_coins" -> { desc = "60 s lang doppelte Coins."; eventLeft = 60; }
            case "turbo" -> { desc = "45 s lang fliegen neue Raketen doppelt so schnell."; eventLeft = 45; }
            case "low_gravity" -> {
                desc = "30 s Sprungkraft & langsames Fallen.";
                eventLeft = 30;
                for (Player p : players()) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 30 * 20, 2, false, true, true));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 30 * 20, 0, false, true, true));
                }
            }
            default -> {
                desc = "60 s lang kommen Items doppelt so schnell.";
                eventLeft = 60;
                itemTimer = Math.min(itemTimer, Math.max(1, plugin.getConfig().getInt("item-interval", 20) / 2));
            }
        }
        activeEvent = id;
        broadcast("<info><bold>⚡ EVENT:</bold> <txt>" + eventName(id) + " <dim>– <mut>" + desc);
        for (Player p : everyone()) {
            p.showTitle(Title.title(Msg.mm("<info><bold>" + eventName(id)), Msg.mm("<mut>" + desc),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.3f);
        }
    }

    // ── Ende ─────────────────────────────────────────────────────────

    public void end(Team winner, String reason) {
        if (state == State.ENDING) return;
        state = State.ENDING;
        missiles.clear();
        broadcast(reason);
        broadcast("Team " + winner.colored() + " <mut>hat gewonnen! <dim>(" + formatTime(seconds) + ")");
        for (Player p : players()) {
            plugin.stats().add(p, team(p) == winner ? Stat.WINS : Stat.LOSSES, 1);
            if (team(p) == winner) plugin.shop().reward(p, "win", "Sieg");
            else plugin.shop().reward(p, "loss", "Teilnahme");
            plugin.preview().hide(p);
            p.hideBossBar(bossBar);
        }
        sendSummary(winner);
        // Map bewerten
        if (plugin.getConfig().getBoolean("map-rating", true)) {
            Component rate = Msg.prefixed("Wie fandest du <txt>" + Msg.esc(map.name()) + "<mut>? ")
                    .append(Msg.mm("<ok><bold>[👍 Gut]").clickEvent(ClickEvent.runCommand("/mw rate gut")))
                    .append(Component.space())
                    .append(Msg.mm("<bad><bold>[👎 Schlecht]").clickEvent(ClickEvent.runCommand("/mw rate schlecht")));
            for (Player p : players()) {
                plugin.games().allowRating(p, map.name());
                p.sendMessage(rate);
            }
        }
        plugin.stats().save();
        for (Player p : everyone()) {
            boolean won = team(p) == winner;
            p.showTitle(Title.title(
                    Msg.mm(won ? "<pri><bold>Sieg!" : (team(p) == null ? winner.colored() + " gewinnt" : "<bad><bold>Niederlage")),
                    Msg.mm("<mut>Team " + winner.colored() + " <mut>gewinnt"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 0.7f, 1f);
            if (team(p) != null) p.setGameMode(GameMode.ADVENTURE);
        }
        boolean anim = plugin.getConfig().getBoolean("end-animation", true) && portalBlocks.containsKey(winner.other());
        if (anim) playDestructionAnimation(winner);
        Location fw = spawnLoc(winner).add(0, 2, 0);
        for (int i = 0; i < (anim ? 0 : 4); i++) {
            final int delay = i * 15;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (world.getPlayers().isEmpty() && delay > 0) return;
                world.spawn(fw, Firework.class, f -> {
                    FireworkMeta m = f.getFireworkMeta();
                    m.addEffect(FireworkEffect.builder().withColor(winner.armorColor())
                            .with(FireworkEffect.Type.BALL_LARGE).flicker(true).build());
                    m.setPower(1);
                    f.setFireworkMeta(m);
                });
            }, delay);
        }
        updateBoard();
        int delay = plugin.getConfig().getInt("end-delay", 8);
        if (anim) delay = Math.max(delay, 11);
        Bukkit.getScheduler().runTaskLater(plugin, this::shutdown, 20L * delay);
    }

    // ── End-Animation: so wurde das Portal zerstört ──────────────────

    private void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!shutDown) r.run();
        }, ticks);
    }

    private void playDestructionAnimation(Team winner) {
        Team loser = winner.other();
        MapConfig.Box b = map.portal(loser);
        boolean ax = map.axisX();
        int f = map.forward(loser);                    // Richtung zum Angreifer
        Location center = new Location(world, b.centerX(), b.centerY(), b.centerZ());
        Location cam = ax ? new Location(world, b.centerX() + f * 30, b.centerY() + 1, b.centerZ())
                : new Location(world, b.centerX(), b.centerY() + 1, b.centerZ() + f * 30);
        cam.setDirection(center.toVector().subtract(cam.toVector()));

        List<Block> order = new ArrayList<>(destroyOrder.getOrDefault(loser, List.of()));
        for (Block bl : portalBlocks.get(loser)) if (bl.getType() != Material.NETHER_PORTAL && !order.contains(bl)) order.add(bl);

        for (Player p : everyone()) {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(cam);
        }
        // 1) Glas zwischen Kamera und Portal zerspringt
        later(12, () -> {
            int along = (int) Math.floor(map.along(b));
            for (int a = along + f; a != along + f * 20; a += f) {
                for (int l = map.lateralMin(b) - 3; l <= map.lateralMax(b) + 3; l++) {
                    for (int y = b.minY() - 2; y <= b.maxY() + 2; y++) {
                        Block g = ax ? world.getBlockAt(a, y, l) : world.getBlockAt(l, y, a);
                        if (!g.getType().name().contains("GLASS")) continue;
                        if (Math.floorMod(l + y, 3) == 0) world.spawnParticle(org.bukkit.Particle.BLOCK,
                                g.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, g.getBlockData());
                        g.setType(Material.AIR, false);
                    }
                }
            }
            world.playSound(center, Sound.BLOCK_GLASS_BREAK, 2f, 0.8f);
        });
        // 2) Portal wird wiederhergestellt …
        later(24, () -> {
            for (Block bl : order) {
                BlockData d = portalData.get(bl);
                if (d != null) bl.setBlockData(d, false);
            }
            world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 1f, 1.4f);
            for (Player p : everyone()) {
                p.teleport(cam);
                p.showTitle(Title.title(Msg.mm("<pri><bold>Wiederholung"),
                        Msg.mm("<mut>So fiel das Portal von Team " + loser.colored()),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(300))));
            }
        });
        // 3) … und im Zeitraffer in der echten Reihenfolge zerstört
        int steps = 20;
        int chunk = Math.max(1, (int) Math.ceil(order.size() / (double) steps));
        for (int i = 0; i < steps; i++) {
            final int from = i * chunk;
            later(40 + i * 4L, () -> {
                if (from >= order.size()) return;
                List<Block> part = order.subList(from, Math.min(order.size(), from + chunk));
                for (Block bl : part) bl.setType(Material.AIR, false);
                Block first = part.getFirst();
                world.spawnParticle(org.bukkit.Particle.EXPLOSION, first.getLocation().add(0.5, 0.5, 0.5), 2, 0.6, 0.6, 0.6, 0);
                world.playSound(first.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
            });
        }
        // 4) Finale: Rest des Portals bricht zusammen
        later(130, () -> {
            for (Block bl : portalBlocks.get(loser)) {
                if (bl.getType() != Material.NETHER_PORTAL) continue;
                bl.setType(Material.AIR, false);
                if (Math.floorMod(bl.getX() + bl.getY() + bl.getZ(), 4) == 0) {
                    world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, bl.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0.05);
                }
            }
            world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, center, 3, 3, 3, 3, 0);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
            world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1.2f);
            for (Player p : everyone()) {
                boolean won = team(p) == winner;
                p.showTitle(Title.title(
                        Msg.mm(won ? "<pri><bold>Portal zerstört!" : team(p) == null ? winner.colored() + " <mut>gewinnt" : "<bad><bold>Euer Portal ist gefallen"),
                        Msg.mm("<mut>Team " + winner.colored() + " <mut>gewinnt"),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(600))));
            }
            for (int i = 0; i < 3; i++) {
                Location l = center.clone().add((i - 1) * 6, 4, 0);
                world.spawn(l, Firework.class, fw -> {
                    FireworkMeta m = fw.getFireworkMeta();
                    m.addEffect(FireworkEffect.builder().withColor(winner.armorColor())
                            .with(FireworkEffect.Type.BALL_LARGE).flicker(true).build());
                    m.setPower(0);
                    fw.setFireworkMeta(m);
                });
            }
        });
    }

    /** Rundenauswertung: Top 3 nach Kills, MVP, eigene Werte. */
    /** Leistungspunkte einer Runde. */
    private int score(UUID id, Team winner) {
        int[] r = round.getOrDefault(id, new int[3]);
        int sc = r[0] * 10 + roundAssists.getOrDefault(id, 0) * 5 + r[2] * 2
                + roundIntercepts.getOrDefault(id, 0) * 6 + roundPortalDmg.getOrDefault(id, 0)
                + roundGlass.getOrDefault(id, 0) / 3 - r[1] * 3;
        if (winner != null && teams.get(id) == winner) sc += 15;
        return Math.max(0, sc);
    }

    private static String grade(int score) {
        if (score >= 90) return "<gradient:#FF2E63:#FFC145><bold>S</bold></gradient>";
        if (score >= 60) return "<ok><bold>A</bold>";
        if (score >= 38) return "<info><bold>B</bold>";
        if (score >= 20) return "<acc><bold>C</bold>";
        return "<bad><bold>D</bold>";
    }

    /** Rundenauswertung mit Leistungsbewertung (S/A/B/C/D) und MVP. */
    private void sendSummary(Team winner) {
        List<UUID> ids = new ArrayList<>(round.keySet());
        for (UUID id : roundAssists.keySet()) if (!ids.contains(id)) ids.add(id);
        ids.sort((x, y) -> Integer.compare(score(y, winner), score(x, winner)));
        List<Component> lines = new ArrayList<>();
        lines.add(Msg.mm("<dim><st>                                             "));
        lines.add(Msg.mm("  <pri><bold>Rundenauswertung</bold> <dim>– " + formatTime(seconds) + " – " + Msg.esc(map.name())));
        if (!ids.isEmpty()) {
            UUID mvp = ids.getFirst();
            lines.add(Msg.mm("  <acc>★ MVP: <txt>" + Msg.esc(names.getOrDefault(mvp, "?")) + " <dim>(" + score(mvp, winner) + " Punkte)"));
            String[] medals = {"<pri>1.", "<mut>2.", "<#cd7f32>3."};
            for (int i = 0; i < Math.min(3, ids.size()); i++) {
                UUID id = ids.get(i);
                int[] r = round.getOrDefault(id, new int[3]);
                lines.add(Msg.mm("  " + medals[i] + " " + grade(score(id, winner)) + " <txt>" + Msg.esc(names.getOrDefault(id, "?"))
                        + " <dim>» <ok>" + r[0] + "K <acc>" + roundAssists.getOrDefault(id, 0) + "A <bad>" + r[1] + "T <info>"
                        + r[2] + "R <dim>· <pri>" + roundPortalDmg.getOrDefault(id, 0) + " Portal"));
            }
        }
        lines.add(Msg.mm("<dim><st>                                             "));
        for (Player p : everyone()) {
            for (Component c : lines) p.sendMessage(c);
            UUID id = p.getUniqueId();
            if (!round.containsKey(id) && !roundAssists.containsKey(id)) continue;
            int sc = score(id, winner);
            int[] r = round.getOrDefault(id, new int[3]);
            p.sendMessage(Msg.mm("  <mut>Deine Bewertung: " + grade(sc) + " <dim>(" + sc + " Punkte)"));
            p.sendMessage(Msg.mm("  <dim>" + r[0] + " Kills · " + roundAssists.getOrDefault(id, 0) + " Assists · " + r[1] + " Tode · "
                    + r[2] + " Raketen · " + roundIntercepts.getOrDefault(id, 0) + " abgefangen · "
                    + roundPortalDmg.getOrDefault(id, 0) + " Portalblöcke · " + roundGlass.getOrDefault(id, 0) + " Glas"));
        }
    }

    private boolean shutDown;
    private boolean transferring;
    private boolean firstBlood;
    private final Map<UUID, Kit> kits = new HashMap<>();
    private final Set<UUID> ready = new HashSet<>();
    private boolean readyTriggered;
    private boolean forcedStart;
    private int readyTimer = -1;
    private int layers = ArenaGenerator.DEFAULT_LAYERS;
    private final Map<UUID, Integer> layerVotes = new HashMap<>();
    private final Map<UUID, Long> afkDeadline = new HashMap<>();
    private final Set<UUID> respawning = new HashSet<>();
    private final Map<UUID, Set<String>> boosts = new HashMap<>();
    // 2.3: Shop, Airdrops, Events, Kopfgeld, Assists, Rache, Abfangen, VIP
    private final Set<Block> placed = new HashSet<>();
    private final Set<Block> airdrops = new HashSet<>();
    private final Map<UUID, Integer> bounties = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> damageLog = new HashMap<>();
    private final Map<UUID, UUID> lastKilledBy = new HashMap<>();
    private final Map<UUID, Integer> roundAssists = new HashMap<>();
    private final Map<UUID, Integer> purchases = new HashMap<>();
    private int airdropTimer = -1, airdropCount;
    private int eventTimer = -1, eventLeft;
    private String activeEvent;
    private boolean vipStartUsed;
    private final Map<UUID, Long> lastActive = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();

    /** Alle raus, Welt löschen. */
    public void shutdown() {
        if (shutDown) return;
        shutDown = true;
        state = State.ENDING;
        if (secondTask != null) secondTask.cancel();
        if (tickTask != null) tickTask.cancel();
        missiles.clear();
        for (Player p : new ArrayList<>(everyone())) {
            UUID id = p.getUniqueId();
            teams.remove(id);
            spectators.remove(id);
            plugin.games().unbind(p);
            plugin.preview().hide(p);
            p.hideBossBar(bossBar);
            PlayerSnapshot snap = snapshots.remove(id);
            if (snap != null) snap.restore(p, plugin.lobby());
            if (!transferring) plugin.serverMode().afterGame(p);
        }
        teams.clear();
        spectators.clear();
        plugin.games().remove(this);
        destroyWorld();
    }

    private void destroyWorld() {
        Location fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (Player p : world.getPlayers()) p.teleport(fallback);
        File folder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Welt " + world.getName() + " konnte nicht entladen werden.");
            return;
        }
        if (!plugin.isEnabled()) {
            deleteFolder(folder);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteFolder(folder));
    }

    public static void deleteFolder(File folder) {
        if (folder == null || !folder.exists()) return;
        try (Stream<Path> s = Files.walk(folder.toPath())) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    // ── Scoreboard ───────────────────────────────────────────────────

    private void updateBoard() {
        List<String> lines = new ArrayList<>();
        lines.add("<dim>━━━━━━━━━━━━━━━━━━");
        lines.add("<mut>Map <txt><bold>" + Msg.esc(map.name()) + "</bold> <dim>" + map.describe());
        switch (state) {
            case WAITING -> lines.add("<mut>Status <acc>Warte auf Spieler <dim>(" + teams.size() + "/" + min() + ")");
            case COUNTDOWN -> lines.add("<mut>Status <ok>Start in " + Math.max(0, countdown) + " s");
            case RUNNING -> lines.add("<mut>Zeit <txt><bold>" + formatTime(seconds) + "</bold>" + (suddenDeath ? " <bad>SUDDEN DEATH" : ""));
            case ENDING -> lines.add("<mut>Status <pri>Runde beendet");
        }
        if (state == State.WAITING || state == State.COUNTDOWN) {
            lines.add("");
            lines.add("<mut>Bereit <ok>" + readyCount() + "<dim>/" + teams.size() + "");
            if (votingOpen() && !votes.isEmpty()) {
                String lead = map.name();
                int lv = votesFor(lead);
                for (MapConfig m : votableMaps()) if (votesFor(m.name()) > lv) { lead = m.name(); lv = votesFor(lead); }
                lines.add("<mut>Abstimmung <acc>" + lead + " <dim>(" + lv + ")");
            }
        }
        lines.add(" ");
        lines.add("<tgreen>■ <txt>" + teamPlayers(Team.GREEN).size() + " <mut>Spieler <dim>vs <txt>"
                + teamPlayers(Team.RED).size() + " <mut>Spieler <tred>■");
        if (state == State.RUNNING || state == State.ENDING) {
            lines.add("  ");
            lines.add("<mut>Portale");
            for (Team t : Team.values()) {
                int hp = portalHp.getOrDefault(t, 100);
                String col = hp > 60 ? "ok" : hp > 25 ? "acc" : "bad";
                String sq = "<" + t.tag() + ">■ ";
                if (lossPercent() > 0) lines.add(sq + Msg.bar(hp / 100.0, 12, col) + " <" + col + ">" + hp + "%");
                else lines.add(sq + "<ok>intakt");
            }
            lines.add("   ");
            lines.add("<mut>Teampunkte <tgreen>" + teamPoints.getOrDefault(Team.GREEN, 0)
                    + " <dim>· <tred>" + teamPoints.getOrDefault(Team.RED, 0));
            lines.add("<mut>Nächstes Item <acc>" + itemTimer + " s");
            if (activeEvent != null) lines.add("<info>⚡ " + eventName(activeEvent) + " <dim>" + eventLeft + " s");
        }
        lines.add("<dim>━━━━━━━━━━━━━━━━━━ ");
        String server = plugin.getConfig().getString("tablist.server-name", "");
        if (server != null && !server.isBlank()) lines.add("<pri>" + Msg.esc(server));
        for (int i = 0; i < lines.size(); i++) {
            Score s = sidebar.getScore("l" + i);
            s.setScore(lines.size() - i);
            s.customName(Msg.mm(lines.get(i).isBlank() ? "<reset>" : lines.get(i)));
        }
        for (int i = lines.size(); i < lastLines; i++) board.resetScores("l" + i);
        lastLines = lines.size();
    }

    private static String formatTime(int s) {
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
