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

    // ── Join-/Leave-Nachrichten ──────────────────────────────────────

    private String rank(Player p) {
        String r = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(plugin.nameTags().prefix(p));
        return r.isBlank() ? "" : r + " ";
    }

    private String fill(String tpl, Player p) {
        return tpl.replace("{player}", Msg.esc(p.getName()))
                .replace("{rank}", rank(p))
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoinMessage(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("join-messages.enabled", true)) return;
        Player p = e.getPlayer();
        boolean first = !p.hasPlayedBefore();
        String tpl = first ? plugin.getConfig().getString("join-messages.first-join",
                "<dim>[<ok>+</ok>]</dim> {rank}<txt>{player} <acc>ist zum ersten Mal da – willkommen! <dim>(#{unique})")
                : plugin.getConfig().getString("join-messages.join", "<dim>[<ok>+</ok>]</dim> {rank}<txt>{player} <dim>({online}/{max})");
        if (tpl == null || tpl.isBlank()) e.joinMessage(null);
        else e.joinMessage(Msg.mm(fill(tpl, p).replace("{unique}", String.valueOf(Bukkit.getOfflinePlayers().length))));
        if (!plugin.getConfig().getBoolean("join-messages.welcome-title", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<logo>"),
                    Msg.mm(first ? "<acc>Willkommen, <txt>" + Msg.esc(p.getName()) + "<acc>!" : "<mut>Willkommen zurück, <txt>" + Msg.esc(p.getName())),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(3), java.time.Duration.ofMillis(700))));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            for (String line : plugin.getConfig().getStringList("join-messages.motd")) p.sendMessage(Msg.mm(fill(line, p)));
        }, 15L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitMessage(PlayerQuitEvent e) {
        if (!plugin.getConfig().getBoolean("join-messages.enabled", true)) return;
        String tpl = plugin.getConfig().getString("join-messages.quit", "<dim>[<bad>-</bad>]</dim> {rank}<txt>{player}");
        e.quitMessage(tpl == null || tpl.isBlank() ? null : Msg.mm(fill(tpl, e.getPlayer())));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        if (!autoJoin()) return;
        Player p = e.getPlayer();
        if (serverMode()) {   // reiner Minigame-Server: Inventar egal; sonst sichert der Snapshot alles
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.games().get(p) == null) plugin.games().quickJoin(p, null, false);
        }, 5L);
    }

    /** MOTD mit Rundenstatus – praktisch für Server-Selector auf dem Proxy. */
    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        if (!plugin.getConfig().getBoolean("server-mode.motd", true) || !autoJoin()) return;
        Game g = plugin.games().openLobby();
        if (g == null) for (Game x : plugin.games().all()) if (g == null || x.playerCount() > g.playerCount()) g = x;
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
