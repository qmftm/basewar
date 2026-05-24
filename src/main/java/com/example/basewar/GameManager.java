package com.example.basewar;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Particle;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private final JavaPlugin plugin;
    private boolean gameInProgress = false;
    private boolean isInvincible = false;

    private final Map<Team, Set<UUID>> teams = new HashMap<>();
    private final Map<Team, Boolean> beaconStatus = new HashMap<>();
    private final Map<Team, Boolean> beaconEverPlaced = new HashMap<>();
    private final Map<Team, Location> beaconLocations = new HashMap<>();
    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private final Map<UUID, Boolean> teamChatStatus = new HashMap<>();
    private final Map<UUID, BukkitTask> miningFatigueTasks = new HashMap<>();
    private final ScoreboardManager scoreboardManager;
    private Scoreboard mainScoreboard;
    private Location globalSpawnLocation;

    private int invincibilityDuration = 10;
    private BossBar invincibilityBossBar;
    private BukkitTask invincibilityTask;

    private boolean beaconPlacementRestricted = false;
    private int minX, maxX, minY, maxY, minZ, maxZ;

    public static final String KIT_GUI_TITLE = "§x§0§0§8§0§f§f기본 아이템 설정";
    public static final String GAMEMODE_GUI_TITLE = "§f게임모드 설정";
    public static final String HARDCORE_REVIVE_GUI_TITLE = "§4하드코어 부활";
    private List<ItemStack> kitItems = new ArrayList<>();
    private GameModeType currentGameMode = GameModeType.DEFAULT;

    // 하드코어 모드 관련
    private final Map<UUID, Team> hardcoreDeadPlayers = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private int hardcoreReviveCost = 32;

    // 관전 관련 (팀 복귀 가능한 관전자)
    private final Set<UUID> gameSpectators = new HashSet<>();

    // 점령전 관련
    private final Map<Team, Double> captureProgress = new HashMap<>();
    private final Map<Team, Team> capturingTeam = new HashMap<>();
    private final Map<Team, BossBar> conquestBossBars = new HashMap<>();
    private BukkitTask conquestTickTask;
    private BukkitTask particleTask;
    private int captureRadius = 15;
    private double captureRate = 5.0;

    public GameManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfigData();
        for (Team team : Team.values()) {
            teams.put(team, new HashSet<>());
            beaconStatus.put(team, false);
            beaconEverPlaced.put(team, false);
        }

        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.mainScoreboard = scoreboardManager.getMainScoreboard();

        for (Team teamEnum : Team.values()) {
            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(teamEnum.name());
            if (sbTeam == null) {
                sbTeam = mainScoreboard.registerNewTeam(teamEnum.name());
            }
            sbTeam.setPrefix(teamEnum.getChatColor() + "");
            sbTeam.setColor(teamEnum.getChatColor());
            sbTeam.setCanSeeFriendlyInvisibles(false);
            sbTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        }
        loadKit();
    }

    private void loadConfigData() {
        plugin.saveDefaultConfig(); // 만약 config.yml이 없다면 생성
        plugin.reloadConfig(); // 최신 설정 불러오기

        this.invincibilityDuration = plugin.getConfig().getInt("invincibility-duration", 10);
        this.captureRadius = plugin.getConfig().getInt("conquest.capture-radius", 15);
        this.captureRate = plugin.getConfig().getDouble("conquest.capture-rate", 5.0);
        this.hardcoreReviveCost = plugin.getConfig().getInt("hardcore.revive-cost", 32);

        ConfigurationSection teamsSection = plugin.getConfig().getConfigurationSection("teams");
        if (teamsSection == null) {
            plugin.getLogger().severe("config.yml에 'teams' 설정이 없습니다! 플러그인을 비활성화합니다.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }

        for (String teamName : teamsSection.getKeys(false)) {
            try {
                Team.valueOf(teamName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("'" + teamName + "'은(는) 유효한 팀 이름이 아닙니다. (config.yml)");
            }
        }

        ConfigurationSection spawnSection = plugin.getConfig().getConfigurationSection("global-spawn");
        if (spawnSection != null) {
            this.globalSpawnLocation = getLocationFromConfig(spawnSection);
        }

        ConfigurationSection beaconRestrictions = plugin.getConfig().getConfigurationSection("beacon-placement-restrictions");
        if (beaconRestrictions != null) {
            this.beaconPlacementRestricted = beaconRestrictions.getBoolean("enabled", false);
            this.minX = beaconRestrictions.getInt("min-x", -1000);
            this.maxX = beaconRestrictions.getInt("max-x", 1000);
            this.minY = beaconRestrictions.getInt("min-y", 0);
            this.maxY = beaconRestrictions.getInt("max-y", 256);
            this.minZ = beaconRestrictions.getInt("min-z", -1000);
            this.maxZ = beaconRestrictions.getInt("max-z", 1000);
        }
    }

    public boolean isGameSpectator(UUID uuid) {
        return gameSpectators.contains(uuid);
    }

    public boolean canSetGameSpectator(Player player) {
        if (!gameInProgress) return true;
        Team team = getPlayerTeam(player);
        if (team == null) return true;
        long aliveCount = teams.get(team).stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline() && p.getGameMode() == GameMode.SURVIVAL)
                .count();
        return aliveCount > 1;
    }

    public void setGameSpectator(Player player) {
        gameSpectators.add(player.getUniqueId());

        // 스코어보드 팀에서 시각적으로 분리
        Team team = getPlayerTeam(player);
        if (team != null) {
            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(team.name());
            if (sbTeam != null) sbTeam.removeEntry(player.getName());
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.setDisplayName(ChatColor.GRAY + player.getName() + ChatColor.RESET);
        player.setPlayerListName(ChatColor.GRAY + "[관전] " + player.getName() + ChatColor.RESET);
        player.sendMessage("§7관전 모드로 전환되었습니다. 다시 명령어를 사용하면 원래 팀으로 돌아옵니다.");
    }

    public void restoreGameSpectator(Player player, org.bukkit.command.CommandSender sender) {
        if (!gameSpectators.remove(player.getUniqueId())) {
            sender.sendMessage("§c해당 플레이어는 관전 모드가 아닙니다.");
            return;
        }

        Team originalTeam = getPlayerTeam(player);
        if (originalTeam == null) {
            sender.sendMessage("§c팀 정보가 없어 복귀할 수 없습니다.");
            return;
        }

        boolean eliminated = beaconEverPlaced.getOrDefault(originalTeam, false)
                && !beaconStatus.getOrDefault(originalTeam, false);

        if (eliminated) {
            player.sendMessage("§c원래 팀(" + originalTeam.getChatColor() + originalTeam.getDisplayName()
                    + "§c)이 탈락하여 복귀할 수 없습니다.");
            if (sender != player) {
                sender.sendMessage("§c" + player.getName() + "의 팀이 탈락하여 복귀할 수 없습니다.");
            }
            player.setGameMode(GameMode.SPECTATOR);
            return;
        }

        player.setGameMode(GameMode.SURVIVAL);
        addPlayerToTeam(player, originalTeam);
        player.sendMessage("§a" + originalTeam.getChatColor() + originalTeam.getDisplayName() + " §a팀으로 복귀했습니다.");
        if (sender != player) {
            sender.sendMessage("§a" + player.getName() + "을(를) "
                    + originalTeam.getChatColor() + originalTeam.getDisplayName() + " §a팀으로 복귀시켰습니다.");
        }
    }

    public void reloadPluginConfig() {
        loadConfigData();
    }

    public boolean isBeaconPlacementAllowed(Location location) {
        if (!beaconPlacementRestricted) {
            return true;
        }
        return location.getX() >= minX && location.getX() <= maxX &&
               location.getY() >= minY && location.getY() <= maxY &&
               location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    public void openGameModeGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GAMEMODE_GUI_TITLE);

        ItemStack filler = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler.clone());
        }

        for (GameModeType mode : GameModeType.values()) {
            gui.setItem(mode.getSlot(), buildGameModeItem(mode));
        }

        player.openInventory(gui);
    }

    private ItemStack buildGameModeItem(GameModeType mode) {
        boolean selected = (currentGameMode == mode);
        ItemStack item = new ItemStack(mode.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((selected ? "§a" : "§f") + mode.getDisplayName());

        List<String> lore = new ArrayList<>(mode.getLore());
        if (selected) {
            lore.add("");
            lore.add("§a▶ 선택됨");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void setCurrentGameMode(GameModeType mode) {
        this.currentGameMode = mode;
    }

    public GameModeType getCurrentGameMode() {
        return currentGameMode;
    }

    // ======================== 점령전 로직 ========================

    private void startConquestTasks() {
        // 1초마다 점령 진행
        conquestTickTask = new BukkitRunnable() {
            @Override public void run() { tickConquest(); }
        }.runTaskTimer(plugin, 20L, 20L);

        // 0.5초마다 파티클 표시
        particleTask = new BukkitRunnable() {
            @Override public void run() { spawnZoneParticles(); }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void stopConquestTasks() {
        if (conquestTickTask != null && !conquestTickTask.isCancelled()) conquestTickTask.cancel();
        if (particleTask != null && !particleTask.isCancelled()) particleTask.cancel();
        for (BossBar bar : conquestBossBars.values()) { bar.setVisible(false); bar.removeAll(); }
        conquestBossBars.clear();
    }

    private void createConquestBossBar(Team owningTeam) {
        BossBar bar = Bukkit.createBossBar(
                owningTeam.getChatColor() + owningTeam.getDisplayName() + " §f- §e0%",
                owningTeam.getBarColor(), org.bukkit.boss.BarStyle.SOLID);
        bar.setProgress(0.0);
        bar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        conquestBossBars.put(owningTeam, bar);
    }

    public void addPlayerToConquestBossBars(Player player) {
        for (BossBar bar : conquestBossBars.values()) bar.addPlayer(player);
    }

    private void tickConquest() {
        for (Team owningTeam : Team.values()) {
            Location beaconLoc = beaconLocations.get(owningTeam);
            if (beaconLoc == null) continue;

            // 구역 내 팀별 인원 카운트
            Map<Team, Integer> counts = new HashMap<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                Team t = getPlayerTeam(p);
                if (t == null) continue;
                double dx = Math.abs(p.getLocation().getX() - beaconLoc.getX());
                double dy = Math.abs(p.getLocation().getY() - beaconLoc.getY());
                double dz = Math.abs(p.getLocation().getZ() - beaconLoc.getZ());
                if (dx <= captureRadius && dy <= captureRadius && dz <= captureRadius) {
                    counts.merge(t, 1, Integer::sum);
                }
            }

            BossBar bar = conquestBossBars.get(owningTeam);
            double progress = captureProgress.getOrDefault(owningTeam, 0.0);
            // capturingTeam = 현재 게이지를 소유한 팀 (구역을 떠나도 유지)
            Team progressOwner = capturingTeam.get(owningTeam);
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();

            if (total == 0) {
                // 아무도 없으면 게이지·소유팀 그대로 유지
                org.bukkit.boss.BarColor color = progressOwner != null
                        ? progressOwner.getBarColor() : owningTeam.getBarColor();
                updateBossBar(bar, owningTeam, color, progress);
                continue;
            }

            // 최다 인원 팀 탐색
            Team majority = null;
            int maxCount = 0;
            for (Map.Entry<Team, Integer> e : counts.entrySet()) {
                if (e.getValue() > maxCount) { maxCount = e.getValue(); majority = e.getKey(); }
            }
            int others = total - maxCount;

            if (majority != null && majority != owningTeam && maxCount > others) {
                if (progressOwner == null || progressOwner == majority) {
                    // 게이지 없거나 같은 팀 → 점령 진행
                    if (progressOwner == null) {
                        capturingTeam.put(owningTeam, majority);
                        notifyOwnerTeam(owningTeam, majority);
                    }
                    progress = Math.min(progress + captureRate, 100.0);
                    captureProgress.put(owningTeam, progress);
                    updateBossBar(bar, owningTeam, majority.getBarColor(), progress);
                    if (progress >= 100.0) captureBeacon(owningTeam, majority);
                } else {
                    // 다른 팀이 구역 진입 → 기존 팀 게이지 감소
                    progress = Math.max(0.0, progress - captureRate);
                    captureProgress.put(owningTeam, progress);
                    updateBossBar(bar, owningTeam, majority.getBarColor(), progress);
                    if (progress <= 0.0) {
                        // 소진 완료 → majority 팀 점령 시작
                        capturingTeam.put(owningTeam, majority);
                        notifyOwnerTeam(owningTeam, majority);
                    }
                }
            } else if (maxCount == others) {
                // 동수 → 흰색 보스바, 진행 멈춤
                updateBossBar(bar, owningTeam, org.bukkit.boss.BarColor.WHITE, progress);

            } else if (majority == owningTeam && maxCount > others) {
                // 아군 다수 → 방어, 게이지 감소
                if (progress > 0) {
                    progress = Math.max(0.0, progress - captureRate / 2.0);
                    captureProgress.put(owningTeam, progress);
                    if (progress <= 0.0) capturingTeam.remove(owningTeam);
                } else {
                    capturingTeam.remove(owningTeam);
                }
                updateBossBar(bar, owningTeam, owningTeam.getBarColor(), progress);
            }
        }
    }

    private void updateBossBar(BossBar bar, Team owningTeam, org.bukkit.boss.BarColor color, double progress) {
        if (bar == null) return;
        bar.setColor(color);
        bar.setProgress(Math.max(0.0, Math.min(progress / 100.0, 1.0)));
        bar.setTitle(owningTeam.getChatColor() + owningTeam.getDisplayName() +
                " §f- §e" + String.format("%.0f", progress) + "%");
    }

    private void notifyOwnerTeam(Team owningTeam, Team attacker) {
        String msg = "§c[경고] §f" + attacker.getChatColor() + attacker.getDisplayName() +
                " §f팀이 " + owningTeam.getChatColor() + owningTeam.getDisplayName() +
                " §f팀의 신호기를 점령하기 시작했습니다!";
        for (UUID uuid : teams.get(owningTeam)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.8f);
            }
        }
    }

    private void captureBeacon(Team owningTeam, Team capturer) {
        Location beaconLoc = beaconLocations.get(owningTeam);
        if (beaconLoc == null) return;

        beaconStatus.put(owningTeam, false);
        beaconLocations.remove(owningTeam);
        captureProgress.remove(owningTeam);
        capturingTeam.remove(owningTeam);
        BossBar capturedBar = conquestBossBars.remove(owningTeam);
        if (capturedBar != null) { capturedBar.setVisible(false); capturedBar.removeAll(); }

        // 신호기 및 주변 블록 제거
        if (beaconLoc.getBlock().getType() == Material.BEACON) {
            beaconLoc.getBlock().setType(Material.AIR);
        }
        Material glassType = owningTeam.getStainedGlass();
        for (int[] d : new int[][]{{0,1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}}) {
            Block b = beaconLoc.clone().add(d[0], d[1], d[2]).getBlock();
            if (b.getType() == glassType) b.setType(Material.AIR);
        }
        World world = beaconLoc.getWorld();
        int bx = beaconLoc.getBlockX(), by = beaconLoc.getBlockY() - 1, bz = beaconLoc.getBlockZ();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block b = world.getBlockAt(bx + x, by, bz + z);
                if (b.getType() == Material.IRON_BLOCK) b.setType(Material.AIR);
            }
        }

        Bukkit.broadcastMessage(capturer.getChatColor() + capturer.getDisplayName() +
                " §f팀이 " + owningTeam.getChatColor() + owningTeam.getDisplayName() +
                " §f팀의 신호기를 점령했습니다!");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        eliminateTeam(owningTeam);
        checkForWinner();
    }

    private void spawnZoneParticles() {
        for (Team owningTeam : Team.values()) {
            Location beaconLoc = beaconLocations.get(owningTeam);
            if (beaconLoc == null) continue;
            World world = beaconLoc.getWorld();
            if (world == null) continue;

            Team capturer = capturingTeam.get(owningTeam);
            org.bukkit.Color color = (capturer != null) ? capturer.getParticleColor() : owningTeam.getParticleColor();
            Particle.DustOptions dust = new Particle.DustOptions(color, 2.5f);

            double bx = beaconLoc.getX(), by = beaconLoc.getY(), bz = beaconLoc.getZ();
            double r = captureRadius;
            double step = 0.75;

            // 정육면체 12개 모서리
            // 아래 면
            spawnEdge(world, bx-r, by-r, bz-r, bx+r, by-r, bz-r, dust, step);
            spawnEdge(world, bx+r, by-r, bz-r, bx+r, by-r, bz+r, dust, step);
            spawnEdge(world, bx+r, by-r, bz+r, bx-r, by-r, bz+r, dust, step);
            spawnEdge(world, bx-r, by-r, bz+r, bx-r, by-r, bz-r, dust, step);
            // 위 면
            spawnEdge(world, bx-r, by+r, bz-r, bx+r, by+r, bz-r, dust, step);
            spawnEdge(world, bx+r, by+r, bz-r, bx+r, by+r, bz+r, dust, step);
            spawnEdge(world, bx+r, by+r, bz+r, bx-r, by+r, bz+r, dust, step);
            spawnEdge(world, bx-r, by+r, bz+r, bx-r, by+r, bz-r, dust, step);
            // 수직 모서리
            spawnEdge(world, bx-r, by-r, bz-r, bx-r, by+r, bz-r, dust, step);
            spawnEdge(world, bx+r, by-r, bz-r, bx+r, by+r, bz-r, dust, step);
            spawnEdge(world, bx+r, by-r, bz+r, bx+r, by+r, bz+r, dust, step);
            spawnEdge(world, bx-r, by-r, bz+r, bx-r, by+r, bz+r, dust, step);
        }
    }

    private void spawnEdge(World world, double x1, double y1, double z1,
                           double x2, double y2, double z2,
                           Particle.DustOptions dust, double step) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int points = Math.max(1, (int) (length / step));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            world.spawnParticle(Particle.DUST, x1 + dx * t, y1 + dy * t, z1 + dz * t, 1, 0, 0, 0, dust);
        }
    }

    public void openKitGui(Player player) {
        Inventory kitGui = Bukkit.createInventory(null, 54, KIT_GUI_TITLE);
        if (!kitItems.isEmpty()) {
            kitGui.setContents(kitItems.toArray(new ItemStack[0]));
        }
        player.openInventory(kitGui);
    }

    public void saveKit(Inventory inventory) {
        kitItems = Arrays.stream(inventory.getContents())
                .filter(item -> item != null)
                .collect(Collectors.toList());
        plugin.getConfig().set("kit", kitItems);
        plugin.saveConfig();
    }

    @SuppressWarnings("unchecked")
    public void loadKit() {
        List<?> rawList = plugin.getConfig().getList("kit");
        if (rawList != null) {
            kitItems.clear();
            for (Object obj : rawList) {
                if (obj instanceof ItemStack) {
                    kitItems.add((ItemStack) obj);
                } else {
                    plugin.getLogger().warning("config.yml의 kit 목록에 잘못된 아이템이 있습니다.");
                }
            }
        }
    }

    public void setGlobalSpawn(Location location) {
        this.globalSpawnLocation = location;
        plugin.getConfig().set("global-spawn.World", location.getWorld().getName());
        plugin.getConfig().set("global-spawn.X", location.getX());
        plugin.getConfig().set("global-spawn.Y", location.getY());
        plugin.getConfig().set("global-spawn.Z", location.getZ());
        plugin.getConfig().set("global-spawn.YAW", location.getYaw());
        plugin.getConfig().set("global-spawn.PITCH", location.getPitch());
        plugin.saveConfig();
    }

    public void setInvincibilityDuration(int duration) {
        this.invincibilityDuration = duration;
        plugin.getConfig().set("invincibility-duration", duration);
        plugin.saveConfig();
    }

    public boolean isInvincible() {
        return isInvincible;
    }

    private Location getLocationFromConfig(ConfigurationSection section) {
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("World", "world"));
        if (world == null) {
            plugin.getLogger().severe("설정 파일에 지정된 월드 '" + section.getString("World") + "'를 찾을 수 없습니다.");
            return null;
        }
        double x = section.getDouble("X");
        double y = section.getDouble("Y");
        double z = section.getDouble("Z");
        float yaw = (float) section.getDouble("YAW", 0.0);
        float pitch = (float) section.getDouble("PITCH", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void startGame() {
        if (gameInProgress) {
            Bukkit.broadcastMessage("게임이 이미 진행 중입니다.");
            return;
        }

        // 팀이 배정되지 않은 온라인 플레이어 확인
        List<String> unassigned = Bukkit.getOnlinePlayers().stream()
                .filter(p -> getPlayerTeam(p) == null)
                .map(Player::getName)
                .collect(Collectors.toList());
        if (!unassigned.isEmpty()) {
            Bukkit.broadcastMessage("§c게임을 시작할 수 없습니다. 팀이 배정되지 않은 플레이어: §e" + String.join(", ", unassigned));
            return;
        }

        // 게임 시작 시 이전 보스바 정리
        if (invincibilityBossBar != null) {
            invincibilityBossBar.removeAll();
        }

        final Location startPoint;
        if (globalSpawnLocation != null) {
            startPoint = globalSpawnLocation;
        } else {
            startPoint = Bukkit.getWorlds().get(0).getSpawnLocation();
            Bukkit.broadcastMessage("§e[알림] §f게임 시작 스폰 지점이 설정되지 않아 기본 월드 스폰에서 시작합니다.");
        }

        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    Bukkit.broadcastMessage("§a게임 시작까지 " + countdown + "초 전!");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (countdown > 1) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        } else {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f); // Higher pitch for the final '1'
                        }
                    }
                    countdown--;
                } else {
                    this.cancel();
                    gameInProgress = true;
                    isInvincible = true;
                    if (currentGameMode == GameModeType.CONQUEST) {
                        startConquestTasks();
                    }

                    // 모든 플레이어를 시작 지점으로 텔레포트하고 보스바에 추가
                    invincibilityBossBar = Bukkit.createBossBar("§a무적 시간", BarColor.GREEN, BarStyle.SEGMENTED_10);
                    invincibilityBossBar.setVisible(true);

                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        Team playerTeam = getPlayerTeam(onlinePlayer);
                        if (playerTeam != null) {
                            onlinePlayer.teleport(startPoint);
                            initializePlayer(onlinePlayer);
                            giveStartingKit(onlinePlayer);
                        }
                        invincibilityBossBar.addPlayer(onlinePlayer);
                    }

                    giveBeaconsToRandomPlayers();
                    Bukkit.broadcastMessage("§a기지전쟁 시작! " + invincibilityDuration + "초의 무적시간이 적용됩니다!");
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }

                    // 무적 시간 타이머 시작
                    startInvincibilityTimer();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startInvincibilityTimer() {
        invincibilityTask = new BukkitRunnable() {
            private int timer = invincibilityDuration;

            @Override
            public void run() {
                if (timer <= 0) {
                    isInvincible = false;
                    invincibilityBossBar.setVisible(false);
                    invincibilityBossBar.removeAll();
                    Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1.0f, 1.0f));
                    Bukkit.broadcastMessage("§c무적 시간이 종료되었습니다! 전투를 시작하세요!");
                    this.cancel();
                    return;
                }

                double progress = (double) timer / invincibilityDuration;
                invincibilityBossBar.setProgress(progress);

                int minutes = timer / 60;
                int seconds = timer % 60;
                String timeString = String.format("%d분 %d초", minutes, seconds);
                invincibilityBossBar.setTitle("§a무적 시간: §e" + timeString);

                if (progress <= 0.3) {
                    invincibilityBossBar.setColor(BarColor.RED);
                } else if (progress <= 0.6) {
                    invincibilityBossBar.setColor(BarColor.YELLOW);
                }

                timer--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void giveBeaconsToRandomPlayers() {
        for (Team team : teams.keySet()) {
            Set<UUID> teamMembers = teams.get(team);
            if (teamMembers.isEmpty()) continue;

            List<Player> onlineMembers = teamMembers.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .collect(Collectors.toList());

            if (onlineMembers.isEmpty()) {
                plugin.getLogger().warning(team.getDisplayName() + " 팀에 온라인 상태인 플레이어가 없어 신호기를 지급할 수 없습니다.");
                continue;
            }

            Player luckyPlayer = onlineMembers.get(new Random().nextInt(onlineMembers.size()));
            luckyPlayer.getInventory().addItem(new ItemStack(Material.BEACON));
            luckyPlayer.sendMessage("§a당신은 팀의 신호기를 받았습니다! 안전한 위치에 설치하세요.");
        }
    }

    public void stopGame(boolean forced) {
        if (!gameInProgress) {
            Bukkit.broadcastMessage("게임이 진행 중이 아닙니다.");
            return;
        }
        for (Location beaconLoc : beaconLocations.values()) {
            if (beaconLoc != null && beaconLoc.getBlock().getType() == Material.BEACON) {
                beaconLoc.getBlock().setType(Material.AIR);
            }
        }
        resetGame();
        if (forced) {
            Bukkit.broadcastMessage("§c게임이 강제 종료되었습니다.");
        }
    }

    private void initializePlayer(Player player) {
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void giveStartingKit(Player player) {
        // 기본 아이템 지급
        for (ItemStack item : kitItems) {
            if (item != null) {
                player.getInventory().addItem(item.clone());
            }
        }
    }

    public void addPlayerToTeam(Player player, Team team) {
        // 플레이어가 속해 있을 수 있는 모든 스코어보드 팀에서 명시적으로 제거
        org.bukkit.scoreboard.Team playerSbTeam = mainScoreboard.getEntryTeam(player.getName());
        if (playerSbTeam != null) {
            playerSbTeam.removeEntry(player.getName());
        }

        Team currentTeam = playerTeams.get(player.getUniqueId());

        // 게임이 진행 중이 아니면 자유롭게 팀에 참여/변경 허용
        if (!gameInProgress) {
            removePlayerFromCurrentTeam(player);
            teams.get(team).add(player.getUniqueId());
            playerTeams.put(player.getUniqueId(), team);

            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(team.name());
            if (sbTeam != null) {
                sbTeam.addEntry(player.getName());
            }
            player.setScoreboard(mainScoreboard);
            player.setDisplayName(team.getChatColor() + player.getName() + ChatColor.RESET);
            player.setPlayerListName(team.getChatColor() + player.getName() + ChatColor.RESET);
            player.sendMessage(team.getChatColor() + team.getDisplayName() + " 팀" + team.getChatColor() + "에 참여했습니다.");
            return;
        }

        // 게임 진행 중인 경우:
        // 재접속 처리: 게임 진행 여부와 관계없이, 플레이어가 이미 팀에 속해있다면 스코어보드만 업데이트
        if (currentTeam == team) {
            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(team.name());
            if (sbTeam != null) {
                sbTeam.addEntry(player.getName());
            }
            player.setScoreboard(mainScoreboard);
            player.setDisplayName(team.getChatColor() + player.getName() + ChatColor.RESET);
            player.setPlayerListName(team.getChatColor() + player.getName() + ChatColor.RESET);
            return;
        }

        // 게임 중에 다른 팀으로 변경하려는 경우
        if (currentTeam != null) {
            player.sendMessage("§c게임 중에는 팀을 변경할 수 없습니다.");
            return;
        }

        // 게임 진행 중 처음 팀에 합류하는 경우
        removePlayerFromCurrentTeam(player);
        teams.get(team).add(player.getUniqueId());
        playerTeams.put(player.getUniqueId(), team);

        org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(team.name());
        if (sbTeam != null) {
            sbTeam.addEntry(player.getName());
        }
        player.setScoreboard(mainScoreboard);
        player.setDisplayName(team.getChatColor() + player.getName() + ChatColor.RESET);
        player.setPlayerListName(team.getChatColor() + player.getName() + ChatColor.RESET);

        player.sendMessage(team.getChatColor() + team.getDisplayName() + " 팀" + team.getChatColor() + "에 참여했습니다.");
    }

    private void removePlayerFromCurrentTeam(Player player) {
        Team currentTeam = playerTeams.get(player.getUniqueId());
        if (currentTeam != null) {
            teams.get(currentTeam).remove(player.getUniqueId());
            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(currentTeam.name());
            if (sbTeam != null) {
                sbTeam.removeEntry(player.getName());
            }
        }
        playerTeams.remove(player.getUniqueId());
        teamChatStatus.remove(player.getUniqueId());
    }

    public void handleBeaconBreak(Block block, Player breaker) {
        if (block.getType() != Material.BEACON) return;

        Team brokenTeam = null;
        for (Map.Entry<Team, Location> entry : beaconLocations.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equals(block.getLocation())) {
                brokenTeam = entry.getKey();
                break;
            }
        }

        if (brokenTeam == null) return;

        Team breakerTeam = getPlayerTeam(breaker);
        if (breakerTeam == brokenTeam) {
            breaker.sendMessage("자신의 팀 신호기는 파괴할 수 없습니다.");
            return;
        }

        if (beaconStatus.get(brokenTeam)) {
            beaconStatus.put(brokenTeam, false);
            beaconLocations.remove(brokenTeam);
            Bukkit.broadcastMessage(brokenTeam.getChatColor() + brokenTeam.getDisplayName() + " 팀§f의 신호기가 파괴되었습니다! 이제 해당 팀은 리스폰할 수 없습니다.");

            Location beaconLoc = block.getLocation();
            World world = beaconLoc.getWorld();
            Material glassType = brokenTeam.getStainedGlass();

            // 주변 유리 블록 제거
            Block[] glassBlocks = {
                beaconLoc.clone().add(0, 1, 0).getBlock(), // Top
                beaconLoc.clone().add(1, 0, 0).getBlock(),  // East
                beaconLoc.clone().add(-1, 0, 0).getBlock(), // West
                beaconLoc.clone().add(0, 0, 1).getBlock(),  // South
                beaconLoc.clone().add(0, 0, -1).getBlock()  // North
            };
            for (Block glassBlock : glassBlocks) {
                if (glassBlock.getType() == glassType) {
                    glassBlock.setType(Material.AIR);
                }
            }

            // 하단 철 블록 제거
            int baseX = beaconLoc.getBlockX();
            int baseY = beaconLoc.getBlockY() - 1;
            int baseZ = beaconLoc.getBlockZ();

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block platformBlock = world.getBlockAt(baseX + x, baseY, baseZ + z);
                    if (platformBlock.getType() == Material.IRON_BLOCK) {
                        platformBlock.setType(Material.AIR);
                    }
                }
            }

            // 하드코어: 신호기 파괴 시 부활 대기 중이던 팀원도 정리
            if (currentGameMode == GameModeType.HARDCORE) {
                Iterator<Map.Entry<UUID, Team>> it = hardcoreDeadPlayers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Team> entry = it.next();
                    if (entry.getValue() == brokenTeam) {
                        savedInventories.remove(entry.getKey());
                        it.remove();
                    }
                }
            }

            eliminateTeam(brokenTeam);
            checkForWinner();
        }
    }

    public void handleBeaconPlace(Block block, Player placer) {
        Team placerTeam = getPlayerTeam(placer);
        if (placerTeam == null) return;

        if (beaconStatus.get(placerTeam)) {
            placer.sendMessage("§c이미 당신의 팀 신호기가 설치되어 있습니다.");
            return;
        }

        beaconStatus.put(placerTeam, true);
        beaconEverPlaced.put(placerTeam, true);
        beaconLocations.put(placerTeam, block.getLocation());
        if (currentGameMode == GameModeType.CONQUEST) {
            captureProgress.put(placerTeam, 0.0);
            createConquestBossBar(placerTeam);
        }
        Bukkit.broadcastMessage(placerTeam.getChatColor() + placerTeam.getDisplayName() + " 팀§f이 신호기를 설치했습니다! 이제부터 리스폰이 가능합니다.");

        // 팀원들에게 신호기 좌표 안내
        String coordsMessage = placerTeam.getChatColor() + "[팀 알림] §f당신의 팀 신호기가 X:" + block.getX() + ", Y:" + block.getY() + ", Z:" + block.getZ() + " 에 설치되었습니다!";
        for (UUID memberUUID : teams.get(placerTeam)) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline() && !member.equals(placer)) {
                member.sendMessage(coordsMessage);
            }
        }

        // 신호기 아래 철 블록 및 주변 유리 블록 설치
        Location beaconLoc = block.getLocation();
        World world = beaconLoc.getWorld();
        Material glassType = placerTeam.getStainedGlass();

        // 철 블록 설치
        int baseX = beaconLoc.getBlockX();
        int baseY = beaconLoc.getBlockY() - 1;
        int baseZ = beaconLoc.getBlockZ();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.IRON_BLOCK);
            }
        }

        // 팀 색깔 유리 블록 설치
        beaconLoc.clone().add(0, 1, 0).getBlock().setType(glassType); // Top
        beaconLoc.clone().add(1, 0, 0).getBlock().setType(glassType);  // East
        beaconLoc.clone().add(-1, 0, 0).getBlock().setType(glassType); // West
        beaconLoc.clone().add(0, 0, 1).getBlock().setType(glassType);  // South
        beaconLoc.clone().add(0, 0, -1).getBlock().setType(glassType); // North
    }

    public void handlePlayerDeath(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) return;

        boolean hasBeacon = beaconStatus.get(team);
        boolean wasBeaconEverPlaced = beaconEverPlaced.getOrDefault(team, false);

        if (currentGameMode == GameModeType.HARDCORE) {
            if (hasBeacon) {
                // 하드코어: 인벤 저장 후 부활 대기 상태로 전환 (onPlayerRespawn에서 관전 처리)
                savedInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
                hardcoreDeadPlayers.put(player.getUniqueId(), team);
                player.sendMessage("§c사망했습니다. 팀원이 신호기에서 다이아몬드 " + hardcoreReviveCost + "개로 부활시켜야 합니다.");
            } else if (wasBeaconEverPlaced) {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("신호기가 파괴되어 최종적으로 사망했습니다. 관전 모드로 전환됩니다.");
                checkForWinner();
            } else {
                player.sendMessage("아직 팀의 신호기가 설치되지 않았습니다. 잠시 후 리스폰됩니다.");
                player.setGameMode(GameMode.SURVIVAL);
            }
            return;
        }

        if (hasBeacon) {
            // 비콘이 있으면 onPlayerRespawn 이벤트에서 지연 리스폰 처리
            return;
        } else {
            if (wasBeaconEverPlaced) {
                // 비콘이 있었는데 파괴되었으면 최종 사망
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("신호기가 파괴되어 최종적으로 사망했습니다. 관전 모드로 전환됩니다.");
                checkForWinner();
            } else {
                // 비콘이 설치된 적 없으면 일반 리스폰
                player.sendMessage("아직 팀의 신호기가 설치되지 않았습니다. 잠시 후 리스폰됩니다.");
                player.setGameMode(GameMode.SURVIVAL);
                // onPlayerRespawn 이벤트에서 글로벌 스폰으로 이동시킴
            }
        }
    }

    public boolean isHardcoreDead(UUID uuid) {
        return hardcoreDeadPlayers.containsKey(uuid);
    }

    public void scheduleHardcoreSpectator(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && isHardcoreDead(player.getUniqueId())) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendTitle("§c사망!", "§e팀원이 신호기에서 부활시켜줄 때까지 기다리세요.", 0, 60, 10);
                    checkForWinner();
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    public void restoreHardcoreDeadState(Player player) {
        if (!hardcoreDeadPlayers.containsKey(player.getUniqueId())) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    public void openHardcoreReviveGui(Player clicker) {
        Team team = getPlayerTeam(clicker);
        if (team == null) return;

        List<UUID> deadTeammates = hardcoreDeadPlayers.entrySet().stream()
                .filter(e -> e.getValue() == team)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (deadTeammates.isEmpty()) {
            clicker.sendMessage("§e현재 부활이 필요한 팀원이 없습니다.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, HARDCORE_REVIVE_GUI_TITLE);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 54; i++) gui.setItem(i, filler.clone());

        int[] itemSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                           28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

        for (int i = 0; i < deadTeammates.size() && i < itemSlots.length; i++) {
            UUID deadUUID = deadTeammates.get(i);
            Player deadPlayer = Bukkit.getPlayer(deadUUID);
            boolean isOnline = deadPlayer != null && deadPlayer.isOnline();
            String playerName = isOnline ? deadPlayer.getName() :
                    Bukkit.getOfflinePlayer(deadUUID).getName();
            if (playerName == null) playerName = "알 수 없음";

            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            ItemMeta meta = skull.getItemMeta();
            if (isOnline) {
                meta.setDisplayName("§c" + playerName + " §7(사망)");
                meta.setLore(Arrays.asList(
                        "",
                        "§f클릭하여 부활시키기",
                        "§e비용: §b다이아몬드 §e" + hardcoreReviveCost + "개",
                        "§0" + deadUUID.toString()
                ));
            } else {
                meta.setDisplayName("§8" + playerName + " §7(사망 - 오프라인)");
                meta.setLore(Arrays.asList(
                        "",
                        "§c오프라인 상태입니다.",
                        "§0" + deadUUID.toString()
                ));
            }
            skull.setItemMeta(meta);
            gui.setItem(itemSlots[i], skull);
        }

        clicker.openInventory(gui);
    }

    public void handleHardcoreReviveClick(Player clicker, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        ItemMeta meta = clickedItem.getItemMeta();
        if (!meta.hasLore()) return;

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return;
        String uuidLine = ChatColor.stripColor(lore.get(lore.size() - 1));

        UUID targetUUID;
        try {
            targetUUID = UUID.fromString(uuidLine);
        } catch (IllegalArgumentException e) {
            return;
        }

        if (!hardcoreDeadPlayers.containsKey(targetUUID)) {
            clicker.sendMessage("§c해당 플레이어는 이미 부활했거나 더 이상 부활할 수 없습니다.");
            return;
        }

        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            clicker.sendMessage("§c해당 플레이어가 오프라인입니다. 접속 중일 때 부활시킬 수 있습니다.");
            return;
        }

        // 다이아몬드 확인
        int diamondCount = 0;
        for (ItemStack item : clicker.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                diamondCount += item.getAmount();
            }
        }
        if (diamondCount < hardcoreReviveCost) {
            clicker.sendMessage("§c다이아몬드가 부족합니다. (보유: §e" + diamondCount + "§c개 / 필요: §e" + hardcoreReviveCost + "§c개)");
            return;
        }

        // 다이아몬드 제거
        int toRemove = hardcoreReviveCost;
        ItemStack[] contents = clicker.getInventory().getContents();
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.DIAMOND) {
                int amount = item.getAmount();
                if (amount <= toRemove) {
                    toRemove -= amount;
                    clicker.getInventory().setItem(i, null);
                } else {
                    item.setAmount(amount - toRemove);
                    toRemove = 0;
                }
            }
        }

        // 부활 처리
        Team deadTeam = hardcoreDeadPlayers.remove(targetUUID);
        ItemStack[] savedItems = savedInventories.remove(targetUUID);

        Location reviveLoc;
        Location beaconLoc = beaconLocations.get(deadTeam);
        if (beaconLoc != null) {
            reviveLoc = findSafeRespawnLocation(beaconLoc);
        } else {
            reviveLoc = globalSpawnLocation != null ? globalSpawnLocation :
                        Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        final Location finalReviveLoc = reviveLoc;
        final ItemStack[] finalSavedItems = savedItems;

        new BukkitRunnable() {
            @Override
            public void run() {
                target.setGameMode(GameMode.SURVIVAL);
                target.setHealth(20.0);
                target.setFoodLevel(20);
                for (PotionEffect effect : target.getActivePotionEffects()) {
                    target.removePotionEffect(effect.getType());
                }
                if (finalSavedItems != null) {
                    target.getInventory().setContents(finalSavedItems);
                }
                target.teleport(finalReviveLoc);
                target.sendTitle("§a부활!", "§e팀원에 의해 부활되었습니다.", 0, 40, 10);
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }.runTask(plugin);

        clicker.closeInventory();
        clicker.sendMessage("§a" + target.getName() + "님을 부활시켰습니다!");
        Bukkit.broadcastMessage(deadTeam.getChatColor() + target.getName() + " §f님이 팀원에 의해 부활했습니다!");
    }

    private Location findSafeRespawnLocation(Location beaconLocation) {
        World world = beaconLocation.getWorld();
        if (world == null) {
            return beaconLocation.clone().add(0.5, 1, 0.5); // Fallback
        }

        Random random = new Random();
        for (int i = 0; i < 20; i++) { // 20번 시도
            double angle = random.nextDouble() * 2 * Math.PI;
            // 19~21 블록 사이의 랜덤 거리
            double radius = 19 + random.nextDouble() * 2;

            double x = beaconLocation.getX() + radius * Math.cos(angle);
            double z = beaconLocation.getZ() + radius * Math.sin(angle);

            Location potentialLocation = world.getHighestBlockAt((int) x, (int) z).getLocation().add(0.5, 1, 0.5);

            // 스폰 위치가 안전한지 확인 (발과 머리 위치에 블록이 없는지)
            Block feet = potentialLocation.getBlock();
            Block head = potentialLocation.clone().add(0, 1, 0).getBlock();

            if (feet.isPassable() && head.isPassable()) {
                // 플레이어가 바라볼 방향을 비콘으로 설정
                potentialLocation.setDirection(beaconLocation.toVector().subtract(potentialLocation.toVector()));
                return potentialLocation;
            }
        }

        // 20번 시도 후에도 안전한 장소를 찾지 못하면 비콘 위로 텔레포트
        return beaconLocation.clone().add(0.5, 1, 0.5);
    }

    public void startRespawnCountdown(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) return;

        Location beaconLocation = beaconLocations.get(team);
        if (beaconLocation == null) { // 혹시 모를 예외 처리
            player.sendMessage("§c리스폰 위치를 찾을 수 없어 즉시 리스폰합니다.");
            initializePlayer(player);
            if(globalSpawnLocation != null) player.teleport(globalSpawnLocation);
            else player.teleport(player.getWorld().getSpawnLocation());
            return;
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(player.getLocation().add(0, 2, 0)); // 살짝 위에서 보도록

        int respawnTime = 5; // 5초 고정

        new BukkitRunnable() {
            int timer = respawnTime;

            @Override
            public void run() {
                if (timer > 0) {
                    player.sendTitle("§c사망!", "§e" + timer + "초 후 리스폰됩니다.", 0, 25, 5);
                    timer--;
                } else {
                    this.cancel();
                    player.setGameMode(GameMode.SURVIVAL);
                    initializePlayer(player);
                    // 텔레포트 후 짧은 지연을 주어 클라이언트 업데이트를 돕습니다.
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Location safeRespawnLocation = findSafeRespawnLocation(beaconLocation);
                            player.teleport(safeRespawnLocation);
                        }
                    }.runTaskLater(plugin, 1L);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void eliminateTeam(Team team) {
        for (UUID uuid : teams.get(team)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getGameMode() != GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage("§c팀의 신호기가 사라져 탈락했습니다. 관전 모드로 전환됩니다.");
            }
        }
    }

    private void checkForWinner() {
        List<Team> teamsWithRemainingPlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR && getPlayerTeam(p) != null)
                .map(this::getPlayerTeam)
                .distinct()
                .collect(Collectors.toList());

        if (teamsWithRemainingPlayers.size() == 1) {
            Team winner = teamsWithRemainingPlayers.get(0);
            String title = winner.getChatColor() + winner.getDisplayName() + " 팀 승리";
            String subtitle = "§7승리했습니다.";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(title, subtitle, 10, 70, 20); // fadeIn, stay, fadeOut ticks
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            stopGame(false);
        } else if (teamsWithRemainingPlayers.isEmpty() && gameInProgress) {
            Bukkit.broadcastMessage("§c모든 팀이 탈락하여 무승부입니다!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            }
            stopGame(false);
        }
    }

    private void resetGame() {
        gameInProgress = false;
        isInvincible = false;

        stopConquestTasks();
        captureProgress.clear();
        capturingTeam.clear();

        // 무적 시간 타이머 및 보스바 정리
        if (invincibilityTask != null && !invincibilityTask.isCancelled()) {
            invincibilityTask.cancel();
        }
        if (invincibilityBossBar != null) {
            invincibilityBossBar.setVisible(false);
            invincibilityBossBar.removeAll();
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setScoreboard(scoreboardManager.getMainScoreboard());
            p.setDisplayName(p.getName());
            p.setPlayerListName(p.getName());
        }

        for (org.bukkit.scoreboard.Team sbTeam : mainScoreboard.getTeams()) {
            sbTeam.unregister();
        }

        // 초기화 후 스코어보드 팀 다시 등록
        for (Team teamEnum : Team.values()) {
            org.bukkit.scoreboard.Team sbTeam = mainScoreboard.getTeam(teamEnum.name());
            if (sbTeam == null) {
                sbTeam = mainScoreboard.registerNewTeam(teamEnum.name());
            }
            sbTeam.setPrefix(teamEnum.getChatColor() + "");
            sbTeam.setColor(teamEnum.getChatColor());
            sbTeam.setCanSeeFriendlyInvisibles(false);
            sbTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        }

        teams.clear();
        playerTeams.clear();
        beaconLocations.clear();
        beaconEverPlaced.clear();
        teamChatStatus.clear();
        hardcoreDeadPlayers.clear();
        savedInventories.clear();
        gameSpectators.clear();

        // 채굴 피로 효과 정리
        for (UUID playerUUID : miningFatigueTasks.keySet()) {
            BukkitTask task = miningFatigueTasks.get(playerUUID);
            task.cancel();
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            }
        }
        miningFatigueTasks.clear();

        for (Team team : Team.values()) {
            teams.put(team, new HashSet<>());
            beaconStatus.put(team, false);
            beaconEverPlaced.put(team, false);
        }
    }

    public boolean isGameInProgress() {
        return gameInProgress;
    }

    public Team getPlayerTeam(Player player) {
        return playerTeams.get(player.getUniqueId());
    }

    public Map<Team, Location> getBeaconLocations() {
        return beaconLocations;
    }

    public Map<Team, Boolean> getBeaconStatus() {
        return beaconStatus;
    }

    public void toggleTeamChat(Player player) {
        boolean currentStatus = teamChatStatus.getOrDefault(player.getUniqueId(), false);
        teamChatStatus.put(player.getUniqueId(), !currentStatus);
        if (!currentStatus) {
            player.sendMessage("§a팀 채팅이 활성화되었습니다.");
        } else {
            player.sendMessage("§c팀 채팅이 비활성화되었습니다.");
        }
    }

    public boolean isTeamChatEnabled(UUID playerUUID) {
        return teamChatStatus.getOrDefault(playerUUID, false);
    }

    public Set<UUID> getTeamMembers(Team team) {
        return teams.get(team);
    }

    public Location getGlobalSpawnLocation() {
        return globalSpawnLocation;
    }

    public void startBeaconMining(Player player, Block beaconBlock) {
        // 이미 작업이 실행중이면 중복 실행 방지
        if (miningFatigueTasks.containsKey(player.getUniqueId())) {
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    stopBeaconMining(player);
                    return;
                }

                // 플레이어가 바라보는 블록이 대상 비콘이 아니면 중지
                Block targetBlock = player.getTargetBlock(null, 5);
                if (targetBlock == null || !targetBlock.equals(beaconBlock)) {
                    stopBeaconMining(player);
                    return;
                }

                // 채굴 피로 효과 적용 (1.3초, 2단계, 파티클 없음)
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 26, 0, true, false));
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 반복

        miningFatigueTasks.put(player.getUniqueId(), task);
    }

    public void stopBeaconMining(Player player) {
        if (miningFatigueTasks.containsKey(player.getUniqueId())) {
            miningFatigueTasks.get(player.getUniqueId()).cancel();
            miningFatigueTasks.remove(player.getUniqueId());
        }
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    public BossBar getInvincibilityBossBar() {
        return invincibilityBossBar;
    }

    // DataManager access methods
    public void setGameInProgress(boolean gameInProgress) {
        this.gameInProgress = gameInProgress;
    }

    public Map<Team, Set<UUID>> getTeams() {
        return teams;
    }

    public Map<UUID, Team> getPlayerTeams() {
        return playerTeams;
    }

    public Map<Team, Boolean> getBeaconEverPlaced() {
        return beaconEverPlaced;
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }
}