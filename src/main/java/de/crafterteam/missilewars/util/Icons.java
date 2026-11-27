package de.crafterteam.missilewars.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eigene Icons aus dem CrafterTeam-Ressourcenpaket (item_model = lobbysystem:&lt;key&gt;).
 * Nur für Spieler, die das Pack geladen haben – sonst bleibt das normale Material (kein "fehlende Textur"-Icon).
 */
public final class Icons {

    private static final Set<UUID> LOADED = ConcurrentHashMap.newKeySet();

    private Icons() {
    }

    public static void loaded(UUID uuid, boolean loaded) {
        if (loaded) LOADED.add(uuid);
        else LOADED.remove(uuid);
    }

    public static boolean has(Player p) {
        return p != null && LOADED.contains(p.getUniqueId());
    }

    public static ItemStack apply(ItemStack item, Player viewer, String key) {
        if (item == null || !has(viewer) || key == null) return item;
        try {
            item.editMeta(m -> m.setItemModel(new NamespacedKey("lobbysystem", key)));
        } catch (Throwable ignored) {
        }
        return item;
    }
}
