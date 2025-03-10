package com.judgementday.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogUtil {

    private static final String LOG_PREFIX = "[JudgementDay] ";
    private static Logger logger;

    static {
        logger = Bukkit.getLogger();
    }

    /**
     * Log an info message
     *
     * @param message The message to log
     */
    public static void info(String message) {
        logger.info(LOG_PREFIX + message);
    }

    /**
     * Log a warning message
     *
     * @param message The message to log
     */
    public static void warning(String message) {
        logger.warning(LOG_PREFIX + message);
    }

    /**
     * Log a severe message
     *
     * @param message The message to log
     */
    public static void severe(String message) {
        logger.severe(LOG_PREFIX + message);
    }

    /**
     * Log a severe message with an exception
     *
     * @param message The message to log
     * @param throwable The exception to log
     */
    public static void severe(String message, Throwable throwable) {
        logger.log(Level.SEVERE, LOG_PREFIX + message, throwable);
    }

    /**
     * Log a debug message if debug mode is enabled
     *
     * @param message The message to log
     */
    public static void debug(String message) {
        if (Bukkit.getPluginManager().getPlugin("JudgementDay") != null) {
            boolean debug = Bukkit.getPluginManager().getPlugin("JudgementDay").getConfig().getBoolean("debug", false);
            if (debug) {
                logger.info(LOG_PREFIX + "[DEBUG] " + message);
            }
        }
    }

    /**
     * Log a punishment action
     *
     * @param action The action performed
     * @param target The target of the action
     * @param type The type of punishment
     * @param reason The reason for the punishment
     * @param issuer The player who issued the punishment
     * @param id The ID of the punishment
     */
    public static void logPunishment(String action, String target, String type, String reason, String issuer, int id) {
        info(action + ": " + target + " - Type: " + type + ", Reason: " + reason + ", By: " + issuer + ", ID: " + id);
    }

    /**
     * Set the logger to use
     *
     * @param newLogger The logger to use
     */
    public static void setLogger(Logger newLogger) {
        logger = newLogger;
    }

    /**
     * Log a message to the console with color
     *
     * @param level The log level
     * @param message The message to log
     */
    public static void colorLog(Level level, String message) {
        if (level.equals(Level.SEVERE)) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + LOG_PREFIX + message);
        } else if (level.equals(Level.WARNING)) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + LOG_PREFIX + message);
        } else {
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + LOG_PREFIX + message);
        }
    }
}