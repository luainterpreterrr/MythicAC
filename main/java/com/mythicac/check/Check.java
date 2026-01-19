package com.mythicac.check;

import com.mythicac.MythicAntiCheat;
import com.mythicac.data.PlayerData;
import org.bukkit.entity.Player;

public abstract class Check {

    protected final MythicAntiCheat plugin;
    protected final String name;
    protected boolean enabled;
    protected boolean debug;

    public Check(MythicAntiCheat plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        reload();
    }

    public abstract void check(Player player, PlayerData data);

    public void reload() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public boolean isDebug() {
        return debug || plugin.isDebug();
    }

    protected double getLagMultiplier() {
        return plugin.getLagMultiplier();
    }
}
