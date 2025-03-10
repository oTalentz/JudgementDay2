package com.judgementday.ui;

import com.judgementday.JudgementDay;
import com.judgementday.PunishmentType;
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
import java.util.List;
import java.util.Map;

public class PunishmentGUI {

    // Static constants for GUI titles
    private static final String TYPE_SELECTOR_TITLE = "§c§lPunishment Type";
    private static final String REASON_SELECTOR_TITLE_PREFIX = "§c§lSelect Reason: ";

    /**
     * Open the punishment type selection GUI
     *
     * @param plugin Plugin instance
     * @param player Staff member
     * @param targetName Target player name
     */
    public static void openTypeSelector(JudgementDay plugin, Player player, String targetName) {
        // Setup pending punishment
        Map<String, Object> pendingPunishment = new HashMap<>();
        pendingPunishment.put("target", targetName);
        plugin.getPunishmentManager().addPendingPunishment(player.getUniqueId(), pendingPunishment);

        // Create inventory
        Inventory inventory = Bukkit.createInventory(null, 27, TYPE_SELECTOR_TITLE);

        // Create type buttons
        ItemStack warnButton = new ItemBuilder(Material.PAPER)
                .name(ChatColor.YELLOW + "Warning")
                .lore(
                        ChatColor.GRAY + "Click to warn " + ChatColor.RED + targetName,
                        ChatColor.GRAY + "For minor infractions"
                )
                .build();

        ItemStack muteButton = new ItemBuilder(Material.BOOK)
                .name(ChatColor.GOLD + "Mute")
                .lore(
                        ChatColor.GRAY + "Click to mute " + ChatColor.RED + targetName,
                        ChatColor.GRAY + "Prevents player from chatting"
                )
                .build();

        ItemStack kickButton = new ItemBuilder(Material.LEATHER_BOOTS)
                .name(ChatColor.LIGHT_PURPLE + "Kick")
                .lore(
                        ChatColor.GRAY + "Click to kick " + ChatColor.RED + targetName,
                        ChatColor.GRAY + "Disconnects player from server"
                )
                .build();

        ItemStack banButton = new ItemBuilder(Material.BARRIER)
                .name(ChatColor.RED + "Ban")
                .lore(
                        ChatColor.GRAY + "Click to ban " + ChatColor.RED + targetName,
                        ChatColor.GRAY + "Prevents player from connecting"
                )
                .build();

        ItemStack targetInfo = new ItemBuilder(Material.SKULL_ITEM, 1, (short) 3)
                .name(ChatColor.RED + "Target: " + ChatColor.WHITE + targetName)
                .skullOwner(targetName)
                .build();

        // Place items in inventory
        inventory.setItem(10, warnButton);
        inventory.setItem(12, muteButton);
        inventory.setItem(14, kickButton);
        inventory.setItem(16, banButton);
        inventory.setItem(4, targetInfo);

        // Create borders
        for (int i = 0; i < 27; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 7)
                        .name(" ")
                        .build());
            }
        }

        // Open inventory
        player.openInventory(inventory);
    }

    /**
     * Open the punishment reason selection GUI
     *
     * @param plugin Plugin instance
     * @param player Staff member
     * @param type Punishment type
     */
    public static void openReasonSelector(JudgementDay plugin, Player player, PunishmentType type) {
        // Get pending punishment
        Map<String, Object> pendingPunishment = plugin.getPunishmentManager().getPendingPunishment(player.getUniqueId());
        if (pendingPunishment == null) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + "Error: No pending punishment.");
            return;
        }

        // Update pending punishment with type
        pendingPunishment.put("type", type);

        // Get target name
        String targetName = (String) pendingPunishment.get("target");

        // Get reasons for this type
        List<String> reasons = plugin.getConfigManager().getPunishmentReasons(type);

        // Calculate inventory size
        int size = Math.min(54, ((reasons.size() + 8) / 9) * 9);

        // Create inventory
        Inventory inventory = Bukkit.createInventory(null, size,
                REASON_SELECTOR_TITLE_PREFIX + type.getDisplayName());

        // Get item for the punishment type
        Material material;
        ChatColor color;

        switch (type) {
            case WARN:
                material = Material.PAPER;
                color = ChatColor.YELLOW;
                break;
            case MUTE:
                material = Material.BOOK;
                color = ChatColor.GOLD;
                break;
            case KICK:
                material = Material.LEATHER_BOOTS;
                color = ChatColor.LIGHT_PURPLE;
                break;
            case BAN:
                material = Material.BARRIER;
                color = ChatColor.RED;
                break;
            default:
                material = Material.STONE;
                color = ChatColor.WHITE;
        }

        // Create reason buttons
        for (int i = 0; i < reasons.size(); i++) {
            String reason = reasons.get(i);

            // Asynchronously get the punishment level
            plugin.getDataManager().getPunishmentLevel(plugin.getPlayerManager().getPlayerUuid(targetName), type, reason)
                    .thenAccept(level -> {
                        // Update on main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // Calculate duration
                            long duration = plugin.getConfigManager().getPunishmentDuration(type, reason, level);
                            String durationStr = duration == -1 ? "Permanent" : MessageUtil.formatDuration(duration);

                            // Create item
                            String levelSuffix = level <= 3 ? " (" + level + ordinalSuffix(level) + ")" : " (3rd+)";
                            ItemStack reasonItem = new ItemBuilder(material)
                                    .name(color + reason + ChatColor.GRAY + levelSuffix)
                                    .lore(
                                            ChatColor.GRAY + "Click to select this reason",
                                            ChatColor.GOLD + "Duration: " + ChatColor.WHITE + durationStr
                                    )
                                    .build();

                            // Add to inventory if player still has it open
                            if (player.getOpenInventory().getTitle().equals(REASON_SELECTOR_TITLE_PREFIX + type.getDisplayName())) {
                                player.getOpenInventory().getTopInventory().setItem(i, reasonItem);
                            }
                        });
                    });
        }

        // Create target info
        ItemStack targetInfo = new ItemBuilder(Material.SKULL_ITEM, 1, (short) 3)
                .name(ChatColor.RED + "Target: " + ChatColor.WHITE + targetName)
                .skullOwner(targetName)
                .build();

        // Place target info at first empty slot after reasons
        inventory.setItem(reasons.size(), targetInfo);

        // Create back button
        ItemStack backButton = new ItemBuilder(Material.ARROW)
                .name(ChatColor.RED + "Back")
                .lore(ChatColor.GRAY + "Return to type selection")
                .build();

        // Place back button at last slot
        inventory.setItem(size - 1, backButton);

        // Open inventory
        player.openInventory(inventory);
    }

    /**
     * Process a click in the punishment GUI
     *
     * @param plugin Plugin instance
     * @param player Staff member
     * @param event Click event
     * @return true if handled, false otherwise
     */
    public static boolean processClick(JudgementDay plugin, Player player, InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Type selector
        if (title.equals(TYPE_SELECTOR_TITLE)) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.STAINED_GLASS_PANE) {
                return true;
            }

            // Check item name to determine action
            String displayName = clicked.getItemMeta().getDisplayName();

            if (displayName.contains("Warning")) {
                openReasonSelector(plugin, player, PunishmentType.WARN);
            } else if (displayName.contains("Mute")) {
                openReasonSelector(plugin, player, PunishmentType.MUTE);
            } else if (displayName.contains("Kick")) {
                openReasonSelector(plugin, player, PunishmentType.KICK);
            } else if (displayName.contains("Ban")) {
                openReasonSelector(plugin, player, PunishmentType.BAN);
            }

            return true;
        }

        // Reason selector
        if (title.startsWith(REASON_SELECTOR_TITLE_PREFIX)) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return true;
            }

            // Get punishment type from title
            PunishmentType type = null;
            String typeStr = title.substring(REASON_SELECTOR_TITLE_PREFIX.length());
            for (PunishmentType t : PunishmentType.values()) {
                if (t.getDisplayName().equals(typeStr)) {
                    type = t;
                    break;
                }
            }

            if (type == null) {
                return true;
            }

            // Check clicked item
            String displayName = clicked.getItemMeta().getDisplayName();

            if (displayName.equals(ChatColor.RED + "Back")) {
                // Back button
                Map<String, Object> pendingPunishment = plugin.getPunishmentManager().getPendingPunishment(player.getUniqueId());
                String targetName = (String) pendingPunishment.get("target");

                // Go back to type selector
                openTypeSelector(plugin, player, targetName);
                return true;
            }

            if (clicked.getType() == Material.SKULL_ITEM) {
                return true; // Target info, ignore
            }

            // Clicked a reason
            Map<String, Object> pendingPunishment = plugin.getPunishmentManager().getPendingPunishment(player.getUniqueId());

            String reason = ChatColor.stripColor(displayName);
            // Remove level suffix
            if (reason.contains("(")) {
                reason = reason.substring(0, reason.lastIndexOf("(")).trim();
            }

            // Update pending punishment
            pendingPunishment.put("reason", reason);

            // Close inventory
            player.closeInventory();

            // Request proof link
            player.sendMessage(MessageUtil.formatMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("punishment.request-proof"), null));

            // Mark player as awaiting proof link
            plugin.getPunishmentManager().addAwaitingProofLink(player.getUniqueId(),
                    (String) pendingPunishment.get("target"));

            return true;
        }

        return false;
    }

    /**
     * Get ordinal suffix for a number (1st, 2nd, 3rd, etc.)
     *
     * @param n Number
     * @return Ordinal suffix
     */
    private static String ordinalSuffix(int n) {
        if (n >= 11 && n <= 13) {
            return "th";
        }

        switch (n % 10) {
            case 1:  return "st";
            case 2:  return "nd";
            case 3:  return "rd";
            default: return "th";
        }
    }
}