package de.crafterteam.missilewars.rank;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.game.Game;
import de.crafterteam.missilewars.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Nametags über dem Kopf, Tab-Liste (Präfix, Sortierung, Header/Footer) und Rang-Präfixe.
 * Mit LuckPerms: Präfix & Gewicht der Hauptgruppe. Ohne: nur Teamfarben.
 */
public final class NameTags implements Listener {

    private final MissileWarsPlugin plugin;
    private RankProvider ranks = RankProvider.NONE;

    public NameTags(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                ranks = new LuckPermsRanks();
                plugin.getLogger().info("LuckPerms gefunden – Rang-Präfixe aktiv.");
            } catch (Throwable t) {
                plugin.getLogger().warning("LuckPerms konnte nicht angebunden werden: " + t.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateLobbyPlayers, 40L, 40L);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("nametags.enabled", true);
    }

    public RankProvider ranks() {
        return ranks;
    }

    /** Präfix als Component (versteht &-Codes, §-Codes, &#hex und MiniMessage). */
    public Component prefix(Player p) {
        return parse(ranks.prefix(p));
    }

    public Component suffix(Player p) {
        return parse(ranks.suffix(p));
    }

    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        if (raw.indexOf('<') >= 0 && raw.indexOf('>') > raw.indexOf('<')) return MiniMessage.miniMessage().deserialize(raw);
        return LegacyComponentSerializer.builder().character('&').hexColors().build()
                .deserialize(raw.replace('§', '&'));
    }

    /** Spieler im Spiel: eigenes Team auf dem Spiel-Scoreboard mit Rang + Teamfarbe. */
    public void applyGame(Scoreboard board, Player p, de.crafterteam.missilewars.game.Team team) {
        for (Team st : board.getTeams()) if (st.hasEntry(p.getName())) st.removeEntry(p.getName());
        String name = "p" + p.getUniqueId().toString().replace("-", "").substring(0, 12);
        Team st = board.getTeam(name);
        if (st == null) st = board.registerNewTeam(name);
        Component rank = enabled() ? prefix(p) : Component.empty();
        Component pre = rank.equals(Component.empty()) ? Component.empty() : rank.append(Component.space());
        if (team != null) {
            st.color(team.color());
            st.prefix(pre.append(Msg.mm("<" + team.tag() + ">" + (team == de.crafterteam.missilewars.game.Team.GREEN ? "▎" : "▎"))));
        } else {
            st.color(NamedTextColor.GRAY);
            st.prefix(pre.append(Msg.mm("<dim>👁 ")));
        }
        st.setAllowFriendlyFire(true);
        st.addEntry(p.getName());
        int weight = Math.max(0, Math.min(999, ranks.weight(p)));
        int teamOrder = team == null ? 0 : team == de.crafterteam.missilewars.game.Team.GREEN ? 2000 : 1000;
        p.setPlayerListOrder(teamOrder + weight);
        p.playerListName(pre.append(Msg.mm(team == null ? "<dim>" + Msg.esc(p.getName())
                : "<" + team.tag() + ">" + Msg.esc(p.getName()))).append(suffix(p)));
    }

    /** Spieler außerhalb einer Runde (Haupt-Scoreboard). */
    public void applyLobby(Player p) {
        if (!enabled()) return;
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (p.getScoreboard() != main) return;
        int weight = Math.max(0, Math.min(999, ranks.weight(p)));
        String name = "mw" + String.format("%03d", 999 - weight) + p.getUniqueId().toString().substring(0, 8);
        for (Team st : main.getTeams()) {
            if (st.hasEntry(p.getName()) && !st.getName().equals(name)) {
                if (st.getName().startsWith("mw")) st.removeEntry(p.getName());
                else return; // Spieler ist in einem fremden Team – nicht anfassen
            }
        }
        Team st = main.getTeam(name);
        if (st == null) st = main.registerNewTeam(name);
        Component pre = prefix(p);
        if (!pre.equals(Component.empty())) pre = pre.append(Component.space());
        st.prefix(pre);
        st.suffix(suffix(p).equals(Component.empty()) ? Component.empty() : Component.space().append(suffix(p)));
        if (!st.hasEntry(p.getName())) st.addEntry(p.getName());
        p.setPlayerListOrder(weight);
        p.playerListName(pre.append(Component.text(p.getName(), NamedTextColor.WHITE)).append(suffix(p)));
    }

    private void updateLobbyPlayers() {
        int online = Bukkit.getOnlinePlayers().size();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Game g = plugin.games().get(p);
            if (g == null) {
                applyLobby(p);
                header(p, "<mut>Online: <txt>" + online, "<mut>/mw join <dim>· <mut>/mw stats");
            }
        }
    }

    public void header(Player p, String line, String footer) {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) return;
        String server = plugin.getConfig().getString("tablist.server-name", "");
        p.sendPlayerListHeaderAndFooter(
                Msg.mm("\n<logo>\n" + line + "\n"),
                Msg.mm("\n" + footer + (server == null || server.isBlank() ? "" : "\n<dim>" + Msg.esc(server)) + "\n"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline() && plugin.games().get(e.getPlayer()) == null) applyLobby(e.getPlayer());
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team st : main.getTeams()) {
            if (st.getName().startsWith("mw") && st.hasEntry(e.getPlayer().getName())) {
                st.removeEntry(e.getPlayer().getName());
                if (st.getEntries().isEmpty()) st.unregister();
            }
        }
    }
}
