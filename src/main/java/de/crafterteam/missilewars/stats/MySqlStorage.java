package de.crafterteam.missilewars.stats;

import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stats in MySQL/MariaDB: eine Zeile pro Spieler (uuid, name, data als YAML-Text).
 * Mehrere Server (BungeeCord/Velocity) können dieselbe Datenbank nutzen.
 * Alle Methoden blockieren – nur asynchron aufrufen (außer beim Start/Stopp).
 */
final class MySqlStorage {

    private final String url;
    private final String user;
    private final String password;
    private final String table;
    private Connection connection;

    MySqlStorage(ConfigurationSection c) {
        if (c == null) throw new IllegalArgumentException("storage.mysql fehlt in der config.yml");
        String host = c.getString("host", "localhost");
        int port = c.getInt("port", 3306);
        String db = c.getString("database", "missilewars");
        String props = c.getString("properties", "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + db + (props == null || props.isBlank() ? "" : "?" + props);
        this.user = c.getString("user", "root");
        this.password = c.getString("password", "");
        String prefix = c.getString("table-prefix", "mw_");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]*")) prefix = "mw_";
        this.table = prefix + "stats";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // Treiber kommt über plugin.yml-libraries; sonst findet DriverManager ihn evtl. trotzdem
        }
    }

    private synchronized Connection conn() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    synchronized void init() throws SQLException {
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("create table if not exists " + table + " ("
                    + "uuid char(36) not null primary key, "
                    + "name varchar(32) not null, "
                    + "data mediumtext not null, "
                    + "updated timestamp not null default current_timestamp on update current_timestamp"
                    + ") default charset=utf8mb4");
        }
    }

    synchronized boolean isEmpty() throws SQLException {
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery("select 1 from " + table + " limit 1")) {
            return !rs.next();
        }
    }

    synchronized Map<UUID, String> loadAll() throws SQLException {
        Map<UUID, String> out = new HashMap<>();
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery("select uuid, data from " + table)) {
            while (rs.next()) {
                try {
                    out.put(UUID.fromString(rs.getString(1)), rs.getString(2));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    synchronized String load(UUID id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("select data from " + table + " where uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** rows: uuid → {name, data}. */
    synchronized void upsert(Map<UUID, String[]> rows) throws SQLException {
        if (rows.isEmpty()) return;
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement("insert into " + table + " (uuid, name, data) values (?, ?, ?) "
                + "on duplicate key update name = values(name), data = values(data)")) {
            for (Map.Entry<UUID, String[]> r : rows.entrySet()) {
                ps.setString(1, r.getKey().toString());
                ps.setString(2, r.getValue()[0] == null ? "?" : r.getValue()[0]);
                ps.setString(3, r.getValue()[1]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    synchronized void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }
}
