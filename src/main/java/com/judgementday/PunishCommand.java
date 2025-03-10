package com.judgementday.command;

import com.judgementday.JudgementDay;
import com.judgementday.model.PunishmentType;
import com.judgementday.ui.PunishmentGUI;
import com.judgementday.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PunishCommand implements CommandExecutor, TabCompleter {

    private final JudgementDay plugin;
    private final PunishmentType defaultType;

    /**
     * Constructor for general punish command that opens GUI
     *
     * @param plugin Plugin instance
     */
    public PunishCommand(JudgementDay plugin) {
        this.plugin = plugin;
        this.defaultType = null;
    }

    /**
     * Constructor for specific punishment type command
     *
     * @param plugin Plugin instance
     * @param type Type of punishment
     */
    public PunishCommand(JudgementDay plugin, String type) {
        this.plugin = plugin;
        this.defaultType = PunishmentType.fromString(type);
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
        if (defaultType != null) {
            if (!player.hasPermission("judgementday." + defaultType.name().toLowerCase())) {
                player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("general.no-permission"), null));
                return true;
            }
        } else {
            if (!player.hasPermission("judgementday.punish")) {
                player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("general.no-permission"), null));
                return true;
            }
        }

        // Check arguments
        if (args.length < 1) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("command.punish.usage"), null));
            return true;
        }

        String targetName = args[0];

        // Check if target is online
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            // Check if player exists
            if (plugin.getPlayerManager().getPlayerUuid(targetName) == null) {
                player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("general.player-not-found"), null));
                return true;
            }
        } else {
            targetName = target.getName(); // Get correct capitalization
        }

        // Check if trying to punish self
        if (target != null && target.equals(player) && !player.hasPermission("judgementday.punish.self")) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("command.punish.self"), null));
            return true;
        }

        // Check if target is exempt
        if (target != null && target.hasPermission("judgementday.exempt") && !player.hasPermission("judgementday.punish.exempt")) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("command.punish.exempt"), null));
            return true;
        }

        // Handle different command formats
        if (defaultType == null) {
            // General punish command - open GUI
            PunishmentGUI.openTypeSelector(plugin, player, targetName);
        } else {
            // Specific punishment type command
            if (args.length < 2) {
                // No reason specified - open reason selector GUI
                Map<String, Object> pendingPunishment = new HashMap<>();
                pendingPunishment.put("target", targetName);
                pendingPunishment.put("type", defaultType);
                plugin.getPunishmentManager().addPendingPunishment(player.getUniqueId(), pendingPunishment);

                PunishmentGUI.openReasonSelector(plugin, player, defaultType);
            } else {
                // Reason specified - direct punishment
                String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

                // Check if reason is valid
                List<String> validReasons = plugin.getConfigManager().getPunishmentReasons(defaultType);
                if (!validReasons.contains(reason) && !player.hasPermission("judgementday.reason.custom")) {
                    player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("command.punish.invalid-reason"), null));
                    return true;
                }

                // Set up pending punishment
                Map<String, Object> pendingPunishment = new HashMap<>();
                pendingPunishment.put("target", targetName);
                pendingPunishment.put("type", defaultType);
                pendingPunishment.put("reason", reason);
                plugin.getPunishmentManager().addPendingPunishment(player.getUniqueId(), pendingPunishment);

                // Request proof link
                player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("punishment.request-proof"), null));

                // Mark player as awaiting proof link
                plugin.getPunishmentManager().addAwaitingProofLink(player.getUniqueId(), targetName);
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;

        // First argument - player name
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getName().toLowerCase().startsWith(partial))
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }

        // Second argument - reason (only for specific punishment types)
        if (args.length == 2 && defaultType != null) {
            String partial = args[1].toLowerCase();
            return plugin.getConfigManager().getPunishmentReasons(defaultType).stream()
                    .filter(reason -> reason.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}