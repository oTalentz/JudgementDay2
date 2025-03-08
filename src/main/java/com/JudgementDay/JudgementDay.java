package com.JudgementDay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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

public class JudgementDay extends JavaPlugin implements Listener, CommandExecutor {

    private File punicoesFile;
    private FileConfiguration punicoesConfig;
    private File configFile;
    private FileConfiguration config;
    private File reportsFile;
    private FileConfiguration reportsConfig;

    private final Map<UUID, String> aguardandoLink = new ConcurrentHashMap<>();
    private final Map<UUID, PunicaoTemporaria> jogadoresComPunicaoAtiva = new ConcurrentHashMap<>();
    private final Map<String, String> motivoInventoryMap = new ConcurrentHashMap<>();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    // Prefixo do plugin
    private final String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "JudgementDay" + ChatColor.DARK_RED + "] ";

    // Tempo em ticks para verificar punições expiradas (1 minuto = 1200 ticks)
    private final long VERIFICACAO_PUNICOES_INTERVAL = 1200L;

    @Override
    public void onEnable() {
        // Registra eventos
        getServer().getPluginManager().registerEvents(this, this);

        // Registra comandos
        getCommand("punir").setExecutor(this);
        getCommand("historico").setExecutor(this);
        getCommand("despunir").setExecutor(this);
        getCommand("reportar").setExecutor(this);
        getCommand("reports").setExecutor(this);

        // Inicializa arquivos de configuração
        setupFiles();

        // Carrega configurações iniciais
        carregarConfiguracoes();

        // Carrega punições ativas
        carregarPunicoesAtivas();

        // Inicia verificador de punições
        iniciarVerificadorPunicoes();

        getLogger().info("Plugin JudgementDay ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        salvarDados();
        getLogger().info("Plugin JudgementDay desativado.");
    }

    private void setupFiles() {
        // Arquivo de configuração principal
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Arquivo de punições
        punicoesFile = new File(getDataFolder(), "punicoes.yml");
        if (!punicoesFile.exists()) {
            try {
                punicoesFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        punicoesConfig = YamlConfiguration.loadConfiguration(punicoesFile);

        // Arquivo de reports
        reportsFile = new File(getDataFolder(), "reports.yml");
        if (!reportsFile.exists()) {
            try {
                reportsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reportsConfig = YamlConfiguration.loadConfiguration(reportsFile);

        // Se o arquivo config.yml estiver vazio, cria configurações padrão
        if (config.getConfigurationSection("motivos") == null) {
            criarConfigPadrao();
        }
    }

    private void criarConfigPadrao() {
        // Configuração para punições do tipo "Warn"
        config.set("motivos.ofensa.tipo", "WARN");
        config.set("motivos.ofensa.niveis.1.tempo", "2h");
        config.set("motivos.ofensa.niveis.1.descricao", "Primeira ofensa");
        config.set("motivos.ofensa.niveis.2.tempo", "12h");
        config.set("motivos.ofensa.niveis.2.descricao", "Segunda ofensa");
        config.set("motivos.ofensa.niveis.3.tempo", "24h");
        config.set("motivos.ofensa.niveis.3.descricao", "Terceira ofensa");

        // Configuração para punições do tipo "Mute"
        config.set("motivos.spam.tipo", "MUTE");
        config.set("motivos.spam.niveis.1.tempo", "30m");
        config.set("motivos.spam.niveis.1.descricao", "Primeiro spam");
        config.set("motivos.spam.niveis.2.tempo", "2h");
        config.set("motivos.spam.niveis.2.descricao", "Segundo spam");
        config.set("motivos.spam.niveis.3.tempo", "12h");
        config.set("motivos.spam.niveis.3.descricao", "Terceiro spam");

        config.set("motivos.publicidade.tipo", "MUTE");
        config.set("motivos.publicidade.niveis.1.tempo", "12h");
        config.set("motivos.publicidade.niveis.1.descricao", "Primeira publicidade");
        config.set("motivos.publicidade.niveis.2.tempo", "2d");
        config.set("motivos.publicidade.niveis.2.descricao", "Segunda publicidade");
        config.set("motivos.publicidade.niveis.3.tempo", "7d");
        config.set("motivos.publicidade.niveis.3.descricao", "Terceira publicidade");

        // Configuração para punições do tipo "Ban"
        config.set("motivos.hack.tipo", "BAN");
        config.set("motivos.hack.niveis.1.tempo", "7d");
        config.set("motivos.hack.niveis.1.descricao", "Primeiro uso de hack");
        config.set("motivos.hack.niveis.2.tempo", "30d");
        config.set("motivos.hack.niveis.2.descricao", "Segundo uso de hack");
        config.set("motivos.hack.niveis.3.tempo", "PERMANENTE");
        config.set("motivos.hack.niveis.3.descricao", "Terceiro uso de hack");

        config.set("motivos.abuso.tipo", "BAN");
        config.set("motivos.abuso.niveis.1.tempo", "3d");
        config.set("motivos.abuso.niveis.1.descricao", "Primeiro abuso de bugs");
        config.set("motivos.abuso.niveis.2.tempo", "15d");
        config.set("motivos.abuso.niveis.2.descricao", "Segundo abuso de bugs");
        config.set("motivos.abuso.niveis.3.tempo", "30d");
        config.set("motivos.abuso.niveis.3.descricao", "Terceiro abuso de bugs");

        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void carregarConfiguracoes() {
        // Este método pode ser expandido no futuro para carregar outras configurações
    }

    private void carregarPunicoesAtivas() {
        jogadoresComPunicaoAtiva.clear();

        for (String uuidStr : punicoesConfig.getKeys(false)) {
            ConfigurationSection playerSection = punicoesConfig.getConfigurationSection(uuidStr);

            if (playerSection != null) {
                UUID playerUUID = UUID.fromString(uuidStr);
                ConfigurationSection punicoesSection = playerSection.getConfigurationSection("punicoes");

                if (punicoesSection != null) {
                    for (String punicaoId : punicoesSection.getKeys(false)) {
                        ConfigurationSection punicaoSection = punicoesSection.getConfigurationSection(punicaoId);

                        if (punicaoSection != null && !punicaoSection.getBoolean("revogada")) {
                            String tipo = punicaoSection.getString("tipo");
                            long expiracao = punicaoSection.getLong("expiracao");

                            // Verifica se a punição ainda está válida
                            if (expiracao > System.currentTimeMillis() || expiracao == -1) {
                                if ("MUTE".equals(tipo) || "BAN".equals(tipo)) {
                                    jogadoresComPunicaoAtiva.put(playerUUID, new PunicaoTemporaria(
                                            punicaoId,
                                            tipo,
                                            punicaoSection.getString("motivo"),
                                            expiracao
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void salvarDados() {
        try {
            punicoesConfig.save(punicoesFile);
            reportsConfig.save(reportsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void iniciarVerificadorPunicoes() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            long tempoAtual = System.currentTimeMillis();

            // Cria uma lista para evitar ConcurrentModificationException
            List<UUID> punicoesParaRemover = new ArrayList<>();

            for (Map.Entry<UUID, PunicaoTemporaria> entry : jogadoresComPunicaoAtiva.entrySet()) {
                UUID uuid = entry.getKey();
                PunicaoTemporaria punicao = entry.getValue();

                // Verifica se a punição expirou
                if (punicao.getExpiracao() != -1 && punicao.getExpiracao() <= tempoAtual) {
                    punicoesParaRemover.add(uuid);

                    // Atualiza o status da punição no arquivo
                    String playerPath = uuid.toString() + ".punicoes." + punicao.getId() + ".expirou";
                    punicoesConfig.set(playerPath, true);

                    // Notifica o jogador se estiver online
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        Bukkit.getScheduler().runTask(this, () ->
                                player.sendMessage(prefix + ChatColor.GREEN + "Sua punição por " + punicao.getMotivo() + " expirou!"));
                    }
                }
            }

            // Remove as punições expiradas
            for (UUID uuid : punicoesParaRemover) {
                jogadoresComPunicaoAtiva.remove(uuid);
            }

            if (!punicoesParaRemover.isEmpty()) {
                try {
                    punicoesConfig.save(punicoesFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, VERIFICACAO_PUNICOES_INTERVAL, VERIFICACAO_PUNICOES_INTERVAL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "punir":
                return handlePunirCommand(sender, args);
            case "historico":
                return handleHistoricoCommand(sender, args);
            case "despunir":
                return handleDespunirCommand(sender, args);
            case "reportar":
                return handleReportarCommand(sender, args);
            case "reports":
                return handleReportsCommand(sender);
            default:
                return false;
        }
    }

    private boolean handlePunirCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("judgementday.punir")) {
            sender.sendMessage(prefix + ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + ChatColor.RED + "Uso correto: /punir <jogador>");
            return true;
        }

        String targetName = args[0];
        UUID targetUUID = null;

        // Tenta obter UUID do jogador
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            targetUUID = targetPlayer.getUniqueId();
        } else {
            // Se o jogador não estiver online, tenta obter UUID de jogador offline
            for (String uuidStr : punicoesConfig.getKeys(false)) {
                String storedName = punicoesConfig.getString(uuidStr + ".nome");
                if (storedName != null && storedName.equalsIgnoreCase(targetName)) {
                    targetUUID = UUID.fromString(uuidStr);
                    break;
                }
            }

            if (targetUUID == null) {
                sender.sendMessage(prefix + ChatColor.RED + "Jogador não encontrado. Certifique-se que ele já jogou no servidor.");
                return true;
            }
        }

        if (sender instanceof Player) {
            Player staffPlayer = (Player) sender;
            exibirMenuMotivos(staffPlayer, targetUUID, targetName);
        } else {
            sender.sendMessage(prefix + ChatColor.RED + "Este comando só pode ser executado por um jogador.");
        }

        return true;
    }

    private void exibirMenuMotivos(Player staffPlayer, UUID targetUUID, String targetName) {
        Inventory menu = Bukkit.createInventory(null, 27, ChatColor.RED + "Punir: " + targetName);

        int slot = 0;
        motivoInventoryMap.clear();

        // Obter todas as configurações de motivos
        ConfigurationSection motivosSection = config.getConfigurationSection("motivos");
        if (motivosSection != null) {
            for (String motivo : motivosSection.getKeys(false)) {
                String tipo = motivosSection.getString(motivo + ".tipo", "WARN");
                Material material;

                // Define o material do item baseado no tipo de punição
                switch (tipo) {
                    case "WARN":
                        material = Material.PAPER;
                        break;
                    case "MUTE":
                        material = Material.BOOK;
                        break;
                    case "BAN":
                        material = Material.BARRIER;
                        break;
                    default:
                        material = Material.PAPER;
                        break;
                }

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.RED + formatarMotivo(motivo));

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Tipo: " + ChatColor.GOLD + tipo);
                lore.add("");

                // Adiciona informações sobre níveis de punição
                ConfigurationSection niveisSection = motivosSection.getConfigurationSection(motivo + ".niveis");
                if (niveisSection != null) {
                    for (String nivel : niveisSection.getKeys(false)) {
                        String descricao = niveisSection.getString(nivel + ".descricao", "");
                        String tempo = niveisSection.getString(nivel + ".tempo", "");
                        lore.add(ChatColor.YELLOW + nivel + ". " + ChatColor.WHITE + descricao + " - " + ChatColor.AQUA + tempo);
                    }
                }

                lore.add("");
                lore.add(ChatColor.GREEN + "Clique para selecionar este motivo");

                meta.setLore(lore);
                item.setItemMeta(meta);

                menu.setItem(slot, item);
                motivoInventoryMap.put(material.name() + ":" + slot, motivo + ":" + targetUUID.toString() + ":" + targetName);

                slot++;
                if (slot >= 27) break; // Limite de 27 slots
            }
        }

        staffPlayer.openInventory(menu);
    }

    private void exibirMenuNiveis(Player staffPlayer, String motivo, UUID targetUUID, String targetName) {
        ConfigurationSection motivoSection = config.getConfigurationSection("motivos." + motivo);
        if (motivoSection == null) {
            staffPlayer.sendMessage(prefix + ChatColor.RED + "Configuração inválida para o motivo: " + motivo);
            return;
        }

        String tipo = motivoSection.getString("tipo", "WARN");
        ConfigurationSection niveisSection = motivoSection.getConfigurationSection("niveis");

        if (niveisSection == null) {
            staffPlayer.sendMessage(prefix + ChatColor.RED + "Não há níveis configurados para este motivo.");
            return;
        }

        // Calcula o tamanho necessário para o inventário (múltiplo de 9)
        int niveisCount = niveisSection.getKeys(false).size();
        int inventorySize = ((niveisCount - 1) / 9 + 1) * 9;

        Inventory menu = Bukkit.createInventory(null, inventorySize,
                ChatColor.RED + "Nível: " + formatarMotivo(motivo));

        int nivelAnterior = calcularNivelAnterior(targetUUID, motivo);
        int proximoNivel = nivelAnterior + 1;

        for (String nivel : niveisSection.getKeys(false)) {
            int nivelInt = Integer.parseInt(nivel);
            Material material;

            // Define o material baseado no nível
            if (nivelInt < proximoNivel) {
                material = Material.STAINED_GLASS_PANE; // Cinza para níveis anteriores
                // Dyed color 8 = cinza escuro
            } else if (nivelInt == proximoNivel) {
                material = Material.EMERALD; // Verde para o próximo nível
            } else {
                material = Material.REDSTONE; // Vermelho para níveis futuros
            }

            String descricao = niveisSection.getString(nivel + ".descricao", "");
            String tempo = niveisSection.getString(nivel + ".tempo", "");

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Nível " + nivel + ": " + ChatColor.WHITE + descricao);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Tipo: " + ChatColor.GOLD + tipo);
            lore.add(ChatColor.GRAY + "Duração: " + ChatColor.AQUA + tempo);

            if (nivelInt < proximoNivel) {
                lore.add(ChatColor.RED + "Jogador já recebeu esta punição");
            } else if (nivelInt == proximoNivel) {
                lore.add(ChatColor.GREEN + "Nível recomendado para este jogador");
                lore.add(ChatColor.GREEN + "Clique para aplicar esta punição");
            } else {
                lore.add(ChatColor.YELLOW + "Nível futuro para este jogador");
                lore.add(ChatColor.YELLOW + "Clique para aplicar esta punição");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);

            int slot = nivelInt - 1;
            menu.setItem(slot, item);

            // Armazena informações para o evento de clique
            String mapKey = material.name() + ":" + slot;
            String mapValue = motivo + ":" + targetUUID.toString() + ":" + targetName + ":" + nivel;
            motivoInventoryMap.put(mapKey, mapValue);
        }

        staffPlayer.openInventory(menu);
    }

    private int calcularNivelAnterior(UUID playerUUID, String motivo) {
        int nivelAtual = 0;

        // Verifica se o jogador tem configuração no arquivo de punições
        if (punicoesConfig.contains(playerUUID.toString())) {
            ConfigurationSection punicoesSection = punicoesConfig.getConfigurationSection(playerUUID.toString() + ".punicoes");

            if (punicoesSection != null) {
                // Conta quantas punições do mesmo motivo o jogador já recebeu
                for (String punicaoId : punicoesSection.getKeys(false)) {
                    String motivoPunicao = punicoesSection.getString(punicaoId + ".motivo", "");

                    if (motivoPunicao.equals(motivo) && !punicoesSection.getBoolean(punicaoId + ".revogada", false)) {
                        nivelAtual++;
                    }
                }
            }
        }

        return nivelAtual;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // Verifica se é nosso inventário de punições
        if (title.startsWith(ChatColor.RED + "Punir: ") || title.startsWith(ChatColor.RED + "Nível: ")) {
            event.setCancelled(true); // Cancela o evento para evitar que o item seja movido

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            int slot = event.getSlot();
            Material material = clickedItem.getType();
            String mapKey = material.name() + ":" + slot;

            if (motivoInventoryMap.containsKey(mapKey)) {
                String[] dados = motivoInventoryMap.get(mapKey).split(":");

                if (title.startsWith(ChatColor.RED + "Punir: ")) {
                    // Menu de seleção de motivo
                    String motivo = dados[0];
                    UUID targetUUID = UUID.fromString(dados[1]);
                    String targetName = dados[2];

                    player.closeInventory();
                    exibirMenuNiveis(player, motivo, targetUUID, targetName);
                } else if (title.startsWith(ChatColor.RED + "Nível: ")) {
                    // Menu de seleção de nível
                    String motivo = dados[0];
                    UUID targetUUID = UUID.fromString(dados[1]);
                    String targetName = dados[2];
                    String nivel = dados[3];

                    player.closeInventory();

                    // Armazena as informações para aguardar o link de prova
                    String punicaoInfo = motivo + ":" + targetUUID.toString() + ":" + targetName + ":" + nivel;
                    aguardandoLink.put(player.getUniqueId(), punicaoInfo);

                    player.sendMessage(prefix + ChatColor.YELLOW + "Digite o link da prova para aplicar a punição:");
                }
            }
        } else if (title.equals(ChatColor.RED + "Reports Pendentes")) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                String reportedPlayer = "";
                String reporter = "";

                for (String line : lore) {
                    if (line.startsWith(ChatColor.GRAY + "Reportado: ")) {
                        reportedPlayer = ChatColor.stripColor(line.substring(line.indexOf(":") + 2));
                    } else if (line.startsWith(ChatColor.GRAY + "Reportado por: ")) {
                        reporter = ChatColor.stripColor(line.substring(line.indexOf(":") + 2));
                    }
                }

                if (!reportedPlayer.isEmpty()) {
                    player.closeInventory();

                    // Executa o comando de punir para o jogador reportado
                    Bukkit.dispatchCommand(player, "punir " + reportedPlayer);

                    // Notifica o reporter que a equipe está analisando o report
                    Player reporterPlayer = Bukkit.getPlayer(reporter);
                    if (reporterPlayer != null && reporterPlayer.isOnline()) {
                        reporterPlayer.sendMessage(prefix + ChatColor.GREEN + "Seu report contra " +
                                ChatColor.YELLOW + reportedPlayer + ChatColor.GREEN + " está sendo analisado pela equipe.");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Verifica se o jogador está com Mute ativo
        if (jogadoresComPunicaoAtiva.containsKey(playerUUID)) {
            PunicaoTemporaria punicao = jogadoresComPunicaoAtiva.get(playerUUID);
            if ("MUTE".equals(punicao.getTipo())) {
                event.setCancelled(true);

                long tempo = punicao.getExpiracao();
                String tempoRestante = tempo == -1 ? "permanentemente" : "por " + formatarTempoRestante(tempo);

                player.sendMessage(prefix + ChatColor.RED + "Você está silenciado " + tempoRestante + ".");
                player.sendMessage(ChatColor.RED + "Motivo: " + ChatColor.YELLOW + punicao.getMotivo());
                player.sendMessage(ChatColor.RED + "ID da punição: " + ChatColor.GRAY + punicao.getId());
                return;
            }
        }

        // Verifica se o staff está respondendo com um link para a punição
        if (aguardandoLink.containsKey(playerUUID)) {
            event.setCancelled(true);
            String mensagem = event.getMessage();

            // Verifica se parece um link
            if (mensagem.startsWith("http://") || mensagem.startsWith("https://") || mensagem.contains("youtu") ||
                    mensagem.contains("imgur") || mensagem.contains("discord") || mensagem.contains(".com") ||
                    mensagem.contains(".net") || mensagem.contains(".org")) {

                String punicaoInfo = aguardandoLink.get(playerUUID);
                String[] dados = punicaoInfo.split(":");

                String motivo = dados[0];
                UUID targetUUID = UUID.fromString(dados[1]);
                String targetName = dados[2];
                String nivel = dados[3];

                aplicarPunicao(player, targetUUID, targetName, motivo, Integer.parseInt(nivel), mensagem);
                aguardandoLink.remove(playerUUID);
            } else {
                player.sendMessage(prefix + ChatColor.RED + "Por favor, forneça um link válido para a prova.");
                player.sendMessage(prefix + ChatColor.YELLOW + "Digite o link da prova ou digite 'cancelar' para abortar:");

                if (mensagem.equalsIgnoreCase("cancelar")) {
                    aguardandoLink.remove(playerUUID);
                    player.sendMessage(prefix + ChatColor.YELLOW + "Punição cancelada.");
                }
            }
        }
    }

    private void aplicarPunicao(Player staffPlayer, UUID targetUUID, String targetName, String motivo, int nivel, String linkProva) {
        ConfigurationSection motivoSection = config.getConfigurationSection("motivos." + motivo);
        if (motivoSection == null) {
            staffPlayer.sendMessage(prefix + ChatColor.RED + "Configuração inválida para o motivo: " + motivo);
            return;
        }

        String tipo = motivoSection.getString("tipo", "WARN");
        String tempoStr = motivoSection.getString("niveis." + nivel + ".tempo", "1h");
        String descricao = motivoSection.getString("niveis." + nivel + ".descricao", "");

        // Gera ID único para a punição
        String punicaoId = gerarPunicaoId();

        // Calcula o tempo de expiração
        long expiracao = calcularExpiracao(tempoStr);

        // Registra a punição no arquivo
        String playerPath = targetUUID.toString();
        punicoesConfig.set(playerPath + ".nome", targetName);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".tipo", tipo);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".motivo", motivo);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".descricao", descricao);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".nivel", nivel);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".tempo", tempoStr);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".expiracao", expiracao);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".data", System.currentTimeMillis());
        punicoesConfig.set(playerPath + punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".staff", staffPlayer.getName());
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".linkProva", linkProva);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".revogada", false);
        punicoesConfig.set(playerPath + ".punicoes." + punicaoId + ".expirou", false);

        try {
            punicoesConfig.save(punicoesFile);
        } catch (IOException e) {
            e.printStackTrace();
            staffPlayer.sendMessage(prefix + ChatColor.RED + "Erro ao salvar a punição. Veja o console para mais detalhes.");
            return;
        }

        // Aplica a punição ao jogador
        Player targetPlayer = Bukkit.getPlayer(targetUUID);

        // Registra punição ativa para mute ou ban
        if ("MUTE".equals(tipo) || "BAN".equals(tipo)) {
            jogadoresComPunicaoAtiva.put(targetUUID, new PunicaoTemporaria(
                    punicaoId,
                    tipo,
                    motivo,
                    expiracao
            ));
        }

        // Formata as mensagens de punição
        String tempoFormatado = expiracao == -1 ? "PERMANENTE" : tempoStr;
        String tempoFrase = expiracao == -1 ? "permanentemente" : "por " + tempoStr;

        // Notifica o staff
        staffPlayer.sendMessage("");
        staffPlayer.sendMessage(prefix + ChatColor.GREEN + "Punição aplicada com sucesso!");
        staffPlayer.sendMessage(ChatColor.WHITE + "Jogador: " + ChatColor.YELLOW + targetName);
        staffPlayer.sendMessage(ChatColor.WHITE + "Tipo: " + ChatColor.GOLD + tipo);
        staffPlayer.sendMessage(ChatColor.WHITE + "Motivo: " + ChatColor.YELLOW + formatarMotivo(motivo) + " (Nível " + nivel + ")");
        staffPlayer.sendMessage(ChatColor.WHITE + "Tempo: " + ChatColor.AQUA + tempoFormatado);
        staffPlayer.sendMessage(ChatColor.WHITE + "ID: " + ChatColor.GRAY + punicaoId);
        staffPlayer.sendMessage("");

        // Notifica o servidor
        String mensagemServidor = prefix + ChatColor.YELLOW + targetName +
                ChatColor.WHITE + " foi " + getTipoPunicaoEmPortugues(tipo) + " " + tempoFrase +
                ChatColor.WHITE + " por " + ChatColor.YELLOW + formatarMotivo(motivo) +
                ChatColor.WHITE + " (" + staffPlayer.getName() + ")";

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(mensagemServidor);
        }

        // Executa ações específicas baseadas no tipo de punição
        switch (tipo) {
            case "WARN":
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetPlayer.sendMessage("");
                    targetPlayer.sendMessage(prefix + ChatColor.RED + "Você recebeu um aviso!");
                    targetPlayer.sendMessage(ChatColor.WHITE + "Motivo: " + ChatColor.YELLOW + formatarMotivo(motivo));
                    targetPlayer.sendMessage(ChatColor.WHITE + "Staff: " + ChatColor.YELLOW + staffPlayer.getName());
                    targetPlayer.sendMessage(ChatColor.WHITE + "ID: " + ChatColor.GRAY + punicaoId);
                    targetPlayer.sendMessage("");
                }
                break;

            case "MUTE":
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetPlayer.sendMessage("");
                    targetPlayer.sendMessage(prefix + ChatColor.RED + "Você foi silenciado " + tempoFrase + "!");
                    targetPlayer.sendMessage(ChatColor.WHITE + "Motivo: " + ChatColor.YELLOW + formatarMotivo(motivo));
                    targetPlayer.sendMessage(ChatColor.WHITE + "Staff: " + ChatColor.YELLOW + staffPlayer.getName());
                    targetPlayer.sendMessage(ChatColor.WHITE + "ID: " + ChatColor.GRAY + punicaoId);
                    targetPlayer.sendMessage("");
                }
                break;

            case "BAN":
                // Se o jogador estiver online, desconecta-o com mensagem
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    String banMessage =
                            ChatColor.RED + "Você foi banido do servidor!\n\n" +
                                    ChatColor.WHITE + "Motivo: " + ChatColor.YELLOW + formatarMotivo(motivo) + "\n" +
                                    ChatColor.WHITE + "Duração: " + ChatColor.AQUA + tempoFormatado + "\n" +
                                    ChatColor.WHITE + "Staff: " + ChatColor.YELLOW + staffPlayer.getName() + "\n" +
                                    ChatColor.WHITE + "ID: " + ChatColor.GRAY + punicaoId + "\n\n" +
                                    ChatColor.WHITE + "Para contestar, entre em contato pelo Discord.";

                    targetPlayer.kickPlayer(banMessage);
                }
                break;
        }
    }

    private boolean handleHistoricoCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("judgementday.historico")) {
            sender.sendMessage(prefix + ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + ChatColor.RED + "Uso correto: /historico <jogador>");
            return true;
        }

        String targetName = args[0];
        UUID targetUUID = null;

        // Tenta obter UUID do jogador
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            targetUUID = targetPlayer.getUniqueId();
        } else {
            // Tenta buscar de jogadores offline
            for (String uuidStr : punicoesConfig.getKeys(false)) {
                String storedName = punicoesConfig.getString(uuidStr + ".nome");
                if (storedName != null && storedName.equalsIgnoreCase(targetName)) {
                    targetUUID = UUID.fromString(uuidStr);
                    break;
                }
            }

            if (targetUUID == null) {
                sender.sendMessage(prefix + ChatColor.RED + "Jogador não encontrado.");
                return true;
            }
        }

        // Obtém o histórico do jogador
        exibirHistorico(sender, targetUUID, targetName);

        return true;
    }

