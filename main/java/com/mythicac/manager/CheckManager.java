package com.mythicac.manager;

import com.mythicac.MythicAntiCheat;
import com.mythicac.check.Check;
import com.mythicac.check.MovementCheck;
import com.mythicac.check.RotationCheck;
import com.mythicac.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CheckManager {

    private final MythicAntiCheat plugin;
    private final List<Check> checks = new ArrayList<>();

    public CheckManager(MythicAntiCheat plugin) {
        this.plugin = plugin;
        loadChecks();
    }

    private void loadChecks() {
        checks.clear();
        
        if (plugin.getConfig().getBoolean("checks.movement.enabled", true)) {
            checks.add(new MovementCheck(plugin));
        }
        
        if (plugin.getConfig().getBoolean("checks.rotation.enabled", true)) {
            checks.add(new RotationCheck(plugin));
        }
        
        plugin.getACLogger().info("Loaded " + checks.size() + " checks");
    }

    public void reloadChecks() {
        loadChecks();
    }

    public void runChecks(Player player) {
        PlayerData data = plugin.getPlayerTracker().getPlayerData(player);
        if (data == null) return;
        
        for (Check check : checks) {
            if (check.isEnabled()) {
                check.check(player, data);
            }
        }
        
        double movementDecay = plugin.getConfig().getDouble("checks.movement.suspicion_decay", 0.98);
        double rotationDecay = plugin.getConfig().getDouble("checks.rotation.suspicion_decay", 0.97);
        
        data.decayMovementSuspicion(movementDecay);
        data.decayRotationSuspicion(rotationDecay);
        
        checkAlerts(player, data);
    }

    private void checkAlerts(Player player, PlayerData data) {
        double movementThreshold = plugin.getConfig().getDouble("checks.movement.alert_threshold", 50.0);
        double rotationThreshold = plugin.getConfig().getDouble("checks.rotation.alert_threshold", 60.0);
        
        long now = System.currentTimeMillis();
        int cooldown = plugin.getConfig().getInt("alerts.alert_cooldown_seconds", 10) * 1000;
        
        if (now - data.getLastAlertTime() < cooldown) return;
        
        boolean alert = false;
        StringBuilder alertMsg = new StringBuilder();
        alertMsg.append("§c[MythicAC] §f").append(player.getName()).append(" §7| ");
        
        if (data.getMovementSuspicion() >= movementThreshold) {
            alert = true;
            alertMsg.append("§eMovement: §f").append(String.format("%.1f", data.getMovementSuspicion())).append(" ");
        }
        
        if (data.getRotationSuspicion() >= rotationThreshold) {
            alert = true;
            alertMsg.append("§eRotation: §f").append(String.format("%.1f", data.getRotationSuspicion())).append(" ");
        }
        
        if (alert) {
            data.setLastAlertTime(now);
            
            plugin.getACLogger().alert(player.getName() + " | Movement: " + 
                String.format("%.1f", data.getMovementSuspicion()) + " | Rotation: " + 
                String.format("%.1f", data.getRotationSuspicion()));
            
            if (plugin.getConfig().getBoolean("alerts.notify_staff", true)) {
                for (Player staff : plugin.getServer().getOnlinePlayers()) {
                    if (staff.hasPermission("mythicac.alerts")) {
                        staff.sendMessage(alertMsg.toString());
                    }
                }
            }
            
            data.decayMovementSuspicion(0.5);
            data.decayRotationSuspicion(0.5);
        }
    }

    public List<Check> getChecks() {
        return checks;
    }
}
