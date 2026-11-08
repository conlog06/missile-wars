package de.crafterteam.missilewars.shop;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.game.Game;
import de.crafterteam.missilewars.game.Kit;
import de.crafterteam.missilewars.gui.Gui;
import de.crafterteam.missilewars.stats.StatsManager.Stat;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coins & Shop.
 * Coins gibt es für Kills, Siege, Portaltreffer usw.
 * Kaufen kann man: Kits (dauerhaft), Raketen-Spuren & Kill-Effekte (dauerhaft, Kosmetik)
 * und Rundenboosts (einmalig für die nächste Runde, nur in der Wartelobby).
 */
public final class Shop {

    public record Trail(String id, String name, Material icon, Particle particle, int price) {}
    public record KillFx(String id, String name, Material icon, int price) {}
    public record Boost(String id, String name, Material icon, String item, int amount, int price, String desc) {}

    public static final Map<String, Trail> TRAILS = new LinkedHashMap<>();
    public static final Map<String, KillFx> KILLFX = new LinkedHashMap<>();
    public static final Map<String, Boost> BOOSTS = new LinkedHashMap<>();
    public static final Map<Kit, Integer> KIT_PRICES = new LinkedHashMap<>();

    static {
        trail(new Trail("flame", "Flammen", Material.BLAZE_POWDER, Particle.FLAME, 0));
        trail(new Trail("soul", "Seelenfeuer", Material.SOUL_LANTERN, Particle.SOUL_FIRE_FLAME, 250));
        trail(new Trail("spark", "Funken", Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK, 300));
        trail(new Trail("endrod", "Sternenstaub", Material.END_ROD, Particle.END_ROD, 350));
        trail(new Trail("hearts", "Herzen", Material.RED_DYE, Particle.HEART, 400));
        trail(new Trail("cherry", "Kirschblüten", Material.CHERRY_LEAVES, Particle.CHERRY_LEAVES, 450));
        trail(new Trail("notes", "Musik", Material.NOTE_BLOCK, Particle.NOTE, 450));
        trail(new Trail("totem", "Totem", Material.TOTEM_OF_UNDYING, Particle.TOTEM_OF_UNDYING, 700));

        killfx(new KillFx("dust", "Teamfarbe", Material.REDSTONE, 0));
        killfx(new KillFx("firework", "Feuerwerk", Material.FIREWORK_ROCKET, 300));
        killfx(new KillFx("lightning", "Blitz", Material.LIGHTNING_ROD, 450));
        killfx(new KillFx("hearts", "Herzregen", Material.PINK_DYE, 350));
        killfx(new KillFx("souls", "Seelen", Material.SOUL_SAND, 500));

        boost(new Boost("b_tomahawk", "Start-Tomahawk", Material.CREEPER_SPAWN_EGG, "tomahawk", 1, 40,
                "Eine Tomahawk direkt beim Start."));
        boost(new Boost("b_shield", "Start-Schild", Material.SNOWBALL, "shield", 1, 30,
                "Ein Schild direkt beim Start."));
        boost(new Boost("b_platform", "Start-Plattform", Material.BLAZE_ROD, "platform", 1, 35,
                "Eine Rettungsplattform beim Start."));
        boost(new Boost("b_arrows", "Pfeil-Bündel", Material.ARROW, "arrows", 3, 20,
                "Drei Extra-Ladungen Pfeile."));
        boost(new Boost("b_juggernaut", "Start-Juggernaut", Material.GHAST_SPAWN_EGG, "juggernaut", 1, 150,
                "Die dicke Rakete direkt zum Start."));

        KIT_PRICES.put(Kit.STANDARD, 0);
        KIT_PRICES.put(Kit.ANGREIFER, 400);
        KIT_PRICES.put(Kit.VERTEIDIGER, 400);
        KIT_PRICES.put(Kit.TECHNIKER, 600);
        KIT_PRICES.put(Kit.SPRINTER, 600);
    }

    private static void trail(Trail t) { TRAILS.put(t.id(), t); }
    private static void killfx(KillFx k) { KILLFX.put(k.id(), k); }
    private static void boost(Boost b) { BOOSTS.put(b.id(), b); }

    private final MissileWarsPlugin plugin;

    public Shop(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("coins.enabled", true);
    }

    // ── Coins ────────────────────────────────────────────────────────

