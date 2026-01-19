package com.mythicac.hook;

import com.mythicac.MythicAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;

public class MythicMobsHook implements Listener {

    private final MythicAntiCheat plugin;
    private boolean available;
    private boolean debug;
    
    private List<String> rotationExemptSkills;
    private List<String> movementExemptSkills;
    private List<String> teleportExemptSkills;
    private Map<String, Object> skillDurations;

    public MythicMobsHook(MythicAntiCheat plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        
        if (available) {
            loadConfig();
            registerMythicMobsEvents();
        }
    }

    private void loadConfig() {
        debug = plugin.getConfig().getBoolean("mythicmobs.debug", false);
        rotationExemptSkills = plugin.getConfig().getStringList("mythicmobs.rotation_exempt_skills");
        movementExemptSkills = plugin.getConfig().getStringList("mythicmobs.movement_exempt_skills");
        teleportExemptSkills = plugin.getConfig().getStringList("mythicmobs.teleport_exempt_skills");
        
        if (plugin.getConfig().isConfigurationSection("mythicmobs.skill_durations")) {
            skillDurations = plugin.getConfig().getConfigurationSection("mythicmobs.skill_durations").getValues(false);
        }
    }

    private void registerMythicMobsEvents() {
        try {
            Class.forName("io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent");
            plugin.getACLogger().debug("MythicMobs 5.x detected");
            registerMythicMobs5Listener();
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobSkillEvent");
                plugin.getACLogger().debug("MythicMobs 4.x detected");
            } catch (ClassNotFoundException ex) {
                plugin.getACLogger().warn("MythicMobs found but could not hook events - using fallback detection");
            }
        }
    }

    private void registerMythicMobs5Listener() {
        try {
            Bukkit.getPluginManager().registerEvents(new MythicMobs5Listener(), plugin);
            plugin.getACLogger().info("MythicMobs 5.x event listener registered");
        } catch (Exception e) {
            plugin.getACLogger().warn("Failed to register MythicMobs listener: " + e.getMessage());
        }
    }

    private class MythicMobs5Listener implements Listener {
        
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMythicSkill(io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent event) {
        }
    }

    public void handleSkillOnPlayer(Player player, String skillName) {
        if (!available) return;
        
        String skillLower = skillName.toLowerCase();
        int defaultDuration = plugin.getConfig().getInt("exemptions.rotation_ticks", 30);
        
        int duration = defaultDuration;
        if (skillDurations != null && skillDurations.containsKey(skillLower)) {
            duration = ((Number) skillDurations.get(skillLower)).intValue();
        }
        
        if (matchesAny(skillLower, rotationExemptSkills)) {
            plugin.getExemptionManager().exemptRotate(player, duration, "mythicmobs:" + skillName);
            logSkillExemption(player, skillName, "rotation", duration);
        }
        
        if (matchesAny(skillLower, movementExemptSkills)) {
            int moveDuration = plugin.getConfig().getInt("exemptions.velocity_ticks", 60);
            if (skillDurations != null && skillDurations.containsKey(skillLower)) {
                moveDuration = ((Number) skillDurations.get(skillLower)).intValue();
            }
            plugin.getExemptionManager().exemptMove(player, moveDuration, "mythicmobs:" + skillName);
            logSkillExemption(player, skillName, "movement", moveDuration);
        }
        
        if (matchesAny(skillLower, teleportExemptSkills)) {
            int tpDuration = plugin.getConfig().getInt("exemptions.teleport_ticks", 40);
            if (skillDurations != null && skillDurations.containsKey(skillLower)) {
                tpDuration = ((Number) skillDurations.get(skillLower)).intValue();
            }
            plugin.getExemptionManager().exemptTeleport(player, tpDuration, "mythicmobs:" + skillName);
            logSkillExemption(player, skillName, "teleport", tpDuration);
        }
    }

    private boolean matchesAny(String skillName, List<String> patterns) {
        if (patterns == null) return false;
        for (String pattern : patterns) {
            if (skillName.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void logSkillExemption(Player player, String skill, String type, int duration) {
        if (debug) {
            plugin.getACLogger().debug("MythicMobs exemption: " + player.getName() + 
                " | Skill: " + skill + " | Type: " + type + " | Duration: " + duration + " ticks");
        }
        plugin.getACLogger().exemption(player.getName(), type, duration, "mythicmobs:" + skill);
    }

    public boolean isLikelyMythicInfluence(Player player) {
        if (!available) return false;
        
        for (Entity entity : player.getNearbyEntities(20, 10, 20)) {
            if (isMythicMob(entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMythicMob(Entity entity) {
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object instance = mythicBukkitClass.getMethod("inst").invoke(null);
            Object mobManager = mythicBukkitClass.getMethod("getMobManager").invoke(instance);
            Object activeMob = mobManager.getClass().getMethod("getActiveMob", java.util.UUID.class)
                .invoke(mobManager, entity.getUniqueId());
            return activeMob != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void reload() {
        loadConfig();
    }
}
