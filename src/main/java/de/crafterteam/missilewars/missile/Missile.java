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
        Pos shift(int dz) { return new Pos(x, y, z + dz); }
    }

    public enum Result { FLYING, STOPPED, EXPLODED, REMOVED }

    public interface Settings {
        int fuseTicks();
        int maxSteps();
        int pierceGlassMax();
        /** Z hinter dem gegnerischen Portal – ab hier wird die Rakete entfernt. */
        double removeBeyondZ();
    }

    private final World world;
    private final MissileType type;
    private final int dir;
    private final int ticksPerStep;
    private final Player owner;
    private final Settings settings;
    /** Position → erwartetes Material (zum Erkennen von abgebauten Teilen). */
    private Map<Pos, Material> parts;
    private int tickCounter;
    private int steps;
    private int pierced;

    public Missile(World world, MissileType type, int dir, int ticksPerStep, Player owner,
                   Map<Pos, Material> parts, Settings settings) {
        this.world = world;
        this.type = type;
        this.dir = dir;
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
            rear = dir > 0 ? Math.min(rear, p.z()) : Math.max(rear, p.z());
            front = dir > 0 ? Math.max(front, p.z()) : Math.min(front, p.z());
            sx += p.x();
            sy += p.y();
        }
        if (positions.isEmpty()) return rear;
        frontZ = front;
        centerX = sx / positions.size() + 0.5;
        centerY = sy / positions.size() + 0.5;
        return rear;
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

    public int frontZ() {
        return frontZ;
    }

    public double centerX() {
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
            Pos n = p.shift(dir);
            if (occupied.contains(n)) continue;
            Block nb = world.getBlockAt(n.x(), n.y(), n.z());
            if (!nb.getType().isAir()) obstacles.add(nb);
        }
        if (!obstacles.isEmpty()) {
            if (type.piercesGlass() && allGlass(obstacles) && pierced + obstacles.size() <= settings.pierceGlassMax()) {
                pierced += obstacles.size();
                for (Block o : obstacles) o.setType(Material.AIR, false);
            } else {
                detonate(current);
                return Result.EXPLODED;
            }
        }

        // 3) außerhalb der Arena? → weg damit
        int frontZ = dir > 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Pos p : occupied) frontZ = dir > 0 ? Math.max(frontZ, p.z()) : Math.min(frontZ, p.z());
        if (dir * (frontZ - settings.removeBeyondZ()) > 0) {
            for (Pos p : occupied) world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR, false);
            return Result.REMOVED;
        }

        // 4) bewegen: alte Blöcke leeren, neue setzen (ohne Physik)
        Set<Pos> next = new HashSet<>();
        for (Pos p : occupied) next.add(p.shift(dir));
        for (Pos p : occupied) {
            if (!next.contains(p)) world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR, false);
        }
        Map<Pos, Material> moved = new HashMap<>();
        for (Map.Entry<Pos, BlockData> e : current.entrySet()) {
            Pos n = e.getKey().shift(dir);
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
        BoundingBox box = new BoundingBox(minX - 0.3, minY, minZ, maxX + 1.3, maxY + 2.2, maxZ + 1);
        for (Entity e : world.getNearbyEntities(box, en -> en instanceof Player)) {
            Player p = (Player) e;
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location to = p.getLocation().add(0, 0, dir);
            p.teleport(to);
        }
    }

    /** Partikelspur am Heck + leises Antriebsgeräusch; merkt sich Front/Mitte für die Warnung. */
    private void effects(Set<Pos> positions) {
        int rear = track(positions);
        Location tail = new Location(world, centerX, centerY, rear + 0.5 - dir * 0.8);
        world.spawnParticle(Particle.FLAME, tail, 4, 0.2, 0.2, 0.1, 0.01);
        world.spawnParticle(Particle.CLOUD, tail, 2, 0.2, 0.2, 0.1, 0.01);
        if (steps % 3 == 0) world.playSound(tail, Sound.BLOCK_PISTON_EXTEND, 0.35f, 1.6f);
    }

    private void detonate(Map<Pos, BlockData> current) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // Einschlag-Effekt an der Front
        Location impact = new Location(world, centerX, centerY, frontZ + 0.5 + dir * 0.6);
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
    }

    private static boolean allGlass(List<Block> blocks) {
        for (Block b : blocks) {
            String n = b.getType().name();
            if (!n.endsWith("GLASS") && !n.endsWith("GLASS_PANE")) return false;
        }
        return true;
    }
}
