package de.crafterteam.missilewars.missile;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Eine fliegende Rakete. Die Bewegung macht das Plugin selbst (wie eine Flugmaschine,
 * aber ohne echtes Redstone) – dadurch läuft es auf jeder Version stabil und lag-arm.
 *
 * Regeln:
 *  - Jeder Schritt schiebt die Rakete um 1 Block in Flugrichtung.
 *  - Trifft die Front auf einen Block → TNT zündet (Fuse aus der Config).
 *  - Wird ein Antriebsteil (alles außer TNT) abgebaut → Rakete bleibt stehen (entschärft).
 *  - Spieler auf/vor der Rakete werden mitgeschoben.
 */
public final class Missile {

    public record Pos(int x, int y, int z) {
        Pos shift(int dx, int dz) { return new Pos(x + dx, y, z + dz); }
    }

    public enum Result { FLYING, STOPPED, EXPLODED, REMOVED }

    public interface Settings {
        int fuseTicks();
        int maxSteps();
        int pierceGlassMax();
        /** Koordinate (entlang der Flugachse) hinter dem gegnerischen Portal – ab hier wird die Rakete entfernt. */
        double removeBeyond();
    }

    private final World world;
    private final MissileType type;
    private final int dir;
    /** Flugachse: true = X, false = Z. */
    private final boolean axisX;
    private final int dx, dz;
    private final int ticksPerStep;
    private final Player owner;
    private final Settings settings;
    /** Position → erwartetes Material (zum Erkennen von abgebauten Teilen). */
    private Map<Pos, Material> parts;
    private int tickCounter;
    private int steps;
    private int pierced;

    public Missile(World world, MissileType type, boolean axisX, int dir, int ticksPerStep, Player owner,
                   Map<Pos, Material> parts, Settings settings) {
        this.world = world;
        this.type = type;
        this.dir = dir;
        this.axisX = axisX;
        this.dx = axisX ? dir : 0;
        this.dz = axisX ? 0 : dir;
        this.ticksPerStep = Math.max(1, ticksPerStep);
        this.owner = owner;
        this.parts = parts;
        this.settings = settings;
        track(parts.keySet());
    }

    /** Front, Heck und Mitte merken (für Warnung, Effekte, Einschlag). */
    private int track(Set<Pos> positions) {
        int rear = dir > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        int front = dir > 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        double sx = 0, sy = 0;
        for (Pos p : positions) {
            int a = along(p);
            rear = dir > 0 ? Math.min(rear, a) : Math.max(rear, a);
            front = dir > 0 ? Math.max(front, a) : Math.min(front, a);
            sx += lateral(p);
            sy += p.y();
        }
        if (positions.isEmpty()) return rear;
        frontZ = front;
        centerX = sx / positions.size() + 0.5;
        centerY = sy / positions.size() + 0.5;
        return rear;
    }

    private int along(Pos p) {
        return axisX ? p.x() : p.z();
    }

    private int lateral(Pos p) {
        return axisX ? p.z() : p.x();
    }

    /** Weltposition aus (entlang, seitlich, y). */
    private Location at(double alongC, double latC, double y) {
        return axisX ? new Location(world, alongC, y, latC) : new Location(world, latC, y, alongC);
    }

    private boolean warned;
    private int frontZ;
    private double centerX, centerY;

    public MissileType type() {
        return type;
    }

    public int dir() {
        return dir;
    }

    /** Front-Koordinate entlang der Flugachse. */
    public int frontAlong() {
        return frontZ;
    }

    /** Mitte quer zur Flugachse. */
    public double centerLateral() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public boolean warned() {
        return warned || steps == 0;
    }

    public void markWarned() {
        warned = true;
    }

    public Player owner() {
        return owner;
    }

    /** Pro Tick aufrufen. */
    public Result tick() {
        if (++tickCounter < ticksPerStep) return Result.FLYING;
        tickCounter = 0;
        return step();
    }

