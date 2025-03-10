package com.judgementday.command;

import com.judgementday.JudgementDay;
import com.judgementday.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final JudgementDay plugin;

    public ReportCommand(JudgementDay plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("general.player-only"), null));
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("judgementday.report")) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("general.no-permission"), null));
            return true;
        }

        // Check arguments
        if (args.length < 2) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("command.report.usage"), null));
            return true;
        }

        String targetName = args[0];

        // Check if trying to report self
        if (player.getName().equalsIgnoreCase(targetName)) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("command.report.self"), null));
            return true;
        }

        // Get target UUID
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUuid;

        if (target != null) {
            targetName = target.getName(); // Get correct capitalization
            targetUuid = target.getUniqueId();

            // Check if target is exempt
            if (target.hasPermission("judgementday.exempt.report")) {
                player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("command.report.exempt"), null));
                return true;
            }
        } else {
            // Check if player exists
            targetUuid = plugin.getPlayerManager().getPlayerUuid(targetName);
            if (targetUuid == null) {
                player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("general.player-not-found"), null));
                return true;
            }
        }

        // Check cooldown
        if (!plugin.getPlayerManager().canReport(player.getUniqueId(), targetUuid)) {
            int cooldown = plugin.getPlayerManager().getReportCooldown(player.getUniqueId(), targetUuid);
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("command.report.cooldown"),
                    Collections.singletonMap("time", formatTime(cooldown))));
            return true;
        }

        // Get reason
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // Validate reason length
        int minLength = plugin.getConfigManager().getMainConfig().getInt("reports.min-reason-length", 3);
        int maxLength = plugin.getConfigManager().getMainConfig().getInt("reports.max-reason-length", 100);

        if (reason.length() < minLength) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("command.report.reason-too-short"),
                    Collections.singletonMap("min", String.valueOf(minLength))));
            return true;
        }

        if (reason.length() > maxLength) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("command.report.reason-too-long"),
                    Collections.singletonMap("max", String.valueOf(maxLength))));
            return true;
        }

        // Create report
        plugin.getReportManager().createReport(
                player.getUniqueId(), player.getName(),
                targetUuid, targetName,
                reason
        ).thenAccept(reportId -> {
            if (reportId > 0) {
                // Report created successfully
                player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                                plugin.getConfigManager().getMessage("command.report.success"),
                        Collections.singletonMap("player", targetName)));
            } else {
                // Failed to create report
                player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("command.report.failed"), null));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;

        if (!player.hasPermission("judgementday.report")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player)) // Can't report self
                    .filter(p -> p.getName().toLowerCase().startsWith(partial))
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Provide common report reasons
            String partial = args[1].toLowerCase();
            List<String> reasons = Arrays.asList(
                    "Hacking", "Cheating", "Spamming", "Harassment",
                    "Inappropriate language", "Threats", "Advertising"
            );

            return reasons.stream()
                    .filter(reason -> reason.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * Format time in seconds to a human-readable string
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }

        if (seconds < 3600) {
            int minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }

        int hours = seconds / 3600;
        return hours + " hour" + (hours == 1 ? "" : "s");
    }
}