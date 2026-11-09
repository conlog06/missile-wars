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

    SUPPLY("Nachschub", Material.CHEST, new int[]{120, 260, 420},
            "Chance auf ein Extra-Item bei jeder Verteilung", "+20 % pro Stufe"),
    TURBO("Raketen-Turbo", Material.FEATHER, new int[]{200, 450},
            "Eure Raketen fliegen schneller", "+1 Tempostufe pro Stufe"),
    SHIELDS("Verstärkte Schilde", Material.SNOWBALL, new int[]{100, 220},
            "Größere Glasschilde", "+1 Radius pro Stufe"),
    ARMOR("Panzerung", Material.IRON_CHESTPLATE, new int[]{150, 350},
            "Schutz-Verzauberung auf der Rüstung", "Schutz I / II"),
    GLASS_REPAIR("Glas-Reparatur", Material.GLASS, new int[]{150},
            "Repariert einen Teil des fehlenden Glases", "Nächstes Glas am Portal zuerst · begrenzt"),
    PORTAL_REPAIR("Portal-Reparatur", Material.CRYING_OBSIDIAN, new int[]{300},
            "Repariert einen Teil eures Portals", "Begrenzt · wird jedes Mal teurer");

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

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /** Preise aus der Config (upgrades.<key>.costs), sonst Standard. */
    private int[] costs(org.bukkit.configuration.file.FileConfiguration cfg) {
        List<Integer> l = cfg.getIntegerList("upgrades." + key() + ".costs");
        if (l.isEmpty()) return costs;
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    public int maxLevel(org.bukkit.configuration.file.FileConfiguration cfg) {
        return repeatable() ? cfg.getInt("upgrades." + key() + ".max-uses", this == GLASS_REPAIR ? 3 : 2) : costs(cfg).length;
    }

    /** Preis der nächsten Stufe, -1 wenn ausgebaut. Reparaturen werden mit jeder Nutzung teurer. */
    public int nextCost(org.bukkit.configuration.file.FileConfiguration cfg, int level) {
        if (level >= maxLevel(cfg)) return -1;
        int[] c = costs(cfg);
        return repeatable() ? c[0] * (level + 1) : c[level];
    }

    /** Anteil (Prozent), den eine Reparatur wiederherstellt. */
    public int repairPercent(org.bukkit.configuration.file.FileConfiguration cfg) {
        return cfg.getInt("upgrades." + key() + ".percent", this == GLASS_REPAIR ? 35 : 10);
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
            org.bukkit.configuration.file.FileConfiguration cfg = g.config();
            int lvl = g.upgradeLevel(t, u);
            int cost = u.nextCost(cfg, lvl);
            List<String> lore = new ArrayList<>();
            lore.add("<mut>" + u.desc);
            lore.add("<dim>" + u.detail);
            lore.add("");
            if (!u.repeatable()) lore.add("<mut>Stufe: " + Msg.bar(lvl / (double) u.maxLevel(cfg), u.maxLevel(cfg) * 3, "acc")
                    + " <txt>" + lvl + "<dim>/" + u.maxLevel(cfg));
            else lore.add("<mut>Genutzt: <txt>" + lvl + "<dim>/" + u.maxLevel(cfg) + " <dim>· stellt " + u.repairPercent(cfg) + " % wieder her");
            if (cost < 0) lore.add(u.repeatable() ? "<bad>✘ Keine Reparaturen mehr übrig" : "<ok>✔ Voll ausgebaut");
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
