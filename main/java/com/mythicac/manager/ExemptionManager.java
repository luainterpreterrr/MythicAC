package com.mythicac.manager;

import com.mythicac.MythicAntiCheat;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExemptionManager {

    private final MythicAntiCheat plugin;
    
    private final Map<UUID, Long> moveExemptUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rotateExemptUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportExemptUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> moveExemptSource = new ConcurrentHashMap<>();
    private final Map<UUID, String> rotateExemptSource = new ConcurrentHashMap<>();
    private final Map<UUID, String> teleportExemptSource = new ConcurrentHashMap<>();
    
    private long currentTick = 0;

    public ExemptionManager(MythicAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void tickExemptions() {
        currentTick++;
    }

    public void exemptMove(Player player, int ticks, String source) {
        UUID uuid = player.getUniqueId();
        long exemptUntil = currentTick + ticks + getPingExtraTicks(player);
        
        if (!moveExemptUntil.containsKey(uuid) || moveExemptUntil.get(uuid) < exemptUntil) {
            moveExemptUntil.put(uuid, exemptUntil);
            moveExemptSource.put(uuid, source);
            
            if (plugin.getConfig().getBoolean("logging.log_exemptions", false)) {
                plugin.getACLogger().debug("EXEMPT_MOVE: " + player.getName() + 
                    " for " + ticks + " ticks (source: " + source + ")");
            }
        }
    }

    public void exemptMoveUntil(Player player, long untilTick, String source) {
        UUID uuid = player.getUniqueId();
        long exemptUntil = untilTick + getPingExtraTicks(player);
        
        if (!moveExemptUntil.containsKey(uuid) || moveExemptUntil.get(uuid) < exemptUntil) {
            moveExemptUntil.put(uuid, exemptUntil);
            moveExemptSource.put(uuid, source);
        }
    }

    public boolean isMoveExempt(Player player) {
        Long until = moveExemptUntil.get(player.getUniqueId());
        return until != null && currentTick <= until;
    }

    public String getMoveExemptSource(Player player) {
        return moveExemptSource.getOrDefault(player.getUniqueId(), "unknown");
    }

    public void exemptRotate(Player player, int ticks, String source) {
        UUID uuid = player.getUniqueId();
        long exemptUntil = currentTick + ticks + getPingExtraTicks(player);
        
        if (!rotateExemptUntil.containsKey(uuid) || rotateExemptUntil.get(uuid) < exemptUntil) {
            rotateExemptUntil.put(uuid, exemptUntil);
            rotateExemptSource.put(uuid, source);
            
            if (plugin.getConfig().getBoolean("logging.log_exemptions", false)) {
                plugin.getACLogger().debug("EXEMPT_ROTATE: " + player.getName() + 
                    " for " + ticks + " ticks (source: " + source + ")");
            }
        }
    }

    public void exemptRotateUntil(Player player, long untilTick, String source) {
        UUID uuid = player.getUniqueId();
        long exemptUntil = untilTick + getPingExtraTicks(player);
        
        if (!rotateExemptUntil.containsKey(uuid) || rotateExemptUntil.get(uuid) < exemptUntil) {
            rotateExemptUntil.put(uuid, exemptUntil);
            rotateExemptSource.put(uuid, source);
        }
    }

    public boolean isRotateExempt(Player player) {
        Long until = rotateExemptUntil.get(player.getUniqueId());
        return until != null && currentTick <= until;
    }

    public String getRotateExemptSource(Player player) {
        return rotateExemptSource.getOrDefault(player.getUniqueId(), "unknown");
    }

    public void exemptTeleport(Player player, int ticks, String source) {
        UUID uuid = player.getUniqueId();
        long exemptUntil = currentTick + ticks + getPingExtraTicks(player);
        
        if (!teleportExemptUntil.containsKey(uuid) || teleportExemptUntil.get(uuid) < exemptUntil) {
            teleportExemptUntil.put(uuid, exemptUntil);
            teleportExemptSource.put(uuid, source);
            
            exemptMove(player, ticks, "teleport:" + source);
            exemptRotate(player, ticks, "teleport:" + source);
            
            if (plugin.getConfig().getBoolean("logging.log_exemptions", false)) {
                plugin.getACLogger().debug("EXEMPT_TELEPORT: " + player.getName() + 
                    " for " + ticks + " ticks (source: " + source + ")");
            }
        }
    }

    public void exemptTeleportUntilTick(Player player, long untilTick, String source) {
        UUID uuid = player.getUniqueId();
        long exemptUntil = untilTick + getPingExtraTicks(player);
        
        if (!teleportExemptUntil.containsKey(uuid) || teleportExemptUntil.get(uuid) < exemptUntil) {
            teleportExemptUntil.put(uuid, exemptUntil);
            teleportExemptSource.put(uuid, source);
            
            int ticks = (int) (untilTick - currentTick);
            exemptMove(player, ticks, "teleport:" + source);
            exemptRotate(player, ticks, "teleport:" + source);
        }
    }

    public boolean isTeleportExempt(Player player) {
        Long until = teleportExemptUntil.get(player.getUniqueId());
        return until != null && currentTick <= until;
    }

    public String getTeleportExemptSource(Player player) {
        return teleportExemptSource.getOrDefault(player.getUniqueId(), "unknown");
    }

    public boolean isExempt(Player player) {
        return isMoveExempt(player) || isRotateExempt(player) || isTeleportExempt(player);
    }

    private int getPingExtraTicks(Player player) {
        int ping = player.getPing();
        int threshold = plugin.getConfig().getInt("exemptions.high_ping_threshold", 200);
        int extraTicks = plugin.getConfig().getInt("exemptions.high_ping_extra_ticks", 20);
        
        return ping > threshold ? extraTicks : 0;
    }

    public void clearExemptions(Player player) {
        UUID uuid = player.getUniqueId();
        moveExemptUntil.remove(uuid);
        rotateExemptUntil.remove(uuid);
        teleportExemptUntil.remove(uuid);
        moveExemptSource.remove(uuid);
        rotateExemptSource.remove(uuid);
        teleportExemptSource.remove(uuid);
    }

    public long getCurrentTick() {
        return currentTick;
    }
}
