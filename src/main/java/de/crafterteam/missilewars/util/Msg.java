package de.crafterteam.missilewars.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Texte & Farbpalette. Eigene Tags in allen Nachrichten:
 * <pri> Raketen-Orange, <acc> Gold, <ok> Grün, <bad> Rot, <info> Hellblau,
 * <mut> Grau, <dim> Dunkelgrau, <txt> fast Weiß, <logo> MissileWars-Schriftzug.
 */
public final class Msg {

    // Palette
    public static final String PRIMARY = "#FF6B35";
    public static final String PRIMARY_2 = "#FF2E63";
    public static final String ACCENT = "#FFC145";
    public static final String OK = "#4ADE80";
    public static final String BAD = "#F87171";
    public static final String INFO = "#60A5FA";
    public static final String MUTED = "#A1A1AA";
    public static final String DIM = "#52525B";
    public static final String TEXT = "#F4F4F5";

    public static final String LOGO = "<bold><gradient:" + PRIMARY_2 + ":" + PRIMARY + ":" + ACCENT + ">MissileWars</gradient></bold>";
    public static final String PREFIX = "<dim>[</dim><gradient:" + PRIMARY_2 + ":" + PRIMARY + ">MW</gradient><dim>]</dim> <mut>";

    private static final TagResolver PALETTE = TagResolver.resolver(
            color("pri", PRIMARY), color("acc", ACCENT), color("ok", OK), color("bad", BAD),
            color("info", INFO), color("mut", MUTED), color("dim", DIM), color("txt", TEXT),
            color("tgreen", "#4ADE80"), color("tred", "#FB7185"),
            Placeholder.parsed("logo", LOGO));

    private static final MiniMessage MM = MiniMessage.builder()
            .editTags(b -> b.resolver(PALETTE))
            .build();

    private Msg() {
    }

    private static TagResolver color(String name, String hex) {
        return TagResolver.resolver(name, (args, ctx) ->
                net.kyori.adventure.text.minimessage.tag.Tag.styling(
                        net.kyori.adventure.text.format.TextColor.fromHexString(hex)));
    }

    public static Component mm(String text) {
        return MM.deserialize(text);
    }

    /** Für Item-Namen/Lore: ohne Kursiv. */
    public static Component item(String text) {
        return MM.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    public static Component prefixed(String text) {
        return MM.deserialize(PREFIX + text);
    }

    /** Nachricht an alle Spieler auf dem Server. */
    public static void broadcastLobby(org.bukkit.plugin.Plugin plugin, String text) {
        Component c = prefixed(text);
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) p.sendMessage(c);
    }

    public static void send(CommandSender to, String text) {
        to.sendMessage(prefixed(text));
    }

    /** Spielernamen etc. für MiniMessage entschärfen. */
    public static String esc(String raw) {
        return MM.escapeTags(raw);
    }

    /** Fortschrittsbalken aus Strichen. */
    public static String bar(double fraction, int length, String fullColor) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * length);
        return "<" + fullColor + ">" + "▌".repeat(filled) + "<dim>" + "▌".repeat(length - filled);
    }
}
