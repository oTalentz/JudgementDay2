package com.judgementday.data;

import com.judgementday.JudgementDay;
import com.judgementday.model.Appeal;
import com.judgementday.model.Punishment;
import com.judgementday.model.PunishmentType;
import com.judgementday.model.Report;
import com.judgementday.util.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseManager implements DataManager {

    private final JudgementDay plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private Connection connection;

    private final AtomicInteger punishmentIdCounter = new AtomicInteger(1);
    private final AtomicInteger reportIdCounter = new AtomicInteger(1);
    private final AtomicInteger appealIdCounter = new AtomicInteger(1);

    // Cache active punishments for better performance
    private final Map<UUID, List<Punishment>> activePlayerPunishments = new ConcurrentHashMap<>();

    public DatabaseManager(JudgementDay plugin) {
        this.plugin = plugin;

        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        this.host = config.getString("storage.mysql.host", "localhost");
        this.port = config.getInt("storage.mysql.port", 3306);
        this.database = config.getString("storage.mysql.database", "minecraft");
        this.username = config.getString("storage.mysql.username", "root");
        this.password = config.getString("storage.mysql.password", "");
        this.tablePrefix = config.getString("storage.mysql.table-prefix", "jd_");
    }

    @Override
    public void initialize() {
        // Open connection
        try {
            openConnection();

            // Create tables if they don't exist
            createTables();

            // Initialize counters
            initializeCounters();

            // Load active punishments into cache
            loadActivePunishmentsCache();

        } catch (SQLException e) {
            LogUtil.severe("Failed to initialize database connection", e);
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    private void openConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        synchronized (this) {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                LogUtil.severe("JDBC driver not found", e);
                throw new SQLException("JDBC driver not found", e);
            }

            String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";

            connection = DriverManager.getConnection(jdbcUrl, username, password);
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Punishments table
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "punishments (" +
                            "id INT PRIMARY KEY AUTO_INCREMENT, " +
                            "target_uuid VARCHAR(36) NOT NULL, " +
                            "target_name VARCHAR(16) NOT NULL, " +
                            "punisher_uuid VARCHAR(36) NOT NULL, " +
                            "punisher_name VARCHAR(16) NOT NULL, " +
                            "type VARCHAR(16) NOT NULL, " +
                            "reason VARCHAR(255) NOT NULL, " +
                            "time_issued BIGINT NOT NULL, " +
                            "duration BIGINT NOT NULL, " +
                            "expiry BIGINT NOT NULL, " +
                            "active BOOLEAN NOT NULL DEFAULT TRUE, " +
                            "proof_link TEXT, " +
                            "level INT NOT NULL DEFAULT 1, " +
                            "revoker_uuid VARCHAR(36), " +
                            "revoker_name VARCHAR(16), " +
                            "time_revoked BIGINT, " +
                            "INDEX idx_target_uuid (target_uuid), " +
                            "INDEX idx_active (active), " +
                            "INDEX idx_type (type))"
            );

            // Player punishment history table
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "punishment_history (" +
                            "id INT PRIMARY KEY AUTO_INCREMENT, " +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "punishment_id INT NOT NULL, " +
                            "type VARCHAR(16) NOT NULL, " +
                            "reason VARCHAR(255) NOT NULL, " +
                            "level INT NOT NULL, " +
                            "FOREIGN KEY (punishment_id) REFERENCES " + tablePrefix + "punishments(id) ON DELETE CASCADE, " +
                            "INDEX idx_player_uuid (player_uuid), " +
                            "INDEX idx_type_reason (type, reason))"
            );

            // Reports table
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "reports (" +
                            "id INT PRIMARY KEY AUTO_INCREMENT, " +
                            "reporter_uuid VARCHAR(36) NOT NULL, " +
                            "reporter_name VARCHAR(16) NOT NULL, " +
                            "reported_uuid VARCHAR(36) NOT NULL, " +
                            "reported_name VARCHAR(16) NOT NULL, " +
                            "reason TEXT NOT NULL, " +
                            "time_created BIGINT NOT NULL, " +
                            "processed BOOLEAN NOT NULL DEFAULT FALSE, " +
                            "processor_uuid VARCHAR(36), " +
                            "processor_name VARCHAR(16), " +
                            "time_processed BIGINT, " +
                            "result_punishment_id INT, " +
                            "FOREIGN KEY (result_punishment_id) REFERENCES " + tablePrefix + "punishments(id) ON DELETE SET NULL, " +
                            "INDEX idx_reported_uuid (reported_uuid), " +
                            "INDEX idx_processed (processed))"
            );

            // Appeals table
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "appeals (" +
                            "id INT PRIMARY KEY AUTO_INCREMENT, " +
                            "punishment_id INT NOT NULL, " +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "player_name VARCHAR(16) NOT NULL, " +
                            "reason TEXT NOT NULL, " +
                            "evidence TEXT, " +
                            "time_created BIGINT NOT NULL, " +
                            "status VARCHAR(16) NOT NULL DEFAULT 'PENDING', " +
                            "reviewer_uuid VARCHAR(36), " +
                            "reviewer_name VARCHAR(16), " +
                            "time_reviewed BIGINT, " +
                            "review_comment TEXT, " +
                            "FOREIGN KEY (punishment_id) REFERENCES " + tablePrefix + "punishments(id) ON DELETE CASCADE, " +
                            "INDEX idx_player_uuid (player_uuid), " +
                            "INDEX idx_status (status))"
            );
        }
    }

    private void initializeCounters() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Get max punishment ID
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM " + tablePrefix + "punishments")) {
                if (rs.next()) {
                    punishmentIdCounter.set(rs.getInt("next_id"));
                }
            }

            // Get max report ID
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM " + tablePrefix + "reports")) {
                if (rs.next()) {
                    reportIdCounter.set(rs.getInt("next_id"));
                }
            }

            // Get max appeal ID
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM " + tablePrefix + "appeals")) {
                if (rs.next()) {
                    appealIdCounter.set(rs.getInt("next_id"));
                }
            }
        }
    }

    private void loadActivePunishmentsCache() throws SQLException {
        activePlayerPunishments.clear();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + tablePrefix + "punishments " +
                        "WHERE active = TRUE AND (expiry > ? OR expiry = -1)")) {

            ps.setLong(1, System.currentTimeMillis());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Punishment punishment = extractPunishmentFromResultSet(rs);
                    UUID targetUuid = punishment.getTargetUuid();

                    activePlayerPunishments.computeIfAbsent(targetUuid, k -> new ArrayList<>())
                            .add(punishment);
                }
            }
        }
    }

    private Punishment extractPunishmentFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> data = new HashMap<>();

        data.put("id", rs.getInt("id"));
        data.put("targetUuid", rs.getString("target_uuid"));
        data.put("targetName", rs.getString("target_name"));
        data.put("punisherUuid", rs.getString("punisher_uuid"));
        data.put("punisherName", rs.getString("punisher_name"));
        data.put("type", rs.getString("type"));
        data.put("reason", rs.getString("reason"));
        data.put("timeIssued", rs.getLong("time_issued"));
        data.put("duration", rs.getLong("duration"));
        data.put("expiry", rs.getLong("expiry"));
        data.put("active", rs.getBoolean("active"));
        data.put("proofLink", rs.getString("proof_link"));
        data.put("level", rs.getInt("level"));

        String revokerUuid = rs.getString("revoker_uuid");
        if (revokerUuid != null) {
            data.put("revokerUuid", revokerUuid);
            data.put("revokerName", rs.getString("revoker_name"));
            data.put("timeRevoked", rs.getLong("time_revoked"));
        }

        return new Punishment(data);
    }

    private Report extractReportFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> data = new HashMap<>();

        data.put("id", rs.getInt("id"));
        data.put("reporterUuid", rs.getString("reporter_uuid"));
        data.put("reporterName", rs.getString("reporter_name"));
        data.put("reportedUuid", rs.getString("reported_uuid"));
        data.put("reportedName", rs.getString("reported_name"));
        data.put("reason", rs.getString("reason"));
        data.put("timeCreated", rs.getLong("time_created"));
        data.put("processed", rs.getBoolean("processed"));

        if (rs.getBoolean("processed")) {
            data.put("processorUuid", rs.getString("processor_uuid"));
            data.put("processorName", rs.getString("processor_name"));
            data.put("timeProcessed", rs.getLong("time_processed"));

            int resultPunishmentId = rs.getInt("result_punishment_id");
            if (!rs.wasNull()) {
                data.put("resultPunishmentId", resultPunishmentId);
            }
        }

        return new Report(data);
    }

    private Appeal extractAppealFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> data = new HashMap<>();

        data.put("id", rs.getInt("id"));
        data.put("punishmentId", rs.getInt("punishment_id"));
        data.put("playerUuid", rs.getString("player_uuid"));
        data.put("playerName", rs.getString("player_name"));
        data.put("reason", rs.getString("reason"));
        data.put("evidence", rs.getString("evidence"));
        data.put("timeCreated", rs.getLong("time_created"));
        data.put("status", rs.getString("status"));

        String reviewerUuid = rs.getString("reviewer_uuid");
        if (reviewerUuid != null) {
            data.put("reviewerUuid", reviewerUuid);
            data.put("reviewerName", rs.getString("reviewer_name"));
            data.put("timeReviewed", rs.getLong("time_reviewed"));
            data.put("reviewComment", rs.getString("review_comment"));
        }

        return new Appeal(data);
    }

    @Override
    public void saveAll() {
        // Nothing to do here - database is saved automatically
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
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) AS count FROM " + tablePrefix + "punishment_history " +
                                "WHERE player_uuid = ? AND type = ? AND reason = ?")) {

                    ps.setString(1, uuid.toString());
                    ps.setString(2, type.name());
                    ps.setString(3, reason);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("count") + 1;
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get punishment level", e);
            }

            return 1; // Default level if error
        });
    }

    @Override
    public CompletableFuture<Void> addPunishment(Punishment punishment) {
        return CompletableFuture.runAsync(() -> {
            try {
                openConnection();
                connection.setAutoCommit(false);

                try {
                    // Insert punishment
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO " + tablePrefix + "punishments " +
                                    "(id, target_uuid, target_name, punisher_uuid, punisher_name, " +
                                    "type, reason, time_issued, duration, expiry, active, proof_link, level) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {

                        ps.setInt(1, punishment.getId());
                        ps.setString(2, punishment.getTargetUuid().toString());
                        ps.setString(3, punishment.getTargetName());
                        ps.setString(4, punishment.getPunisherUuid().toString());
                        ps.setString(5, punishment.getPunisherName());
                        ps.setString(6, punishment.getType().name());
                        ps.setString(7, punishment.getReason());
                        ps.setLong(8, punishment.getTimeIssued());
                        ps.setLong(9, punishment.getDuration());
                        ps.setLong(10, punishment.getExpiry());
                        ps.setBoolean(11, punishment.isActive());
                        ps.setString(12, punishment.getProofLink());
                        ps.setInt(13, punishment.getLevel());

                        ps.executeUpdate();
                    }

                    // Insert history record
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO " + tablePrefix + "punishment_history " +
                                    "(player_uuid, punishment_id, type, reason, level) " +
                                    "VALUES (?, ?, ?, ?, ?)")) {

                        ps.setString(1, punishment.getTargetUuid().toString());
                        ps.setInt(2, punishment.getId());
                        ps.setString(3, punishment.getType().name());
                        ps.setString(4, punishment.getReason());
                        ps.setInt(5, punishment.getLevel());

                        ps.executeUpdate();
                    }

                    connection.commit();

                    // Add to cache
                    if (punishment.isActive() && !punishment.isExpired()) {
                        activePlayerPunishments.computeIfAbsent(punishment.getTargetUuid(), k -> new ArrayList<>())
                                .add(punishment);
                    }

                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }

            } catch (SQLException e) {
                LogUtil.severe("Failed to add punishment", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> revokePunishment(int id, UUID revokerUuid, String revokerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                openConnection();

                // First, check if punishment exists and is active
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "punishments WHERE id = ? AND active = TRUE")) {

                    ps.setInt(1, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return false;
                        }

                        // Get punishment data
                        Punishment punishment = extractPunishmentFromResultSet(rs);
                        punishment.revoke(revokerUuid, revokerName);

                        // Update punishment
                        try (PreparedStatement updatePs = connection.prepareStatement(
                                "UPDATE " + tablePrefix + "punishments SET " +
                                        "active = FALSE, revoker_uuid = ?, revoker_name = ?, time_revoked = ? " +
                                        "WHERE id = ?")) {

                            updatePs.setString(1, revokerUuid.toString());
                            updatePs.setString(2, revokerName);
                            updatePs.setLong(3, System.currentTimeMillis());
                            updatePs.setInt(4, id);

                            int updated = updatePs.executeUpdate();

                            if (updated > 0) {
                                // Remove from cache
                                UUID targetUuid = punishment.getTargetUuid();
                                if (activePlayerPunishments.containsKey(targetUuid)) {
                                    activePlayerPunishments.get(targetUuid).removeIf(p -> p.getId() == id);
                                }

                                return true;
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to revoke punishment", e);
            }

            return false;
        });
    }

    @Override
    public CompletableFuture<Punishment> getPunishment(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "punishments WHERE id = ?")) {

                    ps.setInt(1, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return extractPunishmentFromResultSet(rs);
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get punishment", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getPlayerPunishments(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> punishments = new ArrayList<>();

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "punishments WHERE target_uuid = ? ORDER BY time_issued DESC")) {

                    ps.setString(1, uuid.toString());

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            punishments.add(extractPunishmentFromResultSet(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get player punishments", e);
            }

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
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }

            List<Punishment> punishments = new ArrayList<>();

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "punishments " +
                                "WHERE target_uuid = ? AND active = TRUE AND (expiry > ? OR expiry = -1)")) {

                    ps.setString(1, uuid.toString());
                    ps.setLong(2, System.currentTimeMillis());

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            punishments.add(extractPunishmentFromResultSet(rs));
                        }
                    }
                }

                // Update cache
                activePlayerPunishments.put(uuid, new ArrayList<>(punishments));

            } catch (SQLException e) {
                LogUtil.severe("Failed to get active player punishments", e);
            }

            return punishments;
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> getActivePlayerPunishmentsByType(UUID uuid, PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> {
            // Use cache for better performance
            if (activePlayerPunishments.containsKey(uuid)) {
                return activePlayerPunishments.get(uuid).stream()
                        .filter(p -> p.getType() == type && !p.isExpired())
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }

            List<Punishment> punishments = new ArrayList<>();

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "punishments " +
                                "WHERE target_uuid = ? AND type = ? AND active = TRUE AND (expiry > ? OR expiry = -1)")) {

                    ps.setString(1, uuid.toString());
                    ps.setString(2, type.name());
                    ps.setLong(3, System.currentTimeMillis());

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            punishments.add(extractPunishmentFromResultSet(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get active player punishments by type", e);
            }

            return punishments;
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

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) AS count FROM " + tablePrefix + "punishments " +
                                "WHERE target_uuid = ? AND type = 'BAN' AND active = TRUE AND (expiry > ? OR expiry = -1)")) {

                    ps.setString(1, uuid.toString());
                    ps.setLong(2, System.currentTimeMillis());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("count") > 0;
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to check if player is banned", e);
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

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) AS count FROM " + tablePrefix + "punishments " +
                                "WHERE target_uuid = ? AND type = 'MUTE' AND active = TRUE AND (expiry > ? OR expiry = -1)")) {

                    ps.setString(1, uuid.toString());
                    ps.setLong(2, System.currentTimeMillis());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("count") > 0;
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to check if player is muted", e);
            }

            return false;
        });
    }

    @Override
    public CompletableFuture<List<Report>> getUnprocessedReports() {
        return CompletableFuture.supplyAsync(() -> {
            List<Report> reports = new ArrayList<>();

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "reports WHERE processed = FALSE ORDER BY time_created DESC")) {

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            reports.add(extractReportFromResultSet(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get unprocessed reports", e);
            }

            return reports;
        });
    }

    @Override
    public CompletableFuture<List<Report>> getPlayerReports(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Report> reports = new ArrayList<>();

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "reports WHERE reported_uuid = ? ORDER BY time_created DESC")) {

                    ps.setString(1, uuid.toString());

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            reports.add(extractReportFromResultSet(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get player reports", e);
            }

            return reports;
        });
    }

    @Override
    public CompletableFuture<Void> addReport(Report report) {
        return CompletableFuture.runAsync(() -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + tablePrefix + "reports " +
                                "(id, reporter_uuid, reporter_name, reported_uuid, reported_name, reason, time_created) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)")) {

                    ps.setInt(1, report.getId());
                    ps.setString(2, report.getReporterUuid().toString());
                    ps.setString(3, report.getReporterName());
                    ps.setString(4, report.getReportedUuid().toString());
                    ps.setString(5, report.getReportedName());
                    ps.setString(6, report.getReason());
                    ps.setLong(7, report.getTimeCreated());

                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to add report", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> processReport(int id, UUID processorUuid, String processorName, int punishmentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + tablePrefix + "reports SET " +
                                "processed = TRUE, processor_uuid = ?, processor_name = ?, " +
                                "time_processed = ?, result_punishment_id = ? " +
                                "WHERE id = ? AND processed = FALSE")) {

                    ps.setString(1, processorUuid.toString());
                    ps.setString(2, processorName);
                    ps.setLong(3, System.currentTimeMillis());

                    if (punishmentId > 0) {
                        ps.setInt(4, punishmentId);
                    } else {
                        ps.setNull(4, Types.INTEGER);
                    }

                    ps.setInt(5, id);

                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to process report", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Report> getReport(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "reports WHERE id = ?")) {

                    ps.setInt(1, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return extractReportFromResultSet(rs);
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get report", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> addAppeal(Appeal appeal) {
        return CompletableFuture.runAsync(() -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + tablePrefix + "appeals " +
                                "(id, punishment_id, player_uuid, player_name, reason, evidence, time_created, status) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

                    ps.setInt(1, appeal.getId());
                    ps.setInt(2, appeal.getPunishmentId());
                    ps.setString(3, appeal.getPlayerUuid().toString());
                    ps.setString(4, appeal.getPlayerName());
                    ps.setString(5, appeal.getReason());
                    ps.setString(6, appeal.getEvidence());
                    ps.setLong(7, appeal.getTimeCreated());
                    ps.setString(8, appeal.getStatus().name());

                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to add appeal", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Appeal>> getPendingAppeals() {
        return CompletableFuture.supplyAsync(() -> {
            List<Appeal> appeals = new ArrayList<>();

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "appeals WHERE status = 'PENDING' ORDER BY time_created DESC")) {

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            appeals.add(extractAppealFromResultSet(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get pending appeals", e);
            }

            return appeals;
        });
    }

    @Override
    public CompletableFuture<List<Appeal>> getPlayerAppeals(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Appeal> appeals = new ArrayList<>();

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "appeals WHERE player_uuid = ? ORDER BY time_created DESC")) {

                    ps.setString(1, uuid.toString());

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            appeals.add(extractAppealFromResultSet(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get player appeals", e);
            }

            return appeals;
        });
    }

    @Override
    public CompletableFuture<Appeal> getAppeal(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM " + tablePrefix + "appeals WHERE id = ?")) {

                    ps.setInt(1, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return extractAppealFromResultSet(rs);
                        }
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to get appeal", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> approveAppeal(int id, UUID reviewerUuid, String reviewerName, String comment) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + tablePrefix + "appeals SET " +
                                "status = 'APPROVED', reviewer_uuid = ?, reviewer_name = ?, " +
                                "time_reviewed = ?, review_comment = ? " +
                                "WHERE id = ? AND status = 'PENDING'")) {

                    ps.setString(1, reviewerUuid.toString());
                    ps.setString(2, reviewerName);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setString(4, comment);
                    ps.setInt(5, id);

                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to approve appeal", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> denyAppeal(int id, UUID reviewerUuid, String reviewerName, String comment) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + tablePrefix + "appeals SET " +
                                "status = 'DENIED', reviewer_uuid = ?, reviewer_name = ?, " +
                                "time_reviewed = ?, review_comment = ? " +
                                "WHERE id = ? AND status = 'PENDING'")) {

                    ps.setString(1, reviewerUuid.toString());
                    ps.setString(2, reviewerName);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setString(4, comment);
                    ps.setInt(5, id);

                    return ps.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to deny appeal", e);
                return false;
            }
        });
    }

    @Override
    public void cleanupExpiredPunishments() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + tablePrefix + "punishments SET active = FALSE, auto_expired = TRUE " +
                                "WHERE active = TRUE AND expiry > 0 AND expiry < ?")) {

                    ps.setLong(1, System.currentTimeMillis());
                    int count = ps.executeUpdate();

                    if (count > 0) {
                        LogUtil.info("Cleaned up " + count + " expired punishments");

                        // Reload active punishments cache
                        loadActivePunishmentsCache();
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to cleanup expired punishments", e);
            }
        });
    }

    @Override
    public void cleanupOldReports() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int daysToKeep = plugin.getConfigManager().getMainConfig().getInt("storage.reports-days-to-keep", 30);
            if (daysToKeep <= 0) return; // Keep forever

            try {
                openConnection();

                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM " + tablePrefix + "reports " +
                                "WHERE processed = TRUE AND time_created < ?")) {

                    long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L);
                    ps.setLong(1, cutoffTime);

                    int count = ps.executeUpdate();

                    if (count > 0) {
                        LogUtil.info("Cleaned up " + count + " old reports");
                    }
                }
            } catch (SQLException e) {
                LogUtil.severe("Failed to cleanup old reports", e);
            }
        });
    }
}