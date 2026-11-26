package de.crafterteam.missilewars.network;

import de.crafterteam.missilewars.MissileWarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anbindung an das BungeeSystem (github.com/conlog06/bungeesystem-crafterteam), Kanal {@value #CHANNEL}:
 * <ul>
 *   <li>Clan-Gründung auf dem Proxy bucht die Coins hier ab (MissileWars-Coin-Stand bleibt korrekt)</li>
 *   <li>Clantag hinter dem Namen in Tab & über dem Kopf</li>
 *   <li>Netzwerkweite Onlinezeit (Scoreboard-Platzhalter)</li>
 * </ul>
 * Report, Ban, Mute, Freunde, Party (folgt dem Leader) laufen komplett über den Proxy.
 */
public final class ProxyBridge implements PluginMessageListener {

    public static final String CHANNEL = "crafterteam:bungee";

    private final MissileWarsPlugin plugin;
    private final Map<UUID, String> clanTags = new ConcurrentHashMap<>();
    private final Map<UUID, Long> onlineTime = new ConcurrentHashMap<>();
    private volatile boolean active;

    public ProxyBridge(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var m = plugin.getServer().getMessenger();
        m.registerOutgoingPluginChannel(plugin, CHANNEL);
        m.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public boolean active() {
        return active;
    }

    public String clanTag(UUID uuid) {
        return clanTags.get(uuid);
    }

    /** Onlinezeit in ms (−1 = unbekannt). */
    public long onlineTime(UUID uuid) {
        return onlineTime.getOrDefault(uuid, -1L);
    }

    public void forget(UUID uuid) {
        clanTags.remove(uuid);
        onlineTime.remove(uuid);
    }

    private void send(Player via, String type, DataWriter w) {
        if (via == null || !via.isOnline()) return;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(type);
            w.write(out);
            via.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("BungeeSystem-Nachricht fehlgeschlagen: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface DataWriter {
        void write(DataOutputStream out) throws IOException;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String type = in.readUTF();
            switch (type) {
                case "HELLO" -> {
                    if (!active) plugin.getLogger().info("BungeeSystem erkannt.");
                    active = true;
                }
                case "CLANTAG" -> {
                    UUID uuid = UUID.fromString(in.readUTF());
                    String tag = in.readUTF();
                    if (tag.isEmpty()) clanTags.remove(uuid);
                    else clanTags.put(uuid, tag);
                }
                case "ONLINETIME" -> onlineTime.put(UUID.fromString(in.readUTF()), in.readLong());
                case "COINS_TAKE" -> {
                    int id = in.readInt();
                    UUID uuid = UUID.fromString(in.readUTF());
                    long amount = in.readLong();
                    Player p = Bukkit.getPlayer(uuid);
                    boolean ok;
                    if (p != null && plugin.stats() != null && amount <= Integer.MAX_VALUE
                            && plugin.lobbyBridge() != null && plugin.lobbyBridge().sharedCoins()) {
                        // spend() zieht auch das gemeinsame Lobby-Guthaben (ls_players) ab
                        ok = plugin.stats().spend(p, (int) amount);
                    } else {
                        return; // keine Antwort → der Proxy bucht direkt in der Datenbank ab
                    }
                    send(p, "COINS_RESULT", out -> {
                        out.writeInt(id);
                        out.writeBoolean(ok);
                    });
                }
                case "COINS_ADD" -> {
                    int id = in.readInt();
                    UUID uuid = UUID.fromString(in.readUTF());
                    long amount = in.readLong();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || plugin.stats() == null || amount > Integer.MAX_VALUE
                            || plugin.lobbyBridge() == null || !plugin.lobbyBridge().sharedCoins()) return;
                    // add() schreibt auch ins gemeinsame Lobby-Guthaben (ls_players)
                    plugin.stats().add(p, de.crafterteam.missilewars.stats.StatsManager.Stat.COINS, (int) amount);
                    send(p, "COINS_RESULT", out -> {
                        out.writeInt(id);
                        out.writeBoolean(true);
                    });
                }
                case "COINS_GET" -> {
                    int id = in.readInt();
                    Player p = Bukkit.getPlayer(UUID.fromString(in.readUTF()));
                    if (p == null || plugin.stats() == null || plugin.lobbyBridge() == null || !plugin.lobbyBridge().sharedCoins()) return;
                    int coins = plugin.stats().coins(p);
                    send(p, "COINS_VALUE", out -> {
                        out.writeInt(id);
                        out.writeLong(coins);
                    });
                }
                default -> {
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Ungültige BungeeSystem-Nachricht: " + e.getMessage());
        }
    }
}
