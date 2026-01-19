package com.mythicac;

import com.mythicac.hook.MMOItemsHook;
import com.mythicac.hook.MythicMobsHook;
import com.mythicac.hook.ProtocolLibHook;
import com.mythicac.listener.PlayerListener;
import com.mythicac.manager.CheckManager;
import com.mythicac.manager.ExemptionManager;
import com.mythicac.manager.PlayerTracker;
import com.mythicac.util.ACLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MythicAntiCheat extends JavaPlugin {

    private static MythicAntiCheat instance;
    
    private PlayerTracker playerTracker;
    private ExemptionManager exemptionManager;
    private CheckManager checkManager;
    private MythicMobsHook mythicMobsHook;
    private MMOItemsHook mmoItemsHook;
    private ProtocolLibHook protocolLibHook;
    private ACLogger acLogger;
    
    private boolean debug;
    private boolean logOnlyMode;

    @Override
    public void onEnable() {
        instance = this;
        
        saveDefaultConfig();
        loadConfiguration();
        
        acLogger = new ACLogger(this);
        exemptionManager = new ExemptionManager(this);
        playerTracker = new PlayerTracker(this);
        checkManager = new CheckManager(this);
        
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(playerTracker, this);
        
        if (getConfig().getBoolean("mythicmobs.enabled", true)) {
            mythicMobsHook = new MythicMobsHook(this);
            if (mythicMobsHook.isAvailable()) {
                getServer().getPluginManager().registerEvents(mythicMobsHook, this);
                acLogger.info("MythicMobs integration enabled");
            }
        }
        
        if (getConfig().getBoolean("mmoitems.enabled", true)) {
            mmoItemsHook = new MMOItemsHook(this);
            if (mmoItemsHook.isAvailable()) {
                getServer().getPluginManager().registerEvents(mmoItemsHook, this);
                mmoItemsHook.registerAbilityListener();
                acLogger.info("MMOItems integration enabled");
            }
        }
        
        if (getConfig().getBoolean("protocollib.enabled", true) 
                && getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                protocolLibHook = new ProtocolLibHook(this);
                if (protocolLibHook.isAvailable()) {
                    protocolLibHook.register();
                    acLogger.info("ProtocolLib integration enabled");
                }
            } catch (NoClassDefFoundError e) {
                acLogger.info("ProtocolLib not available - skipping packet hooks");
            }
        }
        
        startCheckTask();
        
        acLogger.info("MythicAntiCheat v" + getDescription().getVersion() + " enabled");
        if (logOnlyMode) {
            acLogger.info("Running in LOG-ONLY mode - no actions will be taken");
        }
    }

    @Override
    public void onDisable() {
        try {
            if (protocolLibHook != null && protocolLibHook.isAvailable()) {
                protocolLibHook.unregister();
            }
        } catch (NoClassDefFoundError ignored) {}
        
        if (acLogger != null) {
            acLogger.info("MythicAntiCheat disabled");
            acLogger.close();
        }
    }

    private void loadConfiguration() {
        debug = getConfig().getBoolean("general.debug", false);
        logOnlyMode = getConfig().getBoolean("general.log_only_mode", true);
    }

    private void startCheckTask() {
        int interval = getConfig().getInt("general.check_interval_ticks", 1);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("mythicac.bypass")) continue;
                checkManager.runChecks(player);
            }
            exemptionManager.tickExemptions();
        }, 1L, interval);
        
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (logOnlyMode) return;
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("mythicac.bypass")) continue;
                
                var data = playerTracker.getPlayerData(player);
                if (data != null && data.isFrozen() && !data.isTesting()) {
                    Location freezeLoc = data.getFreezeLocation();
                    if (freezeLoc != null && freezeLoc.getWorld() != null) {
                        Location setback = freezeLoc.clone();
                        setback.setYaw(player.getLocation().getYaw());
                        setback.setPitch(player.getLocation().getPitch());
                        player.teleport(setback);
                    }
                }
            }
        }, 1L, 1L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("mythicac")) return false;
        if (!sender.hasPermission("mythicac.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§6MythicAntiCheat v" + getDescription().getVersion());
            sender.sendMessage("§7/mythicac reload - Reload config");
            sender.sendMessage("§7/mythicac debug - Toggle debug mode");
            sender.sendMessage("§7/mythicac status - Show status");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                loadConfiguration();
                checkManager.reloadChecks();
                sender.sendMessage("§aConfiguration reloaded.");
            }
            case "debug" -> {
                debug = !debug;
                sender.sendMessage("§aDebug mode: " + (debug ? "§2ON" : "§cOFF"));
            }
            case "status" -> {
                sender.sendMessage("§6MythicAntiCheat Status");
                sender.sendMessage("§7Players tracked: §f" + playerTracker.getTrackedCount());
                sender.sendMessage("§7Log-only mode: " + (logOnlyMode ? "§2ON" : "§cOFF"));
                sender.sendMessage("§7Debug mode: " + (debug ? "§2ON" : "§cOFF"));
                sender.sendMessage("§7MythicMobs: " + (mythicMobsHook != null && mythicMobsHook.isAvailable() ? "§2Connected" : "§cNot found"));
                sender.sendMessage("§7MMOItems: " + (mmoItemsHook != null && mmoItemsHook.isAvailable() ? "§2Connected" : "§cNot found"));
                sender.sendMessage("§7ProtocolLib: " + (protocolLibHook != null && protocolLibHook.isAvailable() ? "§2Connected" : "§cNot found"));
                sender.sendMessage("§7TPS: §f" + String.format("%.2f", Bukkit.getTPS()[0]));
            }
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    public static MythicAntiCheat getInstance() {
        return instance;
    }

    public PlayerTracker getPlayerTracker() {
        return playerTracker;
    }

    public ExemptionManager getExemptionManager() {
        return exemptionManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public MythicMobsHook getMythicMobsHook() {
        return mythicMobsHook;
    }

    public MMOItemsHook getMMOItemsHook() {
        return mmoItemsHook;
    }

    public ProtocolLibHook getProtocolLibHook() {
        return protocolLibHook;
    }

    public ACLogger getACLogger() {
        return acLogger;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isLogOnlyMode() {
        return logOnlyMode;
    }

    public double getLagMultiplier() {
        if (!getConfig().getBoolean("lag_compensation.enabled", true)) {
            return 1.0;
        }
        double tps = Bukkit.getTPS()[0];
        double severe = getConfig().getDouble("lag_compensation.severe_tps", 10.0);
        double heavy = getConfig().getDouble("lag_compensation.heavy_tps", 15.0);
        double moderate = getConfig().getDouble("lag_compensation.moderate_tps", 18.0);
        
        if (tps < severe) {
            return getConfig().getDouble("lag_compensation.severe_multiplier", 2.0);
        } else if (tps < heavy) {
            return getConfig().getDouble("lag_compensation.heavy_multiplier", 1.6);
        } else if (tps < moderate) {
            return getConfig().getDouble("lag_compensation.moderate_multiplier", 1.3);
        }
        return 1.0;
    }
}
