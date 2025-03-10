package com.judgementday.listener;

import com.judgementday.JudgementDay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class LoginListener implements Listener {

    private final JudgementDay plugin;

    public LoginListener(JudgementDay plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        try {
            // Check if player is banned
            boolean banned = plugin.getPlayerManager().isPlayerBanned(uuid).get();

            if (banned) {
                // Get ban message
                String banMessage = plugin.getPlayerManager().getBanMessage(uuid).get();

                if (banMessage != null) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage);
                } else {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "You are banned from this server.");
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            // If there's an error, allow the player to join but log the error
            plugin.getLogger().severe("Error checking ban status for " + name + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        // This event fires if pre-login didn't disallow
        // This is a fallback check in case something went wrong with async check
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            try {
                boolean banned = plugin.getPlayerManager().isPlayerBanned(uuid).get();

                if (banned) {
                    String banMessage = plugin.getPlayerManager().getBanMessage(uuid).get();

                    if (banMessage != null) {
                        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, banMessage);
                    } else {
                        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, "You are banned from this server.");
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                // If there's an error, allow the player to join but log the error
                plugin.getLogger().severe("Error checking ban status for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Notify player about active mutes
        plugin.getPlayerManager().getActivePlayerPunishmentsByType(uuid, com.judgementday.model.PunishmentType.MUTE)
                .thenAccept(mutes -> {
                    if (!mutes.isEmpty()) {
                        // Get the most recent mute
                        mutes.sort((m1, m2) -> Long.compare(m2.getTimeIssued(), m1.getTimeIssued()));
                        com.judgementday.model.Punishment mute = mutes.get(0);

                        // Send notification to player
                        player.sendMessage(plugin.getConfigManager().getPrefix() +
                                "You are currently muted. Type /history to see details.");
                    }
                });

        // Notify player about pending appeals
        if (player.hasPermission("judgementday.appeals")) {
            plugin.getAppealManager().getPendingAppeals()
                    .thenAccept(appeals -> {
                        if (!appeals.isEmpty()) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() +
                                    "There are " + appeals.size() + " pending appeals. Type /appeals to review them.");
                        }
                    });
        }

        // Notify staff about pending reports
        if (player.hasPermission("judgementday.staff")) {
            plugin.getReportManager().getUnprocessedReports()
                    .thenAccept(reports -> {
                        if (!reports.isEmpty()) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() +
                                    "There are " + reports.size() + " unprocessed reports. Type /reports to review them.");
                        }
                    });
        }
    }
}