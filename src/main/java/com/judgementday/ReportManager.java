package com.judgementday;

import com.judgementday.event.ReportCreateEvent;
import com.judgementday.event.ReportProcessEvent;
import com.judgementday.model.Report;
import com.judgementday.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ReportManager {

    private final JudgementDay plugin;

    public ReportManager(JudgementDay plugin) {
        this.plugin = plugin;
    }

    /**
     * Create a new report
     *
     * @param reporterUuid Reporter UUID
     * @param reporterName Reporter name
     * @param reportedUuid Reported player UUID
     * @param reportedName Reported player name
     * @param reason Report reason
     * @return A future that completes with the report ID
     */
    public CompletableFuture<Integer> createReport(UUID reporterUuid, String reporterName,
                                                   UUID reportedUuid, String reportedName,
                                                   String reason) {
        // Check cooldown
        if (!plugin.getPlayerManager().canReport(reporterUuid, reportedUuid)) {
            int cooldown = plugin.getPlayerManager().getReportCooldown(reporterUuid, reportedUuid);
            return CompletableFuture.completedFuture(-1);
        }

        // Set cooldown
        plugin.getPlayerManager().setReportCooldown(reporterUuid, reportedUuid);

        // Create report
        int id = plugin.getDataManager().getNextReportId();
        Report report = new Report(id, reporterUuid, reporterName, reportedUuid, reportedName, reason);

        // Call event
        ReportCreateEvent event = new ReportCreateEvent(report);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(-1);
        }

        // Add report to database
        return plugin.getDataManager().addReport(report)
                .thenApply(v -> {
                    // Broadcast report to staff
                    broadcastReport(report);

                    return id;
                });
    }

    /**
     * Process a report
     *
     * @param id Report ID
     * @param processorUuid Processor UUID
     * @param processorName Processor name
     * @param punishmentId Punishment ID that resulted from the report, or 0 if none
     * @return A future that completes with true if successful, false otherwise
     */
    public CompletableFuture<Boolean> processReport(int id, UUID processorUuid, String processorName, int punishmentId) {
        return plugin.getDataManager().getReport(id)
                .thenCompose(report -> {
                    if (report == null || report.isProcessed()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Call event
                    ReportProcessEvent event = new ReportProcessEvent(report, processorUuid, processorName, punishmentId);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Process report in database
                    return plugin.getDataManager().processReport(id, processorUuid, processorName, punishmentId)
                            .thenApply(success -> {
                                if (success) {
                                    // Broadcast processing to staff
                                    broadcastReportProcessing(report, processorName, punishmentId > 0);
                                }

                                return success;
                            });
                });
    }

    /**
     * Get unprocessed reports
     *
     * @return A future that completes with a list of unprocessed reports
     */
    public CompletableFuture<List<Report>> getUnprocessedReports() {
        return plugin.getDataManager().getUnprocessedReports();
    }

    /**
     * Get reports for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of reports
     */
    public CompletableFuture<List<Report>> getPlayerReports(UUID uuid) {
        return plugin.getDataManager().getPlayerReports(uuid);
    }

    /**
     * Get a report by ID
     *
     * @param id Report ID
     * @return A future that completes with the report, or null if not found
     */
    public CompletableFuture<Report> getReport(int id) {
        return plugin.getDataManager().getReport(id);
    }

    /**
     * Broadcast a report to staff members
     *
     * @param report The report to broadcast
     */
    private void broadcastReport(Report report) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reporter", report.getReporterName());
        placeholders.put("reported", report.getReportedName());
        placeholders.put("reason", report.getReason());
        placeholders.put("id", String.valueOf(report.getId()));

        String message = plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("report.broadcast");
        String formattedMessage = MessageUtil.formatMessage(message, placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.staff")) {
                player.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * Broadcast report processing to staff members
     *
     * @param report The report that was processed
     * @param processorName The name of the staff member who processed the report
     * @param punished Whether the reported player was punished
     */
    private void broadcastReportProcessing(Report report, String processorName, boolean punished) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("processor", processorName);
        placeholders.put("reported", report.getReportedName());
        placeholders.put("id", String.valueOf(report.getId()));

        String messagePath = punished ? "report.processed-punished" : "report.processed-dismissed";
        String message = plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage(messagePath);
        String formattedMessage = MessageUtil.formatMessage(message, placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.staff")) {
                player.sendMessage(formattedMessage);
            }
        }
    }
}