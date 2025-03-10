package com.judgementday.listener;

import com.judgementday.JudgementDay;
import com.judgementday.model.Punishment;
import com.judgementday.model.PunishmentType;
import com.judgementday.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ChatListener implements Listener {

    private final JudgementDay plugin;

    public ChatListener(JudgementDay plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String message = event.getMessage();

        // Check if awaiting proof link
        if (plugin.getPunishmentManager().isAwaitingProofLink(uuid)) {
            event.setCancelled(true);
            handleProofLink(player, message);
            return;
        }

        // Check if player is muted
        try {
            boolean muted = plugin.getPlayerManager().isPlayerMuted(uuid).get();

            if (muted) {
                event.setCancelled(true);

                // Get active mutes
                List<Punishment> mutes = plugin.getPlayerManager().getActivePlayerPunishmentsByType(uuid, PunishmentType.MUTE).get();

                if (!mutes.isEmpty()) {
                    // Get the most recent mute
                    mutes.sort((m1, m2) -> Long.compare(m2.getTimeIssued(), m1.getTimeIssued()));
                    Punishment mute = mutes.get(0);

                    // Send mute message
                    player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                                    plugin.getConfigManager().getMessage("punishment.muted"),
                            Collections.singletonMap("type", mute.getType().getDisplayName())));
                    player.sendMessage(plugin.getPunishmentManager().getPunishmentMessage(mute));
                } else {
                    player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("punishment.muted-generic"), null));
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            plugin.getLogger().severe("Error checking mute status for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleProofLink(Player player, String link) {
        UUID uuid = player.getUniqueId();

        // Validate link
        if (!plugin.getPlayerManager().isValidProofLink(link)) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("punishment.invalid-proof-link"), null));
            return;
        }

        // Get the target player
        String targetPlayer = plugin.getPunishmentManager().removeAwaitingProofLink(uuid);

        if (targetPlayer == null) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("punishment.error"), null));
            return;
        }

        // Get pending punishment data
        if (!plugin.getPunishmentManager().getPendingPunishment(uuid).containsKey("target") ||
                !plugin.getPunishmentManager().getPendingPunishment(uuid).get("target").equals(targetPlayer)) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("punishment.error"), null));
            return;
        }

        // Complete the punishment process
        UUID targetUuid = plugin.getPlayerManager().getPlayerUuid(targetPlayer);
        if (targetUuid == null) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("punishment.invalid-player"), null));
            return;
        }

        // Get punishment data
        PunishmentType type = (PunishmentType) plugin.getPunishmentManager().getPendingPunishment(uuid).get("type");
        String reason = (String) plugin.getPunishmentManager().getPendingPunishment(uuid).get("reason");

        // Apply punishment
        plugin.getPunishmentManager().punishPlayer(
                        targetUuid, targetPlayer,
                        uuid, player.getName(),
                        type, reason, link)
                .thenAccept(id -> {
                    if (id > 0) {
                        player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                                        plugin.getConfigManager().getMessage("punishment.success"),
                                Collections.singletonMap("id", String.valueOf(id))));
                    } else {
                        player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                                plugin.getConfigManager().getMessage("punishment.error"), null));
                    }
                });

        // Remove pending punishment data
        plugin.getPunishmentManager().removePendingPunishment(uuid);
    }
}