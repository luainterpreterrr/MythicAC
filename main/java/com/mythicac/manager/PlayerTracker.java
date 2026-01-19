package com.mythicac.manager;

import com.mythicac.MythicAntiCheat;
import com.mythicac.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTracker implements Listener {

    private final MythicAntiCheat plugin;
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    public PlayerTracker(MythicAntiCheat plugin) {
        this.plugin = plugin;
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            playerDataMap.put(player.getUniqueId(), new PlayerData(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerDataMap.put(player.getUniqueId(), new PlayerData(player));
        
        if (plugin.isDebug()) {
            plugin.getACLogger().debug("Started tracking player: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerData data = playerDataMap.remove(event.getPlayer().getUniqueId());
        
        if (plugin.isDebug() && data != null) {
            plugin.getACLogger().debug("Stopped tracking player: " + event.getPlayer().getName() + 
                " | Movement violations: " + data.getMovementViolations() + 
                " | Rotation violations: " + data.getRotationViolations());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMoveSetback(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.get(player.getUniqueId());
        
        if (data != null && data.isFrozen() && !data.isTesting()) {
            Location freezeLoc = data.getFreezeLocation();
            if (freezeLoc != null && freezeLoc.getWorld() != null) {
                Location to = event.getTo();
                if (to != null) {
                    Location setback = freezeLoc.clone();
                    setback.setYaw(to.getYaw());
                    setback.setPitch(to.getPitch());
                    event.setTo(setback);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = getPlayerData(player);
        if (data != null) {
            if (data.isFrozen() && !data.isTesting()) {
                return;
            }
            
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null) {
                double dx = to.getX() - from.getX();
                double dz = to.getZ() - from.getZ();
                double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                
                data.recordMoveEvent(horizontalDist);
            }
            
            data.update(player);
        }
    }

    public PlayerData getPlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData(player));
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public int getTrackedCount() {
        return playerDataMap.size();
    }

    public void clearData(Player player) {
        playerDataMap.remove(player.getUniqueId());
    }
}
