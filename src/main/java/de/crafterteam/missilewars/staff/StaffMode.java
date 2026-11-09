package de.crafterteam.missilewars.staff;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.game.Game;
import de.crafterteam.missilewars.game.PlayerSnapshot;
import de.crafterteam.missilewars.game.Team;
import de.crafterteam.missilewars.gui.Gui;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-/Mod-Modus (/mw mod): unsichtbar, fliegen, unverwundbar, Werkzeuge in der Hotbar.
 * Rechte: missilewars.mod (Moderation), missilewars.admin (zusätzlich Runde stoppen).
 */
public final class StaffMode implements Listener {

    private final MissileWarsPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, PlayerSnapshot> active = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();

    public StaffMode(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "staff");
    }

    public boolean isStaff(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    public boolean isFrozen(Player p) {
        return frozen.contains(p.getUniqueId());
    }

    public void toggle(Player p) {
        if (isStaff(p)) disable(p, true);
        else enable(p);
    }

    private void enable(Player p) {
        Game g = plugin.games().get(p);
        if (g != null) g.leave(p, false);
        active.put(p.getUniqueId(), PlayerSnapshot.capture(p));
        PlayerSnapshot.reset(p);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvulnerable(true);
        p.setCollidable(false);
        setVanish(p, true);
        giveTools(p);
        Game cur = plugin.games().all().stream().findFirst().orElse(null);
        if (cur != null) p.teleport(cur.map().spectatorSpot(cur.world()));
        Msg.send(p, "<info>Mod-Modus <ok>an<mut>. Unsichtbar, Fliegen, Werkzeuge in der Hotbar.");
    }

    public void disable(Player p, boolean teleport) {
        PlayerSnapshot snap = active.remove(p.getUniqueId());
        setVanish(p, false);
        p.setInvulnerable(false);
        p.setCollidable(true);
        if (snap != null) {
            if (teleport) snap.restore(p, plugin.lobby());
            else snap.restoreHere(p);
        }
        Msg.send(p, "<info>Mod-Modus <bad>aus<mut>.");
    }

    public void disableAll() {
        for (UUID id : Set.copyOf(active.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) disable(p, true);
        }
    }

    private void setVanish(Player p, boolean on) {
        if (on) vanished.add(p.getUniqueId());
        else vanished.remove(p.getUniqueId());
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            if (on && !o.hasPermission("missilewars.mod")) o.hidePlayer(plugin, p);
            else o.showPlayer(plugin, p);
        }
    }

    // ── Werkzeuge ────────────────────────────────────────────────────

    private ItemStack tool(Material m, String id, String name, String... lore) {
        ItemStack it = Gui.item(m, name, lore);
        it.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id));
        return it;
    }

    private void giveTools(Player p) {
        p.getInventory().clear();
        p.getInventory().setItem(0, tool(Material.COMPASS, "players", "<info><bold>Spieler", "<mut>Teleport, Freeze, Team, Kick"));
        p.getInventory().setItem(2, tool(Material.LIME_DYE, "start", "<ok><bold>Runde starten", "<mut>Countdown auf 5 s"));
        if (p.hasPermission("missilewars.admin")) {
            p.getInventory().setItem(3, tool(Material.RED_DYE, "stop", "<bad><bold>Runde stoppen", "<mut>Nur Admins"));
        }
        p.getInventory().setItem(4, tool(Material.ENDER_EYE, "vanish", "<pri><bold>Unsichtbar an/aus"));
        p.getInventory().setItem(5, tool(Material.SPYGLASS, "arena", "<acc><bold>Zur Arena"));
        p.getInventory().setItem(8, tool(Material.BARRIER, "exit", "<bad><bold>Mod-Modus verlassen"));
    }

    private String toolId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (!isStaff(e.getPlayer())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String id = toolId(e.getItem());
        if (id == null) return;
        Game g = plugin.games().all().stream().findFirst().orElse(null);
        switch (id) {
            case "players" -> openPlayers(p);
            case "start" -> {
                if (g == null) Msg.send(p, "<bad>Keine Runde aktiv.");
                else Msg.send(p, g.forceStart() ? "<ok>Runde startet in 5 Sekunden." : "<bad>Gerade nicht möglich.");
            }
            case "stop" -> {
                if (g == null) { Msg.send(p, "<bad>Keine Runde aktiv."); return; }
                g.broadcast("<bad>Die Runde wurde von einem Admin beendet.");
                g.shutdown();
            }
            case "vanish" -> {
                boolean now = !vanished.contains(p.getUniqueId());
                setVanish(p, now);
                Msg.send(p, now ? "<mut>Du bist <ok>unsichtbar<mut>." : "<mut>Du bist <bad>sichtbar<mut>.");
            }
            case "arena" -> {
                if (g != null) p.teleport(g.map().spectatorSpot(g.world()));
            }
            case "exit" -> disable(p, true);
            default -> {
            }
        }
    }

    private void openPlayers(Player viewer) {
        Game g = plugin.games().all().stream().findFirst().orElse(null);
        List<Player> list = g == null ? List.of() : g.everyone();
        Gui gui = new Gui(6, "<info>Mod <dim>» <mut>Spieler (" + list.size() + ")").frame();
        int slot = 10;
        for (Player t : list) {
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot >= 44) break;
            Team team = g.team(t);
            gui.set(slot++, Gui.head(t, "<txt><bold>" + Msg.esc(t.getName()),
                    "<mut>Team: " + (team == null ? "<dim>Zuschauer" : team.colored()),
                    "<mut>Leben: <bad>" + Math.round(t.getHealth()) + "❤",
                    isFrozen(t) ? "<info>❄ Eingefroren" : "", "", "<acc>» Klicken für Aktionen"), p -> openActions(p, t));
        }
        gui.open(viewer);
    }

    private void openActions(Player viewer, Player target) {
        Gui gui = new Gui(3, "<info>Mod <dim>» <txt>" + Msg.esc(target.getName())).frame();
        gui.set(10, Gui.item(Material.ENDER_PEARL, "<info><bold>Teleportieren"), p -> {
            p.closeInventory();
            if (target.isOnline()) p.teleport(target.getLocation());
        });
        gui.set(11, Gui.item(Material.PACKED_ICE, isFrozen(target) ? "<info><bold>Auftauen" : "<info><bold>Einfrieren"), p -> {
            p.closeInventory();
            if (!frozen.remove(target.getUniqueId())) {
                frozen.add(target.getUniqueId());
                Msg.send(target, "<info>❄ Du wurdest von einem Teammitglied eingefroren.");
            } else {
                Msg.send(target, "<ok>Du kannst dich wieder bewegen.");
            }
            Msg.send(p, "<ok>" + Msg.esc(target.getName()) + (isFrozen(target) ? " eingefroren." : " aufgetaut."));
        });
        gui.set(12, Gui.item(Material.LIME_WOOL, "<ok><bold>Team Grün"), p -> switchTeam(p, target, Team.GREEN));
        gui.set(13, Gui.item(Material.RED_WOOL, "<bad><bold>Team Rot"), p -> switchTeam(p, target, Team.RED));
        gui.set(14, Gui.item(Material.ENDER_EYE, "<mut><bold>Zum Zuschauer machen"), p -> {
            p.closeInventory();
            Game g = plugin.games().get(target);
            if (g != null) g.spectate(target);
        });
        gui.set(15, Gui.item(Material.IRON_DOOR, "<bad><bold>Aus der Runde werfen"), p -> {
            p.closeInventory();
            Game g = plugin.games().get(target);
            if (g != null) {
                g.leave(target, true);
                Msg.send(target, "<bad>Du wurdest von einem Teammitglied aus der Runde entfernt.");
            }
        });
        gui.set(16, Gui.item(Material.SUNFLOWER, "<acc><bold>Coins ansehen", "<mut>Kontostand: <acc>" + plugin.stats().coins(target)), null);
        gui.set(22, Gui.item(Material.ARROW, "<mut>« Zurück"), this::openPlayers);
        gui.open(viewer);
    }

    private void switchTeam(Player staff, Player target, Team t) {
        staff.closeInventory();
        Game g = plugin.games().get(target);
        if (g == null) return;
        g.forceTeam(target, t);
        Msg.send(staff, "<ok>" + Msg.esc(target.getName()) + " ist jetzt in Team " + t.colored());
    }

    // ── Events ───────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("missilewars.mod")) return;
        for (UUID id : vanished) {
            Player s = Bukkit.getPlayer(id);
            if (s != null) p.hidePlayer(plugin, s);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        frozen.remove(e.getPlayer().getUniqueId());
        if (isStaff(e.getPlayer())) disable(e.getPlayer(), false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!frozen.contains(e.getPlayer().getUniqueId()) || !e.hasChangedPosition()) return;
        Location to = e.getFrom().clone();
        to.setYaw(e.getTo().getYaw());
        to.setPitch(e.getTo().getPitch());
        e.setTo(to);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && (isStaff(p) || isFrozen(p))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isStaff(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && isStaff(p)) e.setCancelled(true);
    }
}
