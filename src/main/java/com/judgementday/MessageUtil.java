package com.judgementday.util;

import org.bukkit.ChatColor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MessageUtil {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    /**
     * Format a message with color codes
     *
     * @param message The message to format
     * @return The formatted message
     */
    public static String colorize(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Format a message with placeholders
     *
     * @param message The message to format
     * @param placeholders Map of placeholders and their values
     * @return The formatted message
     */
    public static String formatMessage(String message, Map<String, String> placeholders) {
        if (message == null) {
            return "";
        }

        String formatted = message;

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return colorize(formatted);
    }

    /**
     * Format a duration in milliseconds to a human-readable string
     *
     * @param durationMillis Duration in milliseconds
     * @return Formatted duration string
     */
    public static String formatDuration(long durationMillis) {
        if (durationMillis < 0) {
            return "Permanent";
        }

        long days = TimeUnit.MILLISECONDS.toDays(durationMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(durationMillis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60;

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append(days == 1 ? " day" : " days");
            if (hours > 0 || minutes > 0) {
                sb.append(", ");
            }
        }

        if (hours > 0) {
            sb.append(hours).append(hours == 1 ? " hour" : " hours");
            if (minutes > 0) {
                sb.append(", ");
            }
        }

        if (minutes > 0 || (days == 0 && hours == 0)) {
            sb.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }

        return sb.toString();
    }

    /**
     * Format a timestamp to a date string
     *
     * @param timestamp Timestamp in milliseconds
     * @return Formatted date string
     */
    public static String formatDate(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    /**
     * Get the time remaining until a timestamp, formatted as a string
     *
     * @param timestamp Timestamp in milliseconds
     * @return Formatted time remaining
     */
    public static String formatTimeRemaining(long timestamp) {
        long remaining = timestamp - System.currentTimeMillis();

        if (remaining <= 0) {
            return "Expired";
        }

        return formatDuration(remaining);
    }

    /**
     * Truncate a string to a maximum length
     *
     * @param str The string to truncate
     * @param maxLength Maximum length
     * @return Truncated string
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }

        if (str.length() <= maxLength) {
            return str;
        }

        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Center a string in a line of a certain width
     *
     * @param message The message to center
     * @param width The width of the line
     * @return The centered message
     */
    public static String centerMessage(String message, int width) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        int messageWidth = ChatColor.stripColor(message).length();
        int padding = (width - messageWidth) / 2;

        if (padding <= 0) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < padding; i++) {
            sb.append(" ");
        }

        sb.append(message);

        return sb.toString();
    }
}