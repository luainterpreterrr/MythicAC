package com.mythicac.util;

import com.mythicac.MythicAntiCheat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ACLogger {

    private final MythicAntiCheat plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final SimpleDateFormat fileNameFormat = new SimpleDateFormat("yyyy-MM-dd");
    private PrintWriter fileWriter;
    private String currentLogDate;
    private final File logFolder;

    public ACLogger(MythicAntiCheat plugin) {
        this.plugin = plugin;
        this.logFolder = new File(plugin.getDataFolder(), "logs");
        
        if (plugin.getConfig().getBoolean("logging.file_logging", true)) {
            initFileLogging();
        }
    }

    private void initFileLogging() {
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
        
        rotateOldLogs();
        openLogFile();
    }

    private void openLogFile() {
        try {
            currentLogDate = fileNameFormat.format(new Date());
            File logFile = new File(logFolder, "anticheat-" + currentLogDate + ".log");
            fileWriter = new PrintWriter(new FileWriter(logFile, true), true);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to open log file: " + e.getMessage());
        }
    }

    private void rotateOldLogs() {
        File[] logFiles = logFolder.listFiles((dir, name) -> name.endsWith(".log"));
        if (logFiles == null) return;
        
        int maxFiles = plugin.getConfig().getInt("logging.max_log_files", 10);
        if (logFiles.length >= maxFiles) {
            java.util.Arrays.sort(logFiles, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i < logFiles.length - maxFiles + 1; i++) {
                logFiles[i].delete();
            }
        }
    }

    private void checkDateRollover() {
        String today = fileNameFormat.format(new Date());
        if (!today.equals(currentLogDate)) {
            close();
            openLogFile();
        }
    }

    private String timestamp() {
        if (plugin.getConfig().getBoolean("logging.timestamps", true)) {
            return "[" + dateFormat.format(new Date()) + "] ";
        }
        return "";
    }

    public void info(String message) {
        String formatted = timestamp() + "[INFO] " + message;
        plugin.getLogger().info(message);
        writeToFile(formatted);
    }

    public void debug(String message) {
        if (!plugin.isDebug()) return;
        String formatted = timestamp() + "[DEBUG] " + message;
        plugin.getLogger().info("[DEBUG] " + message);
        writeToFile(formatted);
    }

    public void warn(String message) {
        String formatted = timestamp() + "[WARN] " + message;
        plugin.getLogger().warning(message);
        writeToFile(formatted);
    }

    public void alert(String message) {
        String formatted = timestamp() + "[ALERT] " + message;
        plugin.getLogger().warning("[ALERT] " + message);
        writeToFile(formatted);
    }

    public void movement(String playerName, double suspicion, String details) {
        double minLog = plugin.getConfig().getDouble("logging.min_log_suspicion", 15.0);
        if (suspicion < minLog) return;
        
        String formatted = timestamp() + "[MOVEMENT] " + playerName + " | Suspicion: " + 
            String.format("%.1f", suspicion) + " | " + details;
        
        if (plugin.getConfig().getBoolean("checks.movement.debug", false)) {
            plugin.getLogger().info(formatted);
        }
        writeToFile(formatted);
    }

    public void rotation(String playerName, double suspicion, String details) {
        double minLog = plugin.getConfig().getDouble("logging.min_log_suspicion", 15.0);
        if (suspicion < minLog) return;
        
        String formatted = timestamp() + "[ROTATION] " + playerName + " | Suspicion: " + 
            String.format("%.1f", suspicion) + " | " + details;
        
        if (plugin.getConfig().getBoolean("checks.rotation.debug", false)) {
            plugin.getLogger().info(formatted);
        }
        writeToFile(formatted);
    }

    public void exemption(String playerName, String type, int ticks, String source) {
        if (!plugin.getConfig().getBoolean("logging.log_exemptions", false)) return;
        
        String formatted = timestamp() + "[EXEMPT] " + playerName + " | Type: " + type + 
            " | Duration: " + ticks + " ticks | Source: " + source;
        writeToFile(formatted);
    }

    private void writeToFile(String message) {
        if (fileWriter == null || !plugin.getConfig().getBoolean("logging.file_logging", true)) {
            return;
        }
        
        checkDateRollover();
        fileWriter.println(message);
        fileWriter.flush();
    }

    public void close() {
        if (fileWriter != null) {
            fileWriter.close();
            fileWriter = null;
        }
    }

    public File getLogFolder() {
        return logFolder;
    }
}
