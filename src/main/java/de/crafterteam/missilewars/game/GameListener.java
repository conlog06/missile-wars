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
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.CHEST) {
            Game ag = gameOf(e.getClickedBlock().getWorld());
            if (ag != null && ag.isAirdrop(e.getClickedBlock())) {
                e.setCancelled(true);
                if (e.getHand() == EquipmentSlot.HAND && ag.team(p) != null) ag.openAirdrop(p, e.getClickedBlock());
                return;
            }
        }
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
            else de.crafterteam.missilewars.gui.Menus.team(plugin, g, p);
            return;
        }
        if (GameItems.MENU_LOBBY.equals(id)) {
            de.crafterteam.missilewars.gui.Menus.lobby(plugin, g, p);
            return;
        }
        if (GameItems.MENU_VIPSTART.equals(id)) {
            g.vipStart(p);
            return;
        }
        if (GameItems.MENU_SHOP.equals(id)) {
            plugin.shop().open(p);
            return;
        }
        if (GameItems.MENU_MISSILES.equals(id)) {
            de.crafterteam.missilewars.gui.Menus.missiles(plugin, p);
            return;
        }
        if (GameItems.MENU_KIT.equals(id)) {
            de.crafterteam.missilewars.gui.Menus.kits(plugin, g, p);
            return;
        }
        if (GameItems.MENU_READY.equals(id)) {
            g.toggleReady(p);
            return;
        }
        if (GameItems.MENU_GAME.equals(id)) {
            de.crafterteam.missilewars.gui.Menus.main(plugin, p);
            return;
        }
        if (GameItems.MENU_VOTE.equals(id)) {
            VoteMenu.open(plugin, g, p);
            return;
        }
        if (GameItems.MENU_LEAVE.equals(id)) {
            g.leave(p, true);
            if (!plugin.serverMode().handleLeave(p)) Msg.send(p, "Du hast die Runde verlassen.");
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
        } else if (GameItems.PLATFORM.equals(id)) {
            used = g.deployPlatform(p, team);
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

    // ── AFK & Doppelsprung ───────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (g == null) return;
        if (e.hasChangedPosition() || e.hasChangedOrientation()) g.touch(p);
        // Doppelsprung nur in der Wartephase außerhalb der Arena
        if (doubleJumpAllowed(p, g) && p.isOnGround() && !p.getAllowFlight()) p.setAllowFlight(true);
    }

    private boolean doubleJumpAllowed(Player p, Game g) {
        return plugin.getConfig().getBoolean("lobby-double-jump", true)
                && (g.state() == Game.State.WAITING || g.state() == Game.State.COUNTDOWN)
                && !p.getWorld().equals(g.world())
                && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (g == null || !doubleJumpAllowed(p, g) || !e.isFlying()) return;
        e.setCancelled(true);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setVelocity(p.getLocation().getDirection().multiply(1.1).setY(0.9));
        p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 12, 0.3, 0.1, 0.3, 0.02);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 0.6f, 1.4f);
    }

    // ── Wartelobby-Schutz ────────────────────────────────────────────

    private boolean lobbyProtected(org.bukkit.World w, Player p) {
        if (!plugin.waitingLobby().isLobbyWorld(w)) return false;
        return !(p != null && p.hasPermission("missilewars.admin") && p.getGameMode() == GameMode.CREATIVE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyBreak(BlockBreakEvent e) {
        if (lobbyProtected(e.getBlock().getWorld(), e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyPlace(BlockPlaceEvent e) {
        if (lobbyProtected(e.getBlock().getWorld(), e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.waitingLobby().isLobbyWorld(p.getWorld())) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                org.bukkit.Location l = plugin.waitingLobby().spawn();
                if (l != null) p.teleport(l);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyFood(FoodLevelChangeEvent e) {
        if (plugin.waitingLobby().isLobbyWorld(e.getEntity().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbySpawn(CreatureSpawnEvent e) {
        if (plugin.waitingLobby().isLobbyWorld(e.getLocation().getWorld())
                && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    // ── Menüs ────────────────────────────────────────────────────────

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof de.crafterteam.missilewars.gui.Gui gui) gui.handle(e);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof de.crafterteam.missilewars.gui.Gui) e.setCancelled(true);
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
            Msg.send(p, "<bad>Keine Berechtigung für Join-Schilder.");
            return;
        }
        String map = PlainTextComponentSerializer.plainText().serialize(
                e.line(1) == null ? net.kyori.adventure.text.Component.empty() : e.line(1)).trim().toLowerCase();
        if (map.isEmpty()) map = plugin.getConfig().getString("default-map", "standard");
        if (plugin.maps().get(map) == null) {
            Msg.send(p, "<bad>Map '" + Msg.esc(map) + "' gibt es nicht. <mut>Zeile 2 = Mapname");
            return;
        }
        plugin.signs().add(e.getBlock(), map);
        Msg.send(p, "<ok>Join-Schild für '" + map + "' erstellt.");
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
            Msg.send(e.getPlayer(), "<mut>Zum Entfernen: als Admin <acc>schleichen</acc> + abbauen.");
            return;
        }
        plugin.signs().remove(e.getBlock());
        Msg.send(e.getPlayer(), "<ok>Join-Schild entfernt.");
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
        if (t == null) prefix = "<mut>[Zuschauer] ";
        else if (all) prefix = "<" + t.tag() + ">[" + t.displayName() + "] ";
        else prefix = "<" + t.tag() + ">[Team] ";
        net.kyori.adventure.text.Component rank = plugin.nameTags().prefix(p);
        net.kyori.adventure.text.Component head = rank.equals(net.kyori.adventure.text.Component.empty())
                ? Msg.mm(prefix) : Msg.mm(prefix).append(rank).append(net.kyori.adventure.text.Component.space());
        String rest = plugin.progression().badge(p) + " <txt>" + Msg.esc(p.getName()) + "<dim> » <mut>";
        e.renderer((source, displayName, message, viewer) -> head.append(Msg.mm(rest)).append(message));
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
        Material type = e.getBlock().getType();
        boolean glass = type.name().contains("GLASS");
        // Glas nur, wenn es von Schild/Plattform stammt – das Arena-Glas bleibt stehen
        boolean allowed = glass ? g.isPlaced(e.getBlock()) : breakable.contains(type);
        if (g.state() != Game.State.RUNNING || g.team(e.getPlayer()) == null || !allowed) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        g.unmarkPlaced(e.getBlock());
        g.creditIntercept(e.getPlayer(), java.util.List.of(e.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (gameOf(e.getBlock().getWorld()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Game g = gameOf(e.getEntity().getWorld());
        if (g == null) return;
        e.setYield(0f);
        Player src = null;
        if (e.getEntity() instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) src = p;
        else if (e.getEntity() instanceof Projectile proj && proj.getShooter() instanceof Player p) src = p;
        g.creditIntercept(src, e.blockList());
        g.onExplosion(src, e.blockList());
        breakPortalBlocksQuietly(e.blockList());
    }

    /** Portalblöcke selbst (ohne Physik) entfernen, damit nicht das ganze Portal zusammenbricht. */
    private void breakPortalBlocksQuietly(java.util.List<org.bukkit.block.Block> blocks) {
        java.util.Iterator<org.bukkit.block.Block> it = blocks.iterator();
        while (it.hasNext()) {
            org.bukkit.block.Block b = it.next();
            if (b.getType() == Material.NETHER_PORTAL) {
                it.remove();
                b.setType(Material.AIR, false);
            }
        }
    }

    /**
     * Vanilla bricht ein ganzes Netherportal ab, sobald ein Block daneben fehlt.
     * Wir unterdrücken die Form-Updates neben Portalblöcken → Portal-HP funktioniert.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(org.bukkit.event.block.BlockPhysicsEvent e) {
        org.bukkit.block.Block b = e.getBlock();
        if (!b.getWorld().getName().startsWith(Game.WORLD_PREFIX)) return;
        if (b.getType() == Material.NETHER_PORTAL) {
            e.setCancelled(true);
            return;
        }
        for (org.bukkit.block.BlockFace f : PORTAL_FACES) {
            if (b.getRelative(f).getType() == Material.NETHER_PORTAL) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private static final org.bukkit.block.BlockFace[] PORTAL_FACES = {
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN};

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g == null) return;
        e.setCancelled(true);
        if (g.state() == Game.State.RUNNING && g.team(e.getPlayer()) != null) plugin.shop().openRound(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Game g = gameOf(e.getBlock().getWorld());
        if (g == null) return;
        e.setYield(0f);
        g.onExplosion(null, e.blockList());
        breakPortalBlocksQuietly(e.blockList());
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
        if (g != null && !g.allowsWorld(e.getPlayer().getWorld())) {
            g.leave(e.getPlayer(), false);
            Msg.send(e.getPlayer(), "Du hast die Runde verlassen.");
        }
    }
}
