package br.com.judgementday;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class JudgementDay extends JavaPlugin implements Listener {

    private FileConfiguration playerData;
    private File playerDataFile;
    private FileConfiguration punishmentData;
    private File punishmentDataFile;
    private FileConfiguration reportData;
    private File reportDataFile;

    private final Map<UUID, String> awaitingProofLinks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> pendingPunishments = new ConcurrentHashMap<>();
    private final AtomicInteger punishmentIdCounter = new AtomicInteger(1);

    private final Map<String, List<String>> punishmentReasons = new HashMap<>();
    private final Map<String, Map<String, Map<Integer, Long>>> punishmentTimes = new HashMap<>();

    private static final String PREFIX = ChatColor.DARK_RED + "[" + ChatColor.RED + "JudgementDay" + ChatColor.DARK_RED + "] " + ChatColor.RESET;

    @Override
    public void onEnable() {
        // Registrando eventos
        getServer().getPluginManager().registerEvents(this, this);

        // Comandos
        getCommand("punir").setExecutor(new PunirCommand());
        getCommand("revogar").setExecutor(new RevogarCommand());
        getCommand("historico").setExecutor(new HistoricoCommand());
        getCommand("reportar").setExecutor(new ReportarCommand());
        getCommand("reports").setExecutor(new ReportsCommand());

        // Carregando configurações
        saveDefaultConfig();
        setupPunishmentReasons();

        // Configurando arquivos de dados
        setupPlayerData();
        setupPunishmentData();
        setupReportData();

        // Inicializando contador de IDs de punição
        initializePunishmentIdCounter();

        getLogger().info("JudgementDay ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        savePunishmentData();
        savePlayerData();
        saveReportData();
        getLogger().info("JudgementDay desativado com sucesso!");
    }

    private void setupPunishmentReasons() {
        // Configuração padrão de motivos de punição
        if (!getConfig().contains("motivos")) {
            List<String> warnReasons = Arrays.asList("Flood", "Spam", "Ofensa leve", "Capslock");
            List<String> muteReasons = Arrays.asList("Ofensa grave", "Divulgação", "Provocação", "Racismo");
            List<String> banReasons = Arrays.asList("Hack", "Ameaça", "Comportamento tóxico", "Evasão de punição");

            getConfig().set("motivos.warn", warnReasons);
            getConfig().set("motivos.mute", muteReasons);
            getConfig().set("motivos.ban", banReasons);

            // Tempos padrão para cada tipo e nível de punição (em minutos)
            setupDefaultPunishmentTimes();

            saveConfig();
        }

        // Carregando motivos e tempos de punição
        loadPunishmentReasonsAndTimes();
    }

    private void setupDefaultPunishmentTimes() {
        // Warn - tempos em minutos
        getConfig().set("tempos.warn.Flood.1", 30);
        getConfig().set("tempos.warn.Flood.2", 60);
        getConfig().set("tempos.warn.Flood.3", 120);

        getConfig().set("tempos.warn.Spam.1", 30);
        getConfig().set("tempos.warn.Spam.2", 60);
        getConfig().set("tempos.warn.Spam.3", 120);

        getConfig().set("tempos.warn.Ofensa leve.1", 60);
        getConfig().set("tempos.warn.Ofensa leve.2", 120);
        getConfig().set("tempos.warn.Ofensa leve.3", 240);

        getConfig().set("tempos.warn.Capslock.1", 15);
        getConfig().set("tempos.warn.Capslock.2", 30);
        getConfig().set("tempos.warn.Capslock.3", 60);

        // Mute - tempos em minutos
        getConfig().set("tempos.mute.Ofensa grave.1", 120);
        getConfig().set("tempos.mute.Ofensa grave.2", 720);
        getConfig().set("tempos.mute.Ofensa grave.3", 1440);

        getConfig().set("tempos.mute.Divulgação.1", 240);
        getConfig().set("tempos.mute.Divulgação.2", 1440);
        getConfig().set("tempos.mute.Divulgação.3", 10080);

        getConfig().set("tempos.mute.Provocação.1", 120);
        getConfig().set("tempos.mute.Provocação.2", 360);
        getConfig().set("tempos.mute.Provocação.3", 1440);

        getConfig().set("tempos.mute.Racismo.1", 1440);
        getConfig().set("tempos.mute.Racismo.2", 4320);
        getConfig().set("tempos.mute.Racismo.3", 10080);

        // Ban - tempos em minutos
        getConfig().set("tempos.ban.Hack.1", 10080);
        getConfig().set("tempos.ban.Hack.2", 43200);
        getConfig().set("tempos.ban.Hack.3", -1); // Permanente

        getConfig().set("tempos.ban.Ameaça.1", 1440);
        getConfig().set("tempos.ban.Ameaça.2", 10080);
        getConfig().set("tempos.ban.Ameaça.3", 43200);

        getConfig().set("tempos.ban.Comportamento tóxico.1", 4320);
        getConfig().set("tempos.ban.Comportamento tóxico.2", 10080);
        getConfig().set("tempos.ban.Comportamento tóxico.3", 43200);

        getConfig().set("tempos.ban.Evasão de punição.1", 4320);
        getConfig().set("tempos.ban.Evasão de punição.2", 10080);
        getConfig().set("tempos.ban.Evasão de punição.3", -1); // Permanente
    }

    private void loadPunishmentReasonsAndTimes() {
        punishmentReasons.clear();
        punishmentTimes.clear();

        // Carregando motivos
        for (String type : Arrays.asList("warn", "mute", "ban")) {
            List<String> reasons = getConfig().getStringList("motivos." + type);
            punishmentReasons.put(type, reasons);

            // Carregando tempos
            Map<String, Map<Integer, Long>> typeTimesMap = new HashMap<>();
            for (String reason : reasons) {
                Map<Integer, Long> levelTimesMap = new HashMap<>();
                for (int level = 1; level <= 3; level++) {
                    long minutes = getConfig().getLong("tempos." + type + "." + reason + "." + level, 60);
                    levelTimesMap.put(level, minutes * 60 * 1000); // Convertendo para milissegundos
                }
                typeTimesMap.put(reason, levelTimesMap);
            }
            punishmentTimes.put(type, typeTimesMap);
        }
    }

    private void setupPlayerData() {
        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.getParentFile().mkdirs();
                playerDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Não foi possível criar o arquivo playerdata.yml");
                e.printStackTrace();
            }
        }
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    private void setupPunishmentData() {
        punishmentDataFile = new File(getDataFolder(), "punishments.yml");
        if (!punishmentDataFile.exists()) {
            try {
                punishmentDataFile.getParentFile().mkdirs();
                punishmentDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Não foi possível criar o arquivo punishments.yml");
                e.printStackTrace();
            }
        }
        punishmentData = YamlConfiguration.loadConfiguration(punishmentDataFile);
    }

    private void setupReportData() {
        reportDataFile = new File(getDataFolder(), "reports.yml");
        if (!reportDataFile.exists()) {
            try {
                reportDataFile.getParentFile().mkdirs();
                reportDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Não foi possível criar o arquivo reports.yml");
                e.printStackTrace();
            }
        }
        reportData = YamlConfiguration.loadConfiguration(reportDataFile);
    }

    private void savePlayerData() {
        try {
            playerData.save(playerDataFile);
        } catch (IOException e) {
            getLogger().severe("Não foi possível salvar o arquivo playerdata.yml");
            e.printStackTrace();
        }
    }

    private void savePunishmentData() {
        try {
            punishmentData.save(punishmentDataFile);
        } catch (IOException e) {
            getLogger().severe("Não foi possível salvar o arquivo punishments.yml");
            e.printStackTrace();
        }
    }

    private void saveReportData() {
        try {
            reportData.save(reportDataFile);
        } catch (IOException e) {
            getLogger().severe("Não foi possível salvar o arquivo reports.yml");
            e.printStackTrace();
        }
    }

    private void initializePunishmentIdCounter() {
        int maxId = 0;
        if (punishmentData.contains("punishments")) {
            if (punishmentData.getConfigurationSection("punishments") != null) {
                for (String key : punishmentData.getConfigurationSection("punishments").getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        if (id > maxId) {
                            maxId = id;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        punishmentIdCounter.set(maxId + 1);
    }

    // Método para obter o próximo ID de punição disponível
    private int getNextPunishmentId() {
        return punishmentIdCounter.getAndIncrement();
    }

    // Método para obter o nível de punição para um jogador e motivo
    private int getPunishmentLevel(String playerName, String type, String reason) {
        String playerKey = playerName.toLowerCase();
        String path = "players." + playerKey + ".history." + type + "." + reason;

        if (!playerData.contains(path)) {
            return 1;
        }

        List<?> history = playerData.getList(path);
        return history == null ? 1 : history.size() + 1;
    }

    // Método para verificar se um jogador está banido
    private boolean isPlayerBanned(String playerName) {
        String playerKey = playerName.toLowerCase();
        String path = "players." + playerKey + ".punishments.ban";

        if (!playerData.contains(path)) {
            return false;
        }

        List<Map<?, ?>> activeBans = playerData.getMapList(path);
        if (activeBans.isEmpty()) {
            return false;
        }

        // Verificar se existe algum ban ativo
        long currentTime = System.currentTimeMillis();
        for (Map<?, ?> ban : activeBans) {
            long expiry = (long) ban.get("expiry");
            boolean active = (boolean) ban.get("active");

            if (active && (expiry > currentTime || expiry == -1)) {
                return true;
            }
        }

        return false;
    }

    // Método para verificar se um jogador está silenciado
    private boolean isPlayerMuted(String playerName) {
        String playerKey = playerName.toLowerCase();
        String path = "players." + playerKey + ".punishments.mute";

        if (!playerData.contains(path)) {
            return false;
        }

        List<Map<?, ?>> activeMutes = playerData.getMapList(path);
        if (activeMutes.isEmpty()) {
            return false;
        }

        // Verificar se existe algum mute ativo
        long currentTime = System.currentTimeMillis();
        for (Map<?, ?> mute : activeMutes) {
            long expiry = (long) mute.get("expiry");
            boolean active = (boolean) mute.get("active");

            if (active && (expiry > currentTime || expiry == -1)) {
                return true;
            }
        }

        return false;
    }

    // Método para adicionar uma punição
    @SuppressWarnings("unchecked")
    private void addPunishment(String playerName, String type, String reason, String punisherName, long duration, String proofLink) {
        String playerKey = playerName.toLowerCase();

        // Gerando ID único para a punição
        int id = getNextPunishmentId();

        // Registrando no histórico
        String historyPath = "players." + playerKey + ".history." + type + "." + reason;
        List<Map<String, Object>> history = (List<Map<String, Object>>) playerData.getList(historyPath, new ArrayList<>());

        Map<String, Object> punishmentInfo = new HashMap<>();
        punishmentInfo.put("id", id);
        punishmentInfo.put("punisher", punisherName);
        punishmentInfo.put("time", System.currentTimeMillis());
        punishmentInfo.put("duration", duration);
        punishmentInfo.put("proofLink", proofLink);

        // Se não existir uma lista, criar uma nova
        if (history == null) {
            history = new ArrayList<>();
        }

        history.add(punishmentInfo);
        playerData.set(historyPath, history);

        // Registrando punição ativa
        String activePath = "players." + playerKey + ".punishments." + type;
        List<Map<String, Object>> activePunishments = (List<Map<String, Object>>) playerData.getList(activePath, new ArrayList<>());

        Map<String, Object> activePunishment = new HashMap<>();
        activePunishment.put("id", id);
        activePunishment.put("reason", reason);
        activePunishment.put("punisher", punisherName);
        activePunishment.put("time", System.currentTimeMillis());
        activePunishment.put("expiry", duration == -1 ? -1 : System.currentTimeMillis() + duration);
        activePunishment.put("active", true);
        activePunishment.put("proofLink", proofLink);

        // Se não existir uma lista, criar uma nova
        if (activePunishments == null) {
            activePunishments = new ArrayList<>();
        }

        activePunishments.add(activePunishment);
        playerData.set(activePath, activePunishments);

        // Salvar também na lista global de punições por ID
        Map<String, Object> globalPunishment = new HashMap<>();
        globalPunishment.put("player", playerName);
        globalPunishment.put("type", type);
        globalPunishment.put("reason", reason);
        globalPunishment.put("punisher", punisherName);
        globalPunishment.put("time", System.currentTimeMillis());
        globalPunishment.put("duration", duration);
        globalPunishment.put("expiry", duration == -1 ? -1 : System.currentTimeMillis() + duration);
        globalPunishment.put("active", true);
        globalPunishment.put("proofLink", proofLink);

        punishmentData.set("punishments." + id, globalPunishment);

        // Salvando dados
        savePlayerData();
        savePunishmentData();

        // Notificar o jogador se estiver online
        Player target = Bukkit.getPlayer(playerName);
        if (target != null) {
            if (type.equals("ban")) {
                // O jogador será desconectado automaticamente pelo evento de login
                target.kickPlayer(formatPunishmentMessage(type, reason, punisherName, duration, id, proofLink));
            } else {
                target.sendMessage(PREFIX + formatPunishmentMessage(type, reason, punisherName, duration, id, proofLink));
            }
        }

        // Broadcast da punição para staff
        broadcastPunishment(playerName, type, reason, punisherName, duration, id);
    }

    // Método para revogar uma punição
    @SuppressWarnings("unchecked")
    private boolean revokePunishment(int id, String revokerName) {
        if (!punishmentData.contains("punishments." + id)) {
            return false;
        }

        Map<String, Object> punishment = (Map<String, Object>) punishmentData.get("punishments." + id);
        if (punishment == null || !(boolean) punishment.get("active")) {
            return false;
        }

        String playerName = (String) punishment.get("player");
        String type = (String) punishment.get("type");
        String playerKey = playerName.toLowerCase();

        // Desativando a punição global
        punishment.put("active", false);
        punishment.put("revokedBy", revokerName);
        punishment.put("revokedTime", System.currentTimeMillis());
        punishmentData.set("punishments." + id, punishment);

        // Desativando a punição no registro do jogador
        String activePath = "players." + playerKey + ".punishments." + type;
        List<Map<String, Object>> activePunishments = (List<Map<String, Object>>) playerData.getList(activePath);

        if (activePunishments != null) {
            for (Map<String, Object> activePunishment : activePunishments) {
                if ((int) activePunishment.get("id") == id) {
                    activePunishment.put("active", false);
                    activePunishment.put("revokedBy", revokerName);
                    activePunishment.put("revokedTime", System.currentTimeMillis());
                    break;
                }
            }
            playerData.set(activePath, activePunishments);
        }

        // Salvando dados
        savePlayerData();
        savePunishmentData();

        // Broadcast da revogação
        broadcastRevocation(playerName, type, (String) punishment.get("reason"), revokerName, id);

        return true;
    }

    private String formatPunishmentMessage(String type, String reason, String punisher, long duration, int id, String proofLink) {
        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append("Você foi punido!\n");
        message.append(ChatColor.GOLD).append("Tipo: ").append(ChatColor.WHITE).append(formatPunishmentType(type)).append("\n");
        message.append(ChatColor.GOLD).append("Motivo: ").append(ChatColor.WHITE).append(reason).append("\n");
        message.append(ChatColor.GOLD).append("Punido por: ").append(ChatColor.WHITE).append(punisher).append("\n");

        if (duration > 0) {
            message.append(ChatColor.GOLD).append("Duração: ").append(ChatColor.WHITE).append(formatDuration(duration)).append("\n");
        } else if (duration == -1) {
            message.append(ChatColor.GOLD).append("Duração: ").append(ChatColor.WHITE).append("Permanente").append("\n");
        }

        message.append(ChatColor.GOLD).append("ID da punição: ").append(ChatColor.WHITE).append(id).append("\n");
        message.append(ChatColor.GOLD).append("Prova: ").append(ChatColor.AQUA).append(proofLink);

        return message.toString();
    }

    private String formatPunishmentType(String type) {
        switch (type.toLowerCase()) {
            case "warn":
                return "Advertência";
            case "mute":
                return "Silenciamento";
            case "ban":
                return "Banimento";
            default:
                return type;
        }
    }

    private String formatDuration(long duration) {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " dia(s)";
        } else if (hours > 0) {
            return hours + " hora(s)";
        } else if (minutes > 0) {
            return minutes + " minuto(s)";
        } else {
            return seconds + " segundo(s)";
        }
    }

    private void broadcastPunishment(String playerName, String type, String reason, String punisherName, long duration, int id) {
        String message = PREFIX + ChatColor.RED + playerName + ChatColor.GOLD + " recebeu " +
                formatPunishmentType(type) + ChatColor.GOLD + " por " + ChatColor.WHITE + reason +
                ChatColor.GOLD + " (ID: " + ChatColor.WHITE + id + ChatColor.GOLD + ")";

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.staff")) {
                player.sendMessage(message);
            }
        }
        getLogger().info(ChatColor.stripColor(message));
    }

    private void broadcastRevocation(String playerName, String type, String reason, String revokerName, int id) {
        String message = PREFIX + ChatColor.GOLD + "A punição " + formatPunishmentType(type) +
                ChatColor.GOLD + " do jogador " + ChatColor.RED + playerName +
                ChatColor.GOLD + " foi revogada por " + ChatColor.GREEN + revokerName +
                ChatColor.GOLD + " (ID: " + ChatColor.WHITE + id + ChatColor.GOLD + ")";

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("judgementday.staff")) {
                player.sendMessage(message);
            }
        }
        getLogger().info(ChatColor.stripColor(message));
    }

    // Método para abrir o menu de histórico de punições
    private void openHistoryInventory(Player player, String targetPlayerName) {
        String playerKey = targetPlayerName.toLowerCase();

        // Lista para armazenar todas as punições
        List<Map<String, Object>> allPunishments = new ArrayList<>();

        // Verificar se o jogador tem histórico
        if (playerData.contains("players." + playerKey + ".history")) {
            // Obter todas as punições do histórico
            for (String type : Arrays.asList("warn", "mute", "ban")) {
                String typePath = "players." + playerKey + ".history." + type;
                if (!playerData.contains(typePath)) {
                    continue;
                }

                if (playerData.getConfigurationSection(typePath) != null) {
                    for (String reason : playerData.getConfigurationSection(typePath).getKeys(false)) {
                        List<Map<?, ?>> punishments = playerData.getMapList(typePath + "." + reason);

                        for (Map<?, ?> punishmentMap : punishments) {
                            Map<String, Object> punishment = new HashMap<>();
                            punishment.put("id", punishmentMap.get("id"));
                            punishment.put("type", type);
                            punishment.put("reason", reason);
                            punishment.put("punisher", punishmentMap.get("punisher"));
                            punishment.put("time", punishmentMap.get("time"));
                            punishment.put("duration", punishmentMap.get("duration"));
                            punishment.put("proofLink", punishmentMap.get("proofLink"));

                            if (punishmentMap.containsKey("revokedBy")) {
                                punishment.put("revokedBy", punishmentMap.get("revokedBy"));
                                punishment.put("revokedTime", punishmentMap.get("revokedTime"));
                            }

                            allPunishments.add(punishment);
                        }
                    }
                }
            }
        }

        // Se não houver punições
        if (allPunishments.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Nenhum histórico encontrado para " + targetPlayerName);
            return;
        }

        // Ordenar punições (mais recentes primeiro)
        allPunishments.sort((p1, p2) -> Long.compare((long) p2.get("time"), (long) p1.get("time")));

        // Calcular tamanho do inventário
        int size = Math.min(54, ((allPunishments.size() + 8) / 9) * 9);
        Inventory inventory = Bukkit.createInventory(null, size, ChatColor.RED + "Histórico: " + targetPlayerName);

        // Adicionar punições ao inventário
        for (int i = 0; i < allPunishments.size() && i < size; i++) {
            Map<String, Object> punishment = allPunishments.get(i);

            // Escolher material com base no tipo
            Material material;
            ChatColor typeColor;
            String type = (String) punishment.get("type");

            switch (type) {
                case "warn":
                    material = Material.PAPER;
                    typeColor = ChatColor.YELLOW;
                    break;
                case "mute":
                    material = Material.BOOK;
                    typeColor = ChatColor.GOLD;
                    break;
                case "ban":
                    material = Material.BARRIER;
                    typeColor = ChatColor.RED;
                    break;
                default:
                    material = Material.STONE;
                    typeColor = ChatColor.WHITE;
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            // ID e tipo no nome
            meta.setDisplayName(typeColor + formatPunishmentType(type) + ChatColor.GRAY + " #" + punishment.get("id"));

            // Detalhes na lore
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GOLD + "Motivo: " + ChatColor.WHITE + punishment.get("reason"));
            lore.add(ChatColor.GOLD + "Punido por: " + ChatColor.WHITE + punishment.get("punisher"));

            // Data
            String dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date((long) punishment.get("time")));
            lore.add(ChatColor.GOLD + "Data: " + ChatColor.WHITE + dateFormat);

            // Duração
            long duration = (long) punishment.get("duration");
            if (duration == -1) {
                lore.add(ChatColor.GOLD + "Duração: " + ChatColor.WHITE + "Permanente");
            } else {
                lore.add(ChatColor.GOLD + "Duração: " + ChatColor.WHITE + formatDuration(duration));
            }

            // Informações de revogação
            if (punishment.containsKey("revokedBy")) {
                String revoker = (String) punishment.get("revokedBy");
                String revokeDate = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date((long) punishment.get("revokedTime")));

                lore.add(ChatColor.RED + "REVOGADO");
                lore.add(ChatColor.GOLD + "Revogado por: " + ChatColor.WHITE + revoker);
                lore.add(ChatColor.GOLD + "Data da revogação: " + ChatColor.WHITE + revokeDate);
            }

            // Prova
            lore.add(ChatColor.GOLD + "Prova: " + ChatColor.AQUA + punishment.get("proofLink"));

            meta.setLore(lore);
            item.setItemMeta(meta);

            inventory.setItem(i, item);
        }

        player.openInventory(inventory);
    }

    // Evento para verificar se um jogador está banido ao tentar logar
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        if (isPlayerBanned(playerName)) {
            // Obter detalhes do ban ativo
            String playerKey = playerName.toLowerCase();
            List<Map<?, ?>> activeBans = playerData.getMapList("players." + playerKey + ".punishments.ban");

            for (Map<?, ?> ban : activeBans) {
                if ((boolean) ban.get("active")) {
                    long expiry = (long) ban.get("expiry");
                    String reason = (String) ban.get("reason");
                    String punisher = (String) ban.get("punisher");
                    int id = (int) ban.get("id");
                    String proofLink = (String) ban.get("proofLink");

                    // Verificar se o ban expirou
                    if (expiry != -1 && expiry < System.currentTimeMillis()) {
                        continue;
                    }

                    long duration = expiry == -1 ? -1 : expiry - System.currentTimeMillis();
                    event.disallow(PlayerLoginEvent.Result.KICK_BANNED, formatPunishmentMessage("ban", reason, punisher, duration, id, proofLink));
                    return;
                }
            }
        }
    }

    // Evento para verificar se um jogador está silenciado ao tentar falar
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        if (isPlayerMuted(playerName)) {
            event.setCancelled(true);

            // Obter detalhes do mute ativo
            String playerKey = playerName.toLowerCase();
            List<Map<?, ?>> activeMutes = playerData.getMapList("players." + playerKey + ".punishments.mute");

            for (Map<?, ?> mute : activeMutes) {
                if ((boolean) mute.get("active")) {
                    long expiry = (long) mute.get("expiry");

                    // Verificar se o mute expirou
                    if (expiry != -1 && expiry < System.currentTimeMillis()) {
                        continue;
                    }

                    String reason = (String) mute.get("reason");
                    String punisher = (String) mute.get("punisher");
                    int id = (int) mute.get("id");
                    String proofLink = (String) mute.get("proofLink");

                    long duration = expiry == -1 ? -1 : expiry - System.currentTimeMillis();
                    player.sendMessage(PREFIX + ChatColor.RED + "Você está silenciado e não pode falar no chat.");
                    player.sendMessage(formatPunishmentMessage("mute", reason, punisher, duration, id, proofLink));
                    return;
                }
            }
        }
    }

    // Manipulador de eventos para inventários
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && event.getView().getTitle().contains("JudgementDay") ||
                event.getView().getTitle().startsWith(ChatColor.RED + "Histórico:")) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            // Inventário de seleção de tipo de punição
            if (event.getView().getTitle().equals(ChatColor.RED + "JudgementDay - Punir")) {
                handlePunishmentTypeSelection(player, clickedItem);
            }
            // Inventário de seleção de motivo
            else if (event.getView().getTitle().startsWith(ChatColor.RED + "JudgementDay - Motivos")) {
                handlePunishmentReasonSelection(player, clickedItem);
            }
            // Inventário de reports
            else if (event.getView().getTitle().equals(ChatColor.RED + "JudgementDay - Reports")) {
                handleReportClick(player, clickedItem);
            }
            // Inventário de histórico
            else if (event.getView().getTitle().startsWith(ChatColor.RED + "Histórico:")) {
                // Apenas fechar o inventário ao clicar (evento já está cancelado)
                // Sem ação especial para cliques no histórico
            }
        }
    }

    private void handlePunishmentTypeSelection(Player player, ItemStack clickedItem) {
        if (!pendingPunishments.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ocorreu um erro ao processar sua seleção.");
            player.closeInventory();
            return;
        }

        String targetPlayer = (String) pendingPunishments.get(player.getUniqueId()).get("target");
        String displayName = clickedItem.getItemMeta().getDisplayName();

        if (displayName.contains("Advertência")) {
            openReasonsInventory(player, targetPlayer, "warn");
        } else if (displayName.contains("Silenciamento")) {
            openReasonsInventory(player, targetPlayer, "mute");
        } else if (displayName.contains("Banimento")) {
            openReasonsInventory(player, targetPlayer, "ban");
        } else {
            player.closeInventory();
        }
    }

    private void handlePunishmentReasonSelection(Player player, ItemStack clickedItem) {
        if (!pendingPunishments.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ocorreu um erro ao processar sua seleção.");
            player.closeInventory();
            return;
        }

        Map<String, Object> punishment = pendingPunishments.get(player.getUniqueId());
        String targetPlayer = (String) punishment.get("target");
        String type = (String) punishment.get("type");

        String reason = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        if (reason.endsWith(" (1ª)")) {
            reason = reason.substring(0, reason.length() - 5);
        } else if (reason.endsWith(" (2ª)")) {
            reason = reason.substring(0, reason.length() - 5);
        } else if (reason.endsWith(" (3ª)")) {
            reason = reason.substring(0, reason.length() - 5);
        } else if (reason.endsWith(" (3ª+)")) {
            reason = reason.substring(0, reason.length() - 6);
        }

        // Obtém o nível de punição para esse jogador
        int level = getPunishmentLevel(targetPlayer, type, reason);

        // Obtém a duração da punição
        long duration = 0;
        try {
            duration = punishmentTimes.get(type).get(reason).get(Math.min(level, 3));
        } catch (Exception e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Erro ao obter tempo de punição. Usando 1 hora como padrão.");
            duration = 60 * 60 * 1000; // 1 hora em milissegundos
        }

        // Atualiza as informações da punição pendente
        punishment.put("reason", reason);
        punishment.put("duration", duration);

        player.closeInventory();
        player.sendMessage(PREFIX + ChatColor.GOLD + "Você selecionou " + ChatColor.RED + formatPunishmentType(type) +
                ChatColor.GOLD + " por " + ChatColor.WHITE + reason +
                ChatColor.GOLD + " com duração de " + ChatColor.WHITE + formatDuration(duration));

        player.sendMessage(PREFIX + ChatColor.GOLD + "Digite o link da prova no chat:");
        awaitingProofLinks.put(player.getUniqueId(), targetPlayer);
    }

    private void handleReportClick(Player player, ItemStack clickedItem) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                if (line.startsWith(ChatColor.GOLD + "ID: " + ChatColor.WHITE)) {
                    String reportId = ChatColor.stripColor(line).substring(4);

                    if (reportData.contains("reports." + reportId)) {
                        Map<?, ?> report = reportData.getConfigurationSection("reports." + reportId).getValues(true);
                        String reportedPlayer = (String) report.get("reported");

                        // Abrir menu de punição para o jogador reportado
                        pendingPunishments.put(player.getUniqueId(), new HashMap<>());
                        pendingPunishments.get(player.getUniqueId()).put("target", reportedPlayer);
                        openPunishmentTypeInventory(player, reportedPlayer);

                        // Marcar report como processado
                        reportData.set("reports." + reportId + ".processed", true);
                        reportData.set("reports." + reportId + ".processedBy", player.getName());
                        reportData.set("reports." + reportId + ".processedTime", System.currentTimeMillis());
                        saveReportData();

                        player.sendMessage(PREFIX + ChatColor.GREEN + "Você está processando o report contra " +
                                ChatColor.YELLOW + reportedPlayer + ChatColor.GREEN + ".");
                        break;
                    }
                }
            }
        }
    }

    // Método para abrir inventário de seleção de tipo de punição
    private void openPunishmentTypeInventory(Player player, String targetPlayer) {
        Inventory inventory = Bukkit.createInventory(null, 9, ChatColor.RED + "JudgementDay - Punir");

        // Item para Advertência (Warn)
        ItemStack warnItem = new ItemStack(Material.PAPER);
        ItemMeta warnMeta = warnItem.getItemMeta();
        warnMeta.setDisplayName(ChatColor.YELLOW + "Advertência");
        List<String> warnLore = new ArrayList<>();
        warnLore.add(ChatColor.GRAY + "Clique para aplicar uma advertência");
        warnLore.add(ChatColor.GRAY + "a " + ChatColor.RED + targetPlayer);
        warnMeta.setLore(warnLore);
        warnItem.setItemMeta(warnMeta);

        // Item para Silenciamento (Mute)
        ItemStack muteItem = new ItemStack(Material.BOOK);
        ItemMeta muteMeta = muteItem.getItemMeta();
        muteMeta.setDisplayName(ChatColor.GOLD + "Silenciamento");
        List<String> muteLore = new ArrayList<>();
        muteLore.add(ChatColor.GRAY + "Clique para silenciar");
        muteLore.add(ChatColor.GRAY + "a " + ChatColor.RED + targetPlayer);
        muteMeta.setLore(muteLore);
        muteItem.setItemMeta(muteMeta);

        // Item para Banimento (Ban)
        ItemStack banItem = new ItemStack(Material.BARRIER);
        ItemMeta banMeta = banItem.getItemMeta();
        banMeta.setDisplayName(ChatColor.RED + "Banimento");
        List<String> banLore = new ArrayList<>();
        banLore.add(ChatColor.GRAY + "Clique para banir");
        banLore.add(ChatColor.GRAY + "a " + ChatColor.RED + targetPlayer);
        banMeta.setLore(banLore);
        banItem.setItemMeta(banMeta);

        // Adicionar itens ao inventário
        inventory.setItem(2, warnItem);
        inventory.setItem(4, muteItem);
        inventory.setItem(6, banItem);

        // Abrir inventário para o jogador
        player.openInventory(inventory);
    }

    // Método para abrir inventário de seleção de motivo de punição
    private void openReasonsInventory(Player player, String targetPlayer, String type) {
        List<String> reasons = punishmentReasons.get(type);
        int size = Math.min(((reasons.size() + 8) / 9) * 9, 54);

        Inventory inventory = Bukkit.createInventory(null, size, ChatColor.RED + "JudgementDay - Motivos (" + formatPunishmentType(type) + ")");

        // Atualizar informações de punição pendente
        pendingPunishments.get(player.getUniqueId()).put("target", targetPlayer);
        pendingPunishments.get(player.getUniqueId()).put("type", type);

        // Adicionar motivos ao inventário
        for (int i = 0; i < reasons.size(); i++) {
            String reason = reasons.get(i);
            int level = getPunishmentLevel(targetPlayer, type, reason);

            Material material;
            ChatColor color;

            switch (type) {
                case "warn":
                    material = Material.PAPER;
                    color = ChatColor.YELLOW;
                    break;
                case "mute":
                    material = Material.BOOK;
                    color = ChatColor.GOLD;
                    break;
                case "ban":
                    material = Material.BARRIER;
                    color = ChatColor.RED;
                    break;
                default:
                    material = Material.STONE;
                    color = ChatColor.WHITE;
            }

            ItemStack reasonItem = new ItemStack(material);
            ItemMeta reasonMeta = reasonItem.getItemMeta();

            String levelSuffix = level <= 3 ? " (" + level + "ª)" : " (3ª+)";
            reasonMeta.setDisplayName(color + reason + ChatColor.GRAY + levelSuffix);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Clique para selecionar este motivo");

            // Tentar obter duração
            long duration = 60 * 60 * 1000; // 1 hora como padrão
            try {
                duration = punishmentTimes.get(type).get(reason).get(Math.min(level, 3));
            } catch (Exception e) {
                // Manter tempo padrão se houver erro
            }

            if (duration == -1) {
                lore.add(ChatColor.GOLD + "Duração: " + ChatColor.WHITE + "Permanente");
            } else {
                lore.add(ChatColor.GOLD + "Duração: " + ChatColor.WHITE + formatDuration(duration));
            }

            reasonMeta.setLore(lore);
            reasonItem.setItemMeta(reasonMeta);

            inventory.setItem(i, reasonItem);
        }

        // Abrir inventário para o jogador
        player.openInventory(inventory);
    }

    // Evento para capturar o link da prova
    @EventHandler
    public void onChatProofLink(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (awaitingProofLinks.containsKey(playerId)) {
            event.setCancelled(true);
            String link = event.getMessage();
            String targetPlayer = awaitingProofLinks.get(playerId);

            // Remover jogador da lista de espera
            awaitingProofLinks.remove(playerId);

            // Verificar se ainda há informações pendentes
            if (!pendingPunishments.containsKey(playerId)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Ocorreu um erro ao processar a punição.");
                return;
            }

            // Obter informações da punição
            Map<String, Object> punishment = pendingPunishments.get(playerId);
            String type = (String) punishment.get("type");
            String reason = (String) punishment.get("reason");
            long duration = (long) punishment.get("duration");

            // Aplicar punição
            addPunishment(targetPlayer, type, reason, player.getName(), duration, link);

            // Remover jogador da lista de punições pendentes
            pendingPunishments.remove(playerId);

            player.sendMessage(PREFIX + ChatColor.GREEN + "Punição aplicada com sucesso!");
        }
    }

    // Comandos
    private class PunirCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("judgementday.punir")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Você não tem permissão para usar este comando.");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /" + label + " <jogador>");
                return true;
            }

            String targetName = args[0];

            // Verificar se o jogador existe
            if (Bukkit.getPlayer(targetName) == null && Bukkit.getOfflinePlayer(targetName).hasPlayedBefore()) {
                targetName = Bukkit.getOfflinePlayer(targetName).getName();
            }

            if (sender instanceof Player) {
                Player player = (Player) sender;

                // Iniciar processo de punição
                pendingPunishments.put(player.getUniqueId(), new HashMap<>());
                pendingPunishments.get(player.getUniqueId()).put("target", targetName);

                openPunishmentTypeInventory(player, targetName);
            } else {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando só pode ser executado por jogadores.");
            }

            return true;
        }
    }

    private class RevogarCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("judgementday.revogar")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Você não tem permissão para usar este comando.");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /" + label + " <ID>");
                return true;
            }

            int id;
            try {
                id = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "ID de punição inválido.");
                return true;
            }

            if (revokePunishment(id, sender.getName())) {
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Punição revogada com sucesso.");
            } else {
                sender.sendMessage(PREFIX + ChatColor.RED + "Não foi possível encontrar uma punição ativa com este ID.");
            }

            return true;
        }
    }

    private class HistoricoCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("judgementday.historico")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Você não tem permissão para usar este comando.");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /" + label + " <jogador>");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando só pode ser executado por jogadores.");
                return true;
            }

            Player player = (Player) sender;
            String targetName = args[0];

            // Abrir menu GUI com o histórico
            openHistoryInventory(player, targetName);

            return true;
        }
    }

    private class ReportarCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando só pode ser executado por jogadores.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /" + label + " <jogador> <motivo>");
                return true;
            }

            Player player = (Player) sender;
            String reportedName = args[0];
            StringBuilder reasonBuilder = new StringBuilder();

            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }

            String reason = reasonBuilder.toString().trim();

            // Gerar ID único para o report
            int reportId = (int) (System.currentTimeMillis() % 100000);

            // Salvar report
            reportData.set("reports." + reportId + ".reporter", player.getName());
            reportData.set("reports." + reportId + ".reported", reportedName);
            reportData.set("reports." + reportId + ".reason", reason);
            reportData.set("reports." + reportId + ".time", System.currentTimeMillis());
            reportData.set("reports." + reportId + ".processed", false);

            saveReportData();

            player.sendMessage(PREFIX + ChatColor.GREEN + "Você reportou " + ChatColor.YELLOW + reportedName +
                    ChatColor.GREEN + " por " + ChatColor.WHITE + reason + ChatColor.GREEN + ".");

            // Notificar staff online sobre o novo report
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("judgementday.staff")) {
                    staff.sendMessage(PREFIX + ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " reportou " +
                            ChatColor.YELLOW + reportedName + ChatColor.GREEN + " por " +
                            ChatColor.WHITE + reason + ChatColor.GREEN + ".");
                }
            }

            return true;
        }
    }

    private class ReportsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("judgementday.reports")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Você não tem permissão para usar este comando.");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando só pode ser executado por jogadores.");
                return true;
            }

            Player player = (Player) sender;

            // Verificar se existem reports
            if (!reportData.contains("reports") || reportData.getConfigurationSection("reports") == null ||
                    reportData.getConfigurationSection("reports").getKeys(false).isEmpty()) {
                player.sendMessage(PREFIX + ChatColor.RED + "Não há reports pendentes.");
                return true;
            }

            // Coletar reports não processados
            List<Map<String, Object>> pendingReports = new ArrayList<>();

            for (String reportId : reportData.getConfigurationSection("reports").getKeys(false)) {
                boolean processed = reportData.getBoolean("reports." + reportId + ".processed", false);

                if (!processed) {
                    Map<String, Object> report = new HashMap<>();
                    report.put("id", reportId);
                    report.put("reporter", reportData.getString("reports." + reportId + ".reporter"));
                    report.put("reported", reportData.getString("reports." + reportId + ".reported"));
                    report.put("reason", reportData.getString("reports." + reportId + ".reason"));
                    report.put("time", reportData.getLong("reports." + reportId + ".time"));

                    pendingReports.add(report);
                }
            }

            if (pendingReports.isEmpty()) {
                player.sendMessage(PREFIX + ChatColor.RED + "Não há reports pendentes.");
                return true;
            }

            // Ordenar por data (mais recentes primeiro)
            pendingReports.sort((r1, r2) -> Long.compare((long) r2.get("time"), (long) r1.get("time")));

            // Criar inventário de reports
            int size = Math.min(((pendingReports.size() + 8) / 9) * 9, 54);
            Inventory inventory = Bukkit.createInventory(null, size, ChatColor.RED + "JudgementDay - Reports");

            // Adicionar reports ao inventário
            for (int i = 0; i < pendingReports.size() && i < size; i++) {
                Map<String, Object> report = pendingReports.get(i);

                ItemStack reportItem = new ItemStack(Material.BOOK_AND_QUILL);
                ItemMeta meta = reportItem.getItemMeta();

                String reportedPlayer = (String) report.get("reported");
                meta.setDisplayName(ChatColor.RED + reportedPlayer);

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GOLD + "ID: " + ChatColor.WHITE + report.get("id"));
                lore.add(ChatColor.GOLD + "Reportado por: " + ChatColor.WHITE + report.get("reporter"));
                lore.add(ChatColor.GOLD + "Motivo: " + ChatColor.WHITE + report.get("reason"));

                long time = (long) report.get("time");
                String date = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(time));
                lore.add(ChatColor.GOLD + "Data: " + ChatColor.WHITE + date);

                lore.add("");
                lore.add(ChatColor.GREEN + "Clique para processar este report");

                meta.setLore(lore);
                reportItem.setItemMeta(meta);

                inventory.setItem(i, reportItem);
            }

            // Abrir inventário para o jogador
            player.openInventory(inventory);

            return true;
        }
    }
}

