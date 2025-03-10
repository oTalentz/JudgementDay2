package com.judgementday.data;

import com.judgementday.model.Appeal;
import com.judgementday.model.Punishment;
import com.judgementday.model.PunishmentType;
import com.judgementday.model.Report;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DataManager {

    /**
     * Initialize the data manager
     */
    void initialize();

    /**
     * Save all data to disk or database
     */
    void saveAll();

    /**
     * Get the next available ID for punishments
     *
     * @return Next punishment ID
     */
    int getNextPunishmentId();

    /**
     * Get the next available ID for reports
     *
     * @return Next report ID
     */
    int getNextReportId();

    /**
     * Get the next available ID for appeals
     *
     * @return Next appeal ID
     */
    int getNextAppealId();

    /**
     * Get the punishment level for a player and a specific type/reason
     *
     * @param uuid Player UUID
     * @param type Punishment type
     * @param reason Punishment reason
     * @return Current punishment level (starts at 1)
     */
    CompletableFuture<Integer> getPunishmentLevel(UUID uuid, PunishmentType type, String reason);

    /**
     * Add a punishment to the database
     *
     * @param punishment The punishment to add
     * @return A future that completes when the punishment is added
     */
    CompletableFuture<Void> addPunishment(Punishment punishment);

    /**
     * Revoke a punishment
     *
     * @param id Punishment ID
     * @param revokerUuid UUID of the staff member revoking the punishment
     * @param revokerName Name of the staff member revoking the punishment
     * @return A future that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> revokePunishment(int id, UUID revokerUuid, String revokerName);

    /**
     * Get a punishment by ID
     *
     * @param id Punishment ID
     * @return A future that completes with the punishment, or null if not found
     */
    CompletableFuture<Punishment> getPunishment(int id);

    /**
     * Get all punishments for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of punishments
     */
    CompletableFuture<List<Punishment>> getPlayerPunishments(UUID uuid);

    /**
     * Get all active punishments for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of active punishments
     */
    CompletableFuture<List<Punishment>> getActivePlayerPunishments(UUID uuid);

    /**
     * Get all active punishments of a specific type for a player
     *
     * @param uuid Player UUID
     * @param type Punishment type
     * @return A future that completes with a list of active punishments
     */
    CompletableFuture<List<Punishment>> getActivePlayerPunishmentsByType(UUID uuid, PunishmentType type);

    /**
     * Check if a player is currently banned
     *
     * @param uuid Player UUID
     * @return A future that completes with true if the player is banned, false otherwise
     */
    CompletableFuture<Boolean> isPlayerBanned(UUID uuid);

    /**
     * Check if a player is currently muted
     *
     * @param uuid Player UUID
     * @return A future that completes with true if the player is muted, false otherwise
     */
    CompletableFuture<Boolean> isPlayerMuted(UUID uuid);

    /**
     * Get all reports that are not processed
     *
     * @return A future that completes with a list of unprocessed reports
     */
    CompletableFuture<List<Report>> getUnprocessedReports();

    /**
     * Get all reports for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of reports
     */
    CompletableFuture<List<Report>> getPlayerReports(UUID uuid);

    /**
     * Add a report to the database
     *
     * @param report The report to add
     * @return A future that completes when the report is added
     */
    CompletableFuture<Void> addReport(Report report);

    /**
     * Process a report
     *
     * @param id Report ID
     * @param processorUuid UUID of the staff member processing the report
     * @param processorName Name of the staff member processing the report
     * @param punishmentId ID of the punishment that resulted from the report, or 0 if no punishment
     * @return A future that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> processReport(int id, UUID processorUuid, String processorName, int punishmentId);

    /**
     * Get a report by ID
     *
     * @param id Report ID
     * @return A future that completes with the report, or null if not found
     */
    CompletableFuture<Report> getReport(int id);

    /**
     * Add an appeal to the database
     *
     * @param appeal The appeal to add
     * @return A future that completes when the appeal is added
     */
    CompletableFuture<Void> addAppeal(Appeal appeal);

    /**
     * Get all pending appeals
     *
     * @return A future that completes with a list of pending appeals
     */
    CompletableFuture<List<Appeal>> getPendingAppeals();

    /**
     * Get all appeals for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of appeals
     */
    CompletableFuture<List<Appeal>> getPlayerAppeals(UUID uuid);

    /**
     * Get an appeal by ID
     *
     * @param id Appeal ID
     * @return A future that completes with the appeal, or null if not found
     */
    CompletableFuture<Appeal> getAppeal(int id);

    /**
     * Approve an appeal
     *
     * @param id Appeal ID
     * @param reviewerUuid UUID of the staff member approving the appeal
     * @param reviewerName Name of the staff member approving the appeal
     * @param comment Comment from the reviewer
     * @return A future that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> approveAppeal(int id, UUID reviewerUuid, String reviewerName, String comment);

    /**
     * Deny an appeal
     *
     * @param id Appeal ID
     * @param reviewerUuid UUID of the staff member denying the appeal
     * @param reviewerName Name of the staff member denying the appeal
     * @param comment Comment from the reviewer
     * @return A future that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> denyAppeal(int id, UUID reviewerUuid, String reviewerName, String comment);

    /**
     * Clean up expired punishments
     */
    void cleanupExpiredPunishments();

    /**
     * Clean up old reports
     */
    void cleanupOldReports();
}