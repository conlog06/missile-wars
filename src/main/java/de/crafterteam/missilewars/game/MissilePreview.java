package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.missile.Missile;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leuchtende Geister-Vorschau der Rakete (Block-Displays, nur für den haltenden Spieler sichtbar).
 * Grün = hier kann gespawnt werden, Rot = geht nicht.
 */
public final class MissilePreview {

    private static final Transformation SHRINK = new Transformation(
            new Vector3f(0.08f, 0.08f, 0.08f), new AxisAngle4f(),
            new Vector3f(0.84f, 0.84f, 0.84f), new AxisAngle4f());

    private final MissileWarsPlugin plugin;
    private final Map<UUID, List<BlockDisplay>> shown = new HashMap<>();

    public MissilePreview(MissileWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player p, World w, List<Missile.Pos> positions, boolean valid) {
        List<BlockDisplay> list = shown.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
        while (list.size() > positions.size()) list.removeLast().remove();
        BlockData data = (valid ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS).createBlockData();
        Color glow = valid ? Color.LIME : Color.RED;
        for (int i = 0; i < positions.size(); i++) {
            Missile.Pos pos = positions.get(i);
            Location loc = new Location(w, pos.x(), pos.y(), pos.z());
            if (i < list.size() && list.get(i).isValid() && list.get(i).getWorld().equals(w)) {
                BlockDisplay d = list.get(i);
                d.teleport(loc);
                d.setBlock(data);
                d.setGlowColorOverride(glow);
            } else {
                BlockDisplay d = w.spawn(loc, BlockDisplay.class, e -> {
                    e.setVisibleByDefault(false);
                    e.setPersistent(false);
                    e.setBlock(data);
                    e.setTransformation(SHRINK);
                    e.setGlowing(true);
                    e.setGlowColorOverride(glow);
                    e.setTeleportDuration(2);
                });
                p.showEntity(plugin, d);
                if (i < list.size()) {
                    list.get(i).remove();
                    list.set(i, d);
                } else {
                    list.add(d);
                }
            }
        }
    }

    public void hide(Player p) {
        List<BlockDisplay> list = shown.remove(p.getUniqueId());
        if (list != null) list.forEach(BlockDisplay::remove);
    }

    public void hideAll() {
        shown.values().forEach(l -> l.forEach(BlockDisplay::remove));
        shown.clear();
    }
}
