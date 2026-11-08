package de.crafterteam.missilewars.network;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.game.Game;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Spielmodus-Server (Auto-Join) und BungeeCord/Velocity-Anbindung.
 *
 * server-mode.enabled: Spieler landen beim Joinen direkt in einer Runde.
 * bungee.enabled:      "Verlassen" bzw. Rundenende schickt Spieler auf bungee.lobby-server.
 * Funktioniert mit BungeeCord und Velocity (bungee-plugin-message-channel).
 */
public final class ServerMode implements Listener {

    private static final String CHANNEL = "BungeeCord";
    private final MissileWarsPlugin plugin;

    public ServerMode(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public boolean serverMode() {
        return plugin.getConfig().getBoolean("server-mode.enabled", false);
    }

    /** Automatisch beitreten: Spielmodus-Server oder Einzelspiel mit auto-join. */
    public boolean autoJoin() {
        return serverMode() || (plugin.games().singleGame() && plugin.getConfig().getBoolean("single-game.auto-join", true));
    }

    public boolean bungee() {
        String s = plugin.getConfig().getString("bungee.lobby-server", "");
        return plugin.getConfig().getBoolean("bungee.enabled", false) && s != null && !s.isBlank();
    }

    /** Spieler auf den Lobby-Server schicken. @return false wenn Bungee aus ist */
    public boolean toLobbyServer(Player p) {
        if (!bungee()) return false;
        String server = plugin.getConfig().getString("bungee.lobby-server");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            Msg.send(p, "<mut>Verbinde zur Lobby <pri>" + Msg.esc(server) + "<mut> …");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Was nach dem Rundenende mit einem Spieler passiert (nur im Spielmodus). */
    public void afterGame(Player p) {
        if (!autoJoin() || !p.isOnline()) return;
        String mode = plugin.getConfig().getString("server-mode.after-game", "requeue");
        if ("lobby-server".equalsIgnoreCase(mode) && toLobbyServer(p)) return;
        // requeue: in die nächste Runde
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.games().get(p) == null) plugin.games().quickJoin(p, null, false);
        }, 20L);
    }

    /** Verlassen im Spielmodus: Lobby-Server, sonst normal. */
    public boolean handleLeave(Player p) {
        return serverMode() && toLobbyServer(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        if (!autoJoin()) return;
        if (serverMode()) e.joinMessage(null);
        Player p = e.getPlayer();
        if (serverMode()) {   // reiner Minigame-Server: Inventar egal; sonst sichert der Snapshot alles
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.games().get(p) == null) plugin.games().quickJoin(p, null, false);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        if (serverMode()) e.quitMessage(null);
    }

    /** MOTD mit Rundenstatus – praktisch für Server-Selector auf dem Proxy. */
    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        if (!plugin.getConfig().getBoolean("server-mode.motd", true) || !autoJoin()) return;
        Game g = null;
        for (Game x : plugin.games().all()) if (g == null || x.playerCount() > g.playerCount()) g = x;
        String status;
        if (g == null) status = "<ok>● Wartet auf Spieler";
        else status = switch (g.state()) {
            case WAITING -> "<ok>● Wartet <dim>(" + g.playerCount() + " Spieler)";
            case COUNTDOWN -> "<acc>● Startet gleich <dim>(" + g.playerCount() + " Spieler)";
            case RUNNING -> "<pri>● Läuft <dim>– " + g.map().name();
            case ENDING -> "<bad>● Neustart …";
        };
        e.motd(Msg.mm("<logo> <dim>| <mut>Raketen, Portale, Chaos\n" + status));
    }
}
