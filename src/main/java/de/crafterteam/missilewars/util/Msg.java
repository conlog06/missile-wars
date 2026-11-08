package de.crafterteam.missilewars.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Kleine Helfer für MiniMessage-Texte. */
public final class Msg {

    public static final String PREFIX = "<dark_gray>[<red>Missile<white>Wars<dark_gray>] <gray>";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {
    }

    public static Component mm(String text) {
        return MM.deserialize(text);
    }

    public static Component prefixed(String text) {
        return MM.deserialize(PREFIX + text);
    }

    public static void send(CommandSender to, String text) {
        to.sendMessage(prefixed(text));
    }

    /** Spielernamen etc. für MiniMessage entschärfen. */
    public static String esc(String raw) {
        return MM.escapeTags(raw);
    }
}
