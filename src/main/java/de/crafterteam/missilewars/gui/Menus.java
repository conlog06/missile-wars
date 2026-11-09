package de.crafterteam.missilewars.gui;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.game.Game;
import de.crafterteam.missilewars.game.Team;
import de.crafterteam.missilewars.map.MapConfig;
import de.crafterteam.missilewars.stats.StatsManager;
import de.crafterteam.missilewars.stats.StatsManager.Stat;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Alle Inventar-Menüs im einheitlichen Design. */
public final class Menus {

    private Menus() {
    }

    // ── Team-Auswahl ─────────────────────────────────────────────────

    public static void team(MissileWarsPlugin plugin, Game g, Player viewer) {
        Gui gui = new Gui(3, "<dim>» <pri>Team wählen").frame();
        gui.set(11, teamItem(g, Team.GREEN, viewer), p -> pick(g, p, Team.GREEN));
        gui.set(13, Gui.item(Material.NETHER_STAR, "<txt><bold>Zufällig", "<mut>Ins kleinere Team", "", "<acc>» Klicken"),
                p -> {
                    Team cur = g.team(p);
                    if (cur != null && g.teamPlayers(cur.other()).size() < g.teamPlayers(cur).size()) pick(g, p, cur.other());
                    else p.closeInventory();
                });
        gui.set(15, teamItem(g, Team.RED, viewer), p -> pick(g, p, Team.RED));
        gui.set(22, Gui.item(Material.BARRIER, "<bad>Schließen"), Player::closeInventory);
        gui.open(viewer);
    }

    private static void pick(Game g, Player p, Team t) {
        p.closeInventory();
        if (g.team(p) != t) g.changeTeam(p, t);
    }

    private static ItemStack teamItem(Game g, Team t, Player viewer) {
        List<Player> members = g.teamPlayers(t);
        List<String> lore = new ArrayList<>();
        lore.add("<mut>" + members.size() + " Spieler");
        lore.add("");
        for (Player m : members) lore.add("<dim>• <txt>" + Msg.esc(m.getName()));
        if (members.isEmpty()) lore.add("<dim>Noch leer");
        lore.add("");
        lore.add(g.team(viewer) == t ? "<ok>✔ Dein Team" : "<acc>» Beitreten");
        ItemStack it = Gui.item(new ItemStack(t == Team.GREEN ? Material.LIME_WOOL : Material.RED_WOOL,
                Math.max(1, Math.min(64, members.size()))), "<bold>" + t.colored(), lore.toArray(String[]::new));
        return g.team(viewer) == t ? Gui.glow(it) : it;
    }

    // ── Spielmenü (Kompass) ──────────────────────────────────────────

    public static void main(MissileWarsPlugin plugin, Player viewer) {
        Gui gui = new Gui(5, "<logo> <dim>» <mut>Spielmenü").frame();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        for (MapConfig m : plugin.maps().all()) {
            if (i >= slots.length) break;
            if (!m.ready() || !m.complete()) continue;
            Game g = plugin.games().get(m.name());
            String status;
            Material mat;
            if (g == null) { status = "<ok>● Frei – neue Runde starten"; mat = Material.LIME_CONCRETE; }
            else switch (g.state()) {
                case WAITING -> { status = "<ok>● Wartet auf Spieler"; mat = Material.LIME_CONCRETE; }
                case COUNTDOWN -> { status = "<acc>● Startet gleich"; mat = Material.YELLOW_CONCRETE; }
                case RUNNING -> { status = "<pri>● Läuft"; mat = Material.ORANGE_CONCRETE; }
                default -> { status = "<bad>● Endet"; mat = Material.RED_CONCRETE; }
            }
            int count = g == null ? 0 : g.playerCount();
            int max = plugin.getConfig().getInt("players.max", 16);
            String type = m.describe();
            ItemStack it = Gui.item(mat, "<pri><bold>" + m.name().toUpperCase(Locale.ROOT),
                    "<mut>" + type, "", status, "<mut>Spieler: <txt>" + count + "<dim>/" + max,
                    "<mut>" + Msg.bar(count / (double) max, 16, "pri"), "",
                    g != null && g.state() == Game.State.RUNNING ? "<acc>» Klicken: Mitspielen / Zuschauen" : "<acc>» Klicken: Beitreten");
            String name = m.name();
            gui.set(slots[i++], it, p -> {
                p.closeInventory();
                Game cur = plugin.games().get(p);
                if (cur != null && cur.map().name().equals(name)) return;
                if (cur != null) {
                    if (cur.state() == Game.State.RUNNING) {
                        Msg.send(p, "<bad>Du bist mitten in einer Runde.");
                        return;
                    }
                    cur.leave(p, false);
                }
                plugin.games().quickJoin(p, name, false);
            });
        }
        if (i == 0) gui.set(13, Gui.item(Material.BARRIER, "<bad>Keine Map bereit"), null);

        StatsManager.Entry e = plugin.stats().get(viewer.getUniqueId());
        gui.set(38, Gui.head(viewer, "<pri><bold>Deine Statistiken", statLore(e).toArray(String[]::new)), null);
        gui.set(40, Gui.item(Material.GOLD_BLOCK, "<acc><bold>Bestenliste", "<mut>Top 10 nach Siegen", "", "<acc>» Klicken"),
                p -> top(plugin, p));
        gui.set(39, Gui.item(Material.EMERALD, "<ok><bold>Shop", "<mut>Coins: <acc>" + plugin.stats().coins(viewer), "", "<acc>» Öffnen"),
                p -> plugin.shop().open(p));
        gui.set(41, Gui.item(Material.KNOWLEDGE_BOOK, "<pri><bold>Raketen-Übersicht", "", "<acc>» Öffnen"),
                p -> missiles(plugin, p));
        gui.set(42, Gui.item(Material.BARRIER, "<bad>Schließen"), Player::closeInventory);
        gui.open(viewer);
    }

