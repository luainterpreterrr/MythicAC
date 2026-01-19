package com.mythicac.hook;

import com.mythicac.MythicAntiCheat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import org.bukkit.event.Event;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MMOItemsHook implements Listener {

    private final MythicAntiCheat plugin;
    private boolean available;
    private boolean debug;
    
    private int damageExemptTicks;
    private int knockbackExemptTicks;
    private int defaultAbilityTicks;
    private Map<String, Integer> abilityDurations = new HashMap<>();
    
    private Class<?> abilityUseEventClass;
    private Method getAbilityMethod;
    private Method getPlayerMethod;
    private Method getAbilityIdMethod;

    public MMOItemsHook(MythicAntiCheat plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("MMOItems") != null;
        
        if (available) {
            loadConfig();
            setupReflection();
            plugin.getACLogger().info("MMOItems detected - exemption hooks enabled");
        }
    }
    
    private void setupReflection() {
        String[] eventClassNames = {
            "net.Indyuce.mmoitems.api.event.AbilityUseEvent",
            "net.Indyuce.mmoitems.api.event.item.AbilityUseEvent", 
            "net.Indyuce.mmoitems.ability.AbilityUseEvent",
            "io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent",
            "io.lumine.mythic.lib.skill.trigger.TriggerMetadata"
        };
        
        for (String className : eventClassNames) {
            try {
                abilityUseEventClass = Class.forName(className);
                plugin.getLogger().info("[MMOItems] Found event class: " + className);
                
                try {
                    getPlayerMethod = abilityUseEventClass.getMethod("getPlayer");
                } catch (NoSuchMethodException e) {
                    try {
                        getPlayerMethod = abilityUseEventClass.getMethod("getCaster");
                    } catch (NoSuchMethodException e2) {
                        plugin.getLogger().warning("[MMOItems] No getPlayer/getCaster method in " + className);
                        continue;
                    }
                }
                
                try {
                    getAbilityMethod = abilityUseEventClass.getMethod("getAbility");
                } catch (NoSuchMethodException e) {
                    try {
                        getAbilityMethod = abilityUseEventClass.getMethod("getSkill");
                    } catch (NoSuchMethodException e2) {
                        try {
                            getAbilityMethod = abilityUseEventClass.getMethod("getCast");
                        } catch (NoSuchMethodException e3) {
                            plugin.getLogger().warning("[MMOItems] No getAbility/getSkill method in " + className);
                            continue;
                        }
                    }
                }
                
                plugin.getLogger().info("[MMOItems] Ability event reflection setup successful using " + className);
                return;
                
            } catch (ClassNotFoundException e) {
            }
        }
        
        plugin.getLogger().warning("[MMOItems] Could not find any ability event class - using velocity detection instead");
        abilityUseEventClass = null;
    }

    private void loadConfig() {
        debug = plugin.getConfig().getBoolean("mmoitems.debug", false);
        damageExemptTicks = plugin.getConfig().getInt("mmoitems.damage_exempt_ticks", 20);
        knockbackExemptTicks = plugin.getConfig().getInt("mmoitems.knockback_exempt_ticks", 30);
        defaultAbilityTicks = plugin.getConfig().getInt("mmoitems.default_ability_ticks", 10);
        
        abilityDurations.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mmoitems.ability_durations");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                abilityDurations.put(key.toUpperCase(), section.getInt(key));
            }
        }
        
        if (debug) {
            plugin.getACLogger().debug("Loaded " + abilityDurations.size() + " ability duration configs");
        }
    }
    
    public int getAbilityDuration(String abilityId) {
        if (abilityId == null) return defaultAbilityTicks;
        String key = abilityId.toUpperCase().replace(" ", "_");
        return abilityDurations.getOrDefault(key, defaultAbilityTicks);
    }

    public boolean isAvailable() {
        return available;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!available) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        Entity damager = event.getDamager();
        
        if (isMMOItemsEntity(damager)) {
            int moveTicks = knockbackExemptTicks;
            int rotateTicks = damageExemptTicks;
            
            plugin.getExemptionManager().exemptMove(player, moveTicks, "MMOItems-Knockback");
            plugin.getExemptionManager().exemptRotate(player, rotateTicks, "MMOItems-Damage");
            
            if (debug) {
                plugin.getACLogger().debug("MMOItems exemption applied to " + player.getName() + 
                    " - Move: " + moveTicks + " ticks, Rotate: " + rotateTicks + " ticks");
            }
        }
    }

    private boolean isMMOItemsEntity(Entity entity) {
        if (entity == null) return false;
        
        try {
            if (entity instanceof LivingEntity living) {
                if (entity.hasMetadata("MMOItemsEntity")) {
                    return true;
                }
                
                String customName = living.getCustomName();
                if (customName != null && !customName.isEmpty()) {
                    return true;
                }
            }
            
            if (entity.hasMetadata("MMOItems")) {
                return true;
            }
            
        } catch (Exception e) {
            if (debug) {
                plugin.getACLogger().debug("MMOItems entity check failed: " + e.getMessage());
            }
        }
        
        return false;
    }

    public void reload() {
        if (available) {
            loadConfig();
        }
    }
    
    public void registerAbilityListener() {
        if (!available || abilityUseEventClass == null) return;
        
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) abilityUseEventClass;
            plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                this,
                EventPriority.MONITOR,
                (listener, event) -> {
                    try {
                        handleAbilityEvent(event);
                    } catch (Exception e) {
                        if (debug) {
                            plugin.getACLogger().debug("Ability event handling failed: " + e.getMessage());
                        }
                    }
                },
                plugin,
                true
            );
            plugin.getACLogger().info("MMOItems ability listener registered");
        } catch (Exception e) {
            plugin.getACLogger().debug("Failed to register ability listener: " + e.getMessage());
        }
    }
    
    private void handleAbilityEvent(Object event) throws Exception {
        if (getPlayerMethod == null || getAbilityMethod == null) return;
        
        Player player = (Player) getPlayerMethod.invoke(event);
        Object ability = getAbilityMethod.invoke(event);
        
        String abilityId = "UNKNOWN";
        if (ability != null && getAbilityIdMethod != null) {
            abilityId = (String) getAbilityIdMethod.invoke(ability);
        }
        
        int exemptTicks = getAbilityDuration(abilityId);
        
        plugin.getExemptionManager().exemptMove(player, exemptTicks, "MMOItems-Ability-" + abilityId);
        
        if (debug) {
            plugin.getACLogger().debug("MMOItems ability exemption: " + player.getName() + 
                " used " + abilityId + " - exempt " + exemptTicks + " ticks");
        }
    }
}
