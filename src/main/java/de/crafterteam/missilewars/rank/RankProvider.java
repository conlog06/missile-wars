package de.crafterteam.missilewars.rank;

import org.bukkit.entity.Player;

/** Quelle für Rang-Präfixe (LuckPerms oder nichts). */
public interface RankProvider {

    /** Roh-Präfix (Legacy &-Codes oder MiniMessage), leer wenn keiner. */
    String prefix(Player p);

    String suffix(Player p);

    /** Gewicht der Hauptgruppe (höher = weiter oben in der Tab-Liste). */
    int weight(Player p);

    String name();

    RankProvider NONE = new RankProvider() {
        public String prefix(Player p) { return ""; }
        public String suffix(Player p) { return ""; }
        public int weight(Player p) { return 0; }
        public String name() { return "keiner"; }
    };
}
