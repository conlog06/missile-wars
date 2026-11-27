package de.crafterteam.missilewars.network;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.game.Game;
import de.crafterteam.missilewars.stats.Progression;
import de.crafterteam.missilewars.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Brücke zum LobbySystem (gleiche MySQL-Datenbank):
 * <ul>
 *   <li>gemeinsame Coins (ls_players) – verdienen & ausgeben in MissileWars wirkt auf das Lobby-Guthaben</li>
 *   <li>Online-Status für das Freundesystem (Freunde sehen „MissileWars-1“ und können nachspringen)</li>
 *   <li>Heartbeat in ls_servers – Spielerzahl & Status im Navigator / Lobby-Switcher</li>
 *   <li>Stats in ls_game_stats – Bestenlisten & Profil im LobbySystem</li>
 * </ul>
 * Alle DB-Zugriffe laufen in einem eigenen Thread.
 */
public final class LobbyBridge {

    public static final String GAME = "missilewars";

    private final MissileWarsPlugin plugin;
    private final ExecutorService db = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MissileWars-LobbyBridge");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, Long> lobbyCoins = new ConcurrentHashMap<>();
    /** Proxy-Party (BungeeSystem, ls_party_members) je Spieler – für Party-Teams. */
    private final Map<UUID, String> parties = new ConcurrentHashMap<>();
    private volatile boolean partyTable;
    /** Letzter Stand von ls_game_lobbies – unverändert = nur Zeitstempel auffrischen (Performance). */
    private String lastLobbies = "";
    private long lastLobbiesFull, lastLobbiesTouch;
    private Connection connection;
    private String url, user, password;
    private volatile boolean enabled, sharedCoins, coinsTable;
    private String serverName;
    private int heartbeatTask = -1, lobbiesTask = -1;

