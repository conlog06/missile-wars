package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.util.Msg;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Team-Auswahl als kleines Inventar-Menü. */
public final class TeamMenu implements InventoryHolder {

    public static final int SLOT_GREEN = 2;
    public static final int SLOT_RANDOM = 4;
    public static final int SLOT_RED = 6;

    private final Inventory inv;

    public TeamMenu(Game game, Player viewer) {
        inv = Bukkit.createInventory(this, 9, Msg.mm("<dark_gray>Team wählen"));
        inv.setItem(SLOT_GREEN, teamItem(game, Team.GREEN, Material.LIME_WOOL, viewer));
        inv.setItem(SLOT_RED, teamItem(game, Team.RED, Material.RED_WOOL, viewer));
        ItemStack rnd = new ItemStack(Material.WHITE_WOOL);
        rnd.editMeta(m -> m.displayName(Msg.mm("<white><bold>Zufällig").decoration(TextDecoration.ITALIC, false)));
        inv.setItem(SLOT_RANDOM, rnd);
    }

    private static ItemStack teamItem(Game g, Team t, Material mat, Player viewer) {
        List<Player> members = g.teamPlayers(t);
        ItemStack it = new ItemStack(mat, Math.max(1, Math.min(64, members.size())));
        it.editMeta(m -> {
            m.displayName(Msg.mm(t.colored() + (g.team(viewer) == t ? " <gray>(dein Team)" : ""))
                    .decoration(TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Msg.mm("<gray>" + members.size() + " Spieler").decoration(TextDecoration.ITALIC, false));
            for (Player p : members) {
                lore.add(Msg.mm("<dark_gray>• <white>" + Msg.esc(p.getName())).decoration(TextDecoration.ITALIC, false));
            }
            m.lore(lore);
        });
        return it;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
