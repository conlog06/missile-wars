package de.crafterteam.missilewars.rank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

/** Wird nur geladen, wenn LuckPerms installiert ist. */
public final class LuckPermsRanks implements RankProvider {

    private final LuckPerms api = LuckPermsProvider.get();

    private CachedMetaData meta(Player p) {
        User u = api.getUserManager().getUser(p.getUniqueId());
        return u == null ? null : u.getCachedData().getMetaData();
    }

    @Override
    public String prefix(Player p) {
        CachedMetaData m = meta(p);
        return m == null || m.getPrefix() == null ? "" : m.getPrefix();
    }

    @Override
    public String suffix(Player p) {
        CachedMetaData m = meta(p);
        return m == null || m.getSuffix() == null ? "" : m.getSuffix();
    }

    @Override
    public int weight(Player p) {
        User u = api.getUserManager().getUser(p.getUniqueId());
        if (u == null) return 0;
        Group g = api.getGroupManager().getGroup(u.getPrimaryGroup());
        return g == null ? 0 : g.getWeight().orElse(0);
    }

    @Override
    public String name() {
        return "LuckPerms";
    }
}
