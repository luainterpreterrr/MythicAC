package com.mythicac.hook;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.mythicac.MythicAntiCheat;
import com.mythicac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ProtocolLibHook {

    private final MythicAntiCheat plugin;
    private boolean available;
    private boolean debug;
    private ProtocolManager protocolManager;
    private PacketAdapter movementListener;
    private PacketAdapter rotationListener;
    private PacketAdapter attackListener;
    private PacketAdapter useListener;
    
    private final java.util.Map<java.util.UUID, double[]> lastPacketPosition = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastPacketTime = new java.util.concurrent.ConcurrentHashMap<>();

    public ProtocolLibHook(MythicAntiCheat plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
        this.debug = plugin.getConfig().getBoolean("protocollib.debug", false);
        
        if (available) {
            try {
                protocolManager = ProtocolLibrary.getProtocolManager();
                plugin.getACLogger().debug("ProtocolLib manager obtained");
            } catch (Exception e) {
                plugin.getACLogger().warn("Failed to get ProtocolLib manager: " + e.getMessage());
                available = false;
            }
        }
    }

    public void register() {
        if (!available || protocolManager == null) return;
        
        movementListener = new PacketAdapter(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handleMovementPacket(event);
            }
        };
        protocolManager.addPacketListener(movementListener);
        
        rotationListener = new PacketAdapter(plugin, ListenerPriority.MONITOR,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handleRotationPacket(event);
            }
        };
        protocolManager.addPacketListener(rotationListener);
        
        if (plugin.getConfig().getBoolean("protocollib.track_attack_packets", true)) {
            attackListener = new PacketAdapter(plugin, ListenerPriority.MONITOR,
                    PacketType.Play.Client.USE_ENTITY) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    handleAttackPacket(event);
                }
            };
            protocolManager.addPacketListener(attackListener);
        }
        
        if (plugin.getConfig().getBoolean("protocollib.track_use_packets", true)) {
            useListener = new PacketAdapter(plugin, ListenerPriority.MONITOR,
                    PacketType.Play.Client.USE_ITEM,
                    PacketType.Play.Client.BLOCK_PLACE) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    handleUsePacket(event);
                }
            };
            protocolManager.addPacketListener(useListener);
        }
        
        plugin.getACLogger().info("ProtocolLib packet listeners registered");
    }

    public void unregister() {
        if (protocolManager == null) return;
        
        if (movementListener != null) {
            protocolManager.removePacketListener(movementListener);
        }
        if (rotationListener != null) {
            protocolManager.removePacketListener(rotationListener);
        }
        if (attackListener != null) {
            protocolManager.removePacketListener(attackListener);
        }
        if (useListener != null) {
            protocolManager.removePacketListener(useListener);
        }
        
        plugin.getACLogger().debug("ProtocolLib packet listeners unregistered");
    }
    
    private void handleMovementPacket(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.hasPermission("mythicac.bypass")) return;
        
        if (plugin.getExemptionManager().isMoveExempt(player)) return;
        
        try {
            double x = event.getPacket().getDoubles().read(0);
            double y = event.getPacket().getDoubles().read(1);
            double z = event.getPacket().getDoubles().read(2);
            
            java.util.UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            
            double[] lastPos = lastPacketPosition.get(uuid);
            Long lastTime = lastPacketTime.get(uuid);
            
            if (lastPos != null && lastTime != null) {
                double dx = x - lastPos[0];
                double dy = y - lastPos[1];
                double dz = z - lastPos[2];
                double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                long timeDelta = now - lastTime;
                
                double speed = (timeDelta > 0) ? (horizontalDist / timeDelta) * 1000 : 0;
                double maxSpeed = 15.0;
                
                plugin.getLogger().info("[PACKET] " + player.getName() + 
                    " claimed move: " + String.format("%.2f", horizontalDist) + " blocks" +
                    " in " + timeDelta + "ms" +
                    " = " + String.format("%.1f", speed) + " b/s" +
                    " (max=" + maxSpeed + ")");
                
                if (speed > maxSpeed && timeDelta > 10) {
                    PlayerData data = plugin.getPlayerTracker().getPlayerData(player);
                    if (data != null) {
                        double suspicion = Math.min(30.0, (speed - maxSpeed) * 2);
                        data.addMovementSuspicion(suspicion);
                        
                        plugin.getLogger().warning("[SPEED HACK] " + player.getName() + 
                            " claiming " + String.format("%.1f", speed) + " blocks/sec!" +
                            " (max=" + maxSpeed + ") Suspicion: +" + String.format("%.1f", suspicion));
                        
                        plugin.getACLogger().alert("PACKET SPEED: " + player.getName() + 
                            " | " + String.format("%.1f", speed) + " b/s (max " + maxSpeed + ")");
                    }
                }
            }
            
            lastPacketPosition.put(uuid, new double[]{x, y, z});
            lastPacketTime.put(uuid, now);
            
        } catch (Exception e) {
        }
    }

    private void handleRotationPacket(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.hasPermission("mythicac.bypass")) return;
        
        try {
            float yaw = event.getPacket().getFloat().read(0);
            float pitch = event.getPacket().getFloat().read(1);
            
            PlayerData data = plugin.getPlayerTracker().getPlayerData(player);
            if (data == null) return;
            
            float yawDelta = Math.abs(normalizeYaw(yaw - data.getLastYaw()));
            float pitchDelta = Math.abs(pitch - data.getLastPitch());
            
            long now = System.currentTimeMillis();
            long timeSinceLastUpdate = now - data.getLastUpdateTime();
            
            if (debug) {
                if (yawDelta > 50 || pitchDelta > 30) {
                    plugin.getACLogger().debug("RotationPacket: " + player.getName() + 
                        " | Yaw: " + String.format("%.1f", yawDelta) + 
                        " | Pitch: " + String.format("%.1f", pitchDelta) +
                        " | TimeDelta: " + timeSinceLastUpdate + "ms");
                }
            }
            
            if (timeSinceLastUpdate < 50 && (yawDelta > 120 || pitchDelta > 60)) {
                if (!plugin.getExemptionManager().isRotateExempt(player)) {
                    double suspicion = Math.min(15.0, (yawDelta + pitchDelta) / 20.0);
                    data.addRotationSuspicion(suspicion);
                    
                    if (debug) {
                        plugin.getACLogger().debug("PacketSnap detected: " + player.getName() + 
                            " | Added suspicion: " + String.format("%.1f", suspicion));
                    }
                }
            }
        } catch (Exception e) {
            if (debug) {
                plugin.getACLogger().debug("Error reading rotation packet: " + e.getMessage());
            }
        }
    }

    private void handleAttackPacket(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        PlayerData data = plugin.getPlayerTracker().getPlayerData(player);
        if (data != null) {
            data.setLastAttackPacketTime(System.currentTimeMillis());
            
            if (debug) {
                plugin.getACLogger().debug("AttackPacket: " + player.getName());
            }
        }
    }

    private void handleUsePacket(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        PlayerData data = plugin.getPlayerTracker().getPlayerData(player);
        if (data != null) {
            data.setLastUsePacketTime(System.currentTimeMillis());
            
            if (debug) {
                plugin.getACLogger().debug("UsePacket: " + player.getName());
            }
        }
    }

    private float normalizeYaw(float yaw) {
        while (yaw > 180f) yaw -= 360f;
        while (yaw < -180f) yaw += 360f;
        return yaw;
    }

    public boolean isAvailable() {
        return available;
    }

    public void reload() {
        debug = plugin.getConfig().getBoolean("protocollib.debug", false);
    }
}
