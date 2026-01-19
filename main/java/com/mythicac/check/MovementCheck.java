package com.mythicac.check;

import com.mythicac.MythicAntiCheat;
import com.mythicac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class MovementCheck extends Check {

    private static final double WALKING_SPEED = 4.317;
    private static final double SPRINTING_SPEED = 5.612;
    private static final double SPRINT_JUMP_SPEED = 7.127;
    private static final double SPEED_MULTIPLIER_PER_LEVEL = 0.20;
    
    private double suspicionIncrement;
    private boolean setbackEnabled;

    public MovementCheck(MythicAntiCheat plugin) {
        super(plugin, "Movement");
        reload();
    }

    @Override
    public void reload() {
        enabled = plugin.getConfig().getBoolean("checks.movement.enabled", true);
        debug = plugin.getConfig().getBoolean("checks.movement.debug", false);
        suspicionIncrement = plugin.getConfig().getDouble("checks.movement.suspicion_increment", 10.0);
        setbackEnabled = !plugin.isLogOnlyMode();
    }

    @Override
    public void check(Player player, PlayerData data) {
        if (plugin.getExemptionManager().isMoveExempt(player)) {
            data.updateValidLocation(player.getLocation());
            return;
        }
        
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            data.updateValidLocation(player.getLocation());
            return;
        }
        
        if (player.isFlying() || player.isGliding()) {
            data.updateValidLocation(player.getLocation());
            return;
        }
        
        if (player.isInsideVehicle()) {
            data.updateValidLocation(player.getLocation());
            return;
        }
        
        int eventsPerSec = data.getLastMoveEventsPerSecond();
        double distPerSec = data.getLastDistancePerSecond();
        
        double maxDistPerSec = calculateStrictMaxSpeed(player);
        int maxEventsPerSec = 22;
        
        if (isDebug()) {
            plugin.getLogger().info("[SPEED] " + player.getName() + 
                " speed=" + String.format("%.2f", distPerSec) + " b/s" +
                " (max=" + String.format("%.2f", maxDistPerSec) + ")" +
                " events=" + eventsPerSec);
        }
        
        boolean violation = false;
        StringBuilder details = new StringBuilder();
        
        if (eventsPerSec > maxEventsPerSec) {
            violation = true;
            double timerSpeed = (double) eventsPerSec / 20.0;
            double suspicion = suspicionIncrement * (timerSpeed - 1.0) * 5;
            data.addMovementSuspicion(suspicion);
            data.incrementMovementViolations();
            
            details.append("TIMER: ").append(String.format("%.0f", timerSpeed * 100)).append("%");
            
            plugin.getLogger().warning("[SPEED HACK] " + player.getName() + 
                " TIMER=" + String.format("%.0f", timerSpeed * 100) + "% (" + eventsPerSec + " events/sec)");
        }
        
        if (distPerSec > maxDistPerSec && eventsPerSec > 3) {
            violation = true;
            double excess = distPerSec - maxDistPerSec;
            double suspicion = suspicionIncrement * (1.0 + excess / maxDistPerSec) * 2;
            data.addMovementSuspicion(suspicion);
            data.incrementMovementViolations();
            
            if (details.length() > 0) details.append(" | ");
            details.append("SPEED: ").append(String.format("%.1f", distPerSec))
                   .append(">").append(String.format("%.1f", maxDistPerSec));
            
            plugin.getLogger().warning("[SPEED HACK] " + player.getName() + 
                " SPEED=" + String.format("%.1f", distPerSec) + " b/s (max=" + 
                String.format("%.1f", maxDistPerSec) + ")");
        }
        
        if (violation) {
            plugin.getACLogger().movement(player.getName(), data.getMovementSuspicion(), details.toString());
            
            if (setbackEnabled) {
                Location setbackLoc;
                if (data.isTesting() && data.getTestStartLocation() != null) {
                    setbackLoc = data.getTestStartLocation();
                    plugin.getLogger().warning("[TEST FAILED] " + player.getName() + " still hacking - setback to test start");
                } else {
                    setbackLoc = data.getLastValidLocation();
                }
                
                if (setbackLoc != null && setbackLoc.getWorld() != null) {
                    data.setLastValidLocation(setbackLoc);
                    data.freeze();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.teleport(setbackLoc);
                    });
                }
            }
        } else {
            if (data.isTesting()) {
                if (data.canUnfreeze()) {
                    data.unfreeze();
                    plugin.getLogger().info("[UNFREEZE] " + player.getName() + " passed speed test, unfrozen");
                    data.updateValidLocation(player.getLocation());
                }
            } else if (!data.isFrozen()) {
                data.updateValidLocation(player.getLocation());
            }
        }
    }
    
    private double calculateStrictMaxSpeed(Player player) {
        double maxSpeed = SPRINT_JUMP_SPEED;
        maxSpeed *= 1.05;
        maxSpeed *= getLagMultiplier();
        
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int level = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            maxSpeed *= (1.0 + SPEED_MULTIPLIER_PER_LEVEL * level);
        }
        
        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int level = player.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier() + 1;
            maxSpeed *= Math.max(0.1, 1.0 - 0.15 * level);
        }
        
        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) {
            maxSpeed *= 2.0;
        }
        
        return maxSpeed;
    }
}
