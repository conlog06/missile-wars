package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.map.MapConfig;
import de.crafterteam.missilewars.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Map-Abstimmung als Dialog (ab 1.21.6) oder klickbare Chat-Liste. */
public final class VoteMenu {

    private VoteMenu() {
    }

    public static void open(MissileWarsPlugin plugin, Game game, Player viewer) {
        if (!game.votingOpen()) {
            Msg.send(viewer, "<bad>Die Abstimmung ist vorbei.");
            return;
        }
        List<MapConfig> maps = game.votableMaps();
        boolean dialog = "dialog".equalsIgnoreCase(plugin.getConfig().getString("menu-style", "dialog"));
        if (dialog && !viewer.isSneaking()) openDialog(plugin, game, viewer, maps);
        else openChat(game, viewer, maps);
    }

    private static String describe(MapConfig m) {
        String base = m.type() == MapConfig.Type.GENERATED ? "Generierte Arena, Abstand " + m.distance() : "Eigene Map";
        return base + (m.ratingPercent() < 0 ? "" : "\n👍 " + m.ratingPercent() + "% (" + m.ratings() + " Bewertungen)");
    }

    private static void openDialog(MissileWarsPlugin plugin, Game game, Player viewer, List<MapConfig> maps) {
        UUID id = viewer.getUniqueId();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build();
        List<ActionButton> buttons = new ArrayList<>();
        for (MapConfig m : maps) {
            String name = m.name();
            boolean current = name.equals(game.map().name());
            Component label = Msg.mm((current ? "<acc>" : "<txt>") + name + " <mut>(" + game.votesFor(name) + ")");
            DialogAction action = DialogAction.customClick((response, audience) -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                Game g = p == null ? null : plugin.games().get(p);
                if (g != null) g.vote(p, name);
            }), once);
            buttons.add(ActionButton.builder(label).tooltip(Msg.mm("<mut>" + describe(m) + (current ? "\n<acc>aktuelle Map" : "")))
                    .width(150).action(action).build());
        }
        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Msg.mm("<logo> <mut>– Map-Abstimmung"))
                        .canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Msg.mm("<mut>Für welche Map stimmst du? Die Abstimmung endet kurz vor dem Start."))))
                        .build())
                .type(DialogType.multiAction(buttons, ActionButton.builder(Msg.mm("<mut>Schließen")).width(150).build(), 2)));
        viewer.showDialog(d);
    }

    private static void openChat(Game game, Player viewer, List<MapConfig> maps) {
        Msg.send(viewer, "<pri>Map-Abstimmung <mut>– klicke eine Map:");
        for (MapConfig m : maps) {
            viewer.sendMessage(Msg.mm(" <dim>» <acc><u>" + m.name() + "</u> <mut>(" + game.votesFor(m.name()) + " Stimmen)")
                    .clickEvent(ClickEvent.runCommand("/mw vote " + m.name()))
                    .hoverEvent(HoverEvent.showText(Msg.mm("<mut>" + describe(m)))));
        }
    }
}