    private Result step() {
        // 1) aktuellen Zustand einlesen
        Map<Pos, BlockData> current = new LinkedHashMap<>();
        Map<Pos, Material> expected = new HashMap<>();
        for (Map.Entry<Pos, Material> e : parts.entrySet()) {
            Pos p = e.getKey();
            Block b = world.getBlockAt(p.x(), p.y(), p.z());
            if (b.getType() != e.getValue()) {
                if (e.getValue() != Material.TNT) return Result.STOPPED; // Antrieb kaputt
                continue;                                                  // TNT weg (gezündet/abgebaut)
            }
            current.put(p, b.getBlockData());
            expected.put(p, e.getValue());
        }
        if (current.isEmpty()) return Result.STOPPED;

        // 2) Hindernisse vor der Rakete suchen
        Set<Pos> occupied = current.keySet();
        List<Block> obstacles = new ArrayList<>();
        for (Pos p : occupied) {
            Pos n = p.shift(dx, dz);
            if (occupied.contains(n)) continue;
            Block nb = world.getBlockAt(n.x(), n.y(), n.z());
            if (!nb.getType().isAir()) obstacles.add(nb);
        }
        if (!obstacles.isEmpty()) {
            if (type.piercesGlass() && allGlass(obstacles) && pierced + obstacles.size() <= settings.pierceGlassMax()) {
                pierced += obstacles.size();
                for (Block o : obstacles) o.setType(Material.AIR, false);
            } else if (type.drills() && drillable(obstacles) && pierced + obstacles.size() <= settings.pierceGlassMax()) {
                pierced += obstacles.size();
                for (Block o : obstacles) {
                    world.spawnParticle(Particle.BLOCK, o.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, o.getBlockData());
                    o.setType(Material.AIR, false);
                }
                world.playSound(obstacles.getFirst().getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.4f);
            } else {
                detonate(current);
                return Result.EXPLODED;
            }
        }

        // 3) außerhalb der Arena? → weg damit
        int front = dir > 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Pos p : occupied) front = dir > 0 ? Math.max(front, along(p)) : Math.min(front, along(p));
        if (dir * (front - settings.removeBeyond()) > 0) {
            for (Pos p : occupied) world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR, false);
            return Result.REMOVED;
        }

        // 4) bewegen: alte Blöcke leeren, neue setzen (ohne Physik)
        Set<Pos> next = new HashSet<>();
        for (Pos p : occupied) next.add(p.shift(dx, dz));
        for (Pos p : occupied) {
            if (!next.contains(p)) world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR, false);
        }
        Map<Pos, Material> moved = new HashMap<>();
        for (Map.Entry<Pos, BlockData> e : current.entrySet()) {
            Pos n = e.getKey().shift(dx, dz);
            world.getBlockAt(n.x(), n.y(), n.z()).setBlockData(e.getValue(), false);
            moved.put(n, expected.get(e.getKey()));
        }
        parts = moved;

        pushEntities(next);
        effects(next);

        if (++steps >= settings.maxSteps()) return Result.STOPPED;
        return Result.FLYING;
    }

    private void pushEntities(Set<Pos> positions) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Pos p : positions) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
        }
        // Mitfahr-Raketen nehmen Spieler oben drauf mit, alle anderen schieben nur, wer im Weg steht
        double top = type.rideable() ? maxY + 2.2 : maxY + 0.99;
        double padX = axisX ? 0 : 0.3, padZ = axisX ? 0.3 : 0;
        BoundingBox box = new BoundingBox(minX - padX, minY, minZ - padZ, maxX + 1 + padX, top, maxZ + 1 + padZ);
        for (Entity e : world.getNearbyEntities(box, en -> en instanceof Player)) {
            Player p = (Player) e;
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location to = p.getLocation().add(dx, 0, dz);
            p.teleport(to);
        }
    }

    /** Partikelspur am Heck + leises Antriebsgeräusch; merkt sich Front/Mitte für die Warnung. */
    private void effects(Set<Pos> positions) {
        int rear = track(positions);
        Location tail = at(rear + 0.5 - dir * 0.8, centerX, centerY);
        world.spawnParticle(Particle.FLAME, tail, 4, 0.2, 0.2, 0.1, 0.01);
        world.spawnParticle(Particle.CLOUD, tail, 2, 0.2, 0.2, 0.1, 0.01);
        if (steps % 3 == 0) world.playSound(tail, Sound.BLOCK_PISTON_EXTEND, 0.35f, 1.6f);
    }

    private void detonate(Map<Pos, BlockData> current) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // Einschlag-Effekt an der Front
        Location impact = at(frontZ + 0.5 + dir * 0.6, centerX, centerY);
        world.spawnParticle(Particle.ELECTRIC_SPARK, impact, 40, 0.6, 0.6, 0.3, 0.15);
        world.spawnParticle(Particle.LAVA, impact, 8, 0.4, 0.4, 0.2, 0);
        world.playSound(impact, Sound.ENTITY_TNT_PRIMED, 1.4f, 0.8f);
        world.playSound(impact, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.6f);
        boolean instant = type.fuseOverride() >= 0;
        for (Map.Entry<Pos, BlockData> e : current.entrySet()) {
            if (e.getValue().getMaterial() != Material.TNT) continue;
            Pos p = e.getKey();
            world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR, false);
            Location loc = new Location(world, p.x() + 0.5, p.y(), p.z() + 0.5);
            int fuse = instant ? type.fuseOverride() : settings.fuseTicks() + r.nextInt(0, 11);
            world.spawn(loc, TNTPrimed.class, t -> {
                t.setFuseTicks(fuse);
                if (owner != null && owner.isOnline()) t.setSource(owner);
            });
        }
        if (type.cluster()) {
            // Streubombe: 6 zusätzliche TNT in einem Ring um den Einschlag
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + r.nextDouble(0.5);
                double rad = 2.5 + r.nextDouble(1.5);
                Location l = impact.clone().add(Math.cos(a) * rad, r.nextDouble(-1, 2), Math.sin(a) * rad);
                int fuse = settings.fuseTicks() + 15 + r.nextInt(0, 20);
                world.spawn(l, TNTPrimed.class, t -> {
                    t.setFuseTicks(fuse);
                    if (owner != null && owner.isOnline()) t.setSource(owner);
                });
            }
        }
    }

    private static final java.util.Set<Material> HARD = java.util.EnumSet.of(Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            Material.BEDROCK, Material.BARRIER, Material.NETHER_PORTAL, Material.REINFORCED_DEEPSLATE, Material.END_PORTAL_FRAME);

    private static boolean drillable(List<Block> blocks) {
        for (Block b : blocks) if (HARD.contains(b.getType())) return false;
        return true;
    }

    private static boolean allGlass(List<Block> blocks) {
        for (Block b : blocks) {
            String n = b.getType().name();
            if (!n.endsWith("GLASS") && !n.endsWith("GLASS_PANE")) return false;
        }
        return true;
    }
}
