package com.mythicac.check;

import com.mythicac.MythicAntiCheat;
import com.mythicac.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.LinkedList;

public class RotationCheck extends Check {

    private double maxYawChange;
    private double maxPitchChange;
    private double snapThresholdDegrees;
    private int snapTimeMs;
    private double suspicionIncrement;
    private boolean requireMovementCorrelation;

    public RotationCheck(MythicAntiCheat plugin) {
        super(plugin, "Rotation");
        reload();
    }

    @Override
    public void reload() {
        enabled = plugin.getConfig().getBoolean("checks.rotation.enabled", true);
        debug = plugin.getConfig().getBoolean("checks.rotation.debug", false);
        maxYawChange = plugin.getConfig().getDouble("checks.rotation.max_yaw_change", 180.0);
        maxPitchChange = plugin.getConfig().getDouble("checks.rotation.max_pitch_change", 90.0);
        snapThresholdDegrees = plugin.getConfig().getDouble("checks.rotation.snap_threshold_degrees", 120.0);
        snapTimeMs = plugin.getConfig().getInt("checks.rotation.snap_time_ms", 50);
        suspicionIncrement = plugin.getConfig().getDouble("checks.rotation.suspicion_increment", 8.0);
        requireMovementCorrelation = plugin.getConfig().getBoolean("checks.rotation.require_movement_correlation", true);
    }

    @Override
    public void check(Player player, PlayerData data) {
        if (plugin.getExemptionManager().isRotateExempt(player)) {
            if (isDebug()) {
                plugin.getACLogger().debug("Rotation check skipped for " + player.getName() + 
                    " (exempt: " + plugin.getExemptionManager().getRotateExemptSource(player) + ")");
            }
            return;
        }
        
        if (data.getYawHistory().size() < 3) {
            return;
        }
        
        float yawDelta = Math.abs(data.getLastYawDelta());
        float pitchDelta = Math.abs(data.getLastPitchDelta());
        
        boolean violation = false;
        StringBuilder details = new StringBuilder();
        double suspicion = 0;
        
        double rotationMagnitude = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
        if (rotationMagnitude > snapThresholdDegrees) {
            if (isSnapRotation(data)) {
                if (!requireMovementCorrelation || hasMovementCorrelation(data)) {
                    violation = true;
                    suspicion = suspicionIncrement * (rotationMagnitude / snapThresholdDegrees);
                    details.append("Snap: ").append(String.format("%.1f", rotationMagnitude)).append("°");
                }
            }
        }
        
        float currentPitch = data.getLastPitch();
        if (currentPitch < -90.0f || currentPitch > 90.0f) {
            violation = true;
            suspicion += suspicionIncrement * 2;
            details.append(" | InvalidPitch: ").append(String.format("%.1f", currentPitch));
        }
        
        if (hasRoboticPattern(data)) {
            if (hasMovementCorrelation(data)) {
                violation = true;
                suspicion += suspicionIncrement * 0.5;
                details.append(" | RoboticPattern");
            }
        }
        
        if (violation && suspicion > 0) {
            data.addRotationSuspicion(suspicion);
            data.incrementRotationViolations();
            plugin.getACLogger().rotation(player.getName(), data.getRotationSuspicion(), details.toString());
        }
    }

    private boolean isSnapRotation(PlayerData data) {
        LinkedList<Long> timestamps = data.getTimestamps();
        if (timestamps.size() < 2) return false;
        
        long timeDiff = timestamps.getLast() - timestamps.get(timestamps.size() - 2);
        return timeDiff <= snapTimeMs;
    }

    private boolean hasMovementCorrelation(PlayerData data) {
        return data.getMovementSuspicion() > 5.0 || data.getLastHorizontalDistance() > 0.3;
    }

    private boolean hasRoboticPattern(PlayerData data) {
        LinkedList<Float> yawHistory = data.getYawHistory();
        if (yawHistory.size() < 5) return false;
        
        double[] deltas = new double[yawHistory.size() - 1];
        for (int i = 1; i < yawHistory.size(); i++) {
            float diff = yawHistory.get(i) - yawHistory.get(i - 1);
                while (diff > 180f) diff -= 360f;
            while (diff < -180f) diff += 360f;
            deltas[i - 1] = Math.abs(diff);
        }
        
        double avg = 0;
        for (double d : deltas) avg += d;
        avg /= deltas.length;
        
        if (avg < 5.0) return false;
        
        double variance = 0;
        for (double d : deltas) {
            variance += Math.pow(d - avg, 2);
        }
        variance /= deltas.length;
        return variance < 2.0 && avg > 10.0;
    }
}
