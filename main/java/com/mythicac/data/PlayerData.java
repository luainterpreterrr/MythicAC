package com.mythicac.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private final String name;
    
    private final LinkedList<Location> positionHistory = new LinkedList<>();
    private final LinkedList<Long> timestamps = new LinkedList<>();
    private final LinkedList<Float> yawHistory = new LinkedList<>();
    private final LinkedList<Float> pitchHistory = new LinkedList<>();
    private Location lastLocation;
    private Location lastValidLocation;
    private float lastYaw;
    private float lastPitch;
    private boolean lastOnGround;
    private long lastUpdateTime;
    private double lastHorizontalDistance;
    private double lastVerticalDistance;
    private float lastYawDelta;
    private float lastPitchDelta;
    private double movementSuspicion;
    private double rotationSuspicion;
    private int movementViolations;
    private int rotationViolations;
    private long lastAlertTime;
    private long lastAttackPacketTime;
    private long lastUsePacketTime;
    private int moveEventsThisSecond = 0;
    private long secondStartTime = System.currentTimeMillis();
    private double distanceThisSecond = 0.0;
    private int lastMoveEventsPerSecond = 0;
    private double lastDistancePerSecond = 0.0;
    private static final int MAX_HISTORY = 20;

    public PlayerData(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.lastLocation = player.getLocation().clone();
        this.lastYaw = player.getLocation().getYaw();
        this.lastPitch = player.getLocation().getPitch();
        this.lastOnGround = player.isOnGround();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public void update(Player player) {
        Location current = player.getLocation();
        long now = System.currentTimeMillis();
        
        if (lastLocation != null) {
            double dx = current.getX() - lastLocation.getX();
            double dz = current.getZ() - lastLocation.getZ();
            lastHorizontalDistance = Math.sqrt(dx * dx + dz * dz);
            lastVerticalDistance = current.getY() - lastLocation.getY();
        }
        
        lastYawDelta = normalizeYaw(current.getYaw() - lastYaw);
        lastPitchDelta = current.getPitch() - lastPitch;
        
        positionHistory.addLast(current.clone());
        timestamps.addLast(now);
        yawHistory.addLast(current.getYaw());
        pitchHistory.addLast(current.getPitch());
        
        while (positionHistory.size() > MAX_HISTORY) {
            positionHistory.removeFirst();
            timestamps.removeFirst();
            yawHistory.removeFirst();
            pitchHistory.removeFirst();
        }
        
        lastLocation = current.clone();
        lastYaw = current.getYaw();
        lastPitch = current.getPitch();
        lastOnGround = player.isOnGround();
        lastUpdateTime = now;
    }

    public void addMovementSuspicion(double amount) {
        movementSuspicion = Math.min(100.0, movementSuspicion + amount);
    }

    public void addRotationSuspicion(double amount) {
        rotationSuspicion = Math.min(100.0, rotationSuspicion + amount);
    }

    public void decayMovementSuspicion(double multiplier) {
        movementSuspicion *= multiplier;
        if (movementSuspicion < 0.1) movementSuspicion = 0;
    }

    public void decayRotationSuspicion(double multiplier) {
        rotationSuspicion *= multiplier;
        if (rotationSuspicion < 0.1) rotationSuspicion = 0;
    }

    public void incrementMovementViolations() {
        movementViolations++;
    }

    public void incrementRotationViolations() {
        rotationViolations++;
    }

    public void resetSuspicion() {
        movementSuspicion = 0;
        rotationSuspicion = 0;
    }

    private float normalizeYaw(float yaw) {
        while (yaw > 180f) yaw -= 360f;
        while (yaw < -180f) yaw += 360f;
        return yaw;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public LinkedList<Location> getPositionHistory() { return positionHistory; }
    public LinkedList<Long> getTimestamps() { return timestamps; }
    public LinkedList<Float> getYawHistory() { return yawHistory; }
    public LinkedList<Float> getPitchHistory() { return pitchHistory; }
    public Location getLastLocation() { return lastLocation; }
    public float getLastYaw() { return lastYaw; }
    public float getLastPitch() { return lastPitch; }
    public boolean isLastOnGround() { return lastOnGround; }
    public long getLastUpdateTime() { return lastUpdateTime; }
    public double getLastHorizontalDistance() { return lastHorizontalDistance; }
    public double getLastVerticalDistance() { return lastVerticalDistance; }
    public float getLastYawDelta() { return lastYawDelta; }
    public float getLastPitchDelta() { return lastPitchDelta; }
    public double getMovementSuspicion() { return movementSuspicion; }
    public double getRotationSuspicion() { return rotationSuspicion; }
    public int getMovementViolations() { return movementViolations; }
    public int getRotationViolations() { return rotationViolations; }
    public long getLastAlertTime() { return lastAlertTime; }
    public void setLastAlertTime(long time) { this.lastAlertTime = time; }
    public long getLastAttackPacketTime() { return lastAttackPacketTime; }
    public void setLastAttackPacketTime(long time) { this.lastAttackPacketTime = time; }
    public long getLastUsePacketTime() { return lastUsePacketTime; }
    public void setLastUsePacketTime(long time) { this.lastUsePacketTime = time; }
    
    public boolean isInCombat() {
        long now = System.currentTimeMillis();
        return (now - lastAttackPacketTime) < 3000 || (now - lastUsePacketTime) < 3000;
    }

    public double getCombinedSuspicion() {
        return (movementSuspicion + rotationSuspicion) / 2.0;
    }
    
    public void recordMoveEvent(double horizontalDistance) {
        long now = System.currentTimeMillis();
        
        if (now - secondStartTime >= 1000) {
            lastMoveEventsPerSecond = moveEventsThisSecond;
            lastDistancePerSecond = distanceThisSecond;
            
            moveEventsThisSecond = 0;
            distanceThisSecond = 0.0;
            secondStartTime = now;
        }
        
        moveEventsThisSecond++;
        distanceThisSecond += horizontalDistance;
    }
    
    public int getLastMoveEventsPerSecond() { return lastMoveEventsPerSecond; }
    public double getLastDistancePerSecond() { return lastDistancePerSecond; }
    public int getCurrentMoveEvents() { return moveEventsThisSecond; }
    public double getCurrentDistance() { return distanceThisSecond; }
    
    public Location getLastValidLocation() { return lastValidLocation; }
    public void setLastValidLocation(Location loc) { this.lastValidLocation = loc != null ? loc.clone() : null; }
    public void updateValidLocation(Location loc) {
        if (loc != null && loc.getWorld() != null) {
            this.lastValidLocation = loc.clone();
        }
    }
    
    private boolean frozen = false;
    private boolean testing = false;
    private int violationStreak = 0;
    private long frozenUntil = 0;
    private long testingUntil = 0;
    private Location testStartLocation = null;
    private static final long FREEZE_DURATION_MS = 2000;
    private static final long TESTING_DURATION_MS = 1000;
    
    public boolean isFrozen() { 
        if (frozen && !testing && System.currentTimeMillis() > frozenUntil) {
            testing = true;
            testingUntil = System.currentTimeMillis() + TESTING_DURATION_MS;
            testStartLocation = lastValidLocation != null ? lastValidLocation.clone() : null;
        }
        return frozen && !testing;
    }
    
    public boolean isTesting() {
        return frozen && testing;
    }
    
    public void freeze() {
        this.frozen = true;
        this.testing = false;
        this.violationStreak++;
        this.frozenUntil = System.currentTimeMillis() + FREEZE_DURATION_MS;
    }
    
    public boolean canUnfreeze() {
        return frozen && testing && System.currentTimeMillis() > testingUntil;
    }
    
    public void unfreeze() {
        this.frozen = false;
        this.testing = false;
        this.violationStreak = 0;
    }
    
    public int getViolationStreak() { return violationStreak; }
    public long getFreezeTimeRemaining() { 
        return Math.max(0, frozenUntil - System.currentTimeMillis()); 
    }
    
    public Location getFreezeLocation() { return lastValidLocation; }
    public Location getTestStartLocation() { return testStartLocation; }
}
