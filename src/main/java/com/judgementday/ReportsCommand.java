package com.judgementday.command;

import com.judgementday.JudgementDay;
import com.judgementday.ReportGUI;
import com.judgementday.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class ReportsCommand implements CommandExecutor, TabCompleter {

    private final JudgementDay plugin;

    public ReportsCommand(JudgementDay plugin) {
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
        if (!player.hasPermission("judgementday.reports")) {
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("general.no-permission"), null));
            return true;
        }

        // Parse page number if provided
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    page = 1;
                }
            } catch (NumberFormatException ignored) {
                // Invalid page number, use default
            }
        }

        // Open reports GUI
        ReportGUI.openReportsGUI(plugin, player, page);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // No tab completion needed for this command
        return Collections.emptyList();
    }
}