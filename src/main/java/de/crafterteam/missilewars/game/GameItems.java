package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.missile.MissileType;
import de.crafterteam.missilewars.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Erstellt und erkennt die Spiel-Items. */
public final class GameItems {

    public static final String SHIELD = "shield";
    public static final String FIREBALL = "fireball";
    public static final String ARROWS = "arrows";
    public static final String MENU_TEAM = "menu_team";
    public static final String MENU_LEAVE = "menu_leave";
    public static final String MENU_VOTE = "menu_vote";
    public static final String MENU_GAME = "menu_game";

    public static final String WIND_CHARGE = "windcharge";
    public static final String MACE = "mace";
    public static final String PLATFORM = "platform";
    public static final String ENDER_PEARL = "enderpearl";
    public static final String MENU_KIT = "menu_kit";
    public static final String MENU_MISSILES = "menu_missiles";
    public static final String MENU_READY = "menu_ready";
    public static final String MENU_SHOP = "menu_shop";

    public static final List<String> ALL_IDS = List.of(
            "tomahawk", "juggernaut", "shieldbuster", "lightning", "guardian", "gemini", "interceptor",
            "hydra", "drill", "arrow", "carrier", "cluster",
            SHIELD, FIREBALL, ARROWS, PLATFORM, ENDER_PEARL,
            WIND_CHARGE, MACE);

    /** Vanilla-Items ohne Tag – werden über das Material gezählt und funktionieren normal. */
    private static final Map<String, Material> VANILLA = Map.of(
            ARROWS, Material.ARROW, WIND_CHARGE, Material.WIND_CHARGE, MACE, Material.MACE, ENDER_PEARL, Material.ENDER_PEARL);

    private final MissileWarsPlugin plugin;
    private final NamespacedKey itemKey;

