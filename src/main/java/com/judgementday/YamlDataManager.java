package com.judgementday.data;

import com.judgementday.JudgementDay;
import com.judgementday.model.Appeal;
import com.judgementday.model.Punishment;
import com.judgementday.model.PunishmentType;
import com.judgementday.model.Report;
import com.judgementday.util.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class YamlDataManager implements DataManager {

    private final JudgementDay plugin;
    private final File playerDataFile;
    private final File punishmentDataFile;
    private final File reportDataFile;
    private final File appealDataFile;

    private FileConfiguration playerData;
    private FileConfiguration punishmentData;
    private FileConfiguration reportData;
    private FileConfiguration appealData;

    private final AtomicInteger punishmentIdCounter = new AtomicInteger(1);
    private final AtomicInteger reportIdCounter = new AtomicInteger(1);
    private final AtomicInteger appealIdCounter = new AtomicInteger(1);

    // Cache active punishments for better performance
    private final Map<UUID, List<Punishment>> activePlayerPunishments = new ConcurrentHashMap<>();

    public YamlDataManager(JudgementDay plugin) {
        this.plugin = plugin;
        this.playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        this.punishmentDataFile = new File(plugin.getDataFolder(), "punishments.yml");
        this.reportDataFile = new File(plugin.getDataFolder(), "reports.yml");
        this.appealDataFile = new File(plugin.getDataFolder(), "appeals.yml");
    }

    @Override
    public void initialize() {
        // Create files if they don't exist
        createFiles();

        // Load data
        loadData();

        // Initialize counters
        initializeCounters();

        // Load active punishments into cache
        loadActivePunishmentsCache();
    }

    private void createFiles() {
        try {
            if (!playerDataFile.exists()) {
                playerDataFile.getParentFile().mkdirs();
                playerDataFile.createNewFile();
            }
            if (!punishmentDataFile.exists()) {
                punishmentDataFile.getParentFile().mkdirs();
                punishmentDataFile.createNewFile();
            }
            if (!reportDataFile.exists()) {
                reportDataFile.getParentFile().mkdirs();
                reportDataFile.createNewFile();
            }
            if (!appealDataFile.exists()) {
                appealDataFile.getParentFile().mkdirs();
                appealDataFile.createNewFile();
            }
        } catch (IOException e) {
            LogUtil.severe("Failed to create data files", e);
        }
    }

    private void loadData() {
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        punishmentData = YamlConfiguration.loadConfiguration(punishmentDataFile);
        reportData = YamlConfiguration.loadConfiguration(reportDataFile);
        appealData = YamlConfiguration.loadConfiguration(appealDataFile);
    }

    private void initializeCounters() {
        // Initialize punishment ID counter
        if (punishmentData.contains("punishments")) {
            ConfigurationSection section = punishmentData.getConfigurationSection("punishments");
            if (section != null) {
                int maxId = 0;
                for (String key : section.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        if (id > maxId) {
                            maxId = id;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                punishmentIdCounter.set(maxId + 1);
            }
        }

        // Initialize report ID counter
        if (reportData.contains("reports")) {
            ConfigurationSection section = reportData.getConfigurationSection("reports");
            if (section != null) {
                int maxId = 0;
                for (String key : section.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        if (id > maxId) {
                            maxId = id;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                reportIdCounter.set(maxId + 1);
            }
        }

        // Initialize appeal ID counter
        if (appealData.contains("appeals")) {
            ConfigurationSection section = appealData.getConfigurationSection("appeals");
            if (section != null) {
                int maxId = 0;
                for (String key : section.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        if (id > maxId) {
                            maxId = id;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                appealIdCounter.set(maxId + 1);
            }
        }
    }

    private void loadActivePunishmentsCache() {
        activePlayerPunishments.clear();

        if (punishmentData.contains("punishments")) {
            ConfigurationSection punishmentsSection = punishmentData.getConfigurationSection("punishments");
            if (punishmentsSection != null) {
                for (String key : punishmentsSection.getKeys(false)) {
                    ConfigurationSection punishmentSection = punishmentsSection.getConfigurationSection(key);
                    if (punishmentSection != null) {
                        boolean active = punishmentSection.getBoolean("active");
                        if (active) {
                            long expiry = punishmentSection.getLong("expiry");
                            // Skip if expired
                            if (expiry != -1 && expiry < System.currentTimeMillis()) {
                                continue;
                            }

                            Map<String, Object> map = new HashMap<>();
                            for (String subKey : punishmentSection.getKeys(false)) {
                                map.put(subKey, punishmentSection.get(subKey));
                            }
                            map.put("id", Integer.parseInt(key));

                            Punishment punishment = new Punishment(map);
                            UUID targetUuid = punishment.getTargetUuid();

                            activePlayerPunishments.computeIfAbsent(targetUuid, k -> new ArrayList<>())
                                    .add(punishment);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void saveAll() {
        try {
            playerData.save(playerDataFile);
            punishmentData.save(punishmentDataFile);
            reportData.save(reportDataFile);
            appealData.save(appealDataFile);
        } catch (IOException e) {
            LogUtil.severe("Failed to save data files", e);
        }
    }

    @Override
    public int getNextPunishmentId() {
        return punishmentIdCounter.getAndIncrement();
    }

    @Override
    public int getNextReportId() {
        return reportIdCounter.getAndIncrement();
    }

    @Override
    public int getNextAppealId() {
        return appealIdCounter.getAndIncrement();
    }

    @Override
    public CompletableFuture<Integer> getPunishmentLevel(UUID uuid, PunishmentType type, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "players." + uuid.toString() + ".history." + type.name().toLowerCase() + "." + reason;

            if (!playerData.contains(path)) {
                return 1;
            }

            List<?> history = playerData.getList(path);
            return history == null ? 1 : history.size() + 1;
        });
    }

    @Override
    public CompletableFuture<Void> addPunishment(Punishment punishment) {
        return CompletableFuture.runAsync(() -> {
            int id = punishment.getId();
            UUID targetUuid = punishment.getTargetUuid();

            // Add to global punishments
            punishmentData.set("punishments." + id, punishment.serialize());

            // Add to player's history
            String historyPath = "players." + targetUuid.toString() + ".history." +
                    punishment.getType().name().toLowerCase() + "." + punishment.getReason();

            List<Map<String, Object>> history = new ArrayList<>();
            if (playerData.contains(historyPath)) {
                history = (List<Map<String, Object>>) playerData.getList(historyPath, new ArrayList<>());
            }

            history.add(punishment.serialize());
            playerData.set(historyPath, history);

            // Add to active punishments if applicable
            if (punishment.isActive() && !punishment.isExpired()) {
                // Add to cache
                activePlayerPunishments.computeIfAbsent(targetUuid, k -> new ArrayList<>())
                        .add(punishment);
            }

            // Save data
            saveAll();
        });
    }

    @Override
    public CompletableFuture<Boolean> revokePunishment(int id, UUID revokerUuid, String revokerName) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "punishments." + id;

            if (!punishmentData.contains(path)) {
                return false;
            }

            ConfigurationSection punishmentSection = punishmentData.getConfigurationSection(path);
            if (punishmentSection == null || !punishmentSection.getBoolean("active")) {
                return false;
            }

            // Get punishment data
            Map<String, Object> data = new HashMap<>();
            for (String key : punishmentSection.getKeys(false)) {
                data.put(key, punishmentSection.get(key));
            }
            data.put("id", id);

            Punishment punishment = new Punishment(data);
            punishment.revoke(revokerUuid, revokerName);

            // Update punishment data
            punishmentData.set(path, punishment.serialize());

            // Remove from active punishments cache
            UUID targetUuid = punishment.getTargetUuid();
            if (activePlayerPunishments.containsKey(targetUuid)) {
                activePlayerPunishments.get(targetUuid).removeIf(p -> p.getId() == id);
            }

            // Save data
            saveAll();

            return true;
        });
    }

    @Override
    public CompletableFuture<Punishment> getPunishment(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "punishments." + id;

            if (!punishmentData.contains(path)) {
                return null;
            }

            ConfigurationSection section = punishmentData.getConfigurationSection(path);
            if (section == null) {
                return null;
            }

            Map<String, Object> data = new HashMap<>();
            for (String key : section.getKeys(false)) {
                data.put(key, section.get(key));
            }
            data.put("id", id);

            return new Punishment(data);
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getPlayerPunishments(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> punishments = new ArrayList<>();

            // Check all punishment types
            for (PunishmentType type : PunishmentType.values()) {
                String typeName = type.name().toLowerCase();
                String historyPath = "players." + uuid.toString() + ".history." + typeName;

                if (playerData.contains(historyPath)) {
                    ConfigurationSection typeSection = playerData.getConfigurationSection(historyPath);
                    if (typeSection != null) {
                        for (String reason : typeSection.getKeys(false)) {
                            List<Map<?, ?>> reasonPunishments = playerData.getMapList(historyPath + "." + reason);

                            for (Map<?, ?> punishmentMap : reasonPunishments) {
                                Map<String, Object> data = new HashMap<>();
                                for (Map.Entry<?, ?> entry : punishmentMap.entrySet()) {
                                    data.put(entry.getKey().toString(), entry.getValue());
                                }

                                punishments.add(new Punishment(data));
                            }
                        }
                    }
                }
            }

            // Sort by time issued (newest first)
            punishments.sort((p1, p2) -> Long.compare(p2.getTimeIssued(), p1.getTimeIssued()));

            return punishments;
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getActivePlayerPunishments(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // Use cache for better performance
            if (activePlayerPunishments.containsKey(uuid)) {
                return activePlayerPunishments.get(uuid).stream()
                        .filter(p -> !p.isExpired())
                        .collect(Collectors.toList());
            }

            return new ArrayList<>();
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getActivePlayerPunishmentsByType(UUID uuid, PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> {
            // Use cache for better performance
            if (activePlayerPunishments.containsKey(uuid)) {
                return activePlayerPunishments.get(uuid).stream()
                        .filter(p -> p.getType() == type && !p.isExpired())
                        .collect(Collectors.toList());
            }

            return new ArrayList<>();
        });
    }

    @Override
    public CompletableFuture<Boolean> isPlayerBanned(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // Use cache for better performance
            if (activePlayerPunishments.containsKey(uuid)) {
                return activePlayerPunishments.get(uuid).stream()
                        .anyMatch(p -> p.getType() == PunishmentType.BAN && !p.isExpired());
            }

            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> isPlayerMuted(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // Use cache for better performance
            if (activePlayerPunishments.containsKey(uuid)) {
                return activePlayerPunishments.get(uuid).stream()
                        .anyMatch(p -> p.getType() == PunishmentType.MUTE && !p.isExpired());
            }

            return false;
        });
    }

    @Override
    public CompletableFuture<List<Report>> getUnprocessedReports() {
        return CompletableFuture.supplyAsync(() -> {
            List<Report> reports = new ArrayList<>();

            if (reportData.contains("reports")) {
                ConfigurationSection reportsSection = reportData.getConfigurationSection("reports");
                if (reportsSection != null) {
                    for (String key : reportsSection.getKeys(false)) {
                        ConfigurationSection reportSection = reportsSection.getConfigurationSection(key);
                        if (reportSection != null && !reportSection.getBoolean("processed", false)) {
                            try {
                                int id = Integer.parseInt(key);

                                Map<String, Object> data = new HashMap<>();
                                for (String subKey : reportSection.getKeys(false)) {
                                    data.put(subKey, reportSection.get(subKey));
                                }
                                data.put("id", id);

                                reports.add(new Report(data));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }

            // Sort by creation time (newest first)
            reports.sort((r1, r2) -> Long.compare(r2.getTimeCreated(), r1.getTimeCreated()));

            return reports;
        });
    }

    @Override
    public CompletableFuture<List<Report>> getPlayerReports(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Report> reports = new ArrayList<>();

            if (reportData.contains("reports")) {
                ConfigurationSection reportsSection = reportData.getConfigurationSection("reports");
                if (reportsSection != null) {
                    for (String key : reportsSection.getKeys(false)) {
                        ConfigurationSection reportSection = reportsSection.getConfigurationSection(key);
                        if (reportSection != null) {
                            String reportedUuid = reportSection.getString("reportedUuid");
                            if (uuid.toString().equals(reportedUuid)) {
                                try {
                                    int id = Integer.parseInt(key);

                                    Map<String, Object> data = new HashMap<>();
                                    for (String subKey : reportSection.getKeys(false)) {
                                        data.put(subKey, reportSection.get(subKey));
                                    }
                                    data.put("id", id);

                                    reports.add(new Report(data));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }

            // Sort by creation time (newest first)
            reports.sort((r1, r2) -> Long.compare(r2.getTimeCreated(), r1.getTimeCreated()));

            return reports;
        });
    }

    @Override
    public CompletableFuture<Void> addReport(Report report) {
        return CompletableFuture.runAsync(() -> {
            int id = report.getId();

            // Add to reports
            reportData.set("reports." + id, report.serialize());

            // Save data
            try {
                reportData.save(reportDataFile);
            } catch (IOException e) {
                LogUtil.severe("Failed to save report data", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> processReport(int id, UUID processorUuid, String processorName, int punishmentId) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "reports." + id;

            if (!reportData.contains(path)) {
                return false;
            }

            ConfigurationSection reportSection = reportData.getConfigurationSection(path);
            if (reportSection == null || reportSection.getBoolean("processed", false)) {
                return false;
            }

            // Get report data
            Map<String, Object> data = new HashMap<>();
            for (String key : reportSection.getKeys(false)) {
                data.put(key, reportSection.get(key));
            }
            data.put("id", id);

            Report report = new Report(data);
            report.markProcessed(processorUuid, processorName, punishmentId);

            // Update report data
            reportData.set(path, report.serialize());

            // Save data
            try {
                reportData.save(reportDataFile);
            } catch (IOException e) {
                LogUtil.severe("Failed to save report data", e);
                return false;
            }

            return true;
        });
    }

    @Override
    public CompletableFuture<Report> getReport(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "reports." + id;

            if (!reportData.contains(path)) {
                return null;
            }

            ConfigurationSection section = reportData.getConfigurationSection(path);
            if (section == null) {
                return null;
            }

            Map<String, Object> data = new HashMap<>();
            for (String key : section.getKeys(false)) {
                data.put(key, section.get(key));
            }
            data.put("id", id);

            return new Report(data);
        });
    }

    @Override
    public CompletableFuture<Void> addAppeal(Appeal appeal) {
        return CompletableFuture.runAsync(() -> {
            int id = appeal.getId();

            // Add to appeals
            appealData.set("appeals." + id, appeal.serialize());

            // Save data
            try {
                appealData.save(appealDataFile);
            } catch (IOException e) {
                LogUtil.severe("Failed to save appeal data", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Appeal>> getPendingAppeals() {
        return CompletableFuture.supplyAsync(() -> {
            List<Appeal> appeals = new ArrayList<>();

            if (appealData.contains("appeals")) {
                ConfigurationSection appealsSection = appealData.getConfigurationSection("appeals");
                if (appealsSection != null) {
                    for (String key : appealsSection.getKeys(false)) {
                        ConfigurationSection appealSection = appealsSection.getConfigurationSection(key);
                        if (appealSection != null && appealSection.getString("status", "").equals(Appeal.AppealStatus.PENDING.name())) {
                            try {
                                int id = Integer.parseInt(key);

                                Map<String, Object> data = new HashMap<>();
                                for (String subKey : appealSection.getKeys(false)) {
                                    data.put(subKey, appealSection.get(subKey));
                                }
                                data.put("id", id);

                                appeals.add(new Appeal(data));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }

            // Sort by creation time (newest first)
            appeals.sort((a1, a2) -> Long.compare(a2.getTimeCreated(), a1.getTimeCreated()));

            return appeals;
        });
    }

    @Override
    public CompletableFuture<List<Appeal>> getPlayerAppeals(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Appeal> appeals = new ArrayList<>();

            if (appealData.contains("appeals")) {
                ConfigurationSection appealsSection = appealData.getConfigurationSection("appeals");
                if (appealsSection != null) {
                    for (String key : appealsSection.getKeys(false)) {
                        ConfigurationSection appealSection = appealsSection.getConfigurationSection(key);
                        if (appealSection != null) {
                            String playerUuid = appealSection.getString("playerUuid");
                            if (uuid.toString().equals(playerUuid)) {
                                try {
                                    int id = Integer.parseInt(key);

                                    Map<String, Object> data = new HashMap<>();
                                    for (String subKey : appealSection.getKeys(false)) {
                                        data.put(subKey, appealSection.get(subKey));
                                    }
                                    data.put("id", id);

                                    appeals.add(new Appeal(data));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }

            // Sort by creation time (newest first)
            appeals.sort((a1, a2) -> Long.compare(a2.getTimeCreated(), a1.getTimeCreated()));

            return appeals;
        });
    }

    @Override
    public CompletableFuture<Appeal> getAppeal(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "appeals." + id;

            if (!appealData.contains(path)) {
                return null;
            }

            ConfigurationSection section = appealData.getConfigurationSection(path);
            if (section == null) {
                return null;
            }

            Map<String, Object> data = new HashMap<>();
            for (String key : section.getKeys(false)) {
                data.put(key, section.get(key));
            }
            data.put("id", id);

            return new Appeal(data);
        });
    }

    @Override
    public CompletableFuture<Boolean> approveAppeal(int id, UUID reviewerUuid, String reviewerName, String comment) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "appeals." + id;

            if (!appealData.contains(path)) {
                return false;
            }

            ConfigurationSection appealSection = appealData.getConfigurationSection(path);
            if (appealSection == null || !appealSection.getString("status", "").equals(Appeal.AppealStatus.PENDING.name())) {
                return false;
            }

            // Get appeal data
            Map<String, Object> data = new HashMap<>();
            for (String key : appealSection.getKeys(false)) {
                data.put(key, appealSection.get(key));
            }
            data.put("id", id);

            Appeal appeal = new Appeal(data);
            appeal.approve(reviewerUuid, reviewerName, comment);

            // Update appeal data
            appealData.set(path, appeal.serialize());

            // Save data
            try {
                appealData.save(appealDataFile);
            } catch (IOException e) {
                LogUtil.severe("Failed to save appeal data", e);
                return false;
            }

            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> denyAppeal(int id, UUID reviewerUuid, String reviewerName, String comment) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "appeals." + id;

            if (!appealData.contains(path)) {
                return false;
            }

            ConfigurationSection appealSection = appealData.getConfigurationSection(path);
            if (appealSection == null || !appealSection.getString("status", "").equals(Appeal.AppealStatus.PENDING.name())) {
                return false;
            }

            // Get appeal data
            Map<String, Object> data = new HashMap<>();
            for (String key : appealSection.getKeys(false)) {
                data.put(key, appealSection.get(key));
            }
            data.put("id", id);

            Appeal appeal = new Appeal(data);
            appeal.deny(reviewerUuid, reviewerName, comment);

            // Update appeal data
            appealData.set(path, appeal.serialize());

            // Save data
            try {
                appealData.save(appealDataFile);
            } catch (IOException e) {
                LogUtil.severe("Failed to save appeal data", e);
                return false;
            }

            return true;
        });
    }

    @Override
    public void cleanupExpiredPunishments() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean changes = false;

            if (punishmentData.contains("punishments")) {
                ConfigurationSection punishmentsSection = punishmentData.getConfigurationSection("punishments");
                if (punishmentsSection != null) {
                    long currentTime = System.currentTimeMillis();

                    for (String key : punishmentsSection.getKeys(false)) {
                        ConfigurationSection punishmentSection = punishmentsSection.getConfigurationSection(key);
                        if (punishmentSection != null) {
                            boolean active = punishmentSection.getBoolean("active");
                            long expiry = punishmentSection.getLong("expiry");

                            if (active && expiry != -1 && expiry < currentTime) {
                                punishmentSection.set("active", false);
                                punishmentSection.set("autoExpired", true);
                                changes = true;

                                LogUtil.info("Auto-expired punishment #" + key);

                                // Remove from cache if present
                                String targetUuidStr = punishmentSection.getString("targetUuid");
                                if (targetUuidStr != null) {
                                    try {
                                        UUID targetUuid = UUID.fromString(targetUuidStr);
                                        int id = Integer.parseInt(key);

                                        if (activePlayerPunishments.containsKey(targetUuid)) {
                                            activePlayerPunishments.get(targetUuid).removeIf(p -> p.getId() == id);
                                        }
                                    } catch (IllegalArgumentException ignored) {}
                                }
                            }
                        }
                    }
                }
            }

            if (changes) {
                try {
                    punishmentData.save(punishmentDataFile);
                } catch (IOException e) {
                    LogUtil.severe("Failed to save punishment data during cleanup", e);
                }
            }
        });
    }

    @Override
    public void cleanupOldReports() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int daysToKeep = plugin.getConfigManager().getMainConfig().getInt("storage.reports-days-to-keep", 30);
            if (daysToKeep <= 0) return; // Keep forever

            boolean changes = false;

            if (reportData.contains("reports")) {
                ConfigurationSection reportsSection = reportData.getConfigurationSection("reports");
                if (reportsSection != null) {
                    long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L);

                    for (String key : reportsSection.getKeys(false)) {
                        ConfigurationSection reportSection = reportsSection.getConfigurationSection(key);
                        if (reportSection != null) {
                            boolean processed = reportSection.getBoolean("processed", false);
                            long timeCreated = reportSection.getLong("timeCreated", 0);

                            if (processed && timeCreated < cutoffTime) {
                                reportsSection.set(key, null);
                                changes = true;

                                LogUtil.info("Removed old report #" + key);
                            }
                        }
                    }
                }
            }

            if (changes) {
                try {
                    reportData.save(reportDataFile);
                } catch (IOException e) {
                    LogUtil.severe("Failed to save report data during cleanup", e);
                }
            }
        });
    }
}