package de.crafterteam.missilewars.stats;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.gui.Gui;
import de.crafterteam.missilewars.stats.StatsManager.Entry;
import de.crafterteam.missilewars.stats.StatsManager.Stat;
import de.crafterteam.missilewars.util.Msg;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Level, Erfolge und Tagesbelohnung. */
public final class Progression {

    public record Achievement(String id, String name, String desc, Material icon, Stat stat, int goal, int reward) {}

    public static final List<Achievement> ACHIEVEMENTS = List.of(
            new Achievement("first_win", "Erster Sieg", "Gewinne eine Runde", Material.GOLDEN_SWORD, Stat.WINS, 1, 50),
            new Achievement("wins_25", "Veteran", "Gewinne 25 Runden", Material.NETHERITE_SWORD, Stat.WINS, 25, 300),
            new Achievement("kills_10", "Schütze", "Erziele 10 Kills", Material.BOW, Stat.KILLS, 10, 50),
            new Achievement("kills_100", "Scharfschütze", "Erziele 100 Kills", Material.CROSSBOW, Stat.KILLS, 100, 300),
            new Achievement("missiles_50", "Raketentechniker", "Starte 50 Raketen", Material.TNT, Stat.MISSILES, 50, 75),
            new Achievement("missiles_500", "Raketenfabrik", "Starte 500 Raketen", Material.TNT_MINECART, Stat.MISSILES, 500, 400),
            new Achievement("portal_1", "Portalbrecher", "Zerstöre ein Portal", Material.OBSIDIAN, Stat.PORTALS, 1, 75),
            new Achievement("portal_10", "Zerstörer", "Zerstöre 10 Portale", Material.CRYING_OBSIDIAN, Stat.PORTALS, 10, 300),
            new Achievement("streak_5", "Rampage", "Schaffe eine 5er-Killserie", Material.BLAZE_POWDER, Stat.BEST_STREAK, 5, 150),
            new Achievement("intercept_10", "Luftabwehr", "Fange 10 Raketen ab", Material.PHANTOM_MEMBRANE, Stat.INTERCEPTS, 10, 150),
            new Achievement("airdrop_5", "Schatzsucher", "Öffne 5 Airdrops", Material.CHEST, Stat.AIRDROPS, 5, 100),
            new Achievement("assists_25", "Teamplayer", "Sammle 25 Assists", Material.SHIELD, Stat.ASSISTS, 25, 150),
            new Achievement("games_50", "Stammgast", "Spiele 50 Runden", Material.CLOCK, Stat.GAMES, 50, 200));

    private final MissileWarsPlugin plugin;

