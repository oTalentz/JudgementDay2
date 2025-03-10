package com.judgementday;

import com.judgementday.command.*;
import com.judgementday.config.ConfigManager;
import com.judgementday.data.DataManager;
import com.judgementday.data.DatabaseManager;
import com.judgementday.data.YamlDataManager;
import com.judgementday.listener.ChatListener;
import com.judgementday.listener.LoginListener;
import com.judgementday.listener.PunishmentListener;
import com.judgementday.manager.AppealManager;
import com.judgementday.manager.PlayerManager;
import com.judgementday.manager.PunishmentManager;
import com.judgementday.manager.ReportManager;
import com.judgementday.util.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class JudgementDay extends JavaPlugin {

    private ConfigManager configManager;
    private DataManager dataManager;
    private PunishmentManager punishmentManager;
    private ReportManager reportManager;
    private PlayerManager playerManager;
    private AppealManager appealManager;
    private static JudgementDay instance;

    @Override
    public void onEnable() {
        instance = this;

        // Create config directory if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize config and messages
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        // Initialize data manager based on config
        setupDataManager();

        // Initialize managers
        punishmentManager = new PunishmentManager(this);
        reportManager = new ReportManager(this);
        playerManager = new PlayerManager(this);
        appealManager = new AppealManager(this);

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Schedule auto-cleanup task
        scheduleCleanupTask();

        LogUtil.info("JudgementDay v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save all data
        if (dataManager != null) {
            dataManager.saveAll();
        }

        LogUtil.info("JudgementDay has been disabled!");
    }

    private void setupDataManager() {
        String storageType = configManager.getMainConfig().getString("storage.type", "yaml");

        if (storageType.equalsIgnoreCase("mysql") || storageType.equalsIgnoreCase("mariadb")) {
            try {
                this.dataManager = new DatabaseManager(this);
                LogUtil.info("Using database storage for JudgementDay data.");
            } catch (Exception e) {
                LogUtil.severe("Failed to initialize database connection. Falling back to YAML storage.", e);
                this.dataManager = new YamlDataManager(this);
            }
        } else {
            this.dataManager = new YamlDataManager(this);
            LogUtil.info("Using YAML storage for JudgementDay data.");
        }

        dataManager.initialize();
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new LoginListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PunishmentListener(this), this);
    }

    private void registerCommands() {
        getCommand("punish").setExecutor(new PunishCommand(this));
        getCommand("warn").setExecutor(new PunishCommand(this, "warn"));
        getCommand("mute").setExecutor(new PunishCommand(this, "mute"));
        getCommand("ban").setExecutor(new PunishCommand(this, "ban"));
        getCommand("revoke").setExecutor(new RevokeCommand(this));
        getCommand("history").setExecutor(new HistoryCommand(this));
        getCommand("report").setExecutor(new ReportCommand(this));
        getCommand("reports").setExecutor(new ReportsCommand(this));
        getCommand("appeal").setExecutor(new AppealCommand(this));
        getCommand("appeals").setExecutor(new AppealsCommand(this));
        getCommand("jdreload").setExecutor(new ReloadCommand(this));
    }

    private void scheduleCleanupTask() {
        int cleanupInterval = configManager.getMainConfig().getInt("storage.cleanup-interval", 30);
        if (cleanupInterval > 0) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                LogUtil.info("Running automatic data cleanup task...");
                dataManager.cleanupExpiredPunishments();
                dataManager.cleanupOldReports();
            }, 20 * 60 * 60, 20 * 60 * 60 * cleanupInterval); // Convert hours to ticks
        }
    }

    public static JudgementDay getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public AppealManager getAppealManager() {
        return appealManager;
    }
}