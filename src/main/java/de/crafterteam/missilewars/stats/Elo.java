package de.crafterteam.missilewars.stats;

import de.crafterteam.missilewars.MissileWarsPlugin;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Elo-Wertung: jeder startet bei 1000. Nach einer Runde gewinnt/verliert jeder Spieler Punkte je nachdem,
 * wie stark das gegnerische Team im Schnitt war (Sieg gegen Stärkere = mehr Punkte).
 */
public final class Elo {

    public record Tier(String name, String color, int min) {
        public String colored() {
            return "<" + color + ">" + name + "</" + color + ">";
        }
    }

    private static final Tier[] TIERS = {
            new Tier("Bronze", "#CD7F32", 0),
            new Tier("Silber", "#C0C0C0", 900),
            new Tier("Gold", "#FFC145", 1100),
            new Tier("Platin", "#4FD1C5", 1300),
            new Tier("Diamant", "#5AB0FF", 1500),
            new Tier("Meister", "#C471ED", 1700),
            new Tier("Champion", "#FF2E63", 1900),
    };

    private Elo() {
    }

    public static int start(MissileWarsPlugin plugin) {
        return plugin.getConfig().getInt("elo.start", 1000);
    }

    /** Wertung eines Eintrags (0 = noch nie gewertet → Startwert). */
    public static int rating(MissileWarsPlugin plugin, StatsManager.Entry e) {
        int v = e == null ? 0 : e.get(StatsManager.Stat.ELO);
        return v <= 0 ? start(plugin) : v;
    }

    public static Tier tier(int rating) {
        Tier t = TIERS[0];
        for (Tier x : TIERS) if (rating >= x.min()) t = x;
        return t;
    }

    private static double average(MissileWarsPlugin plugin, Collection<Player> team) {
        if (team.isEmpty()) return start(plugin);
        double sum = 0;
        for (Player p : team) sum += rating(plugin, plugin.stats().entry(p));
        return sum / team.size();
    }

    /**
     * Neue Wertungen berechnen. Gibt Spieler → Änderung zurück (wird schon gespeichert).
     * Weniger als elo.min-players Spieler insgesamt → keine Wertung (verhindert Farmen zu zweit gegen Freunde nicht ganz, aber 1-gegen-0).
     */
    public static Map<UUID, Integer> apply(MissileWarsPlugin plugin, Collection<Player> winners, Collection<Player> losers) {
        Map<UUID, Integer> out = new HashMap<>();
        if (!plugin.getConfig().getBoolean("elo.enabled", true)) return out;
        if (winners.isEmpty() || losers.isEmpty() || winners.size() + losers.size() < plugin.getConfig().getInt("elo.min-players", 2)) return out;
        double k = plugin.getConfig().getDouble("elo.k-factor", 32);
        double avgW = average(plugin, winners), avgL = average(plugin, losers);
        for (Player p : winners) out.put(p.getUniqueId(), change(plugin, p, avgL, 1, k));
        for (Player p : losers) out.put(p.getUniqueId(), change(plugin, p, avgW, 0, k));
        return out;
    }

    private static int change(MissileWarsPlugin plugin, Player p, double opponent, double score, double k) {
        StatsManager.Entry e = plugin.stats().entry(p);
        int own = rating(plugin, e);
        double expected = 1.0 / (1.0 + Math.pow(10, (opponent - own) / 400.0));
        int delta = (int) Math.round(k * (score - expected));
        if (score > 0 && delta < 1) delta = 1;   // ein Sieg bringt immer etwas
        int next = Math.max(100, own + delta);
        plugin.stats().set(p, StatsManager.Stat.ELO, next);
        return next - own;
    }
}
