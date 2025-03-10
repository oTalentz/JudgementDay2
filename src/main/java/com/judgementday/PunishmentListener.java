package com.judgementday.listener;

import com.judgementday.JudgementDay;
import com.judgementday.event.PunishmentEvent;
import com.judgementday.event.PunishmentRevokeEvent;
import com.judgementday.manager.PunishmentManager;
import com.judgementday.model.Punishment;
import com.judgementday.model.PunishmentType;
import com.judgementday.ui.HistoryGUI;
import com.judgementday.ui.PunishmentGUI;
import com.judgementday.ui.ReportGUI;
import com.judgementday.util.LogUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;

public class PunishmentListener implements Listener {

    private final JudgementDay plugin;

    public PunishmentListener(JudgementDay plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPunishment(PunishmentEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Punishment punishment = event.getPunishment();

        // Log the punishment
        LogUtil.logPunishment(
                "Punishment issued",
                punishment.getTargetName(),
                punishment.getType().getDisplayName(),
                punishment.getReason(),
                punishment.getPunisherName(),
                punishment.getId()
        );

        // Discord integration if enabled
        if (plugin.getConfigManager().getMainConfig().getBoolean("discord.enabled", false)) {
            sendDiscordNotification(punishment, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPunishmentRevoke(PunishmentRevokeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Punishment punishment = event.getPunishment();
        String revokerName = event.getRevokerName();

        // Log the revocation
        LogUtil.logPunishment(
                "Punishment revoked",
                punishment.getTargetName(),
                punishment.getType().getDisplayName(),
                punishment.getReason(),
                revokerName,
                punishment.getId()
        );

        // Discord integration if enabled
        if (plugin.getConfigManager().getMainConfig().getBoolean("discord.enabled", false)) {
            sendDiscordNotification(punishment, true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getClickedInventory();

        if (inventory == null) {
            return;
        }

        // Process GUI clicks
        if (PunishmentGUI.processClick(plugin, player, event)) {
            event.setCancelled(true);
            return;
        }

        if (HistoryGUI.processClick(plugin, player, event)) {
            event.setCancelled(true);
            return;
        }

        if (ReportGUI.processClick(plugin, player, event)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // Clean up pending punishments if the player closes GUI
        if (event.getInventory().getType() == InventoryType.CHEST) {
            String title = event.getView().getTitle();
            if (title.contains("Punish") || title.contains("Reasons")) {
                // Check if there's an ongoing punishment process
                PunishmentManager punishmentManager = plugin.getPunishmentManager();
                Map<String, Object> pendingPunishment = punishmentManager.getPendingPunishment(player.getUniqueId());

                if (pendingPunishment != null && !punishmentManager.isAwaitingProofLink(player.getUniqueId())) {
                    // If the player closed the inventory without selecting a proof link,
                    // clean up the pending punishment
                    punishmentManager.removePendingPunishment(player.getUniqueId());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Clean up pending punishments
        plugin.getPunishmentManager().removePendingPunishment(uuid);

        // Clean up awaiting proof links
        if (plugin.getPunishmentManager().isAwaitingProofLink(uuid)) {
            plugin.getPunishmentManager().removeAwaitingProofLink(uuid);
        }
    }

    private void sendDiscordNotification(Punishment punishment, boolean revoked) {
        // This would be implemented with a Discord webhook
        // For now, just log that we would send a notification
        if (revoked) {
            LogUtil.info("Would send Discord notification: Punishment revoked - " +
                    punishment.getType().getDisplayName() + " for " + punishment.getTargetName());
        } else {
            LogUtil.info("Would send Discord notification: Punishment issued - " +
                    punishment.getType().getDisplayName() + " for " + punishment.getTargetName());
        }
    }
}