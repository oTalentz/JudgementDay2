package com.judgementday.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for building ItemStacks
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    /**
     * Create a new ItemBuilder with the specified material
     *
     * @param material Material to use
     */
    public ItemBuilder(Material material) {
        this(material, 1);
    }

    /**
     * Create a new ItemBuilder with the specified material and amount
     *
     * @param material Material to use
     * @param amount Amount of items
     */
    public ItemBuilder(Material material, int amount) {
        this(material, amount, (short) 0);
    }

    /**
     * Create a new ItemBuilder with the specified material, amount, and durability
     *
     * @param material Material to use
     * @param amount Amount of items
     * @param durability Item durability
     */
    public ItemBuilder(Material material, int amount, short durability) {
        item = new ItemStack(material, amount, durability);
        meta = item.getItemMeta();
    }

    /**
     * Create a new ItemBuilder from an existing ItemStack
     *
     * @param item ItemStack to copy
     */
    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    /**
     * Set the display name of the item
     *
     * @param name New display name
     * @return This ItemBuilder
     */
    public ItemBuilder name(String name) {
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        return this;
    }

    /**
     * Set the lore of the item
     *
     * @param lore New lore
     * @return This ItemBuilder
     */
    public ItemBuilder lore(String... lore) {
        meta.setLore(Arrays.stream(lore)
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList()));
        return this;
    }

    /**
     * Set the lore of the item
     *
     * @param lore New lore
     * @return This ItemBuilder
     */
    public ItemBuilder lore(List<String> lore) {
        meta.setLore(lore.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList()));
        return this;
    }

    /**
     * Add a line to the item's lore
     *
     * @param line Line to add
     * @return This ItemBuilder
     */
    public ItemBuilder addLoreLine(String line) {
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        lore.add(ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lore);
        return this;
    }

    /**
     * Add an enchantment to the item
     *
     * @param enchantment Enchantment to add
     * @param level Enchantment level
     * @return This ItemBuilder
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    /**
     * Add an enchantment glow to the item without an actual enchantment
     *
     * @return This ItemBuilder
     */
    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    /**
     * Make the item unbreakable
     *
     * @return This ItemBuilder
     */
    public ItemBuilder unbreakable() {
        meta.spigot().setUnbreakable(true);
        return this;
    }

    /**
     * Add item flags to the item
     *
     * @param flags Flags to add
     * @return This ItemBuilder
     */
    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    /**
     * Set the owner of a skull item
     *
     * @param owner Skull owner's name
     * @return This ItemBuilder
     */
    public ItemBuilder skullOwner(String owner) {
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwner(owner);
        }
        return this;
    }

    /**
     * Build the final ItemStack
     *
     * @return The built ItemStack
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}