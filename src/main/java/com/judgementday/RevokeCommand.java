package com.judgementday.command;

import com.judgementday.JudgementDay;
import com.judgementday.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class RevokeCommand implements CommandExecutor, TabCompleter {

    private final JudgementDay plugin;

    public RevokeCommand(JudgementDay plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission
        if (!sender.hasPermission("judgementday.revoke")) {
            sender.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("general.no-permission"), null));
            return true;
        }

        // Check arguments
        if (args.length < 1) {
            sender.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("command.revoke.usage"), null));
            return true;
        }

        // Parse punishment ID
        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("command.revoke.invalid-id"), null));
            return true;
        }

        // Get punishment
        try {
            if (plugin.getDataManager().getPunishment(id).get() == null) {
                sender.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("command.revoke.not-found"), null));
                return true;
            }
        } catch (InterruptedException | ExecutionException e) {
            sender.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("general.error"), null));
            return true;
        }

        // Get optional reason
        String reason = "";
        if (args.length > 1) {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        // Get revoker info
        UUID revokerUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        String revokerName = sender.getName();
        if (revokerUuid == null) {
            revokerName = "Console";
        }

        // Revoke punishment
        final String finalReason = reason;
        plugin.getPunishmentManager().revokePunishment(id, revokerUuid, revokerName)
                .thenAccept(success -> {
                    if (success) {
                        // Get punishment details for confirmation message
                        plugin.getDataManager().getPunishment(id).thenAccept(punishment -> {
                            Map<String, String> placeholders = new HashMap<>();
                            placeholders.put("id", String.valueOf(id));
                            placeholders.put("target", punishment.getTargetName());
                            placeholders.put("type", punishment.getType().getDisplayName());

                            sender.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                                    plugin.getConfigManager().getMessage("command.revoke.success"), placeholders));

                            // Log the reason if provided
                            if (!finalReason.isEmpty()) {
                                plugin.getLogger().info(revokerName + " revoked punishment #" + id +
                                        " (" + punishment.getType().getDisplayName() + " for " +
                                        punishment.getTargetName() + ") with reason: " + finalReason);
                            }
                        });
                    } else {
                        sender.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                                plugin.getConfigManager().getMessage("command.revoke.failed"), null));
                    }
                });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("judgementday.revoke")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // In a real implementation, we would provide a list of recent punishment IDs
            // This is simplified as it would require additional methods to get recent punishments
            return Collections.emptyList();
        }

        if (args.length == 2) {
            // Provide common revocation reasons
            String partial = args[1].toLowerCase();
            List<String> reasons = Arrays.asList(
                    "False positive", "Staff error", "Evidence insufficient",
                    "Appeal approved", "Rule change", "Automated expiry"
            );

            return reasons.stream()
                    .filter(reason -> reason.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}