    public GameItems(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "item");
    }

    public NamespacedKey key() {
        return itemKey;
    }

    public ItemStack create(String id) {
        MissileType mt = MissileType.byId(id);
        if (mt != null) {
            int ticks = Math.max(1, plugin.getConfig().getInt("missile.speed." + id, 4));
            long tnt = mt.tntCount();
            String speed = String.format(java.util.Locale.ROOT, "%.1f", 20.0 / ticks);
            return tagged(new ItemStack(mt.item()), id, "<pri><bold>" + mt.displayName(),
                    "<mut>" + mt.description(),
                    "<dim>Tempo: <txt>" + speed + " Blöcke/s <dim>| Ladung: <bad>" + tnt + " TNT",
                    mt.rideable() ? "<info>⛵ Mitfahren möglich" : "<dim>Kein Mitfahren",
                    "<dim>Rechtsklick auf einen Block (Vorschau beim Halten)");
        }
        return switch (id) {
            case SHIELD -> tagged(new ItemStack(Material.SNOWBALL), id, "<info><bold>Schild",
                    "<mut>Werfen – klappt nach kurzer Zeit", "<mut>zu einer Glaswand auf.");
            case FIREBALL -> tagged(new ItemStack(Material.FIRE_CHARGE), id, "<bad><bold>Feuerball",
                    "<mut>Rechtsklick zum Abfeuern.", "<mut>Kann zurückgeschlagen werden!");
            case ARROWS -> new ItemStack(Material.ARROW, plugin.getConfig().getInt("items.arrows.amount", 3));
            case WIND_CHARGE -> new ItemStack(Material.WIND_CHARGE, plugin.getConfig().getInt("items.windcharge.amount", 2));
            case MACE -> {
                ItemStack mace = new ItemStack(Material.MACE);
                mace.editMeta(m -> m.setUnbreakable(true));
                yield mace;
            }
            case MENU_TEAM -> tagged(new ItemStack(Material.WHITE_WOOL), id, "<acc><bold>Team wählen",
                    "<mut>Rechtsklick");
            case ENDER_PEARL -> new ItemStack(Material.ENDER_PEARL);
            case PLATFORM -> tagged(new ItemStack(Material.BLAZE_ROD), id, "<info><bold>Rettungsplattform",
                    "<mut>Rechtsklick: Schleim-Plattform unter dir", "<mut>– rettet dich vor dem Void (8 s).");
            case MENU_KIT -> tagged(new ItemStack(Material.CHEST), id, "<acc><bold>Kit wählen", "<dim>Rechtsklick");
            case MENU_MISSILES -> tagged(new ItemStack(Material.KNOWLEDGE_BOOK), id, "<pri><bold>Raketen-Übersicht",
                    "<mut>Alle Raketen & Items im Überblick", "<dim>Rechtsklick");
            case MENU_READY -> tagged(new ItemStack(Material.LIME_DYE), id, "<ok><bold>Bereit!",
                    "<mut>Wenn alle bereit sind, startet die Runde sofort", "<dim>Rechtsklick");
            case MENU_SHOP -> tagged(new ItemStack(Material.EMERALD), id, "<ok><bold>Shop",
                    "<mut>Kits, Raketen-Spuren, Kill-Effekte, Boosts", "<dim>Rechtsklick");
            case MENU_GAME -> tagged(new ItemStack(Material.RECOVERY_COMPASS), id, "<pri><bold>Spielmenü",
                    "<mut>Maps, Statistiken, Bestenliste", "<dim>Rechtsklick");
            case MENU_VOTE -> tagged(new ItemStack(Material.MAP), id, "<pri><bold>Map-Abstimmung",
                    "<mut>Rechtsklick");
            case MENU_LEAVE -> tagged(new ItemStack(Material.RED_BED), id, "<bad><bold>Verlassen",
                    "<mut>Rechtsklick");
            default -> null;
        };
    }

    private ItemStack tagged(ItemStack item, String id, String name, String... lore) {
        item.editMeta(meta -> {
            meta.displayName(Msg.mm(name).decoration(TextDecoration.ITALIC, false));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.mm(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        });
        return item;
    }

    /** Item-ID oder null. */
    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) continue;
            Material vanilla = VANILLA.get(id);
            if (vanilla != null ? it.getType() == vanilla : id.equals(idOf(it))) n += it.getAmount();
        }
        return n;
    }

    /** Zufälliges Item nach Gewichtung, das der Spieler noch nicht "voll" hat. Null = nichts frei. */
    public String roll(Player p) {
        return roll(p, Kit.STANDARD);
    }

    /** Wahrscheinlichkeit (0..1) für ein Item mit Standard-Gewichten – für die Übersicht. */
    public double chance(String id, Kit kit) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items");
        if (sec == null) return 0;
        double total = 0, own = 0;
        for (String i : ALL_IDS) {
            double w = sec.getInt(i + ".weight", 0) * kit.multiplier(i);
            total += w;
            if (i.equals(id)) own = w;
        }
        return total <= 0 ? 0 : own / total;
    }

    public String roll(Player p, Kit kit) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items");
        if (sec == null) return null;
        List<String> ids = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (String id : ALL_IDS) {
            int w = (int) Math.round(sec.getInt(id + ".weight", 0) * 10 * (kit == null ? 1.0 : kit.multiplier(id)));
            int max = sec.getInt(id + ".max", 1);
            if (w <= 0 || count(p, id) >= max) continue;
            ids.add(id);
            weights.add(w);
            total += w;
        }
        if (total == 0) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < ids.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return ids.get(i);
        }
        return ids.getLast();
    }

    /** Items in der Wartephase. */
    public void giveLobbyItems(Player p, Team team, boolean vote) {
        giveLobbyItems(p, team, vote, false);
    }

    public void giveLobbyItems(Player p, Team team, boolean vote, boolean ready) {
        p.getInventory().clear();
        if (plugin.getConfig().getBoolean("kits", true)) p.getInventory().setItem(1, create(MENU_KIT));
        p.getInventory().setItem(3, create(MENU_MISSILES));
        ItemStack readyItem = create(MENU_READY);
        if (ready) readyItem = tagged(new ItemStack(Material.GRAY_DYE), MENU_READY, "<mut><bold>Nicht mehr bereit",
                "<ok>Du bist bereit.", "<dim>Rechtsklick zum Zurücknehmen");
        p.getInventory().setItem(6, readyItem);
        if (plugin.getConfig().getBoolean("coins.enabled", true)) p.getInventory().setItem(5, create(MENU_SHOP));
        Material wool = team == Team.GREEN ? Material.LIME_WOOL : Material.RED_WOOL;
        p.getInventory().setItem(0, tagged(new ItemStack(wool), MENU_TEAM, "<acc><bold>Team wählen",
                "<mut>Rechtsklick"));
        p.getInventory().setItem(8, create(MENU_LEAVE));
        if (vote) p.getInventory().setItem(4, create(MENU_VOTE));
        p.getInventory().setItem(2, create(MENU_GAME));
    }

    /** Zuschauer (Spectator-Modus kann keine Items benutzen – Teleporter per /mw teleporter). */
    public void giveSpectatorItems(Player p) {
        p.getInventory().clear();
    }

    public void giveKit(Player p, Team team) {
        ItemStack bow = new ItemStack(Material.BOW);
        bow.editMeta(m -> {
            m.displayName(Msg.mm("<acc><bold>Bogen").decoration(TextDecoration.ITALIC, false));
            m.addEnchant(Enchantment.FLAME, 1, true);
            m.setUnbreakable(true);
        });
        p.getInventory().setItem(0, bow);
        int arrows = plugin.getConfig().getInt("start-arrows", 3);
        if (arrows > 0) p.getInventory().setItem(8, new ItemStack(Material.ARROW, arrows));

        p.getInventory().setChestplate(armor(Material.LEATHER_CHESTPLATE, team));
        p.getInventory().setLeggings(armor(Material.LEATHER_LEGGINGS, team));
        p.getInventory().setBoots(armor(Material.LEATHER_BOOTS, team));
    }

    private ItemStack armor(Material m, Team team) {
        ItemStack it = new ItemStack(m);
        it.editMeta(LeatherArmorMeta.class, meta -> {
            meta.setColor(team.armorColor());
            meta.setUnbreakable(true);
        });
        return it;
    }
}
