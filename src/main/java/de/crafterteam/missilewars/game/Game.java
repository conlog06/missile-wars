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
import org.bukkit.entity.Firework;
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

        if (running) {
            p.setGameMode(GameMode.SURVIVAL);
            plugin.items().giveKit(p, t);
            giveItem(p);
            broadcast(Msg.esc(p.getName()) + " ist Team " + t.colored() + " <gray>beigetreten.");
        } else {
            p.setGameMode(GameMode.ADVENTURE);
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
        Msg.send(p, "Du bist jetzt in Team " + t.colored() + "<gray>.");
        updateBoard();
    }

    /** Spieler verlässt das Spiel (Befehl, Quit, Weltwechsel). */
    public void leave(Player p, boolean teleportBack) {
        UUID id = p.getUniqueId();
        Team t = teams.remove(id);
        spectators.remove(id);
        plugin.games().unbind(p);
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
                if (--itemTimer <= 0) {
                    itemTimer = interval();
                    for (Player p : players()) giveItem(p);
                }
                Component bar = Msg.mm("<gray>Nächstes Item in <yellow>" + itemTimer + "s");
                for (Player p : players()) p.sendActionBar(bar);
            }
            default -> {
            }
        }
        updateBoard();
    }

    private int interval() {
        return Math.max(1, plugin.getConfig().getInt("item-interval", 15));
    }

    private void start() {
        state = State.RUNNING;
        seconds = 0;
        itemTimer = interval();
        int startItems = plugin.getConfig().getInt("start-items", 1);
        for (Player p : players()) {
            Team t = team(p);
            PlayerSnapshot.reset(p);
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(map.spawn(t).toLocation(world));
            plugin.items().giveKit(p, t);
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
            Missile.Result r = it.next().tick();
            if (r != Missile.Result.FLYING) it.remove();
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
                for (Block b : portalBlocks.get(t)) {
                    if (b.getType() != Material.NETHER_PORTAL) {
                        end(t.other(), "Das Portal von Team " + t.colored() + " <gray>wurde zerstört!");
                        return;
                    }
                }
            }
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
            if (k != null && !k.equals(p) && team(k) != null) killer ="<" + team(k).tag() + ">" + Msg.esc(k.getName()) + "</" + team(k).tag() + ">";
        }
        lastAttacker.remove(p.getUniqueId());
        lastAttackTime.remove(p.getUniqueId());

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

    /**
     * Rakete spawnen. origin = Block, an dem das Heck mittig sitzt.
     * @return true wenn gespawnt (Item verbrauchen)
     */
    public boolean spawnMissile(Player p, MissileType type, Block origin) {
        Team t = team(p);
        if (t == null || state != State.RUNNING) return false;
        int dir = map.forward(t);
        double mid = map.midZ();
        double ownPortalZ = map.portal(t).centerZ();
        boolean ownHalf = plugin.getConfig().getBoolean("missile.own-half-only", true);

        Map<Missile.Pos, Material> parts = new LinkedHashMap<>();
        Map<Missile.Pos, BlockData> datas = new LinkedHashMap<>();
        for (MissileType.Part part : type.parts()) {
            int wx = origin.getX() + (dir > 0 ? part.x() : -part.x());
            int wy = origin.getY() + part.y();
            int wz = origin.getZ() + dir * part.z();
            if (wy < world.getMinHeight() || wy >= world.getMaxHeight()) {
                Msg.send(p, "<red>Hier ist kein Platz für die Rakete.");
                return false;
            }
            Block b = world.getBlockAt(wx, wy, wz);
            if (!b.getType().isAir() && !b.isLiquid()) {
                Msg.send(p, "<red>Hier ist kein Platz für die Rakete.");
                return false;
            }
            if (ownHalf && dir * (wz + 0.5 - mid) > 0) {
                Msg.send(p, "<red>Raketen nur auf deiner Hälfte spawnen!");
                return false;
            }
            if (dir * (wz + 0.5 - ownPortalZ) <= 0) {
                Msg.send(p, "<red>Nicht hinter dem eigenen Portal!");
                return false;
            }
            BlockData data = part.material().createBlockData();
            if (part.facing() != null && data instanceof Directional d) {
                BlockFace f = part.facing();
                if (dir < 0 && f != BlockFace.UP && f != BlockFace.DOWN) f = f.getOppositeFace();
                if (d.getFaces().contains(f)) d.setFacing(f);
            }
            Missile.Pos pos = new Missile.Pos(wx, wy, wz);
            parts.put(pos, part.material());
            datas.put(pos, data);
        }
        for (Map.Entry<Missile.Pos, BlockData> e : datas.entrySet()) {
            Missile.Pos pos = e.getKey();
            world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(e.getValue(), false);
        }

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
        lines.add(" ");
        lines.add(Team.GREEN.colored() + "<gray>: <white>" + teamPlayers(Team.GREEN).size());
        lines.add(Team.RED.colored() + "<gray>: <white>" + teamPlayers(Team.RED).size());
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