    public Progression(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Level ────────────────────────────────────────────────────────

    public static int xp(Entry e) {
        if (e == null) return 0;
        return e.get(Stat.KILLS) * 10 + e.get(Stat.WINS) * 50 + e.get(Stat.LOSSES) * 15 + e.get(Stat.MISSILES) * 2
                + e.get(Stat.PORTALS) * 40 + e.get(Stat.ASSISTS) * 5 + e.get(Stat.INTERCEPTS) * 8 + e.get(Stat.AIRDROPS) * 10;
    }

    public static int level(int xp) {
        return 1 + (int) Math.floor(Math.sqrt(xp / 40.0));
    }

    public static int level(Entry e) {
        return level(xp(e));
    }

    /** XP, ab der ein Level beginnt. */
    public static int xpFor(int level) {
        return (level - 1) * (level - 1) * 40;
    }

    public int level(Player p) {
        return level(plugin.stats().get(p.getUniqueId()));
    }

    /** "[12]" in Akzentfarbe, für Chat & Tab. */
    public String badge(Player p) {
        int lvl = level(p);
        String col = lvl >= 50 ? "<gradient:#FF2E63:#FFC145>" : lvl >= 25 ? "<pri>" : lvl >= 10 ? "<acc>" : "<mut>";
        String close = lvl >= 50 ? "</gradient>" : "";
        return "<dim>[" + col + lvl + close + "<dim>]";
    }

    /** Nach jeder Stat-Änderung: Level-Up & Erfolge prüfen. */
    void afterStat(Player p, Entry e, int levelBefore) {
        int now = level(e);
        if (levelBefore > 0 && now > levelBefore && p.isOnline()) {
            int reward = now * 10;
            Msg.send(p, "<acc><bold>LEVEL UP!</bold> <mut>Du bist jetzt Level <pri>" + now + "<mut>. <acc>+" + reward + " Coins");
            p.showTitle(Title.title(Msg.mm("<acc><bold>Level " + now), Msg.mm("<mut>+" + reward + " Coins"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            plugin.stats().add(p, Stat.COINS, reward);
        }
        for (Achievement a : ACHIEVEMENTS) {
            String key = "ach:" + a.id();
            if (e.unlocks.contains(key) || e.get(a.stat()) < a.goal()) continue;
            e.unlocks.add(key);
            if (!p.isOnline()) continue;
            Msg.send(p, "<acc>🏆 Erfolg freigeschaltet: <txt><bold>" + a.name() + "</bold> <dim>– " + a.desc() + " <acc>+" + a.reward() + " Coins");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            plugin.stats().add(p, Stat.COINS, a.reward());
        }
    }

    // ── Tagesbelohnung ───────────────────────────────────────────────

    public boolean dailyAvailable(Player p) {
        return !String.valueOf(LocalDate.now().toEpochDay()).equals(plugin.stats().selected(p, "daily.day", ""));
    }

    public void claimDaily(Player p) {
        if (!dailyAvailable(p)) {
            Msg.send(p, "<bad>Du hast deine Tagesbelohnung heute schon abgeholt. <mut>Morgen wieder!");
            return;
        }
        long today = LocalDate.now().toEpochDay();
        long last;
        try {
            last = Long.parseLong(plugin.stats().selected(p, "daily.day", "-10"));
        } catch (NumberFormatException e) {
            last = -10;
        }
        int streak;
        try {
            streak = Integer.parseInt(plugin.stats().selected(p, "daily.streak", "0"));
        } catch (NumberFormatException e) {
            streak = 0;
        }
        streak = last == today - 1 ? Math.min(streak + 1, 7) : 1;
        int amount = plugin.getConfig().getInt("daily.base", 50) + (streak - 1) * plugin.getConfig().getInt("daily.per-day", 15);
        if (p.hasPermission("missilewars.vip")) amount *= 2;
        plugin.stats().select(p, "daily.day", String.valueOf(today));
        plugin.stats().select(p, "daily.streak", String.valueOf(streak));
        plugin.stats().add(p, Stat.COINS, amount);
        plugin.stats().save();
        Msg.send(p, "<ok>Tagesbelohnung: <acc>+" + amount + " Coins <dim>(Tag " + streak + "/7"
                + (p.hasPermission("missilewars.vip") ? ", VIP x2" : "") + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
    }

    // ── GUI ──────────────────────────────────────────────────────────

    public void openAchievements(Player p) {
        Entry e = plugin.stats().entry(p);
        long done = ACHIEVEMENTS.stream().filter(a -> e.unlocks.contains("ach:" + a.id())).count();
        Gui gui = new Gui(4, "<logo> <dim>» <acc>Erfolge <dim>(" + done + "/" + ACHIEVEMENTS.size() + ")").frame();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < ACHIEVEMENTS.size() && i < slots.length; i++) {
            Achievement a = ACHIEVEMENTS.get(i);
            boolean ok = e.unlocks.contains("ach:" + a.id());
            int have = Math.min(e.get(a.stat()), a.goal());
            List<String> lore = new ArrayList<>();
            lore.add("<mut>" + a.desc());
            lore.add("");
            lore.add(Msg.bar(have / (double) a.goal(), 14, ok ? "ok" : "acc") + " <txt>" + have + "<dim>/" + a.goal());
            lore.add("<mut>Belohnung: <acc>" + a.reward() + " Coins");
            lore.add(ok ? "<ok>✔ Freigeschaltet" : "<dim>Noch gesperrt");
            org.bukkit.inventory.ItemStack it = Gui.item(ok ? a.icon() : Material.GRAY_DYE, (ok ? "<ok><bold>" : "<mut><bold>") + a.name(),
                    lore.toArray(String[]::new));
            gui.set(slots[i], ok ? Gui.glow(it) : it, null);
        }
        int xp = xp(e), lvl = level(xp), next = xpFor(lvl + 1), cur = xpFor(lvl);
        gui.set(31, Gui.item(Material.EXPERIENCE_BOTTLE, "<acc><bold>Level " + lvl,
                "<mut>XP: <txt>" + xp, Msg.bar((xp - cur) / (double) Math.max(1, next - cur), 16, "acc"),
                "<mut>Nächstes Level bei <txt>" + next + " XP"), null);
        gui.open(p);
    }

    /** Für ToIntFunction-Nutzer (z. B. Hologramme). */
    public static ToIntFunction<Entry> levelFn() {
        return Progression::level;
    }
}
