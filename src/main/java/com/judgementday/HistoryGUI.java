package com.judgementday.ui;

import com.judgementday.JudgementDay;
import com.judgementday.model.Punishment;
import com.judgementday.model.PunishmentType;
import com.judgementday.util.ItemBuilder;
import com.judgementday.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HistoryGUI {

    private static final String HISTORY_TITLE_PREFIX = "§c§lHistory: ";
    private static final int PAGE_SIZE = 45; // Number of items per page

    /**
     * Open the punishment history GUI for a player
     *
     * @param plugin Plugin instance
     * @param viewer Staff member viewing the history
     * @param targetName Target player name
     * @param page Page number (starting from 1)
     */
    public static void openHistoryGUI(JudgementDay plugin, Player viewer, String targetName, int page) {
        UUID targetUuid = plugin.getPlayerManager().getPlayerUuid(targetName);
        if (targetUuid == null) {
            viewer.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("history.player-not-found"), null));
            return;
        }

        // Get punishments asynchronously
        plugin.getPlayerManager().getPlayerPunishments(targetUuid).thenAccept(punishments -> {
            // Handle on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (punishments.isEmpty()) {
                    viewer.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("history.no-punishments"), null));
                    return;
                }

                // Sort punishments (newest first)
                punishments.sort((p1, p2) -> Long.compare(p2.getTimeIssued(), p1.getTimeIssued()));

                // Calculate number of pages
                int totalPages = (punishments.size() + PAGE_SIZE - 1) / PAGE_SIZE;
                int actualPage = Math.min(Math.max(1, page), totalPages);

                // Create inventory
                Inventory inventory = Bukkit.createInventory(null, 54,
                        HISTORY_TITLE_PREFIX + targetName + " (" + actualPage + "/" + totalPages + ")");

                // Calculate start and end index for current page
                int startIndex = (actualPage - 1) * PAGE_SIZE;
                int endIndex = Math.min(startIndex + PAGE_SIZE, punishments.size());

                // Add punishments to inventory
                for (int i = startIndex; i < endIndex; i++) {
                    Punishment punishment = punishments.get(i);

                    // Choose material based on type
                    Material material;
                    ChatColor typeColor;

                    switch (punishment.getType()) {
                        case WARN:
                            material = Material.PAPER;
                            typeColor = ChatColor.YELLOW;
                            break;
                        case MUTE:
                            material = Material.BOOK;
                            typeColor = ChatColor.GOLD;
                            break;
                        case KICK:
                            material = Material.LEATHER_BOOTS;
                            typeColor = ChatColor.LIGHT_PURPLE;
                            break;
                        case BAN:
                            material = Material.BARRIER;
                            typeColor = ChatColor.RED;
                            break;
                        default:
                            material = Material.STONE;
                            typeColor = ChatColor.WHITE;
                    }

                    // Create item
                    ItemBuilder builder = new ItemBuilder(material)
                            .name(typeColor + punishment.getType().getDisplayName() +
                                    ChatColor.GRAY + " #" + punishment.getId());

                    // Build lore
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GOLD + "Reason: " + ChatColor.WHITE + punishment.getReason());
                    lore.add(ChatColor.GOLD + "Staff: " + ChatColor.WHITE + punishment.getPunisherName());
                    lore.add(ChatColor.GOLD + "Date: " + ChatColor.WHITE +
                            MessageUtil.formatDate(punishment.getTimeIssued()));

                    // Duration
                    if (punishment.getDuration() == -1) {
                        lore.add(ChatColor.GOLD + "Duration: " + ChatColor.WHITE + "Permanent");
                    } else {
                        lore.add(ChatColor.GOLD + "Duration: " + ChatColor.WHITE +
                                MessageUtil.formatDuration(punishment.getDuration()));
                    }

                    // Revocation info
                    if (!punishment.isActive()) {
                        if (punishment.getRevokerName() != null) {
                            lore.add(" ");
                            lore.add(ChatColor.RED + "REVOKED");
                            lore.add(ChatColor.GOLD + "Revoked by: " + ChatColor.WHITE +
                                    punishment.getRevokerName());
                            lore.add(ChatColor.GOLD + "Date: " + ChatColor.WHITE +
                                    MessageUtil.formatDate(punishment.getTimeRevoked()));
                        } else if (punishment.isExpired()) {
                            lore.add(" ");
                            lore.add(ChatColor.RED + "EXPIRED");
                        }
                    } else if (punishment.getExpiry() != -1) {
                        lore.add(ChatColor.GOLD + "Expires: " + ChatColor.WHITE +
                                MessageUtil.formatDate(punishment.getExpiry()));
                        lore.add(ChatColor.GOLD + "Time remaining: " + ChatColor.WHITE +
                                MessageUtil.formatTimeRemaining(punishment.getExpiry()));
                    }

                    // Proof link
                    lore.add(" ");
                    lore.add(ChatColor.GOLD + "Proof: " + ChatColor.AQUA +
                            MessageUtil.truncate(punishment.getProofLink(), 40));

                    // Set lore and add to inventory
                    builder.lore(lore);
                    inventory.setItem(i - startIndex, builder.build());
                }

                // Add player head at the bottom
                ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
                skullMeta.setOwner(targetName);
                skullMeta.setDisplayName(ChatColor.RED + targetName + "'s History");
                skull.setItemMeta(skullMeta);
                inventory.setItem(49, skull);

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

                // Open inventory
                viewer.openInventory(inventory);
            });
        });
    }

    /**
     * Process a click in the history GUI
     *
     * @param plugin Plugin instance
     * @param player Staff member
     * @param event Click event
     * @return true if handled, false otherwise
     */
    public static boolean processClick(JudgementDay plugin, Player player, InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.startsWith(HISTORY_TITLE_PREFIX)) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return true;
            }

            // Parse page info from title
            String pageInfo = title.substring(title.lastIndexOf("(") + 1, title.lastIndexOf(")"));
            String[] pageParts = pageInfo.split("/");
            int currentPage = Integer.parseInt(pageParts[0]);

            // Parse player name from title
            String targetName = title.substring(HISTORY_TITLE_PREFIX.length(), title.lastIndexOf("(")).trim();

            // Check if clicked a navigation button
            String displayName = clicked.getItemMeta().getDisplayName();

            if (displayName.equals(ChatColor.GREEN + "Previous Page")) {
                openHistoryGUI(plugin, player, targetName, currentPage - 1);
                return true;
            } else if (displayName.equals(ChatColor.GREEN + "Next Page")) {
                openHistoryGUI(plugin, player, targetName, currentPage + 1);
                return true;
            }

            // Check if clicked a punishment
            if (clicked.getItemMeta().hasDisplayName() && clicked.getItemMeta().hasLore()) {
                // Display detailed information
                displayPunishmentDetails(player, clicked);
                return true;
            }

            return true;
        }

        return false;
    }

    /**
     * Display detailed information about a punishment
     *
     * @param player Staff member
     * @param item Punishment item
     */
    private static void displayPunishmentDetails(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName() || !meta.hasLore()) {
            return;
        }

        // Get punishment ID from display name
        String displayName = meta.getDisplayName();
        int idIndex = displayName.lastIndexOf("#");
        if (idIndex == -1) {
            return;
        }

        try {
            int id = Integer.parseInt(displayName.substring(idIndex + 1));

            // Send detailed information to player
            player.sendMessage(ChatColor.GRAY + "=== " + displayName + ChatColor.GRAY + " ===");
            meta.getLore().forEach(player::sendMessage);

            // Add option to revoke if active
            if (!meta.getLore().contains(ChatColor.RED + "REVOKED") &&
                    !meta.getLore().contains(ChatColor.RED + "EXPIRED") &&
                    player.hasPermission("judgementday.revoke")) {
                player.sendMessage(" ");
                player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/revoke " + id +
                        ChatColor.GRAY + " to revoke this punishment.");
            }
        } catch (NumberFormatException ignored) {
            // Not a valid ID
        }
    }
}