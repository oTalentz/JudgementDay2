package com.judgementday.manager;

import com.judgementday.JudgementDay;
import com.judgementday.event.PunishmentEvent;
import com.judgementday.event.PunishmentRevokeEvent;
import com.judgementday.model.Punishment;
import com.judgementday.model.PunishmentType;
import com.judgementday.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentManager {

    private final JudgementDay plugin;
    private final Map<UUID, Map<String, Object>> pendingPunishments = new ConcurrentHashMap<>();
    private final Map<UUID, String> awaitingProofLinks = new ConcurrentHashMap<>();

    public PunishmentManager(JudgementDay plugin) {
        this.plugin = plugin;
    }

    /**
     * Apply a punishment to a player
     *
     * @param targetUuid Target player UUID
     * @param targetName Target player name
     * @param punisherUuid Staff member UUID
     * @param punisherName Staff member name
     * @param type Punishment type
     * @param reason Punishment reason
     * @param proofLink Link to proof
     * @return A future that completes with the punishment ID
     */
    public CompletableFuture<Integer> punishPlayer(UUID targetUuid, String targetName,
                                                   UUID punisherUuid, String punisherName,
                                                   PunishmentType type, String reason,
                                                   String proofLink) {
        // Get punishment level
        return plugin.getDataManager().getPunishmentLevel(targetUuid, type, reason)
                .thenCompose(level -> {
                    // Get punishment duration
                    long duration = plugin.getConfigManager().getPunishmentDuration(type, reason, level);

                    // Create punishment
                    int id = plugin.getDataManager().getNextPunishmentId();
                    Punishment punishment = new Punishment(id, targetUuid, targetName, punisherUuid, punisherName,
                            type, reason, duration, proofLink, level);

                    // Call event
                    PunishmentEvent event = new PunishmentEvent(punishment);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return CompletableFuture.completedFuture(-1);
                    }

                    // Add punishment to database
                    return plugin.getDataManager().addPunishment(punishment)
                            .thenApply(v -> {
                                // Execute punishment
                                executePunishment(punishment);

                                // Broadcast punishment
                                broadcastPunishment(punishment);

                                return id;
                            });
                });
    }

    /**
     * Revoke a punishment
     *
     * @param id Punishment ID
     * @param revokerUuid Staff member UUID
     * @param revokerName Staff member name
     * @return A future that completes with true if successful, false otherwise
     */
    public CompletableFuture<Boolean> revokePunishment(int id, UUID revokerUuid, String revokerName) {
        return plugin.getDataManager().getPunishment(id)
                .thenCompose(punishment -> {
                    if (punishment == null || !punishment.isActive()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Call event
                    PunishmentRevokeEvent event = new PunishmentRevokeEvent(punishment, revokerUuid, revokerName);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Revoke punishment in database
                    return plugin.getDataManager().revokePunishment(id, revokerUuid, revokerName)
                            .thenApply(success -> {
                                if (success) {
                                    // Broadcast revocation
                                    broadcastRevocation(punishment, revokerName);

                                    // Execute revocation
                                    executeRevocation(punishment);
                                }

                                return success;
                            });
                });
    }

    /**
     * Get pending punishment data for a staff member
     *
     * @param uuid Staff member UUID
     * @return Pending punishment data or null if none
     */
    public Map<String, Object> getPendingPunishment(UUID uuid) {
        return pendingPunishments.get(uuid);
    }

    /**
     * Add pending punishment data for a staff member
     *
     * @param uuid Staff member UUID
     * @param data Punishment data
     */
    public void addPendingPunishment(UUID uuid, Map<String, Object> data) {
        pendingPunishments.put(uuid, data);
    }

    /**
     * Remove pending punishment data for a staff member
     *
     * @param uuid Staff member UUID
     */
    public void removePendingPunishment(UUID uuid) {
        pendingPunishments.remove(uuid);
    }

    /**
     * Check if a staff member is awaiting a proof link
     *
     * @param uuid Staff member UUID
     * @return true if awaiting proof link, false otherwise
     */
    public boolean isAwaitingProofLink(UUID uuid) {
        return awaitingProofLinks.containsKey(uuid);
    }

    /**
     * Add a staff member to the awaiting proof link list
     *
     * @param uuid Staff member UUID
     * @param targetName Target player name
     */
    public void addAwaitingProofLink(UUID uuid, String targetName) {
        awaitingProofLinks.put(uuid, targetName);
    }

    /**
     * Remove a staff member from the awaiting proof link list
     *
     * @param uuid Staff member UUID
     * @return The target player name or null if not found
     */
    public String removeAwaitingProofLink(UUID uuid) {
        return awaitingProofLinks.remove(uuid);
    }

    /**
     * Get the punishment message for a punishment
     *
     * @param punishment The punishment
     * @return The formatted punishment message
     */
    public String getPunishmentMessage(Punishment punishment) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("type", punishment.getType().getDisplayName());
        placeholders.put("reason", punishment.getReason());
        placeholders.put("punisher", punishment.getPunisherName());
        placeholders.put("id", String.valueOf(punishment.getId()));
        placeholders.put("proof", punishment.getProofLink());

        if (punishment.getDuration() == -1) {
            placeholders.put("duration", "Permanent");
            return MessageUtil.formatMessage(plugin.getConfigManager().getMessage("punishment.permanent-message"), placeholders);
        } else {
            placeholders.put("duration", MessageUtil.formatDuration(punishment.getDuration()));
            placeholders.put("expiry", MessageUtil.formatDate(punishment.getExpiry()));
            return MessageUtil.formatMessage(plugin.getConfigManager().getMessage("punishment.temp-message"), placeholders);
        }
    }

    /**
     * Execute a punishment
     *
     * @param punishment The punishment to execute
     */
    private void executePunishment(Punishment punishment) {
        Player target = Bukkit.getPlayer(punishment.getTargetUuid());
        if (target == null) return;

        switch (punishment.getType()) {
            case KICK:
                target.kickPlayer(getPunishmentMessage(punishment));
                break;
            case BAN:
                target.kickPlayer(getPunishmentMessage(punishment));
                break;
            case MUTE:
            case WARN:
                target.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                                plugin.getConfigManager().getMessage("punishment.notification"),
                        Collections.singletonMap("type", punishment.getType().getDisplayName())));
                target.sendMessage(getPunishmentMessage(punishment));
                break;
        }
    }

    /**
     * Execute a punishment revocation
     *
     * @param punishment The punishment that was revoked
     */
    private void executeRevocation(Punishment punishment) {
        Player target = Bukkit.getPlayer(punishment.getTargetUuid());
        if (target == null) return;

        switch (punishment.getType()) {
            case MUTE:
            case WARN:
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("type", punishment.getType().getDisplayName());
                placeholders.put("revoker", punishment.getRevokerName());

                target.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("punishment.revoked"), placeholders));
                break;
        }
    }

    /**
     * Broadcast a punishment to staff members
     *
     * @param punishment The punishment to broadcast
     */
    private void broadcastPunishment(Punishment punishment) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", punishment.getTargetName());
        placeholders.put("type", punishment.getType().getDisplayName().toLowerCase());
        placeholders.put("reason", punishment.getReason());
        placeholders.put("punisher", punishment.getPunisherName());
        placeholders.put("id", String.valueOf(punishment.getId()));

        String message = plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("punishment.broadcast");
        String formattedMessage = MessageUtil.formatMessage(message, placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.staff")) {
                player.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * Broadcast a punishment revocation to staff members
     *
     * @param punishment The punishment that was revoked
     * @param revokerName The name of the staff member who revoked the punishment
     */
    private void broadcastRevocation(Punishment punishment, String revokerName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", punishment.getTargetName());
        placeholders.put("type", punishment.getType().getDisplayName().toLowerCase());
        placeholders.put("reason", punishment.getReason());
        placeholders.put("revoker", revokerName);
        placeholders.put("id", String.valueOf(punishment.getId()));

        String message = plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("punishment.revoke-broadcast");
        String formattedMessage = MessageUtil.formatMessage(message, placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.staff")) {
                player.sendMessage(formattedMessage);
            }
        }
    }
}