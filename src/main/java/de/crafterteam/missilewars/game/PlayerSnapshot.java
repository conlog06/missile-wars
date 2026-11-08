package de.crafterteam.missilewars.game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;

/** Inventar & Zustand vor dem Beitritt – wird beim Verlassen wiederhergestellt. */
public record PlayerSnapshot(ItemStack[] contents, Location location, GameMode mode, double health,
                             int food, float saturation, int level, float exp,
                             Collection<PotionEffect> effects, boolean allowFlight) {

    public static PlayerSnapshot capture(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return new PlayerSnapshot(copy, p.getLocation().clone(), p.getGameMode(), p.getHealth(),
                p.getFoodLevel(), p.getSaturation(), p.getLevel(), p.getExp(),
                new ArrayList<>(p.getActivePotionEffects()), p.getAllowFlight());
    }

    public void restore(Player p, Location target) {
        reset(p);
        p.getInventory().setContents(contents);
        p.setGameMode(mode);
        p.setHealth(Math.min(health, maxHealth(p)));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
        p.addPotionEffects(effects);
        p.setAllowFlight(allowFlight);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.teleport(target != null ? target : location);
    }

    public static double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a != null ? a.getValue() : 20.0;
    }

    /** Alles zurücksetzen (leeres Inventar, volle Leben). */
    public static void reset(Player p) {
        p.getInventory().clear();
        p.setItemOnCursor(null);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setHealth(maxHealth(p));
        p.setFoodLevel(20);
        p.setSaturation(5f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setLevel(0);
        p.setExp(0f);
        p.setAllowFlight(false);
        p.setFlying(false);
        setAttackSpeed(p, 4.0);
    }

    /** 4.0 = Vanilla, sehr hoch = kein Angriffs-Cooldown (1.8-PvP). */
    public static void setAttackSpeed(Player p, double value) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_SPEED);
        if (a != null) a.setBaseValue(value);
    }
}
