package de.crafterteam.missilewars.gui;

import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Inventar-Menü mit Rahmen und Klick-Aktionen pro Slot. */
public class Gui implements InventoryHolder {

    private final Inventory inv;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    public Gui(int rows, String title) {
        this.inv = Bukkit.createInventory(this, rows * 9, Msg.mm(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    /**
     * Rahmen im MissileWars-Look: dunkler Rand, oben ein Farbverlauf (Rot → Orange → Gold → Orange → Rot),
     * leere Innenfelder dezent hellgrau.
     */
    public Gui frame() {
        int size = inv.getSize();
        int rows = size / 9;
        ItemStack edge = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack inner = pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        Material[] top = {Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
                Material.BLACK_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE};
        for (int i = 0; i < size; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0) inv.setItem(i, pane(top[c]));
            else if (r == rows - 1 && (c == 0 || c == 8)) inv.setItem(i, pane(Material.ORANGE_STAINED_GLASS_PANE));
            else if (r == rows - 1 || c == 0 || c == 8) inv.setItem(i, edge);
            else inv.setItem(i, inner);
        }
        return this;
    }

    public Gui fill(Material pane) {
        ItemStack it = pane(pane);
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, it);
        return this;
    }

    public Gui set(int slot, ItemStack item, Consumer<Player> action) {
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    public void open(Player p) {
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    public void handle(InventoryClickEvent e) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != inv) return;
        Consumer<Player> a = actions.get(e.getSlot());
        if (a == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.8f);
        a.accept(p);
    }

    // ── Item-Helfer ──────────────────────────────────────────────────

    public static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(Msg.item(" "));
            meta.setHideTooltip(true);
        });
        return it;
    }

    public static ItemStack item(Material m, String name, String... lore) {
        return item(new ItemStack(m), name, lore);
    }

    public static ItemStack item(ItemStack it, String name, String... lore) {
        it.editMeta(meta -> {
            meta.displayName(Msg.item(name));
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            if (lore.length > 0) l.add(Msg.item("<dim>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));   // Trennlinie unter dem Namen
            for (String s : lore) l.add(Msg.item(s));
            meta.lore(l);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }

    public static ItemStack head(OfflinePlayer owner, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        it.editMeta(SkullMeta.class, m -> m.setOwningPlayer(owner));
        return item(it, name, lore);
    }

    public static ItemStack glow(ItemStack it) {
        it.editMeta(m -> m.setEnchantmentGlintOverride(true));
        return it;
    }
}