    public LobbyBridge(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Echten Servernamen vom Proxy (BungeeSystem) übernehmen. */
    public void serverName(String name) {
        if (name == null || name.isBlank() || name.equals(serverName)) return;
        plugin.getLogger().info("Servername vom Proxy übernommen: " + name + " (config: " + serverName + ")");
        serverName = name;
    }

    public String databaseId() {
        var m = plugin.getConfig().getConfigurationSection("lobbysystem.mysql");
        return m == null ? "" : m.getString("host", "localhost") + ":" + m.getInt("port", 3306) + "/" + m.getString("database", "lobby");
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean sharedCoins() {
        return enabled && sharedCoins && coinsTable;
    }

    public void start() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("lobbysystem");
        enabled = c != null && c.getBoolean("enabled", false);
        if (!enabled) return;
        sharedCoins = c.getBoolean("shared-coins", true);
        serverName = c.getString("server-name", "MissileWars-1");
        ConfigurationSection m = c.getConfigurationSection("mysql");
        if (m == null) m = c.createSection("mysql");
        String props = m.getString("properties", "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        url = "jdbc:mysql://" + m.getString("host", "localhost") + ":" + m.getInt("port", 3306) + "/"
                + m.getString("database", "lobby") + (props == null || props.isBlank() ? "" : "?" + props);
        user = m.getString("user", "root");
        password = m.getString("password", "");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
        }
        run(con -> {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("create table if not exists ls_servers (name varchar(64) not null primary key, "
                        + "online int not null default 0, max_players int not null default 0, updated bigint not null default 0)");
                st.executeUpdate("create table if not exists ls_game_stats (uuid varchar(36) not null, game varchar(32) not null, "
                        + "name varchar(16) not null, wins int not null default 0, kills int not null default 0, "
                        + "deaths int not null default 0, games int not null default 0, level int not null default 0, "
                        + "updated bigint not null default 0, primary key (uuid, game))");
            }
            addColumn(con, "ls_servers", "state", "varchar(32) not null default ''");
            addColumn(con, "ls_game_stats", "elo", "int not null default 0");
            partyTable = tableExists(con, "ls_party_members");
            addColumn(con, "ls_servers", "game", "varchar(32) not null default ''");
            coinsTable = tableExists(con, "ls_players");
            if (!coinsTable) plugin.getLogger().warning("LobbySystem-Tabelle ls_players nicht gefunden – "
                    + "gemeinsame Coins & Online-Status erst, wenn das LobbySystem einmal mit dieser DB lief.");
            else addColumn(con, "ls_players", "online_server", "varchar(64) not null default ''");
            plugin.getLogger().info("LobbySystem-Integration aktiv (Server: " + serverName + ", gemeinsame Coins: " + sharedCoins() + ").");
        });
        run(con -> {
            try (Statement st = con.createStatement()) {
                // Lobbys/Runden für das LobbySystem (Navigator → Lobby-Auswahl) + Beitrittswünsche aus der Lobby
                st.executeUpdate("create table if not exists ls_game_lobbies (server varchar(64) not null, game varchar(32) not null, "
                        + "lobby_id int not null, map varchar(64) not null, state varchar(16) not null, players int not null default 0, "
                        + "max_players int not null default 0, updated bigint not null default 0, primary key (server, game, lobby_id))");
                st.executeUpdate("create table if not exists ls_game_joins (uuid varchar(36) not null primary key, server varchar(64) not null, "
                        + "game varchar(32) not null, lobby_id int not null, created bigint not null)");
            }
        });
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::heartbeat, 40L, 200L).getTaskId();
        lobbiesTask = Bukkit.getScheduler().runTaskTimer(plugin, this::publishLobbies, 60L, 40L).getTaskId();
    }

    public void stop() {
        if (!enabled) return;
        if (heartbeatTask != -1) Bukkit.getScheduler().cancelTask(heartbeatTask);
        if (lobbiesTask != -1) Bukkit.getScheduler().cancelTask(lobbiesTask);
        String srv = serverName;
        run(con -> exec(con, "delete from ls_game_lobbies where server = ?", srv));
        String name = serverName;
        run(con -> {
            exec(con, "update ls_servers set online = 0, updated = 0 where name = ?", name);
            if (coinsTable) exec(con, "update ls_players set online_server = '' where online_server = ?", name);
        });
        db.shutdown();
        try {
            db.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
        }
    }

    // ── Lobbys für das LobbySystem ───────────────────────────────────

    /** Alle 2 s: offene/laufende Runden in ls_game_lobbies (für die Lobby-Auswahl im LobbySystem). */
    private void publishLobbies() {
        if (plugin.games() == null) return;
        List<Object[]> rows = new ArrayList<>();
        for (Game g : plugin.games().all()) {
            String state = switch (g.state()) {
                case WAITING -> "WAITING";
                case COUNTDOWN -> "COUNTDOWN";
                case RUNNING -> "INGAME";
                case ENDING -> "ENDING";
            };
            rows.add(new Object[]{g.id(), g.map().name(), state, g.playerCount(), g.maxPlayers()});
        }
        String srv = serverName;
        long now = System.currentTimeMillis();
        StringBuilder sig = new StringBuilder();
        for (Object[] r : rows) sig.append(java.util.Arrays.toString(r)).append(';');
        String signature = sig.toString();
        if (signature.equals(lastLobbies) && now - lastLobbiesFull < 60_000L) {
            // nichts geändert → nur "lebt noch" (die Lobby zeigt Einträge bis 15 s alt); alle 6 s reicht
            if (now - lastLobbiesTouch >= 6000L) {
                lastLobbiesTouch = now;
                run(con -> exec(con, "update ls_game_lobbies set updated = ? where server = ? and game = ?", now, srv, GAME));
            }
            return;
        }
        lastLobbies = signature;
        lastLobbiesFull = lastLobbiesTouch = now;
        run(con -> {
            exec(con, "delete from ls_game_lobbies where server = ? and game = ?", srv, GAME);
            for (Object[] r : rows) {
                exec(con, "insert into ls_game_lobbies (server, game, lobby_id, map, state, players, max_players, updated) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        srv, GAME, r[0], r[1], r[2], r[3], r[4], now);
            }
        });
    }

    /** Beitrittswunsch aus der Lobby holen (max. 60 s alt). Callback im Main-Thread: null = keiner, sonst Lobby-Nummer. */
    public void pendingJoin(Player p, java.util.function.Consumer<Integer> then) {
        String id = p.getUniqueId().toString();
        boolean[] done = {false};
        run(con -> {
            Integer lobby = null;
            try (PreparedStatement ps = con.prepareStatement("select lobby_id from ls_game_joins where uuid = ? and game = ? and created > ?")) {
                ps.setString(1, id);
                ps.setString(2, GAME);
                ps.setLong(3, System.currentTimeMillis() - 60_000L);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) lobby = rs.getInt(1);
                }
            }
            exec(con, "delete from ls_game_joins where uuid = ?", id);
            Integer result = lobby;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (done[0]) return;
                done[0] = true;
                then.accept(result);
            });
        });
        // Fallback, falls die DB hängt
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (done[0]) return;
            done[0] = true;
            then.accept(null);
        }, 40L);
    }

    // ── Heartbeat ────────────────────────────────────────────────────

    private void heartbeat() {
        int online = Bukkit.getOnlinePlayers().size();
        // Parties nur für Spieler in wartenden Runden auffrischen (dort werden Teams gebildet)
        if (plugin.games() != null && partyTable) {
            List<UUID> waiting = new ArrayList<>();
            for (Game g : plugin.games().all()) {
                if (g.state() == Game.State.WAITING || g.state() == Game.State.COUNTDOWN) for (Player p : g.players()) waiting.add(p.getUniqueId());
            }
            refreshParties(waiting);
        }
        int max = Bukkit.getMaxPlayers();
        String state = "WAITING";
        if (plugin.games() != null) {
            boolean running = false, waiting = false;
            for (Game g : plugin.games().all()) {
                if (g.state() == Game.State.RUNNING || g.state() == Game.State.ENDING) running = true;
                else waiting = true;
            }
            state = waiting ? "WAITING" : running ? "INGAME" : "WAITING";
        }
        String s = state, name = serverName;
        run(con -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = con.prepareStatement(
                    "insert into ls_servers (name, online, max_players, updated, state, game) values (?, ?, ?, ?, ?, ?) "
                            + "on duplicate key update online = values(online), max_players = values(max_players), "
                            + "updated = values(updated), state = values(state), game = values(game)")) {
                ps.setString(1, name);
                ps.setInt(2, online);
                ps.setInt(3, max);
                ps.setLong(4, now);
                ps.setString(5, s);
                ps.setString(6, GAME);
                ps.executeUpdate();
            }
        });
    }

    // ── Join / Quit ──────────────────────────────────────────────────

    public void onJoin(Player p) {
        if (!enabled) return;
        UUID id = p.getUniqueId();
        refreshParties(List.of(id));
        String name = p.getName();
        run(con -> {
            if (!coinsTable) return;
            ensurePlayer(con, id, name);
            try (PreparedStatement ps = con.prepareStatement("update ls_players set online_server = ?, last_seen = ?, name = ? where uuid = ?")) {
                ps.setString(1, serverName);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, name);
                ps.setString(4, id.toString());
                ps.executeUpdate();
            }
            if (!sharedCoins) return;
            long coins = 0;
            try (PreparedStatement ps = con.prepareStatement("select coins from ls_players where uuid = ?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) coins = rs.getLong(1);
                }
            }
            long value = coins;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                lobbyCoins.put(id, value);
                StatsManager.Entry e = plugin.stats().get(id);
                if (e != null) applyCoins(e);
            });
        });
    }

    // ── Party-Teams ──────────────────────────────────────────────────

    /** Party-ID des Spielers oder null. */
    public String party(UUID id) {
        return parties.get(id);
    }

    /** Party-Zugehörigkeit der angegebenen Spieler mit einer Abfrage neu laden. */
    public void refreshParties(java.util.Collection<UUID> ids) {
        if (!enabled || !partyTable || ids.isEmpty()) return;
        List<UUID> list = new ArrayList<>(ids);
        run(con -> {
            Map<UUID, String> found = new java.util.HashMap<>();
            String in = String.join(",", java.util.Collections.nCopies(list.size(), "?"));
            try (PreparedStatement ps = con.prepareStatement("select uuid, party_id from ls_party_members where uuid in (" + in + ")")) {
                for (int i = 0; i < list.size(); i++) ps.setString(i + 1, list.get(i).toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) found.put(UUID.fromString(rs.getString(1)), rs.getString(2));
                }
            }
            for (UUID u : list) {
                String party = found.get(u);
                if (party == null) parties.remove(u);
                else parties.put(u, party);
            }
        });
    }

    public void onQuit(Player p) {
        if (!enabled) return;
        UUID id = p.getUniqueId();
        lobbyCoins.remove(id);
        parties.remove(id);
        String name = serverName;
        run(con -> {
            if (coinsTable) exec(con, "update ls_players set online_server = '', last_seen = ? where uuid = ? and online_server = ?",
                    System.currentTimeMillis(), id.toString(), name);
        });
    }

    // ── Coins ────────────────────────────────────────────────────────

    /** Setzt den MissileWars-Coin-Stand auf das Lobby-Guthaben (falls geladen). */
    public void applyCoins(StatsManager.Entry e) {
        if (!sharedCoins()) return;
        Long v = lobbyCoins.get(e.id);
        if (v != null) e.values.put(StatsManager.Stat.COINS, (int) Math.min(Integer.MAX_VALUE, Math.max(0, v)));
    }

    /** Wird bei jeder Coin-Änderung in MissileWars aufgerufen (Verdienst oder Kauf). */
    public void coinsChanged(UUID id, String name, int delta) {
        if (!sharedCoins() || delta == 0) return;
        lobbyCoins.computeIfPresent(id, (k, v) -> Math.max(0, v + delta));
        run(con -> {
            ensurePlayer(con, id, name);
            try (PreparedStatement ps = con.prepareStatement(
                    "update ls_players set coins = case when coins + ? < 0 then 0 else coins + ? end where uuid = ?")) {
                ps.setLong(1, delta);
                ps.setLong(2, delta);
                ps.setString(3, id.toString());
                ps.executeUpdate();
            }
        });
    }

    private void ensurePlayer(Connection con, UUID id, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("select 1 from ls_players where uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "insert into ls_players (uuid, name, settings, selected, owned) values (?, ?, '', '', '')")) {
            ps.setString(1, id.toString());
            ps.setString(2, name == null ? "?" : name);
            ps.executeUpdate();
        }
    }

    // ── Stats für Bestenlisten & Profil im LobbySystem ──────────────

    /** Im Main-Thread aufrufen – macht einen Schnappschuss und schreibt asynchron. */
    public void pushStats(List<StatsManager.Entry> entries) {
        if (!enabled || entries.isEmpty()) return;
        List<Object[]> rows = new ArrayList<>();
        for (StatsManager.Entry e : entries) {
            rows.add(new Object[]{e.id.toString(), e.name, e.get(StatsManager.Stat.WINS), e.get(StatsManager.Stat.KILLS),
                    e.get(StatsManager.Stat.DEATHS), e.get(StatsManager.Stat.GAMES), Progression.level(e),
                    de.crafterteam.missilewars.stats.Elo.rating(plugin, e)});
        }
        run(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "insert into ls_game_stats (uuid, game, name, wins, kills, deaths, games, level, updated, elo) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                            + "on duplicate key update name = values(name), wins = values(wins), kills = values(kills), deaths = values(deaths), "
                            + "games = values(games), level = values(level), updated = values(updated), elo = values(elo)")) {
                long now = System.currentTimeMillis();
                for (Object[] r : rows) {
                    ps.setString(1, (String) r[0]);
                    ps.setString(2, GAME);
                    String n = r[1] == null ? "?" : (String) r[1];
                    ps.setString(3, n.length() > 16 ? n.substring(0, 16) : n);
                    for (int i = 2; i <= 6; i++) ps.setInt(i + 2, (Integer) r[i]);
                    ps.setLong(9, now);
                    ps.setInt(10, (Integer) r[7]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    // ── DB-Helfer ────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Task {
        void run(Connection con) throws SQLException;
    }

    private void run(Task task) {
        if (db.isShutdown()) return;
        db.execute(() -> {
            try {
                task.run(conn());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "LobbySystem-Integration: " + e.getMessage());
            }
        });
    }

    private Connection conn() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    private static void exec(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection con, String table) throws SQLException {
        try (ResultSet rs = con.getMetaData().getTables(con.getCatalog(), null, table, null)) {
            return rs.next();
        }
    }

    private static void addColumn(Connection con, String table, String column, String definition) throws SQLException {
        if (!tableExists(con, table)) return;
        try (ResultSet rs = con.getMetaData().getColumns(con.getCatalog(), null, table, column)) {
            if (rs.next()) return;
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate("alter table " + table + " add column " + column + " " + definition);
        }
    }
}
