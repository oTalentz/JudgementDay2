package com.judgementday;

import com.judgementday.model.Report;
import com.judgementday.util.ItemBuilder;
import com.judgementday.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ReportGUI {

    private static final String REPORTS_TITLE = "§c§lPending Reports";
    private static final int PAGE_SIZE = 45; // Number of items per page

    /**
     * Open the reports GUI
     *
     * @param plugin Plugin instance
     * @param staff Staff member viewing the reports
     * @param page Page number (starting from 1)
     */
    public static void openReportsGUI(JudgementDay plugin, Player staff, int page) {
        // Get reports asynchronously
        plugin.getReportManager().getUnprocessedReports().thenAccept(reports -> {
            // Handle on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (reports.isEmpty()) {
                    staff.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("report.no-reports"), null));
                    return;
                }

                // Calculate number of pages
                int totalPages = (reports.size() + PAGE_SIZE - 1) / PAGE_SIZE;
                int actualPage = Math.min(Math.max(1, page), totalPages);

                // Create inventory
                Inventory inventory = Bukkit.createInventory(null, 54,
                        REPORTS_TITLE + " (" + actualPage + "/" + totalPages + ")");

                // Calculate start and end index for current page
                int startIndex = (actualPage - 1) * PAGE_SIZE;
                int endIndex = Math.min(startIndex + PAGE_SIZE, reports.size());

                // Add reports to inventory
                for (int i = startIndex; i < endIndex; i++) {
                    Report report = reports.get(i);

                    // Create item
                    ItemBuilder builder = new ItemBuilder(Material.BOOK_AND_QUILL)
                            .name(ChatColor.RED + report.getReportedName() +
                                    ChatColor.GRAY + " (ID: " + report.getId() + ")")
                            .lore(
                                    ChatColor.GOLD + "Reported by: " + ChatColor.WHITE + report.getReporterName(),
                                    ChatColor.GOLD + "Reason: " + ChatColor.WHITE + report.getReason(),
                                    ChatColor.GOLD + "Date: " + ChatColor.WHITE +
                                            MessageUtil.formatDate(report.getTimeCreated()),
                                    "",
                                    ChatColor.GREEN + "Click to process this report"
                            );

                    inventory.setItem(i - startIndex, builder.build());
                }

                // Add navigation buttons if multiple pages
                if (totalPages > 1) {
                    // Previous page button
                    if (actualPage > 1) {
                        ItemStack prevButton = new ItemBuilder(Material.ARROW)
                                .name(ChatColor.GREEN + "Previous Page")
                                .lore(ChatColor.GRAY + "Go to page " + (actualPage - 1))
                                .build();
                        inventory.setItem(45, prevButton);
                    }

                    // Next page button
                    if (actualPage < totalPages) {
                        ItemStack nextButton = new ItemBuilder(Material.ARROW)
                                .name(ChatColor.GREEN + "Next Page")
                                .lore(ChatColor.GRAY + "Go to page " + (actualPage + 1))
                                .build();
                        inventory.setItem(53, nextButton);
                    }
                }

                // Add information item
                ItemStack infoItem = new ItemBuilder(Material.PAPER)
                        .name(ChatColor.YELLOW + "Pending Reports")
                        .lore(
                                ChatColor.GRAY + "Total reports: " + reports.size(),
                                "",
                                ChatColor.GRAY + "Click on a report to process it"
                        )
                        .build();
                inventory.setItem(49, infoItem);

                // Open inventory
                staff.openInventory(inventory);
            });
        });
    }

    /**
     * Process a click in the reports GUI
     *
     * @param plugin Plugin instance
     * @param staff Staff member
     * @param event Click event
     * @return true if handled, false otherwise
     */
    public static boolean processClick(JudgementDay plugin, Player staff, InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.startsWith(REPORTS_TITLE)) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return true;
            }

            // Parse page info from title
            String pageInfo = title.substring(title.lastIndexOf("(") + 1, title.lastIndexOf(")"));
            String[] pageParts = pageInfo.split("/");
            int currentPage = Integer.parseInt(pageParts[0]);

            // Check if clicked a navigation button
            String displayName = clicked.getItemMeta().getDisplayName();

            if (displayName.equals(ChatColor.GREEN + "Previous Page")) {
                openReportsGUI(plugin, staff, currentPage - 1);
                return true;
            } else if (displayName.equals(ChatColor.GREEN + "Next Page")) {
                openReportsGUI(plugin, staff, currentPage + 1);
                return true;
            } else if (displayName.equals(ChatColor.YELLOW + "Pending Reports")) {
                return true; // Info item, do nothing
            }

            // Check if clicked a report
            if (clicked.getType() == Material.BOOK_AND_QUILL && clicked.getItemMeta().hasDisplayName()) {
                // Extract report ID
                String nameWithId = clicked.getItemMeta().getDisplayName();
                int idIndex = nameWithId.lastIndexOf("ID: ") + 4;
                int endIndex = nameWithId.lastIndexOf(")");

                if (idIndex != -1 && endIndex != -1) {
                    try {
                        int reportId = Integer.parseInt(nameWithId.substring(idIndex, endIndex));

                        // Get report info
                        plugin.getReportManager().getReport(reportId).thenAccept(report -> {
                            if (report != null && !report.isProcessed()) {
                                // Extract reported player name
                                String reportedName = report.getReportedName();

                                // Close inventory
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    staff.closeInventory();

                                    // Setup a punishment for the reported player
                                    Map<String, Object> pendingPunishment = new HashMap<>();
                                    pendingPunishment.put("target", reportedName);
                                    pendingPunishment.put("reportId", reportId);
                                    plugin.getPunishmentManager().addPendingPunishment(staff.getUniqueId(), pendingPunishment);

                                    // Open punishment type selector
                                    PunishmentGUI.openTypeSelector(plugin, staff, reportedName);
                                });
                            } else {
                                // Report not found or already processed
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    staff.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                                            plugin.getConfigManager().getMessage("report.already-processed"), null));
                                    // Refresh GUI
                                    openReportsGUI(plugin, staff, currentPage);
                                });
                            }
                        });
                    } catch (NumberFormatException ignored) {
                        // Invalid ID format, ignore
                    }
                }

                return true;
            }

            return true;
        }

        return false;
    }
}