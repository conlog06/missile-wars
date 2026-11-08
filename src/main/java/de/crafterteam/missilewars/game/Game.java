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
                Msg.mm("<red><bold>Missile</bold><white><bold>Wars"));
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
            ArenaGenerator.build(w, map.distance());
        } else {
            if (!map.ready()) throw new GameException("Map '" + map.name() + "' ist im Setup – erst /mw map finish " + map.name());
            if (Bukkit.getWorld(map.templateWorld()) != null)
                throw new GameException("Template-Welt ist noch geladen – /mw map finish " + map.name());
            File src = MapManager.worldFolder(map.templateWorld());
            if (!src.isDirectory()) throw new GameException("Template-Ordner fehlt: " + src.getName());
            try {
                copyWorld(src.toPath(), MapManager.worldFolder(worldName).toPath());
            } catch (IOException e) {
                throw new GameException("Map konnte nicht kopiert werden: " + e.getMessage());
            }
            w = new WorldCreator(worldName).generator(new VoidGenerator()).createWorld();
            if (w == null) throw new GameException("Welt konnte nicht geladen werden.");
        }
        w.setAutoSave(false);
        w.setDifficulty(Difficulty.NORMAL);
        w.setTime(6000);
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
        }
        return true;
    }

    private static void copyWorld(Path src, Path dst) throws IOException {
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (n.equals("uid.dat") || n.equals("session.lock")) continue;
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
            Msg.send(p, "<red>Die Runde endet gleich – versuch es in ein paar Sekunden nochmal.");
            return;
        }
        boolean running = state == State.RUNNING;
        if (running && !plugin.getConfig().getBoolean("players.allow-late-join", true)) {
            spectate(p);
            return;
        }
        if (teams.size() >= max()) {
            Msg.send(p, "<red>Die Runde ist voll. Du schaust zu.");
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
        p.teleport(map.spawn(t).toLocation(world));

        names.put(p.getUniqueId(), p.getName());
        if (running) {
            p.setGameMode(GameMode.SURVIVAL);
            plugin.items().giveKit(p, t);
            applyCombat(p);
            giveItem(p);
            p.showBossBar(bossBar);
            plugin.stats().add(p, Stat.GAMES, 1);
            broadcast(Msg.esc(p.getName()) + " ist Team " + t.colored() + " <gray>beigetreten.");
        } else {
            p.setGameMode(GameMode.ADVENTURE);
            giveLobbyItems(p, t);
            broadcast(Msg.esc(p.getName()) + " ist beigetreten. <dark_gray>(" + teams.size() + "/" + max() + ")");
            p.sendMessage(Msg.prefixed("Du bist in Team " + t.colored() + "<gray>. Wechseln: ")
                    .append(Msg.mm("<green><u>[Grün]").clickEvent(ClickEvent.runCommand("/mw team gruen")))
                    .append(Component.space())
                    .append(Msg.mm("<red><u>[Rot]").clickEvent(ClickEvent.runCommand("/mw team rot"))));
            checkStart();
        }
        updateBoard();
    }

    public void spectate(Player p) {
        snapshots.putIfAbsent(p.getUniqueId(), PlayerSnapshot.capture(p));
        teams.remove(p.getUniqueId());
        spectators.add(p.getUniqueId());
        plugin.games().bind(p, this);
        PlayerSnapshot.reset(p);
        p.setScoreboard(board);
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(map.spectatorSpot(world));
        Msg.send(p, "Du schaust jetzt zu. <dark_gray>/mw leave zum Verlassen");
        updateBoard();
    }

    public void changeTeam(Player p, Team t) {
        Team cur = teams.get(p.getUniqueId());
        if (cur == null) {
            Msg.send(p, "<red>Du bist in keinem Team.");
            return;
        }
        if (state != State.WAITING && state != State.COUNTDOWN) {
            Msg.send(p, "<red>Teamwechsel geht nur vor dem Start.");
            return;
        }
        if (cur == t) {
            Msg.send(p, "Du bist schon in Team " + t.colored() + "<gray>.");
            return;
        }
        if (teamPlayers(t).size() > teamPlayers(cur).size() - 1) {
            Msg.send(p, "<red>Team " + t.colored() + " <red>hat schon genug Spieler.");
            return;
        }
        teams.put(p.getUniqueId(), t);
        setBoardTeam(p, t);
        p.teleport(map.spawn(t).toLocation(world));
        giveLobbyItems(p, t);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        Msg.send(p, "Du bist jetzt in Team " + t.colored() + "<gray>.");
        updateBoard();
    }

    /** Spieler verlässt das Spiel (Befehl, Quit, Weltwechsel). */
    public void leave(Player p, boolean teleportBack) {
        UUID id = p.getUniqueId();
        Team t = teams.remove(id);
        spectators.remove(id);
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
            end(t.other(), "Team " + t.colored() + " <gray>hat keine Spieler mehr.");
            return;
        }
        if (state == State.COUNTDOWN && teams.size() < min()) {
            state = State.WAITING;
            broadcast("<red>Zu wenige Spieler – Countdown abgebrochen.");
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
        for (org.bukkit.scoreboard.Team st : board.getTeams()) st.removeEntry(p.getName());
        org.bukkit.scoreboard.Team st = board.getTeam("mw_" + t.name().toLowerCase());
        if (st != null) st.addEntry(p.getName());
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
            Msg.send(p, "<red>Die Abstimmung ist vorbei.");
            return;
        }
        MapConfig m = plugin.maps().get(mapName);
        if (m == null || !votableMaps().contains(m)) {
            Msg.send(p, "<red>Für diese Map kann nicht abgestimmt werden.");
            return;
        }
        votes.put(p.getUniqueId(), m.name());
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
        broadcast(Msg.esc(p.getName()) + " stimmt für <yellow>" + m.name() + " <dark_gray>(" + votesFor(m.name()) + " Stimmen)");
        updateBoard();
    }

    private void giveLobbyItems(Player p, Team t) {
        plugin.items().giveLobbyItems(p, t, votingOpen() && votableMaps().size() > 1);
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
            broadcast("<gold>Abstimmung: <white>" + map.name() + " <gray>bleibt. <dark_gray>(" + bestVotes + " Stimmen)");
            return;
        }
        MapConfig target = plugin.maps().get(best);
        if (target == null || plugin.games().get(best) != null) return;
        broadcast("<gold>Abstimmung: <white>" + best + " <gray>gewinnt mit " + bestVotes + " Stimmen – Arena wird gewechselt …");
        try {
            Game ng = plugin.games().getOrCreate(target);
            transferTo(ng);
        } catch (GameException e) {
            broadcast("<red>Map-Wechsel fehlgeschlagen: " + Msg.esc(e.getMessage()));
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
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(map.spectatorSpot(world));
            return;
        }
        teams.put(p.getUniqueId(), t);
        setBoardTeam(p, t);
        p.teleport(map.spawn(t).toLocation(world));
        giveLobbyItems(p, t);
    }

    // ── Ablauf ───────────────────────────────────────────────────────

    private void checkStart() {
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
        state = State.COUNTDOWN;
        countdown = Math.min(countdown <= 0 ? 5 : countdown, 5);
        return true;
    }

    private void everySecond() {
        switch (state) {
            case COUNTDOWN -> {
                if (teams.size() < min() && countdown > 5) {
                    state = State.WAITING;
                    broadcast("<red>Zu wenige Spieler – Countdown abgebrochen.");
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
                        p.showTitle(Title.title(Msg.mm("<yellow>" + countdown), Component.empty(),
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
                    broadcast("<dark_red><bold>SUDDEN DEATH!</bold> <red>Items kommen jetzt doppelt so schnell.");
                    for (Player p : everyone()) {
                        p.showTitle(Title.title(Msg.mm("<dark_red><bold>Sudden Death"), Msg.mm("<red>Items x2"),
                                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);
                    }
                }
                if (--itemTimer <= 0) {
                    itemTimer = interval();
                    for (Player p : players()) giveItem(p);
                }
                updateBossBar();
            }
            default -> {
            }
        }
        updateBoard();
    }

    private int interval() {
        int base = Math.max(1, plugin.getConfig().getInt("item-interval", 15));
        if (suddenDeath) base = Math.max(1, (int) Math.round(base * plugin.getConfig().getDouble("sudden-death.item-factor", 0.5)));
        return base;
    }

    private void updateBossBar() {
        int iv = interval();
        bossBar.progress(Math.max(0f, Math.min(1f, itemTimer / (float) iv)));
        bossBar.color(suddenDeath ? BossBar.Color.RED : BossBar.Color.YELLOW);
        String prefix = suddenDeath ? "<dark_red><bold>SUDDEN DEATH</bold> <dark_gray>| " : "";
        bossBar.name(Msg.mm(prefix + "<gray>Nächstes Item in <yellow>" + itemTimer + "s"));
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
            Msg.send(moved, "<yellow>Teams ausgeglichen – du bist jetzt in Team " + target.colored() + "<yellow>.");
        }
    }

    private void applyCombat(Player p) {
        if (plugin.getConfig().getBoolean("combat.old-pvp", false)) PlayerSnapshot.setAttackSpeed(p, 1024.0);
    }

    private int[] round(UUID id) {
        return round.computeIfAbsent(id, k -> new int[3]);
    }

    private void start() {
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
            p.teleport(map.spawn(t).toLocation(world));
            plugin.items().giveKit(p, t);
            applyCombat(p);
            for (int i = 0; i < startItems; i++) giveItem(p);
            p.showTitle(Title.title(Msg.mm("<green><bold>Los!"),
                    Msg.mm("<gray>Zerstöre das Portal von Team " + t.other().colored()),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        }
        broadcast("<green>Die Runde startet! <gray>Alle " + interval() + "s gibt es ein neues Item.");
    }

    private void giveItem(Player p) {
        String id = plugin.items().roll(p);
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
                    m.owner().sendActionBar(Msg.mm("<gold>💥 " + m.type().displayName() + " eingeschlagen!"));
                }
                if (r == Missile.Result.STOPPED && m.owner() != null && m.owner().isOnline()) {
                    m.owner().sendActionBar(Msg.mm("<gray>Deine " + m.type().displayName() + " wurde gestoppt."));
                }
                if (r == Missile.Result.EXPLODED && m.owner() != null) {
                    Team defender = map.forward(Team.GREEN) == m.dir() ? Team.RED : Team.GREEN;
                    if (Math.abs(map.portal(defender).centerZ() - m.frontZ()) <= 10) {
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
                if (p.getLocation().getY() < map.deathY()) {
                    if (state == State.RUNNING) die(p, "ist ins Void gefallen", true);
                    else p.teleport(map.spawn(team(p)).toLocation(world));
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
                        by = " <dark_gray>(von <white>" + Msg.esc(hp.getName()) + "<dark_gray>)";
                    }
                    updatePortalHolograms();
                    end(t.other(), "Das Portal von Team " + t.colored() + " <gray>wurde zerstört!" + by);
                    return;
                }
                Integer before = lastPortalLeft.put(t, left);
                if (before != null && left < before) {
                    for (Player pl : teamPlayers(t)) {
                        pl.showTitle(Title.title(Component.empty(), Msg.mm("<red>Euer Portal wird getroffen! <white>" + portalHp.get(t) + "%"),
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
            String bar = "<" + color + ">" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(20 - filled);
            String mode = lossPercent() <= 0 ? "<gray>1 Treffer = Niederlage" : bar + " <white>" + hp + "%";
            td.text(Msg.mm("<bold>" + t.colored() + "</bold>\n<gray>Portal\n" + mode));
        }
    }

    // ── Raketen-Vorschau ─────────────────────────────────────────────

    /** Startblock (Heck) einer Rakete aus Blickrichtung bzw. angeklicktem Block. */
    public Block missileOrigin(Player p, Block clicked, BlockFace face) {
        if (clicked != null && face != null) return clicked.getRelative(face);
        Block target = p.getTargetBlockExact(5);
        BlockFace f = p.getTargetBlockFace(5);
        if (target != null && f != null) return target.getRelative(f);
        Team t = team(p);
        return p.getLocation().getBlock().getRelative(0, 0, (t == null ? 1 : map.forward(t)) * 2);
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
        if (Math.abs(portal.centerZ() - m.frontZ()) > dist) return;
        // nur warnen, wenn die Rakete grob aufs Portal zielt
        if (m.centerX() < portal.minX() - 6 || m.centerX() > portal.maxX() + 7) return;
        m.markWarned();
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(defender);
        if (last != null && now - last < 2500) return; // nicht spammen
        lastWarning.put(defender, now);
        // aus Sicht des Verteidigers (Blick zum Gegner): rechts = -X bei Blick +Z, +X bei Blick -Z
        double rel = (m.centerX() - portal.centerX()) * -map.forward(defender);
        String side = rel > 3 ? "rechts" : rel < -3 ? "links" : "mittig";
        for (Player p : teamPlayers(defender)) {
            p.showTitle(Title.title(Msg.mm("<red><bold>⚠ Rakete!"),
                    Msg.mm("<gray>" + m.type().displayName() + " im Anflug <dark_gray>(" + side + ")"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
        }
    }

    // ── Tod ──────────────────────────────────────────────────────────

    public void recordAttack(Player victim, Player attacker) {
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttackTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    /** "Tod" ohne Todesbildschirm: Nachricht, zurück zum Spawn, heilen. */
    public void die(Player p, String reason, boolean voidDeath) {
        Team t = team(p);
        if (t == null) return;
        String killer = null;
        Long when = lastAttackTime.get(p.getUniqueId());
        if (when != null && System.currentTimeMillis() - when < 10_000) {
            Player k = Bukkit.getPlayer(lastAttacker.get(p.getUniqueId()));
            if (k != null && !k.equals(p) && team(k) != null) {
                killer = "<" + team(k).tag() + ">" + Msg.esc(k.getName()) + "</" + team(k).tag() + ">";
                round(k.getUniqueId())[0]++;
                plugin.stats().add(k, Stat.KILLS, 1);
                k.playSound(k.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            }
        }
        lastAttacker.remove(p.getUniqueId());
        lastAttackTime.remove(p.getUniqueId());
        round(p.getUniqueId())[1]++;
        plugin.stats().add(p, Stat.DEATHS, 1);

        String name = "<" + t.tag() + ">" + Msg.esc(p.getName()) + "</" + t.tag() + ">";
        if (killer != null) {
            broadcast(name + " <gray>wurde von " + killer + " <gray>" + (voidDeath ? "ins Void befördert." : "getötet."));
        } else {
            broadcast(name + " <gray>" + reason + ".");
        }

        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setVelocity(new org.bukkit.util.Vector());
        p.setHealth(PlayerSnapshot.maxHealth(p));
        p.setFoodLevel(20);
        p.teleport(map.spawn(t).toLocation(world));
        p.setNoDamageTicks(40);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
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
        double mid = map.midZ();
        double ownPortalZ = map.portal(t).centerZ();
        boolean ownHalf = plugin.getConfig().getBoolean("missile.own-half-only", true);
        Map<Missile.Pos, Material> parts = new LinkedHashMap<>();
        Map<Missile.Pos, BlockData> datas = new LinkedHashMap<>();
        String error = null;
        for (MissileType.Part part : type.parts()) {
            int wx = origin.getX() + (dir > 0 ? part.x() : -part.x());
            int wy = origin.getY() + part.y();
            int wz = origin.getZ() + dir * part.z();
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
            } else if (ownHalf && dir * (wz + 0.5 - mid) > 0) {
                error = "Raketen nur auf deiner Hälfte spawnen!";
            } else if (dir * (wz + 0.5 - ownPortalZ) <= 0) {
                error = "Nicht hinter dem eigenen Portal!";
            }
            BlockData data = mat.createBlockData();
            if (part.facing() != null && data instanceof Directional d) {
                BlockFace f = part.facing();
                if (dir < 0 && f != BlockFace.UP && f != BlockFace.DOWN) f = f.getOppositeFace();
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
                Msg.send(p, "<red>Dein Team hat schon " + active + " Raketen in der Luft – warte kurz.");
                return false;
            }
        }
        Placement pl = placement(t, type, origin);
        if (pl.error() != null) {
            Msg.send(p, "<red>" + pl.error());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return false;
        }
        Map<Missile.Pos, Material> parts = pl.parts();
        for (Map.Entry<Missile.Pos, BlockData> e : pl.datas().entrySet()) {
            Missile.Pos pos = e.getKey();
            world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(e.getValue(), false);
        }
        plugin.preview().hide(p);

        double enemyPortalZ = map.portal(t.other()).centerZ();
        double removeBeyond = enemyPortalZ + dir * 12;
        int speed = plugin.getConfig().getInt("missile.speed." + type.id(), 4);
        Missile.Settings settings = new Missile.Settings() {
            public int fuseTicks() { return plugin.getConfig().getInt("missile.fuse-ticks", 60); }
            public int maxSteps() { return plugin.getConfig().getInt("missile.max-steps", 260); }
            public int pierceGlassMax() { return plugin.getConfig().getInt("missile.pierce-glass-max", 12); }
            public double removeBeyondZ() { return removeBeyond; }
        };
        missiles.add(new Missile(world, type, dir, speed, p, parts, settings));
        round(p.getUniqueId())[2]++;
        plugin.stats().add(p, Stat.MISSILES, 1);
        world.playSound(origin.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1f, 0.7f);
        return true;
    }

    /** Glasschild senkrecht zur Flugachse aufklappen. */
    public void deployShield(Location center, Team team) {
        if (state != State.RUNNING) return;
        int r = Math.max(1, plugin.getConfig().getInt("shield.radius", 3));
        Block c = center.getBlock();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy > r * r + 1) continue;
                Block b = c.getRelative(dx, dy, 0);
                if (b.getType().isAir()) b.setType(team.glass(), false);
            }
        }
        world.playSound(center, Sound.BLOCK_GLASS_PLACE, 1f, 1f);
    }

    // ── Ende ─────────────────────────────────────────────────────────

    public void end(Team winner, String reason) {
        if (state == State.ENDING) return;
        state = State.ENDING;
        missiles.clear();
        broadcast(reason);
        broadcast("Team " + winner.colored() + " <gray>hat gewonnen! <dark_gray>(" + formatTime(seconds) + ")");
        for (Player p : players()) {
            plugin.stats().add(p, team(p) == winner ? Stat.WINS : Stat.LOSSES, 1);
            plugin.preview().hide(p);
            p.hideBossBar(bossBar);
        }
        sendSummary();
        plugin.stats().save();
        for (Player p : everyone()) {
            boolean won = team(p) == winner;
            p.showTitle(Title.title(
                    Msg.mm(won ? "<gold><bold>Sieg!" : (team(p) == null ? winner.colored() + " gewinnt" : "<red><bold>Niederlage")),
                    Msg.mm("<gray>Team " + winner.colored() + " <gray>gewinnt"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 0.7f, 1f);
            if (team(p) != null) p.setGameMode(GameMode.ADVENTURE);
        }
        Location fw = map.spawn(winner).toLocation(world).add(0, 2, 0);
        for (int i = 0; i < 4; i++) {
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
        Bukkit.getScheduler().runTaskLater(plugin, this::shutdown, 20L * plugin.getConfig().getInt("end-delay", 8));
    }

    /** Rundenauswertung: Top 3 nach Kills, MVP, eigene Werte. */
    private void sendSummary() {
        List<Map.Entry<UUID, int[]>> list = new ArrayList<>(round.entrySet());
        list.sort((a, b) -> {
            int c = Integer.compare(b.getValue()[0], a.getValue()[0]);
            if (c != 0) return c;
            c = Integer.compare(b.getValue()[2], a.getValue()[2]);
            return c != 0 ? c : Integer.compare(a.getValue()[1], b.getValue()[1]);
        });
        List<Component> lines = new ArrayList<>();
        lines.add(Msg.mm("<dark_gray><st>                                        "));
        lines.add(Msg.mm("  <gold><bold>Rundenauswertung</bold> <dark_gray>– " + formatTime(seconds)));
        if (!list.isEmpty()) {
            Map.Entry<UUID, int[]> mvp = list.getFirst();
            int[] v = mvp.getValue();
            lines.add(Msg.mm("  <yellow>★ MVP: <white>" + Msg.esc(names.getOrDefault(mvp.getKey(), "?"))
                    + " <gray>(" + v[0] + " Kills, " + v[2] + " Raketen)"));
            String[] medals = {"<gold>1.", "<gray>2.", "<#cd7f32>3."};
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                int[] s = list.get(i).getValue();
                lines.add(Msg.mm("  " + medals[i] + " <white>" + Msg.esc(names.getOrDefault(list.get(i).getKey(), "?"))
                        + " <dark_gray>» <green>" + s[0] + " K <red>" + s[1] + " T <aqua>" + s[2] + " R"));
            }
        }
        lines.add(Msg.mm("<dark_gray><st>                                        "));
        for (Player p : everyone()) {
            for (Component c : lines) p.sendMessage(c);
            int[] own = round.get(p.getUniqueId());
            if (own != null) {
                p.sendMessage(Msg.mm("  <gray>Du: <green>" + own[0] + " Kills <dark_gray>| <red>" + own[1]
                        + " Tode <dark_gray>| <aqua>" + own[2] + " Raketen"));
            }
        }
    }

    private boolean shutDown;

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
        lines.add("<dark_gray>" + map.name());
        lines.add("");
        switch (state) {
            case WAITING -> lines.add("<gray>Warte auf Spieler <dark_gray>(" + teams.size() + "/" + min() + ")");
            case COUNTDOWN -> lines.add("<gray>Start in <yellow>" + Math.max(0, countdown) + "s");
            case RUNNING -> lines.add("<gray>Zeit: <white>" + formatTime(seconds));
            case ENDING -> lines.add("<gold>Runde beendet");
        }
        if (votingOpen() && !votes.isEmpty()) {
            String lead = map.name();
            int lv = votesFor(lead);
            for (MapConfig m : votableMaps()) if (votesFor(m.name()) > lv) { lead = m.name(); lv = votesFor(lead); }
            lines.add("<gray>Vote: <yellow>" + lead + " <dark_gray>(" + lv + ")");
        }
        lines.add(" ");
        lines.add(Team.GREEN.colored() + "<gray>: <white>" + teamPlayers(Team.GREEN).size());
        lines.add(Team.RED.colored() + "<gray>: <white>" + teamPlayers(Team.RED).size());
        if (state == State.RUNNING && lossPercent() > 0) {
            lines.add("   ");
            for (Team t : Team.values()) {
                int hp = portalHp.getOrDefault(t, 100);
                lines.add("<gray>Portal " + t.colored() + "<gray>: " + (hp > 60 ? "<green>" : hp > 25 ? "<yellow>" : "<red>") + hp + "%");
            }
        }
        if (state == State.RUNNING) {
            lines.add("  ");
            lines.add("<gray>Item in <yellow>" + itemTimer + "s");
        }
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
