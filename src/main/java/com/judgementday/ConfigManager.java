package com.judgementday.config;

import com.judgementday.JudgementDay;
import com.judgementday.model.PunishmentType;
import com.judgementday.util.LogUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigManager {

    private final JudgementDay plugin;
    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration punishmentsConfig;

    private File mainConfigFile;
    private File messagesConfigFile;
    private File punishmentsConfigFile;

    // Cache for punishment reasons and durations
    private final Map<PunishmentType, List<String>> punishmentReasons = new EnumMap<>(PunishmentType.class);
    private final Map<PunishmentType, Map<String, Map<Integer, Long>>> punishmentDurations = new EnumMap<>(PunishmentType.class);

    public ConfigManager(JudgementDay plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        createMainConfig();
        createMessagesConfig();
        createPunishmentsConfig();

        loadPunishmentReasonsAndDurations();
    }

    public void reloadConfigs() {
        loadMainConfig();
        loadMessagesConfig();
        loadPunishmentsConfig();

        loadPunishmentReasonsAndDurations();
    }

    private void createMainConfig() {
        mainConfigFile = new File(plugin.getDataFolder(), "config.yml");
        if (!mainConfigFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        loadMainConfig();
    }

    private void createMessagesConfig() {
        messagesConfigFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesConfigFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        loadMessagesConfig();
    }

    private void createPunishmentsConfig() {
        punishmentsConfigFile = new File(plugin.getDataFolder(), "punishments.yml");
        if (!punishmentsConfigFile.exists()) {
            plugin.saveResource("punishments.yml", false);
        }
        loadPunishmentsConfig();
    }

    private void loadMainConfig() {
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);

        // Set defaults if they don't exist
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
            mainConfig.setDefaults(defaultConfig);
            mainConfig.options().copyDefaults(true);
            saveMainConfig();
        }
    }

    private void loadMessagesConfig() {
        messagesConfig = YamlConfiguration.loadConfiguration(messagesConfigFile);

        // Set defaults if they don't exist
        InputStream defaultMessagesStream = plugin.getResource("messages.yml");
        if (defaultMessagesStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultMessagesStream, StandardCharsets.UTF_8));
            messagesConfig.setDefaults(defaultMessages);
            messagesConfig.options().copyDefaults(true);
            saveMessagesConfig();
        }
    }

    private void loadPunishmentsConfig() {
        punishmentsConfig = YamlConfiguration.loadConfiguration(punishmentsConfigFile);

        // Set defaults if they don't exist
        InputStream defaultPunishmentsStream = plugin.getResource("punishments.yml");
        if (defaultPunishmentsStream != null) {
            YamlConfiguration defaultPunishments = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultPunishmentsStream, StandardCharsets.UTF_8));
            punishmentsConfig.setDefaults(defaultPunishments);
            punishmentsConfig.options().copyDefaults(true);
            savePunishmentsConfig();
        }
    }

    public void saveMainConfig() {
        try {
            mainConfig.save(mainConfigFile);
        } catch (IOException e) {
            LogUtil.severe("Could not save config.yml", e);
        }
    }

    public void saveMessagesConfig() {
        try {
            messagesConfig.save(messagesConfigFile);
        } catch (IOException e) {
            LogUtil.severe("Could not save messages.yml", e);
        }
    }

    public void savePunishmentsConfig() {
        try {
            punishmentsConfig.save(punishmentsConfigFile);
        } catch (IOException e) {
            LogUtil.severe("Could not save punishments.yml", e);
        }
    }

    private void loadPunishmentReasonsAndDurations() {
        punishmentReasons.clear();
        punishmentDurations.clear();

        // Initialize all punishment types
        for (PunishmentType type : PunishmentType.values()) {
            punishmentReasons.put(type, new ArrayList<>());
            punishmentDurations.put(type, new HashMap<>());

            String typeName = type.name().toLowerCase();

            // Load reasons
            List<String> reasons = punishmentsConfig.getStringList("reasons." + typeName);
            if (reasons.isEmpty()) {
                // Set default reasons if none exist
                reasons = getDefaultReasons(type);
                punishmentsConfig.set("reasons." + typeName, reasons);
            }
            punishmentReasons.put(type, reasons);

            // Load durations for each reason and level
            Map<String, Map<Integer, Long>> reasonDurations = new HashMap<>();
            for (String reason : reasons) {
                Map<Integer, Long> levelDurations = new HashMap<>();

                for (int level = 1; level <= 3; level++) {
                    String path = "durations." + typeName + "." + reason + "." + level;
                    long minutes = punishmentsConfig.getLong(path, getDefaultDuration(type, level));
                    levelDurations.put(level, minutes * 60 * 1000); // Convert to milliseconds
                }

                reasonDurations.put(reason, levelDurations);
            }
            punishmentDurations.put(type, reasonDurations);
        }

        savePunishmentsConfig();
    }

    private List<String> getDefaultReasons(PunishmentType type) {
        switch (type) {
            case WARN:
                return Arrays.asList("Flood", "Spam", "Minor offense", "Excessive caps");
            case MUTE:
                return Arrays.asList("Harassment", "Advertisement", "Hate speech", "Toxicity");
            case BAN:
                return Arrays.asList("Cheating", "Threats", "Severe toxicity", "Punishment evasion");
            case KICK:
                return Arrays.asList("Temporary measure", "Server rule violation", "Suspicious activity");
            default:
                return new ArrayList<>();
        }
    }

    private long getDefaultDuration(PunishmentType type, int level) {
        switch (type) {
            case WARN:
                return level * 30; // 30, 60, 90 minutes
            case MUTE:
                return level * 120; // 2, 4, 6 hours
            case BAN:
                switch (level) {
                    case 1: return 1440; // 1 day
                    case 2: return 7200; // 5 days
                    case 3: return -1; // Permanent
                    default: return 1440;
                }
            case KICK:
                return 0; // Kicks don't have duration
            default:
                return 60;
        }
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getPunishmentsConfig() {
        return punishmentsConfig;
    }

    public List<String> getPunishmentReasons(PunishmentType type) {
        return punishmentReasons.getOrDefault(type, new ArrayList<>());
    }

    public long getPunishmentDuration(PunishmentType type, String reason, int level) {
        Map<String, Map<Integer, Long>> reasonMap = punishmentDurations.get(type);
        if (reasonMap == null) return 60 * 60 * 1000; // Default 1 hour

        Map<Integer, Long> levelMap = reasonMap.get(reason);
        if (levelMap == null) return 60 * 60 * 1000; // Default 1 hour

        // Cap at level 3
        int actualLevel = Math.min(level, 3);

        return levelMap.getOrDefault(actualLevel, 60 * 60 * 1000); // Default 1 hour
    }

    public String getMessage(String path) {
        return messagesConfig.getString(path, "Missing message: " + path);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return message;
    }

    public String getPrefix() {
        return messagesConfig.getString("prefix", "&c[&4JudgementDay&c] &r");
    }
}