    /** Coins vergeben (Betrag aus coins.<reason> in der Config). */
    public void reward(Player p, String reason, String label) {
        if (!enabled() || p == null || !p.isOnline()) return;
        int amount = plugin.getConfig().getInt("coins." + reason, 0);
        if (amount <= 0) return;
        plugin.stats().add(p, Stat.COINS, amount);
        p.sendActionBar(Msg.mm("<acc>+" + amount + " Coins <dim>(" + label + ")"));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.8f);
    }

    public int coins(Player p) {
        return plugin.stats().coins(p);
    }

    // ── Kosmetik anwenden ────────────────────────────────────────────

    public Particle trail(Player owner) {
        if (owner == null) return Particle.FLAME;
        Trail t = TRAILS.get(plugin.stats().selected(owner, "trail", "flame"));
        return t == null ? Particle.FLAME : t.particle();
    }

    public void playKillEffect(Player killer, Location at, Color teamColor) {
        World w = at.getWorld();
        String id = killer == null ? "dust" : plugin.stats().selected(killer, "killfx", "dust");
        switch (id) {
            case "firework" -> {
                w.spawnParticle(Particle.FIREWORK, at, 60, 0.3, 0.5, 0.3, 0.15);
                w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
            }
            case "lightning" -> w.strikeLightningEffect(at);
            case "hearts" -> {
                w.spawnParticle(Particle.HEART, at, 14, 0.6, 0.6, 0.6, 0);
                w.playSound(at, Sound.ENTITY_CAT_PURR, 1f, 1.4f);
            }
            case "souls" -> {
                w.spawnParticle(Particle.SOUL, at, 25, 0.4, 0.7, 0.4, 0.04);
                w.playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 1f, 1f);
            }
            default -> {
                w.spawnParticle(Particle.DUST, at, 40, 0.4, 0.6, 0.4, 0, new Particle.DustOptions(teamColor, 1.6f));
                w.spawnParticle(Particle.LARGE_SMOKE, at, 10, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    public boolean ownsKit(Player p, Kit k) {
        int price = KIT_PRICES.getOrDefault(k, 0);
        return price <= 0 || !enabled() || plugin.stats().has(p, "kit:" + k.name());
    }

    // ── GUI ──────────────────────────────────────────────────────────

    private String coinLine(Player p) {
        return "<mut>Deine Coins: <acc>" + coins(p) + " ⛃";
    }

    public void open(Player p) {
        if (!enabled()) {
            Msg.send(p, "<bad>Der Shop ist deaktiviert.");
            return;
        }
        Gui gui = new Gui(3, "<logo> <dim>» <acc>Shop").frame();
        gui.set(4, Gui.item(Material.SUNFLOWER, "<acc><bold>" + coins(p) + " Coins",
                "<mut>Verdienen: Kills, Siege, Portaltreffer,", "<mut>Raketen, First Blood & Killstreaks."), null);
        gui.set(10, Gui.item(Material.CHEST, "<acc><bold>Kits", "<mut>Dauerhaft freischalten", "", "<acc>» Öffnen"), this::openKits);
        gui.set(12, Gui.item(Material.FIREWORK_STAR, "<pri><bold>Raketen-Spuren", "<mut>Partikel hinter deinen Raketen", "", "<acc>» Öffnen"), this::openTrails);
        gui.set(14, Gui.item(Material.WITHER_ROSE, "<bad><bold>Kill-Effekte", "<mut>Effekt, wenn du jemanden erledigst", "", "<acc>» Öffnen"), this::openKillFx);
        gui.set(16, Gui.item(Material.EXPERIENCE_BOTTLE, "<ok><bold>Rundenboosts", "<mut>Extra-Items für die nächste Runde", "", "<acc>» Öffnen"), this::openBoosts);
        gui.set(22, Gui.item(Material.BARRIER, "<bad>Schließen"), Player::closeInventory);
        gui.open(p);
    }

    private void openKits(Player p) {
        Gui gui = new Gui(3, "<dim>» <acc>Shop · Kits").frame();
        int slot = 11;
        for (Map.Entry<Kit, Integer> e : KIT_PRICES.entrySet()) {
            Kit k = e.getKey();
            int price = e.getValue();
            boolean owned = ownsKit(p, k);
            List<String> lore = new ArrayList<>(List.of(k.description().split("\n")));
            lore.add("");
            lore.add(owned ? "<ok>✔ Freigeschaltet <dim>– im Kit-Menü auswählen" : "<mut>Preis: <acc>" + price + " Coins");
            lore.add(coinLine(p));
            gui.set(slot++, Gui.item(k.icon(), "<acc><bold>" + k.displayName(), lore.toArray(String[]::new)), pl -> {
                if (ownsKit(pl, k)) return;
                if (buy(pl, price, "kit:" + k.name(), k.displayName())) openKits(pl);
            });
        }
        gui.set(22, Gui.item(Material.ARROW, "<mut>« Zurück"), this::open);
        gui.open(p);
    }

    private void openTrails(Player p) {
        Gui gui = new Gui(4, "<dim>» <pri>Shop · Raketen-Spuren").frame();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        String sel = plugin.stats().selected(p, "trail", "flame");
        for (Trail t : TRAILS.values()) {
            boolean owned = t.price() <= 0 || plugin.stats().has(p, "trail:" + t.id());
            boolean active = sel.equals(t.id());
            gui.set(slots[i++], cosmeticItem(p, t.icon(), "<pri><bold>" + t.name(), t.price(), owned, active), pl -> {
                if (cosmeticClick(pl, "trail", t.id(), "trail:" + t.id(), t.name(), t.price())) openTrails(pl);
            });
        }
        gui.set(31, Gui.item(Material.ARROW, "<mut>« Zurück"), this::open);
        gui.open(p);
    }

    private void openKillFx(Player p) {
        Gui gui = new Gui(3, "<dim>» <bad>Shop · Kill-Effekte").frame();
        int slot = 11;
        String sel = plugin.stats().selected(p, "killfx", "dust");
        for (KillFx k : KILLFX.values()) {
            boolean owned = k.price() <= 0 || plugin.stats().has(p, "killfx:" + k.id());
            boolean active = sel.equals(k.id());
            gui.set(slot++, cosmeticItem(p, k.icon(), "<bad><bold>" + k.name(), k.price(), owned, active), pl -> {
                if (cosmeticClick(pl, "killfx", k.id(), "killfx:" + k.id(), k.name(), k.price())) openKillFx(pl);
            });
        }
        gui.set(22, Gui.item(Material.ARROW, "<mut>« Zurück"), this::open);
        gui.open(p);
    }

    private void openBoosts(Player p) {
        Gui gui = new Gui(3, "<dim>» <ok>Shop · Rundenboosts").frame();
        Game g = plugin.games().get(p);
        boolean canBuy = g != null && (g.state() == Game.State.WAITING || g.state() == Game.State.COUNTDOWN);
        int slot = 11;
        for (Boost b : BOOSTS.values()) {
            boolean bought = g != null && g.hasBoost(p, b.id());
            List<String> lore = new ArrayList<>();
            lore.add("<mut>" + b.desc());
            lore.add("");
            if (bought) lore.add("<ok>✔ Für diese Runde gekauft");
            else if (!canBuy) lore.add("<bad>Nur in der Wartelobby kaufbar");
            else lore.add("<mut>Preis: <acc>" + b.price() + " Coins");
            lore.add(coinLine(p));
            gui.set(slot++, Gui.item(b.icon(), "<ok><bold>" + b.name(), lore.toArray(String[]::new)), pl -> {
                Game gg = plugin.games().get(pl);
                if (gg == null || !(gg.state() == Game.State.WAITING || gg.state() == Game.State.COUNTDOWN)) {
                    Msg.send(pl, "<bad>Boosts gibt es nur in der Wartelobby vor dem Start.");
                    return;
                }
                if (gg.hasBoost(pl, b.id())) return;
                if (!plugin.stats().spend(pl, b.price())) {
                    notEnough(pl, b.price());
                    return;
                }
                gg.addBoost(pl, b.id());
                Msg.send(pl, "<ok>Gekauft: <txt>" + b.name() + " <dim>(-" + b.price() + " Coins)");
                pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                openBoosts(pl);
            });
        }
        gui.set(22, Gui.item(Material.ARROW, "<mut>« Zurück"), this::open);
        gui.open(p);
    }

    private org.bukkit.inventory.ItemStack cosmeticItem(Player p, Material icon, String name, int price, boolean owned, boolean active) {
        List<String> lore = new ArrayList<>();
        if (active) lore.add("<ok>✔ Aktiv");
        else if (owned) lore.add("<acc>» Klicken zum Auswählen");
        else {
            lore.add("<mut>Preis: <acc>" + price + " Coins");
            lore.add("<acc>» Klicken zum Kaufen");
        }
        lore.add(coinLine(p));
        org.bukkit.inventory.ItemStack it = Gui.item(icon, name, lore.toArray(String[]::new));
        return active ? Gui.glow(it) : it;
    }

    /** Kaufen oder auswählen. @return true wenn sich etwas geändert hat */
    private boolean cosmeticClick(Player p, String category, String id, String unlock, String name, int price) {
        boolean owned = price <= 0 || plugin.stats().has(p, unlock);
        if (!owned && !buy(p, price, unlock, name)) return false;
        plugin.stats().select(p, category, id);
        Msg.send(p, "<ok>Ausgewählt: <txt>" + name);
        return true;
    }

    private boolean buy(Player p, int price, String unlock, String name) {
        if (!plugin.stats().spend(p, price)) {
            notEnough(p, price);
            return false;
        }
        plugin.stats().unlock(p, unlock);
        plugin.stats().save();
        Msg.send(p, "<ok>Freigeschaltet: <txt>" + name + " <dim>(-" + price + " Coins)");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        return true;
    }

    private void notEnough(Player p, int price) {
        Msg.send(p, "<bad>Nicht genug Coins. <mut>Du hast <acc>" + coins(p) + "<mut>, brauchst <acc>" + price + "<mut>.");
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
    }
}