    public static List<String> statLore(StatsManager.Entry e) {
        List<String> l = new ArrayList<>();
        if (e == null) {
            l.add("<mut>Noch keine Runden gespielt.");
            return l;
        }
        int games = e.get(Stat.GAMES), wins = e.get(Stat.WINS), kills = e.get(Stat.KILLS), deaths = e.get(Stat.DEATHS);
        l.add("<mut>Runden: <txt>" + games);
        l.add("<mut>Siege: <ok>" + wins + " <dim>(" + (games == 0 ? 0 : Math.round(wins * 100.0 / games)) + "%)");
        l.add("<mut>Kills: <ok>" + kills + " <dim>| <mut>Tode: <bad>" + deaths);
        l.add("<mut>K/D: <txt>" + String.format(Locale.ROOT, "%.2f", deaths == 0 ? kills : kills / (double) deaths));
        l.add("<mut>Raketen: <info>" + e.get(Stat.MISSILES));
        l.add("<mut>Portale zerstört: <acc>" + e.get(Stat.PORTALS));
        l.add("<mut>Coins: <acc>" + e.get(Stat.COINS) + " ⛃");
        return l;
    }

    public static void top(MissileWarsPlugin plugin, Player viewer) {
        Gui gui = new Gui(4, "<logo> <dim>» <acc>Bestenliste").frame();
        List<StatsManager.Entry> top = plugin.stats().top(Stat.WINS, 14);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            StatsManager.Entry e = top.get(i);
            String rank = i == 0 ? "<acc>#1" : i == 1 ? "<txt>#2" : i == 2 ? "<pri>#3" : "<mut>#" + (i + 1);
            List<String> lore = statLore(e);
            gui.set(slots[i], Gui.head(org.bukkit.Bukkit.getOfflinePlayer(e.id), rank + " <txt>" + Msg.esc(e.name),
                    lore.toArray(String[]::new)), null);
        }
        if (top.isEmpty()) gui.set(13, Gui.item(Material.PAPER, "<mut>Noch keine Einträge"), null);
        gui.set(31, Gui.item(Material.ARROW, "<mut>« Zurück"), p -> main(plugin, p));
        gui.open(viewer);
    }

    // ── Zuschauer-Teleporter ─────────────────────────────────────────

    public static void spectator(Game g, Player viewer) {
        List<Player> list = g.players();
        int rows = Math.max(3, Math.min(6, 2 + (list.size() + 6) / 7));
        Gui gui = new Gui(rows, "<info>Spieler-Teleporter").frame();
        int slot = 10;
        for (Player target : list) {
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot >= rows * 9 - 9) break;
            Team t = g.team(target);
            gui.set(slot++, Gui.head(target, "<bold>" + (t == null ? "<txt>" : "<" + t.tag() + ">") + Msg.esc(target.getName()),
                    "<mut>Team " + (t == null ? "-" : t.colored()),
                    "<mut>Leben: <bad>" + Math.round(target.getHealth()) + "❤", "", "<acc>» Teleportieren"), p -> {
                p.closeInventory();
                if (target.isOnline() && target.getWorld().equals(p.getWorld())) p.teleport(target.getLocation());
            });
        }
        gui.open(viewer);
    }

    // ── Raketen-Übersicht ────────────────────────────────────────────

    public static void missiles(MissileWarsPlugin plugin, Player viewer) {
        Gui gui = new Gui(6, "<logo> <dim>» <pri>Raketen & Items").frame();
        de.crafterteam.missilewars.game.Game g = plugin.games().get(viewer);
        de.crafterteam.missilewars.game.Kit kit = g == null ? de.crafterteam.missilewars.game.Kit.STANDARD : g.kit(viewer);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        for (de.crafterteam.missilewars.missile.MissileType t : de.crafterteam.missilewars.missile.MissileType.values()) {
            if (i >= slots.length) break;
            int ticks = Math.max(1, plugin.getConfig().getInt("missile.speed." + t.id(), 4));
            double chance = plugin.items().chance(t.id(), kit) * 100;
            List<String> lore = new ArrayList<>();
            lore.add("<mut>" + t.description());
            lore.add("");
            lore.add("<mut>Tempo      <txt>" + String.format(Locale.ROOT, "%.1f", 20.0 / ticks) + " Blöcke/s");
            lore.add("<mut>Ladung     <bad>" + t.tntCount() + " TNT");
            lore.add("<mut>Größe      <txt>" + t.size() + " <dim>(B×H×L)");
            lore.add("<mut>Mitfahren  " + (t.rideable() ? "<ok>✔ ja" : "<dim>✘ nein"));
            lore.add("<mut>Besonders  <acc>" + t.special());
            lore.add("");
            lore.add("<mut>Chance pro Item: <pri>" + String.format(Locale.ROOT, "%.1f", chance) + "%"
                    + (kit != de.crafterteam.missilewars.game.Kit.STANDARD ? " <dim>(Kit " + kit.displayName() + ")" : ""));
            gui.set(slots[i++], Gui.item(t.item(), "<pri><bold>" + t.displayName(), lore.toArray(String[]::new)), null);
        }
        // Hilfsmittel
        Object[][] utils = {
                {Material.SNOWBALL, "shield", "<info><bold>Schild", "Werfen – klappt zu einer Glaswand auf."},
                {Material.FIRE_CHARGE, "fireball", "<bad><bold>Feuerball", "Abfeuern – lässt sich zurückschlagen."},
                {Material.BLAZE_ROD, "platform", "<info><bold>Rettungsplattform", "Schleim-Plattform unter dir für 8 s."},
                {Material.WIND_CHARGE, "windcharge", "<txt><bold>Windkugel", "Weite Sprünge, Gegner wegschleudern."},
                {Material.ENDER_PEARL, "enderpearl", "<ok><bold>Enderperle", "Teleport – ab in die Gegnerbasis."},
                {Material.ARROW, "arrows", "<acc><bold>Pfeile", "Für den Flammenbogen – zündet TNT."},
        };
        int[] uslots = {37, 38, 39, 41, 42, 43};
        for (int u = 0; u < utils.length; u++) {
            double chance = plugin.items().chance((String) utils[u][1], kit) * 100;
            gui.set(uslots[u], Gui.item((Material) utils[u][0], (String) utils[u][2], "<mut>" + utils[u][3], "",
                    "<mut>Chance pro Item: <pri>" + String.format(Locale.ROOT, "%.1f", chance) + "%"), null);
        }
        int interval = plugin.getConfig().getInt("item-interval", 20);
        gui.set(40, Gui.item(Material.CLOCK, "<acc><bold>Item-Regen", "<mut>Alle <txt>" + interval + " s<mut> ein zufälliges Item.",
                "<mut>Volle Stapel werden übersprungen.", "", "<dim>Halte eine Rakete, um die Vorschau zu sehen."), null);
        gui.open(viewer);
    }

    // ── Kits ─────────────────────────────────────────────────────────

    public static void kits(MissileWarsPlugin plugin, de.crafterteam.missilewars.game.Game g, Player viewer) {
        Gui gui = new Gui(3, "<dim>» <acc>Kit wählen").frame();
        int slot = 11;
        for (de.crafterteam.missilewars.game.Kit k : de.crafterteam.missilewars.game.Kit.values()) {
            boolean sel = g.kit(viewer) == k;
            boolean owned = plugin.shop().ownsKit(viewer, k);
            List<String> lore = new ArrayList<>(List.of(k.description().split("\n")));
            lore.add("");
            lore.add(sel ? "<ok>✔ Ausgewählt" : owned ? "<acc>» Auswählen"
                    : "<bad>🔒 Gesperrt <dim>– im Shop für " + de.crafterteam.missilewars.shop.Shop.KIT_PRICES.get(k) + " Coins");
            ItemStack it = Gui.item(k.icon(), "<acc><bold>" + k.displayName(), lore.toArray(String[]::new));
            gui.set(slot++, sel ? Gui.glow(it) : it, p -> {
                g.setKit(p, k);
                p.closeInventory();
            });
        }
        gui.open(viewer);
    }

    // ── Wartelobby-Menü (ersetzt die vielen Hotbar-Items) ────────────

    public static void lobby(MissileWarsPlugin plugin, de.crafterteam.missilewars.game.Game g, Player viewer) {
        Gui gui = new Gui(4, "<logo> <dim>» <mut>Wartelobby").frame();
        boolean vote = g.votingOpen() && g.votableMaps().size() > 1;
        gui.set(10, Gui.item(vote ? Material.FILLED_MAP : Material.MAP, "<acc><bold>Map-Abstimmung",
                "<mut>Aktuell: <txt>" + g.map().name() + " <dim>(" + g.map().describe() + ")", "",
                vote ? "<acc>» Abstimmen" : "<dim>Abstimmung geschlossen"),
                p -> { p.closeInventory(); de.crafterteam.missilewars.game.VoteMenu.open(plugin, g, p); });
        gui.set(11, Gui.item(Material.CHEST, "<acc><bold>Kit", "<mut>Gewählt: <txt>" + g.kit(viewer).displayName(), "", "<acc>» Wählen"),
                p -> kits(plugin, g, p));
        gui.set(12, Gui.item(Material.EMERALD, "<ok><bold>Shop", "<mut>Coins: <acc>" + plugin.stats().coins(viewer), "", "<acc>» Öffnen"),
                p -> plugin.shop().open(p));
        gui.set(13, Gui.item(Material.KNOWLEDGE_BOOK, "<pri><bold>Raketen-Übersicht", "", "<acc>» Öffnen"), p -> missiles(plugin, p));
        gui.set(14, Gui.item(Material.GOLDEN_HELMET, "<acc><bold>Erfolge & Level", "<mut>Level " + plugin.progression().level(viewer), "",
                "<acc>» Öffnen"), p -> plugin.progression().openAchievements(p));
        gui.set(15, Gui.head(viewer, "<pri><bold>Statistiken & Bestenliste", "", "<acc>» Öffnen"), p -> main(plugin, p));
        gui.set(16, Gui.item(Material.CLOCK, "<acc><bold>Tagesbelohnung", plugin.progression().dailyAvailable(viewer)
                ? "<ok>Bereit zum Abholen!" : "<dim>Heute schon abgeholt", "", "<acc>» Abholen"), p -> {
            plugin.progression().claimDaily(p);
            p.closeInventory();
        });
        if (plugin.games().maxGames() > 1) {
            gui.set(20, Gui.item(Material.COMPASS, "<pri><bold>Lobby wechseln",
                    "<mut>Du bist in Lobby <txt>#" + g.id(), "<mut>Offene Runden: <txt>" + plugin.games().all().size(), "",
                    "<acc>» Übersicht"), p -> lobbies(plugin, p));
        }
        if (!g.layerOptions().isEmpty()) {
            gui.set(22, Gui.item(Material.LIME_STAINED_GLASS, "<info><bold>Glasdicke abstimmen",
                    "<mut>Wie viele Blöcke Glas vor den Portalen?", "<mut>Mehr Glas = längere Runden.", "",
                    "<mut>Aktuell: <txt>" + g.layers() + " Blöcke", "", "<acc>» Abstimmen"), p -> glassVote(plugin, g, p));
        }
        gui.set(31, Gui.item(Material.BARRIER, "<bad>Schließen"), Player::closeInventory);
        gui.open(viewer);
    }

    // ── Lobby-Auswahl (mehrere Runden gleichzeitig) ──────────────────

    public static void lobbies(MissileWarsPlugin plugin, Player viewer) {
        List<de.crafterteam.missilewars.game.Game> all = new ArrayList<>(plugin.games().all());
        Gui gui = new Gui(4, "<logo> <dim>» <pri>Lobbys & Runden").frame();
        de.crafterteam.missilewars.game.Game mine = plugin.games().get(viewer);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        for (de.crafterteam.missilewars.game.Game g : all) {
            if (i >= slots.length) break;
            Material icon;
            String status;
            String action;
            switch (g.state()) {
                case WAITING -> { icon = Material.LIME_CONCRETE; status = "<ok>● Wartet"; action = "<acc>» Beitreten"; }
                case COUNTDOWN -> { icon = Material.YELLOW_CONCRETE; status = "<acc>● Startet gleich"; action = "<acc>» Beitreten"; }
                case RUNNING -> { icon = Material.RED_CONCRETE; status = "<bad>● Läuft"; action = "<info>» Zuschauen"; }
                default -> { icon = Material.GRAY_CONCRETE; status = "<dim>● Endet"; action = "<dim>Gleich vorbei"; }
            }
            boolean here = g == mine;
            ItemStack it = Gui.item(new ItemStack(icon, Math.max(1, Math.min(64, g.playerCount()))),
                    "<pri><bold>Lobby #" + g.id() + (here ? " <ok>(du)" : ""),
                    "<mut>Status: " + status,
                    "<mut>Map: <txt>" + g.map().name() + " <dim>" + g.map().describe(),
                    "<mut>Spieler: <txt>" + g.playerCount() + "<dim>/" + g.maxPlayers()
                            + (g.spectatorCount() > 0 ? " <dim>· " + g.spectatorCount() + " Zuschauer" : ""),
                    "", here ? "<ok>✔ Hier bist du" : action);
            gui.set(slots[i++], here ? Gui.glow(it) : it, here ? null : p -> {
                p.closeInventory();
                plugin.games().switchTo(p, g);
            });
        }
        if (all.isEmpty()) gui.set(13, Gui.item(Material.BARRIER, "<mut>Keine Runde offen", "<dim>Mit /mw join öffnest du eine."), null);
        gui.set(31, Gui.item(Material.ARROW, "<mut>« Zurück"), p -> {
            de.crafterteam.missilewars.game.Game g = plugin.games().get(p);
            if (g != null && (g.state() == de.crafterteam.missilewars.game.Game.State.WAITING
                    || g.state() == de.crafterteam.missilewars.game.Game.State.COUNTDOWN)) lobby(plugin, g, p);
            else p.closeInventory();
        });
        gui.open(viewer);
    }

    public static void glassVote(MissileWarsPlugin plugin, de.crafterteam.missilewars.game.Game g, Player viewer) {
        Gui gui = new Gui(3, "<dim>» <info>Glasdicke abstimmen").frame();
        List<Integer> opts = g.layerOptions();
        int[] slots = opts.size() <= 3 ? new int[]{11, 13, 15} : new int[]{10, 11, 12, 13, 14, 15, 16};
        Material[] icons = {Material.GLASS_PANE, Material.GLASS, Material.TINTED_GLASS, Material.OBSIDIAN};
        for (int i = 0; i < opts.size() && i < slots.length; i++) {
            int n = opts.get(i);
            int votes = g.layerVotes(n);
            String len = n <= 15 ? "Schnelle Runden" : n <= 24 ? "Mittlere Runden" : "Lange Belagerungen";
            ItemStack it = Gui.item(new ItemStack(icons[Math.min(i, icons.length - 1)], Math.max(1, Math.min(64, n))),
                    "<info><bold>" + n + " Blöcke Glas", "<mut>" + len, "", "<mut>Stimmen: <acc>" + votes,
                    g.layers() == n ? "<ok>✔ aktuell" : "", "", "<acc>» Abstimmen");
            gui.set(slots[i], g.layers() == n ? Gui.glow(it) : it, p -> {
                g.voteLayers(p, n);
                glassVote(plugin, g, p);
            });
        }
        gui.set(22, Gui.item(Material.ARROW, "<mut>« Zurück"), p -> lobby(plugin, g, p));
        gui.open(viewer);
    }
}
