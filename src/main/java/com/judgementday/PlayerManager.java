package com.judgementday.manager;

import com.judgementday.JudgementDay;
import com.judgementday.model.Punishment;
import com.judgementday.model.PunishmentType;
import com.judgementday.util.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerManager {

    private final JudgementDay plugin;
    private final Map<UUID, Map<UUID, Long>> playerCooldowns = new ConcurrentHashMap<>();

    public PlayerManager(JudgementDay plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if a player is banned
     *
     * @param uuid Player UUID
     * @return A future that completes with true if the player is banned, false otherwise
     */
    public CompletableFuture<Boolean> isPlayerBanned(UUID uuid) {
        return plugin.getDataManager().isPlayerBanned(uuid);
    }

    /**
     * Check if a player is muted
     *
     * @param uuid Player UUID
     * @return A future that completes with true if the player is muted, false otherwise
     */
    public CompletableFuture<Boolean> isPlayerMuted(UUID uuid) {
        return plugin.getDataManager().isPlayerMuted(uuid);
    }

    /**
     * Get active punishments for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of active punishments
     */
    public CompletableFuture<List<Punishment>> getActivePlayerPunishments(UUID uuid) {
        return plugin.getDataManager().getActivePlayerPunishments(uuid);
    }

    /**
     * Get active punishments of a specific type for a player
     *
     * @param uuid Player UUID
     * @param type Punishment type
     * @return A future that completes with a list of active punishments
     */
    public CompletableFuture<List<Punishment>> getActivePlayerPunishmentsByType(UUID uuid, PunishmentType type) {
        return plugin.getDataManager().getActivePlayerPunishmentsByType(uuid, type);
    }

    /**
     * Get all punishments for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of all punishments
     */
    public CompletableFuture<List<Punishment>> getPlayerPunishments(UUID uuid) {
        return plugin.getDataManager().getPlayerPunishments(uuid);
    }

    /**
     * Get the ban message for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with the ban message or null if the player is not banned
     */
    public CompletableFuture<String> getBanMessage(UUID uuid) {
        return getActivePlayerPunishmentsByType(uuid, PunishmentType.BAN)
                .thenApply(punishments -> {
                    if (punishments.isEmpty()) {
                        return null;
                    }

                    // Find the most recent ban
                    Punishment ban = punishments.stream()
                            .reduce((p1, p2) -> p1.getTimeIssued() > p2.getTimeIssued() ? p1 : p2)
                            .orElse(null);

                    if (ban == null) {
                        return null;
                    }

                    return plugin.getPunishmentManager().getPunishmentMessage(ban);
                });
    }

    /**
     * Get all active mutes for a player
     *
     * @param uuid Player UUID
     * @return A future that completes with a list of all active mutes
     */
    public CompletableFuture<List<Punishment>> getPlayerMutes(UUID uuid) {
        return getActivePlayerPunishmentsByType(uuid, PunishmentType.MUTE);
    }

    /**
     * Check if a player can report another player (cooldown check)
     *
     * @param reporterUuid Reporter UUID
     * @param reportedUuid Reported player UUID
     * @return true if the player can report, false otherwise
     */
    public boolean canReport(UUID reporterUuid, UUID reportedUuid) {
        Map<UUID, Long> cooldowns = playerCooldowns.get(reporterUuid);
        if (cooldowns == null) {
            return true;
        }

        Long cooldownEnd = cooldowns.get(reportedUuid);
        if (cooldownEnd == null) {
            return true;
        }

        return System.currentTimeMillis() >= cooldownEnd;
    }

    /**
     * Set a report cooldown for a player
     *
     * @param reporterUuid Reporter UUID
     * @param reportedUuid Reported player UUID
     */
    public void setReportCooldown(UUID reporterUuid, UUID reportedUuid) {
        int cooldownSeconds = plugin.getConfigManager().getMainConfig().getInt("reports.cooldown", 300);
        long cooldownEnd = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cooldownSeconds);

        playerCooldowns.computeIfAbsent(reporterUuid, k -> new ConcurrentHashMap<>())
                .put(reportedUuid, cooldownEnd);
    }

    /**
     * Get the remaining cooldown time for a player to report another player
     *
     * @param reporterUuid Reporter UUID
     * @param reportedUuid Reported player UUID
     * @return Remaining cooldown time in seconds, or 0 if no cooldown
     */
    public int getReportCooldown(UUID reporterUuid, UUID reportedUuid) {
        Map<UUID, Long> cooldowns = playerCooldowns.get(reporterUuid);
        if (cooldowns == null) {
            return 0;
        }

        Long cooldownEnd = cooldowns.get(reportedUuid);
        if (cooldownEnd == null) {
            return 0;
        }

        long remaining = cooldownEnd - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) TimeUnit.MILLISECONDS.toSeconds(remaining);
    }

    /**
     * Validate a proof link
     *
     * @param link The proof link to validate
     * @return true if the link is valid, false otherwise
     */
    public boolean isValidProofLink(String link) {
        // If no validation is configured, accept all links
        if (!plugin.getConfigManager().getMainConfig().getBoolean("validation.proof-links.enabled", false)) {
            return true;
        }

        // Check if link is too short
        if (link.length() < 10) {
            return false;
        }

        // Check if link is a valid URL
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            return false;
        }

        // Check allowed domains if configured
        List<String> allowedDomains = plugin.getConfigManager().getMainConfig().getStringList("validation.proof-links.allowed-domains");
        if (!allowedDomains.isEmpty()) {
            boolean validDomain = false;
            for (String domain : allowedDomains) {
                if (link.contains(domain)) {
                    validDomain = true;
                    break;
                }
            }
            if (!validDomain) {
                return false;
            }
        }

        // All checks passed
        return true;
    }

    /**
     * Get a player UUID by name
     *
     * @param name Player name
     * @return Player UUID or null if not found
     */
    public UUID getPlayerUuid(String name) {
        // First check online players
        Player player = Bukkit.getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }

        // Then check offline players (this can be slow)
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Bukkit.getOfflinePlayer(name).getUniqueId();
            } catch (Exception e) {
                LogUtil.warning("Failed to get UUID for player: " + name);
                return null;
            }
        }).join();
    }

    /**
     * Clear expired cooldowns
     */
    public void clearExpiredCooldowns() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Map<UUID, Long>> entry : playerCooldowns.entrySet()) {
            entry.getValue().entrySet().removeIf(cooldown -> cooldown.getValue() <= now);

            // Remove empty maps
            if (entry.getValue().isEmpty()) {
                playerCooldowns.remove(entry.getKey());
            }
        }
    }
}