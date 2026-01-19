package com.mythicac.listener;

import com.mythicac.MythicAntiCheat;
import com.mythicac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

public class PlayerListener implements Listener {

    private final MythicAntiCheat plugin;

    public PlayerListener(MythicAntiCheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        int exemptTicks = plugin.getConfig().getInt("exemptions.teleport_ticks", 40);
        
        String source = "teleport:" + event.getCause().name().toLowerCase();
        plugin.getExemptionManager().exemptTeleport(player, exemptTicks, source);
        
        PlayerData data = plugin.getPlayerTracker().getPlayerData(player);
        if (data != null) {
            data.resetSuspicion();
        }
        
        if (plugin.isDebug()) {
            plugin.getACLogger().debug("PlayerTeleport: " + player.getName() + 
                " cause=" + event.getCause() + " exempted for " + exemptTicks + " ticks");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        int baseExemptTicks = plugin.getConfig().getInt("exemptions.velocity_ticks", 20);
        int maxExemptTicks = plugin.getConfig().getInt("exemptions.max_velocity_ticks", 40);
        
        double velocityMagnitude = event.getVelocity().length();
        if (velocityMagnitude > 0.5) {
            int adjustedTicks = Math.min((int) (baseExemptTicks * (1 + velocityMagnitude * 0.3)), maxExemptTicks);
            
            plugin.getExemptionManager().exemptMove(player, adjustedTicks, "velocity:" + 
                String.format("%.2f", velocityMagnitude));
            
            plugin.getExemptionManager().exemptRotate(player, adjustedTicks / 2, "velocity_rotation");
            
            if (plugin.isDebug()) {
                plugin.getACLogger().debug("PlayerVelocity: " + player.getName() + 
                    " magnitude=" + String.format("%.2f", velocityMagnitude) + 
                    " exempted for " + adjustedTicks + " ticks");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            PlayerData data = plugin.getPlayerTracker().getPlayerData(player);
            if (data != null) {
                data.setLastAttackPacketTime(System.currentTimeMillis());
            }
        }
        
        if (event.getEntity() instanceof Player victim) {
            int exemptTicks = plugin.getConfig().getInt("exemptions.velocity_ticks", 60) / 2;
            plugin.getExemptionManager().exemptMove(victim, exemptTicks, "damage_knockback");
        }
    }
}
