package com.judgementday.manager;

import com.judgementday.JudgementDay;
import com.judgementday.event.AppealCreateEvent;
import com.judgementday.event.AppealProcessEvent;
import com.judgementday.model.Appeal;
import com.judgementday.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AppealManager {

    private final JudgementDay plugin;

    public AppealManager(JudgementDay plugin) {
        this.plugin = plugin;
    }

    /**
     * Create a new appeal
     *
     * @param punishmentId Punishment ID being appealed
     * @param playerUuid Player UUID
     * @param playerName Player name
     * @param reason Appeal reason
     * @param evidence Evidence for the appeal
     * @return A future that completes with the appeal ID, or -1 if failed
     */
    public CompletableFuture<Integer> createAppeal(int punishmentId, UUID playerUuid, String playerName,
                                                   String reason, String evidence) {
        return plugin.getDataManager().getPunishment(punishmentId)
                .thenCompose(punishment -> {
                    if (punishment == null) {
                        return CompletableFuture.completedFuture(-1);
                    }

                    // Check if player is the target of the punishment
                    if (!punishment.getTargetUuid().equals(playerUuid)) {
                        return CompletableFuture.completedFuture(-2);
                    }

                    // Check if punishment is still active
                    if (!punishment.isActive() || punishment.isExpired()) {
                        return CompletableFuture.completedFuture(-3);
                    }

                    // Check if there's already a pending appeal for this punishment
                    return getPendingAppeals()
                            .thenCompose(appeals -> {
                                for (Appeal appeal : appeals) {
                                    if (appeal.getPunishmentId() == punishmentId &&
                                            appeal.getStatus() == Appeal.AppealStatus.PENDING) {
                                        return CompletableFuture.completedFuture(-4);
                                    }
                                }

                                // Create new appeal
                                int id = plugin.getDataManager().getNextAppealId();
                                Appeal appeal = new Appeal(id, punishmentId, playerUuid, playerName, reason, evidence);

                                // Call event
                                AppealCreateEvent event = new AppealCreateEvent(appeal);
                                Bukkit.getPluginManager().callEvent(event);

                                if (event.isCancelled()) {
                                    return CompletableFuture.completedFuture(-5);
                                }

                                // Save appeal
                                return plugin.getDataManager().addAppeal(appeal)
                                        .thenApply(v -> {
                                            // Broadcast to staff
                                            broadcastAppeal(appeal);

                                            return id;
                                        });
                            });
                });
    }

    /**
     * Approve an appeal and revoke the associated punishment
     *
     * @param id Appeal ID
     * @param reviewerUuid Reviewer UUID
     * @param reviewerName Reviewer name
     * @param comment Comment from the reviewer
     * @return A future that completes with true if successful, false otherwise
     */
    public CompletableFuture<Boolean> approveAppeal(int id, UUID reviewerUuid, String reviewerName, String comment) {
        return plugin.getDataManager().getAppeal(id)
                .thenCompose(appeal -> {
                    if (appeal == null || appeal.getStatus() != Appeal.AppealStatus.PENDING) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Call event
                    AppealProcessEvent event = new AppealProcessEvent(appeal, reviewerUuid, reviewerName,
                            comment, true);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Approve appeal in database
                    return plugin.getDataManager().approveAppeal(id, reviewerUuid, reviewerName, comment)
                            .thenCompose(success -> {
                                if (success) {
                                    // Revoke the punishment
                                    return plugin.getPunishmentManager().revokePunishment(
                                                    appeal.getPunishmentId(), reviewerUuid, reviewerName)
                                            .thenApply(revoked -> {
                                                if (revoked) {
                                                    // Notify the player
                                                    notifyAppealResult(appeal, true, reviewerName, comment);

                                                    // Broadcast to staff
                                                    broadcastAppealApproval(appeal, reviewerName);
                                                }

                                                return revoked;
                                            });
                                }

                                return CompletableFuture.completedFuture(false);
                            });
                });
    }

    /**
     * Deny an appeal
     *
     * @param id Appeal ID
     * @param reviewerUuid Reviewer UUID
     * @param reviewerName Reviewer name
     * @param comment Comment from the reviewer
     * @return A future that completes with true if successful, false otherwise
     */
    public CompletableFuture<Boolean> denyAppeal(int id, UUID reviewerUuid, String reviewerName, String comment) {
        return plugin.getDataManager().getAppeal(id)
                .thenCompose(appeal -> {
                    if (appeal == null || appeal.getStatus() != Appeal.AppealStatus.PENDING) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Call event
                    AppealProcessEvent event = new AppealProcessEvent(appeal, reviewerUuid, reviewerName,
                            comment, false);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Deny appeal in database
                    return plugin.getDataManager().denyAppeal(id, reviewerUuid, reviewerName, comment)
                            .thenApply(success -> {
                                if (success) {
                                    // Notify the player
                                    notifyAppealResult(appeal, false, reviewerName, comment);

                                    // Broadcast to staff
                                    broadcastAppealDenial(appeal, reviewerName);
                                }

                                return success;
                            });
                });
    }

    /**
     * Get a list of pending appeals
     *
     * @return A future that completes with a list of pending appeals
     */
    public CompletableFuture<List<Appeal>> getPendingAppeals() {
        return plugin.getDataManager().getPendingAppeals();
    }

    /**
     * Get appeals for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of appeals
     */
    public CompletableFuture<List<Appeal>> getPlayerAppeals(UUID uuid) {
        return plugin.getDataManager().getPlayerAppeals(uuid);
    }

    /**
     * Get an appeal by ID
     *
     * @param id Appeal ID
     * @return A future that completes with the appeal, or null if not found
     */
    public CompletableFuture<Appeal> getAppeal(int id) {
        return plugin.getDataManager().getAppeal(id);
    }

    /**
     * Check if a player can submit a new appeal (cooldown check)
     *
     * @param uuid Player UUID
     * @return A future that completes with true if the player can submit a new appeal, false otherwise
     */
    public CompletableFuture<Boolean> canSubmitAppeal(UUID uuid) {
        return getPlayerAppeals(uuid)
                .thenApply(appeals -> {
                    if (appeals.isEmpty()) {
                        return true;
                    }

                    // Check how many appeals were submitted recently
                    long cooldownTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
                    long recentAppeals = appeals.stream()
                            .filter(appeal -> appeal.getTimeCreated() >= cooldownTime)
                            .count();

                    int maxAppealsPerDay = plugin.getConfigManager().getMainConfig()
                            .getInt("appeals.max-per-day", 3);

                    return recentAppeals < maxAppealsPerDay;
                });
    }

    /**
     * Get the remaining time until a player can submit a new appeal
     *
     * @param uuid Player UUID
     * @return A future that completes with the remaining time in milliseconds, or 0 if no cooldown
     */
    public CompletableFuture<Long> getAppealCooldown(UUID uuid) {
        return getPlayerAppeals(uuid)
                .thenApply(appeals -> {
                    if (appeals.isEmpty()) {
                        return 0L;
                    }

                    // Find the oldest appeal within the last 24 hours
                    long cooldownTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);

                    Appeal oldestRecent = appeals.stream()
                            .filter(appeal -> appeal.getTimeCreated() >= cooldownTime)
                            .min((a1, a2) -> Long.compare(a1.getTimeCreated(), a2.getTimeCreated()))
                            .orElse(null);

                    if (oldestRecent == null) {
                        return 0L;
                    }

                    // Calculate when this appeal will no longer count toward the daily limit
                    long expiry = oldestRecent.getTimeCreated() + TimeUnit.HOURS.toMillis(24);
                    long remaining = expiry - System.currentTimeMillis();

                    return Math.max(0L, remaining);
                });
    }

    /**
     * Broadcast an appeal to staff members
     *
     * @param appeal The appeal to broadcast
     */
    private void broadcastAppeal(Appeal appeal) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", appeal.getPlayerName());
        placeholders.put("id", String.valueOf(appeal.getId()));
        placeholders.put("punishment_id", String.valueOf(appeal.getPunishmentId()));

        String message = plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("appeal.broadcast");
        String formattedMessage = MessageUtil.formatMessage(message, placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.appeals")) {
                player.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * Broadcast an appeal approval to staff members
     *
     * @param appeal The appeal that was approved
     * @param reviewerName The name of the staff member who approved the appeal
     */
    private void broadcastAppealApproval(Appeal appeal, String reviewerName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", appeal.getPlayerName());
        placeholders.put("reviewer", reviewerName);
        placeholders.put("id", String.valueOf(appeal.getId()));
        placeholders.put("punishment_id", String.valueOf(appeal.getPunishmentId()));

        String message = plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("appeal.approved-broadcast");
        String formattedMessage = MessageUtil.formatMessage(message, placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.appeals")) {
                player.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * Broadcast an appeal denial to staff members
     *
     * @param appeal The appeal that was denied
     * @param reviewerName The name of the staff member who denied the appeal
     */
    private void broadcastAppealDenial(Appeal appeal, String reviewerName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", appeal.getPlayerName());
        placeholders.put("reviewer", reviewerName);
        placeholders.put("id", String.valueOf(appeal.getId()));
        placeholders.put("punishment_id", String.valueOf(appeal.getPunishmentId()));

        String message = plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("appeal.denied-broadcast");
        String formattedMessage = MessageUtil.formatMessage(message, placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.appeals")) {
                player.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * Notify a player about the result of their appeal
     *
     * @param appeal The appeal
     * @param approved Whether the appeal was approved
     * @param reviewerName The name of the staff member who reviewed the appeal
     * @param comment The comment from the reviewer
     */
    private void notifyAppealResult(Appeal appeal, boolean approved, String reviewerName, String comment) {
        Player player = Bukkit.getPlayer(appeal.getPlayerUuid());
        if (player == null) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", String.valueOf(appeal.getId()));
        placeholders.put("punishment_id", String.valueOf(appeal.getPunishmentId()));
        placeholders.put("reviewer", reviewerName);
        placeholders.put("comment", comment);

        String messagePath = approved ? "appeal.approved-notification" : "appeal.denied-notification";
        String message = plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage(messagePath);
        String formattedMessage = MessageUtil.formatMessage(message, placeholders);

        player.sendMessage(formattedMessage);
    }
}