package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.missile.MissileType;
import de.crafterteam.missilewars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class GameListener implements Listener {

    private final MissileWarsPlugin plugin;
    private final NamespacedKey shieldKey;
    private final Set<Material> breakable = EnumSet.noneOf(Material.class);

    public GameListener(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.shieldKey = new NamespacedKey(plugin, "shield");
        reloadBreakable();
    }

    public void reloadBreakable() {
        breakable.clear();
        List<String> list = plugin.getConfig().getStringList("breakable");
        for (String s : list) {
            Material m = Material.matchMaterial(s);
            if (m != null) breakable.add(m);
            else plugin.getLogger().warning("Unbekanntes Material in 'breakable': " + s);
        }
        // Alles, woraus Raketen bestehen, ist immer abbaubar (entschärfen)
        for (de.crafterteam.missilewars.missile.MissileType t : de.crafterteam.missilewars.missile.MissileType.values()) {
            for (var part : t.parts()) {
                if (part.material() != null && part.material() != de.crafterteam.missilewars.missile.MissileType.TEAM_ACCENT) breakable.add(part.material());
            }
        }
        for (Team t : Team.values()) {
            breakable.add(t.accent());
            breakable.add(t.glass());
        }
    }

    private Game gameOf(Player p) {
        return plugin.games().get(p);
    }

    private Game gameOf(org.bukkit.World w) {
        return plugin.games().get(w);
    }

    // ── Items benutzen ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        String id = plugin.items().idOf(item);
        if (id == null) return;
        // Spiel-Items lösen nie Vanilla-Verhalten aus (Mob-Spawn, Feuer, …)
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;

        Game g = gameOf(p);
        if (g == null) return;
        if (GameItems.MENU_TEAM.equals(id)) {
            if (g.team(p) == null) return;
            boolean dialog = "dialog".equalsIgnoreCase(plugin.getConfig().getString("menu-style", "dialog"));
            // Schleichen + Rechtsklick öffnet immer das Inventar-Menü (Fallback für alte Clients)
            if (dialog && !p.isSneaking()) TeamDialog.open(plugin, g, p);
            else p.openInventory(new TeamMenu(g, p).getInventory());
            return;
        }
        if (GameItems.MENU_VOTE.equals(id)) {
            VoteMenu.open(plugin, g, p);
            return;
        }
        if (GameItems.MENU_LEAVE.equals(id)) {
            g.leave(p, true);
            Msg.send(p, "Du hast die Runde verlassen.");
            return;
        }
        if (g.state() != Game.State.RUNNING || g.team(p) == null) return;
        Team team = g.team(p);
        if (item != null && p.hasCooldown(item.getType())) return;

        boolean used = false;
        MissileType mt = MissileType.byId(id);
        if (mt != null) {
            Block origin = g.missileOrigin(p, e.getClickedBlock(), e.getClickedBlock() == null ? null : e.getBlockFace());
            used = g.spawnMissile(p, mt, origin);
        } else if (GameItems.SHIELD.equals(id)) {
            Snowball ball = p.launchProjectile(Snowball.class);
            ball.getPersistentDataContainer().set(shieldKey, PersistentDataType.STRING, team.name());
            int delay = Math.max(1, plugin.getConfig().getInt("shield.deploy-ticks", 20));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!ball.isValid()) return;
                Game gg = gameOf(ball.getWorld());
                if (gg != null) gg.deployShield(ball.getLocation(), team);
                ball.remove();
            }, delay);
            used = true;
        } else if (GameItems.FIREBALL.equals(id)) {
            Fireball fb = p.launchProjectile(Fireball.class);
            fb.setYield((float) plugin.getConfig().getDouble("fireball.yield", 2.0));
            fb.setIsIncendiary(false);
            used = true;
        }

        if (used) {
            int cd = plugin.getConfig().getInt("item-cooldown-ticks", 20);
            if (cd > 0 && item != null) p.setCooldown(item.getType(), cd);
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (p.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
                p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
            }
        }
    }

    // ── Team-Menü ────────────────────────────────────────────────────

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof TeamMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Game g = gameOf(p);
        if (g == null) {
            p.closeInventory();
            return;
        }
        Team target = switch (e.getSlot()) {
            case TeamMenu.SLOT_GREEN -> Team.GREEN;
            case TeamMenu.SLOT_RED -> Team.RED;
            case TeamMenu.SLOT_RANDOM -> {
                int gr = g.teamPlayers(Team.GREEN).size(), rd = g.teamPlayers(Team.RED).size();
                Team cur = g.team(p);
                // wechseln nur, wenn es das andere Team nicht überfüllt
                yield cur != null && g.teamPlayers(cur.other()).size() < g.teamPlayers(cur).size() ? cur.other()
                        : (gr == rd ? (Math.random() < 0.5 ? Team.GREEN : Team.RED) : (gr < rd ? Team.GREEN : Team.RED));
            }
            default -> null;
        };
        if (target == null) return;
        p.closeInventory();
        if (target != g.team(p)) g.changeTeam(p, target);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof TeamMenu) e.setCancelled(true);
    }

    // ── Join-Schilder ────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onSignCreate(SignChangeEvent e) {
        String l0 = PlainTextComponentSerializer.plainText().serialize(
                e.line(0) == null ? net.kyori.adventure.text.Component.empty() : e.line(0)).trim();
        if (!l0.equalsIgnoreCase("[MissileWars]") && !l0.equalsIgnoreCase("[mw]")) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("missilewars.admin")) {
            e.setCancelled(true);
            Msg.send(p, "<red>Keine Berechtigung für Join-Schilder.");
            return;
        }
        String map = PlainTextComponentSerializer.plainText().serialize(
                e.line(1) == null ? net.kyori.adventure.text.Component.empty() : e.line(1)).trim().toLowerCase();
        if (map.isEmpty()) map = plugin.getConfig().getString("default-map", "standard");
        if (plugin.maps().get(map) == null) {
            Msg.send(p, "<red>Map '" + Msg.esc(map) + "' gibt es nicht. <gray>Zeile 2 = Mapname");
            return;
        }
        plugin.signs().add(e.getBlock(), map);
        Msg.send(p, "<green>Join-Schild für '" + map + "' erstellt.");
        Bukkit.getScheduler().runTask(plugin, plugin.signs()::updateAll);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSignClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        String map = plugin.signs().mapAt(e.getClickedBlock());
        if (map == null) return;
        e.setCancelled(true);
        e.getPlayer().performCommand("mw join " + map);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignBreak(BlockBreakEvent e) {
        if (plugin.signs().mapAt(e.getBlock()) == null) return;
        if (!e.getPlayer().hasPermission("missilewars.admin") || !e.getPlayer().isSneaking()) {
            e.setCancelled(true);
            Msg.send(e.getPlayer(), "<gray>Zum Entfernen: als Admin <yellow>schleichen</yellow> + abbauen.");
            return;
        }
        plugin.signs().remove(e.getBlock());
        Msg.send(e.getPlayer(), "<green>Join-Schild entfernt.");
    }

    // ── Teamchat ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (g == null || !plugin.getConfig().getBoolean("team-chat", true)) return;
        Team t = g.team(p);
        String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
        boolean all = t == null || raw.startsWith("@") || g.state() != Game.State.RUNNING;
        if (raw.startsWith("@")) e.message(net.kyori.adventure.text.Component.text(raw.substring(1).trim()));

        List<Player> receivers = all ? g.everyone() : g.teamPlayers(t);
        e.viewers().removeIf(a -> a instanceof Player);
        e.viewers().addAll(receivers);

        String prefix;
        if (t == null) prefix = "<gray>[Zuschauer] ";
        else if (all) prefix = "<" + t.tag() + ">[" + t.displayName() + "] ";
        else prefix = "<" + t.tag() + ">[Team] ";
        String namePart = prefix + "<white>" + Msg.esc(p.getName()) + "<dark_gray> » <gray>";
        e.renderer((source, displayName, message, viewer) -> Msg.mm(namePart).append(message));
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball ball)) return;
        String teamName = ball.getPersistentDataContainer().get(shieldKey, PersistentDataType.STRING);
        if (teamName == null) return;
        Game g = gameOf(ball.getWorld());
        if (g != null) {
            try {
                g.deployShield(ball.getLocation(), Team.valueOf(teamName));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ball.remove();
    }

    // ── Blöcke ───────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Game g = gameOf(e.getBlock().getWorld());
        if (g == null) return;
        if (g.state() != Game.State.RUNNING || g.team(e.getPlayer()) == null
                || !breakable.contains(e.getBlock().getType())) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (gameOf(e.getBlock().getWorld()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (gameOf(e.getEntity().getWorld()) != null) e.setYield(0f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (gameOf(e.getBlock().getWorld()) != null) e.setYield(0f);
    }

    /** Echte Kolben bleiben aus – die Raketen bewegt das Plugin. */
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (gameOf(e.getBlock().getWorld()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (gameOf(e.getBlock().getWorld()) != null) e.setCancelled(true);
    }

    /** TNT nur durch Explosionen, Feuer & Pfeile – nicht durch Redstone in den Raketen. */
    @EventHandler(ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent e) {
        if (gameOf(e.getBlock().getWorld()) == null) return;
        if (e.getCause() == TNTPrimeEvent.PrimeCause.REDSTONE) e.setCancelled(true);
    }

    // ── Schaden ──────────────────────────────────────────────────────

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageBy(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Game g = gameOf(victim);
        if (g == null) return;
        Player a = attacker(e.getDamager());
        if (a == null || a.equals(victim)) return;
        Team vt = g.team(victim);
        Team at = g.team(a);
        if (vt == null || at == null) {
            e.setCancelled(true);
            return;
        }
        boolean direct = e.getDamager() instanceof Player || e.getDamager() instanceof Projectile;
        if (vt == at && direct) {
            e.setCancelled(true);
            return;
        }
        if (vt != at) g.recordAttack(victim, a);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = gameOf(p);
        if (g == null) return;
        if (g.state() != Game.State.RUNNING || g.team(p) == null) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            g.die(p, "ist ins Void gefallen", true);
            return;
        }
        if (e.getFinalDamage() >= p.getHealth() + p.getAbsorptionAmount()) {
            e.setCancelled(true);
            String reason = switch (e.getCause()) {
                case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "wurde in die Luft gesprengt";
                case FALL -> "ist zu tief gefallen";
                case PROJECTILE -> "wurde erschossen";
                case FIRE, FIRE_TICK, LAVA -> "ist verbrannt";
                default -> "ist gestorben";
            };
            g.die(p, reason, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && gameOf(p) != null) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    // ── Sonstiges ────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && gameOf(p) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent e) {
        if (gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (plugin.games().isMwWorld(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (plugin.games().isMwWorld(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (gameOf(e.getLocation().getWorld()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (e.toWeatherState() && gameOf(e.getWorld()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null) g.leave(e.getPlayer(), true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null && !e.getPlayer().getWorld().equals(g.world())) {
            g.leave(e.getPlayer(), false);
            Msg.send(e.getPlayer(), "Du hast die Runde verlassen.");
        }
    }
}