    private void exibirHistorico(CommandSender sender, UUID targetUUID, String targetName) {
        String playerPath = targetUUID.toString();

        if (!punicoesConfig.contains(playerPath + ".punicoes")) {
            sender.sendMessage(prefix + ChatColor.GREEN + targetName + " não tem histórico de punições.");
            return;
        }

        ConfigurationSection punicoesSection = punicoesConfig.getConfigurationSection(playerPath + ".punicoes");
        if (punicoesSection == null || punicoesSection.getKeys(false).isEmpty()) {
            sender.sendMessage(prefix + ChatColor.GREEN + targetName + " não tem histórico de punições.");
            return;
        }

        // Ordena as punições por data (mais recente primeiro)
        List<String> punicaoIds = new ArrayList<>(punicoesSection.getKeys(false));
        punicaoIds.sort((id1, id2) -> {
            long data1 = punicoesSection.getLong(id1 + ".data", 0);
            long data2 = punicoesSection.getLong(id2 + ".data", 0);
            return Long.compare(data2, data1); // Ordem decrescente
        });

        sender.sendMessage("");
        sender.sendMessage(prefix + ChatColor.YELLOW + "Histórico de " + targetName + ":");

        int count = 0;
        for (String punicaoId : punicaoIds) {
            ConfigurationSection punicaoSection = punicoesSection.getConfigurationSection(punicaoId);
            if (punicaoSection == null) continue;

            String tipo = punicaoSection.getString("tipo", "DESCONHECIDO");
            String motivo = punicaoSection.getString("motivo", "Desconhecido");
            String staff = punicaoSection.getString("staff", "Desconhecido");
            String tempoStr = punicaoSection.getString("tempo", "?");
            boolean revogada = punicaoSection.getBoolean("revogada", false);
            boolean expirou = punicaoSection.getBoolean("expirou", false);
            long data = punicaoSection.getLong("data", 0);

            String status;
            if (revogada) {
                status = ChatColor.GREEN + "REVOGADA";
            } else if (expirou) {
                status = ChatColor.YELLOW + "EXPIRADA";
            } else {
                long expiracao = punicaoSection.getLong("expiracao", -1);
                if (expiracao == -1) {
                    status = ChatColor.RED + "PERMANENTE";
                } else if (expiracao > System.currentTimeMillis()) {
                    status = ChatColor.RED + "ATIVA";
                } else {
                    status = ChatColor.YELLOW + "EXPIRADA";
                }
            }

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "ID: " + punicaoId + " - " + status);
            sender.sendMessage(ChatColor.WHITE + "Tipo: " + ChatColor.GOLD + tipo);
            sender.sendMessage(ChatColor.WHITE + "Motivo: " + ChatColor.YELLOW + formatarMotivo(motivo));
            sender.sendMessage(ChatColor.WHITE + "Aplicada por: " + ChatColor.YELLOW + staff);
            sender.sendMessage(ChatColor.WHITE + "Duração: " + ChatColor.AQUA + tempoStr);
            sender.sendMessage(ChatColor.WHITE + "Data: " + ChatColor.AQUA + formatarData(data));

            count++;
            // Limita a 10 punições por vez para não sobrecarregar o chat
            if (count >= 10) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.YELLOW + "Mostrando 10 punições mais recentes. Use /historico " + targetName + " <página> para ver mais.");
                break;
            }
        }

        sender.sendMessage("");
    }

    private boolean handleDespunirCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("judgementday.despunir")) {
            sender.sendMessage(prefix + ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + ChatColor.RED + "Uso correto: /despunir <ID>");
            return true;
        }

        String punicaoId = args[0];

        // Busca a punição pelo ID
        for (String uuidStr : punicoesConfig.getKeys(false)) {
            ConfigurationSection playerSection = punicoesConfig.getConfigurationSection(uuidStr);
            if (playerSection != null && playerSection.contains("punicoes." + punicaoId)) {
                String playerName = playerSection.getString("nome", "Desconhecido");
                revogarPunicao(sender, UUID.fromString(uuidStr), playerName, punicaoId);
                return true;
            }
        }

        sender.sendMessage(prefix + ChatColor.RED + "Punição com ID " + punicaoId + " não encontrada.");
        return true;
    }

    private void revogarPunicao(CommandSender sender, UUID targetUUID, String targetName, String punicaoId) {
        String playerPath = targetUUID.toString();
        String punicaoPath = playerPath + ".punicoes." + punicaoId;

        if (!punicoesConfig.contains(punicaoPath)) {
            sender.sendMessage(prefix + ChatColor.RED + "Punição com ID " + punicaoId + " não encontrada.");
            return;
        }

        // Verifica se a punição já foi revogada
        if (punicoesConfig.getBoolean(punicaoPath + ".revogada", false)) {
            sender.sendMessage(prefix + ChatColor.RED + "Esta punição já foi revogada anteriormente.");
            return;
        }

        // Revoga a punição
        punicoesConfig.set(punicaoPath + ".revogada", true);

        // Registra quem revogou
        punicoesConfig.set(punicaoPath + ".revogadaPor", sender.getName());
        punicoesConfig.set(punicaoPath + ".dataRevogacao", System.currentTimeMillis());

        // Salva as alterações
        try {
            punicoesConfig.save(punicoesFile);
        } catch (IOException e) {
            e.printStackTrace();
            sender.sendMessage(prefix + ChatColor.RED + "Erro ao salvar a revogação da punição.");
            return;
        }

        // Remove a punição ativa (se aplicável)
        jogadoresComPunicaoAtiva.remove(targetUUID);

        // Obtém informações da punição
        String tipo = punicoesConfig.getString(punicaoPath + ".tipo", "DESCONHECIDO");
        String motivo = punicoesConfig.getString(punicaoPath + ".motivo", "Desconhecido");

        // Notifica o staff
        sender.sendMessage("");
        sender.sendMessage(prefix + ChatColor.GREEN + "Punição revogada com sucesso!");
        sender.sendMessage(ChatColor.WHITE + "Jogador: " + ChatColor.YELLOW + targetName);
        sender.sendMessage(ChatColor.WHITE + "Tipo: " + ChatColor.GOLD + tipo);
        sender.sendMessage(ChatColor.WHITE + "Motivo: " + ChatColor.YELLOW + formatarMotivo(motivo));
        sender.sendMessage("");

        // Notifica o servidor
        String mensagemServidor = prefix + ChatColor.YELLOW + targetName +
                ChatColor.WHITE + " teve sua punição de " + ChatColor.GOLD + tipo +
                ChatColor.WHITE + " por " + ChatColor.YELLOW + formatarMotivo(motivo) +
                ChatColor.WHITE + " revogada por " + ChatColor.YELLOW + sender.getName();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(mensagemServidor);
        }

        // Notifica o jogador se estiver online
        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage("");
            targetPlayer.sendMessage(prefix + ChatColor.GREEN + "Sua punição foi revogada!");
            targetPlayer.sendMessage(ChatColor.WHITE + "Tipo: " + ChatColor.GOLD + tipo);
            targetPlayer.sendMessage(ChatColor.WHITE + "Motivo original: " + ChatColor.YELLOW + formatarMotivo(motivo));
            targetPlayer.sendMessage(ChatColor.WHITE + "Revogada por: " + ChatColor.YELLOW + sender.getName());
            targetPlayer.sendMessage("");
        }
    }

    private boolean handleReportarCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + ChatColor.RED + "Este comando só pode ser executado por um jogador.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(prefix + ChatColor.RED + "Uso correto: /reportar <jogador> <motivo>");
            return true;
        }

        String targetName = args[0];

        // Verifica se o jogador existe
        Player targetPlayer = Bukkit.getPlayer(targetName);
        UUID targetUUID = null;

        if (targetPlayer != null) {
            targetUUID = targetPlayer.getUniqueId();
        } else {
            // Tenta buscar de jogadores offline
            for (String uuidStr : punicoesConfig.getKeys(false)) {
                String storedName = punicoesConfig.getString(uuidStr + ".nome");
                if (storedName != null && storedName.equalsIgnoreCase(targetName)) {
                    targetUUID = UUID.fromString(uuidStr);
                    targetName = storedName; // Usa o nome exato armazenado
                    break;
                }
            }

            if (targetUUID == null) {
                player.sendMessage(prefix + ChatColor.RED + "Jogador não encontrado.");
                return true;
            }
        }

        // Não permite reportar a si mesmo
        if (player.getUniqueId().equals(targetUUID)) {
            player.sendMessage(prefix + ChatColor.RED + "Você não pode reportar a si mesmo.");
            return true;
        }

        // Monta o motivo
        StringBuilder motivo = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            motivo.append(args[i]).append(" ");
        }

        // Registra o report
        String reportId = gerarReportId();

        reportsConfig.set(reportId + ".reportado", targetName);
        reportsConfig.set(reportId + ".reportadoUUID", targetUUID.toString());
        reportsConfig.set(reportId + ".reporter", player.getName());
        reportsConfig.set(reportId + ".reporterUUID", player.getUniqueId().toString());
        reportsConfig.set(reportId + ".motivo", motivo.toString().trim());
        reportsConfig.set(reportId + ".data", System.currentTimeMillis());
        reportsConfig.set(reportId + ".resolvido", false);

        try {
            reportsConfig.save(reportsFile);
        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage(prefix + ChatColor.RED + "Erro ao salvar o report.");
            return true;
        }

        // Mensagem para o jogador
        player.sendMessage(prefix + ChatColor.GREEN + "Seu report contra " + targetName + " foi enviado com sucesso!");
        player.sendMessage(ChatColor.GREEN + "A equipe irá analisar seu report em breve.");

        // Notifica os staffs online
        for (Player staffPlayer : Bukkit.getOnlinePlayers()) {
            if (staffPlayer.hasPermission("judgementday.reports")) {
                staffPlayer.sendMessage("");
                staffPlayer.sendMessage(prefix + ChatColor.YELLOW + "Novo report recebido!");
                staffPlayer.sendMessage(ChatColor.WHITE + "Reportado: " + ChatColor.YELLOW + targetName);
                staffPlayer.sendMessage(ChatColor.WHITE + "Reportado por: " + ChatColor.YELLOW + player.getName());
                staffPlayer.sendMessage(ChatColor.WHITE + "Motivo: " + ChatColor.YELLOW + motivo.toString().trim());
                staffPlayer.sendMessage(ChatColor.WHITE + "Use " + ChatColor.YELLOW + "/reports" + ChatColor.WHITE + " para ver todos os reports pendentes.");
                staffPlayer.sendMessage("");
            }
        }

        return true;
    }

    private boolean handleReportsCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + ChatColor.RED + "Este comando só pode ser executado por um jogador.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("judgementday.reports")) {
            player.sendMessage(prefix + ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        exibirMenuReports(player);
        return true;
    }

    private void exibirMenuReports(Player staffPlayer) {
        // Obter reports não resolvidos
        List<String> reportsPendentes = new ArrayList<>();

        for (String reportId : reportsConfig.getKeys(false)) {
            if (!reportsConfig.getBoolean(reportId + ".resolvido", false)) {
                reportsPendentes.add(reportId);
            }
        }

        if (reportsPendentes.isEmpty()) {
            staffPlayer.sendMessage(prefix + ChatColor.GREEN + "Não há reports pendentes.");
            return;
        }

        // Calcula o tamanho necessário para o inventário (múltiplo de 9)
        int reportCount = reportsPendentes.size();
        int inventorySize = ((reportCount - 1) / 9 + 1) * 9;
        inventorySize = Math.min(inventorySize, 54); // Máximo de 54 slots (6 linhas)

        Inventory menu = Bukkit.createInventory(null, inventorySize, ChatColor.RED + "Reports Pendentes");

        int slot = 0;
        for (String reportId : reportsPendentes) {
            if (slot >= inventorySize) break; // Limita ao tamanho do inventário

            String reportado = reportsConfig.getString(reportId + ".reportado", "Desconhecido");
            String reporter = reportsConfig.getString(reportId + ".reporter", "Desconhecido");
            String motivo = reportsConfig.getString(reportId + ".motivo", "Desconhecido");
            long data = reportsConfig.getLong(reportId + ".data", 0);

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Report: " + reportId);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Reportado: " + ChatColor.YELLOW + reportado);
            lore.add(ChatColor.GRAY + "Reportado por: " + ChatColor.YELLOW + reporter);
            lore.add(ChatColor.GRAY + "Motivo: " + ChatColor.YELLOW + motivo);
            lore.add(ChatColor.GRAY + "Data: " + ChatColor.AQUA + formatarData(data));
            lore.add("");
            lore.add(ChatColor.GREEN + "Clique para punir o jogador");

            meta.setLore(lore);
            item.setItemMeta(meta);

            menu.setItem(slot, item);
            slot++;
        }

        staffPlayer.openInventory(menu);
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Verifica se o jogador está banido
        if (jogadoresComPunicaoAtiva.containsKey(playerUUID)) {
            PunicaoTemporaria punicao = jogadoresComPunicaoAtiva.get(playerUUID);

            if ("BAN".equals(punicao.getTipo())) {
                long tempo = punicao.getExpiracao();

                // Verifica se o banimento ainda está ativo
                if (tempo > System.currentTimeMillis() || tempo == -1) {
                    String tempoRestante = tempo == -1 ? "permanentemente" : "por " + formatarTempoRestante(tempo);
                    String motivo = punicao.getMotivo();
                    String punicaoId = punicao.getId();

                    // Obtém informações adicionais
                    String staffName = "Desconhecido";
                    for (String uuidStr : punicoesConfig.getKeys(false)) {
                        ConfigurationSection playerSection = punicoesConfig.getConfigurationSection(uuidStr);
                        if (playerSection != null && playerSection.contains("punicoes." + punicaoId)) {
                            staffName = playerSection.getString("punicoes." + punicaoId + ".staff", "Desconhecido");
                            break;
                        }
                    }

                    // Formata a mensagem de kick
                    String banMessage =
                            ChatColor.RED + "Você está banido do servidor!\n\n" +
                                    ChatColor.WHITE + "Motivo: " + ChatColor.YELLOW + formatarMotivo(motivo) + "\n" +
                                    ChatColor.WHITE + "Duração: " + ChatColor.AQUA + tempoRestante + "\n" +
                                    ChatColor.WHITE + "Staff: " + ChatColor.YELLOW + staffName + "\n" +
                                    ChatColor.WHITE + "ID: " + ChatColor.GRAY + punicaoId + "\n\n" +
                                    ChatColor.WHITE + "Para contestar, entre em contato pelo Discord.";

                    event.disallow(PlayerLoginEvent.Result.KICK_BANNED, banMessage);
                } else {
                    // Se o banimento expirou, remove da lista
                    jogadoresComPunicaoAtiva.remove(playerUUID);

                    // Atualiza o status da punição no arquivo
                    for (String uuidStr : punicoesConfig.getKeys(false)) {
                        UUID uuid = UUID.fromString(uuidStr);
                        if (uuid.equals(playerUUID)) {
                            String punicaoPath = uuidStr + ".punicoes." + punicao.getId();
                            if (punicoesConfig.contains(punicaoPath)) {
                                punicoesConfig.set(punicaoPath + ".expirou", true);
                                try {
                                    punicoesConfig.save(punicoesFile);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    // Utilitários

    private String gerarPunicaoId() {
        // Gera um ID único de 8 caracteres
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    private String gerarReportId() {
        // Gera um ID único para reports
        return "R" + System.currentTimeMillis() % 10000 + new Random().nextInt(1000);
    }

    private long calcularExpiracao(String tempoStr) {
        if (tempoStr.equalsIgnoreCase("PERMANENTE")) {
            return -1; // Indica punição permanente
        }

        long duracao = 0;
        String tempoLimpo = tempoStr.toLowerCase();

        try {
            if (tempoLimpo.contains("s")) {
                int segundos = Integer.parseInt(tempoLimpo.replace("s", ""));
                duracao = segundos * 1000L;
            } else if (tempoLimpo.contains("m") && !tempoLimpo.contains("mo")) {
                int minutos = Integer.parseInt(tempoLimpo.replace("m", ""));
                duracao = minutos * 60 * 1000L;
            } else if (tempoLimpo.contains("h")) {
                int horas = Integer.parseInt(tempoLimpo.replace("h", ""));
                duracao = horas * 60 * 60 * 1000L;
            } else if (tempoLimpo.contains("d")) {
                int dias = Integer.parseInt(tempoLimpo.replace("d", ""));
                duracao = dias * 24 * 60 * 60 * 1000L;
            } else if (tempoLimpo.contains("mo")) {
                int meses = Integer.parseInt(tempoLimpo.replace("mo", ""));
                duracao = meses * 30 * 24 * 60 * 60 * 1000L;
            } else if (tempoLimpo.contains("y")) {
                int anos = Integer.parseInt(tempoLimpo.replace("y", ""));
                duracao = anos * 365 * 24 * 60 * 60 * 1000L;
            } else {
                // Se não tiver unidade, assume segundos
                duracao = Integer.parseInt(tempoLimpo) * 1000L;
            }
        } catch (NumberFormatException e) {
            return 3600 * 1000L; // Padrão: 1 hora
        }

        return System.currentTimeMillis() + duracao;
    }

    private String formatarTempoRestante(long expiracao) {
        long tempoRestante = expiracao - System.currentTimeMillis();

        if (tempoRestante <= 0) {
            return "0s";
        }

        long segundos = tempoRestante / 1000 % 60;
        long minutos = tempoRestante / (60 * 1000) % 60;
        long horas = tempoRestante / (60 * 60 * 1000) % 24;
        long dias = tempoRestante / (24 * 60 * 60 * 1000);

        StringBuilder sb = new StringBuilder();

        if (dias > 0) {
            sb.append(dias).append("d ");
        }

        if (horas > 0 || sb.length() > 0) {
            sb.append(horas).append("h ");
        }

        if (minutos > 0 || sb.length() > 0) {
            sb.append(minutos).append("m ");
        }

        sb.append(segundos).append("s");

        return sb.toString().trim();
    }

    private String formatarData(long timestamp) {
        return dateFormat.format(new Date(timestamp));
    }

    private String formatarMotivo(String motivo) {
        // Formata o motivo para exibição (primeira letra maiúscula, resto minúsculo)
        if (motivo.isEmpty()) return motivo;

        return motivo.substring(0, 1).toUpperCase() + motivo.substring(1).toLowerCase();
    }

    private String getTipoPunicaoEmPortugues(String tipo) {
        switch (tipo) {
            case "WARN":
                return "advertido";
            case "MUTE":
                return "silenciado";
            case "BAN":
                return "banido";
            default:
                return "punido";
        }
    }