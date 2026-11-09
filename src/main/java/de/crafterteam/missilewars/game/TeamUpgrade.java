package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.gui.Gui;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Team-Upgrades. Bezahlt wird mit Teampunkten:
 * zerstörtes Glas der gegnerischen Basis (+1 pro Block), Kills (+3), abgefangene Raketen (+5), Airdrops (+5).
 */
public enum TeamUpgrade {

    SUPPLY("Nachschub", Material.CHEST, new int[]{40, 80, 120},
            "Chance auf ein Extra-Item bei jeder Verteilung", "+20 % pro Stufe"),
    TURBO("Raketen-Turbo", Material.FEATHER, new int[]{60, 120},
            "Eure Raketen fliegen schneller", "+1 Tempostufe pro Stufe"),
    SHIELDS("Verstärkte Schilde", Material.SNOWBALL, new int[]{30, 60},
            "Größere Glasschilde", "+1 Radius pro Stufe"),
    ARMOR("Panzerung", Material.IRON_CHESTPLATE, new int[]{50, 100},
            "Schutz-Verzauberung auf der Rüstung", "Schutz I / II"),
    GLASS_REPAIR("Glas-Reparatur", Material.GLASS, new int[]{50},
            "Baut das Glas eurer Basis wieder auf", "Beliebig oft kaufbar"),
    PORTAL_REPAIR("Portal-Reparatur", Material.CRYING_OBSIDIAN, new int[]{120},
            "Repariert bis zu 10 % eures Portals", "Beliebig oft kaufbar");

    private final String displayName;
    private final Material icon;
    private final int[] costs;
    private final String desc;
    private final String detail;

    TeamUpgrade(String displayName, Material icon, int[] costs, String desc, String detail) {
        this.displayName = displayName;
        this.icon = icon;
        this.costs = costs;
        this.desc = desc;
        this.detail = detail;
    }

    public String displayName() { return displayName; }

    public boolean repeatable() {
        return this == GLASS_REPAIR || this == PORTAL_REPAIR;
    }

    public int maxLevel() {
        return repeatable() ? Integer.MAX_VALUE : costs.length;
    }

    /** Preis der nächsten Stufe, -1 wenn ausgebaut. */
    public int nextCost(int level) {
        if (repeatable()) return costs[0];
        return level >= costs.length ? -1 : costs[level];
    }

    public static void open(Game g, Player viewer) {
        Team t = g.team(viewer);
        if (t == null) return;
        Gui gui = new Gui(4, "<dim>» " + t.colored() + " <mut>Team-Upgrades").frame();
        int points = g.teamPoints(t);
        gui.set(4, Gui.item(Material.BEACON, "<acc><bold>" + points + " Teampunkte",
                "<mut>Verdienen: gegnerisches Basis-Glas zerstören (+1),",
                "<mut>Kills (+3), Raketen abfangen (+5), Airdrops (+5)."), null);
        int[] slots = {10, 11, 12, 14, 15, 16};
        int i = 0;
        for (TeamUpgrade u : values()) {
            int lvl = g.upgradeLevel(t, u);
            int cost = u.nextCost(lvl);
            List<String> lore = new ArrayList<>();
            lore.add("<mut>" + u.desc);
            lore.add("<dim>" + u.detail);
            lore.add("");
            if (!u.repeatable()) lore.add("<mut>Stufe: <txt>" + lvl + "<dim>/" + u.costs.length);
            if (cost < 0) lore.add("<ok>✔ Voll ausgebaut");
            else {
                lore.add("<mut>Kosten: <acc>" + cost + " Teampunkte");
                lore.add(points >= cost ? "<acc>» Klicken zum Kaufen" : "<bad>Zu wenig Teampunkte");
            }
            org.bukkit.inventory.ItemStack it = Gui.item(u.icon, "<pri><bold>" + u.displayName, lore.toArray(String[]::new));
            gui.set(slots[i++], lvl > 0 && !u.repeatable() ? Gui.glow(it) : it, p -> {
                if (g.buyUpgrade(p, u)) open(g, p);
            });
        }
        gui.set(31, Gui.item(Material.BARRIER, "<bad>Schließen"), Player::closeInventory);
        gui.open(viewer);
    }

    static String name(TeamUpgrade u) {
        return Msg.esc(u.displayName);
    }
}
