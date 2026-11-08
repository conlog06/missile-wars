package de.crafterteam.missilewars.game;

import de.crafterteam.missilewars.MissileWarsPlugin;
import de.crafterteam.missilewars.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Team-Auswahl als nativer Dialog (ab 1.21.6). Ältere Clients: menu-style: inventory. */
public final class TeamDialog {

    private TeamDialog() {
    }

    public static void open(MissileWarsPlugin plugin, Game game, Player viewer) {
        UUID id = viewer.getUniqueId();
        ClickCallback.Options once = ClickCallback.Options.builder()
                .uses(1).lifetime(Duration.ofMinutes(2)).build();

        List<ActionButton> buttons = new ArrayList<>();
        for (Team t : new Team[]{Team.GREEN, null, Team.RED}) {
            Component label;
            Component tooltip;
            if (t == null) {
                label = Msg.mm("<txt>Zufällig");
                tooltip = Msg.mm("<mut>Ins kleinere Team");
            } else {
                List<Player> members = game.teamPlayers(t);
                label = Msg.mm(t.colored() + " <mut>(" + members.size() + ")");
                StringBuilder sb = new StringBuilder("<mut>");
                if (members.isEmpty()) sb.append("Noch leer");
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append("• ").append(Msg.esc(members.get(i).getName()));
                }
                tooltip = Msg.mm(sb.toString());
            }
            final Team choice = t;
            DialogAction action = DialogAction.customClick((response, audience) ->
                    Bukkit.getScheduler().runTask(plugin, () -> choose(plugin, id, choice)), once);
            buttons.add(ActionButton.builder(label).tooltip(tooltip).width(100).action(action).build());
        }

        Team cur = game.team(viewer);
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Msg.mm("<logo> <mut>– Team wählen"))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Msg.mm(cur == null ? "<mut>Wähle dein Team."
                                : "<mut>Du bist in Team " + cur.colored() + "<mut>. Wechseln?"))))
                        .build())
                .type(DialogType.multiAction(buttons, ActionButton.builder(Msg.mm("<mut>Schließen")).width(100).build(), 3)));
        viewer.showDialog(dialog);
    }

    private static void choose(MissileWarsPlugin plugin, UUID id, Team choice) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        Game g = plugin.games().get(p);
        if (g == null || g.team(p) == null) return;
        Team target = choice;
        if (target == null) {
            int gr = g.teamPlayers(Team.GREEN).size(), rd = g.teamPlayers(Team.RED).size();
            Team cur = g.team(p);
            target = g.teamPlayers(cur.other()).size() < g.teamPlayers(cur).size() ? cur.other()
                    : (gr == rd ? cur : (gr < rd ? Team.GREEN : Team.RED));
        }
        if (target != g.team(p)) g.changeTeam(p, target);
    }
}
