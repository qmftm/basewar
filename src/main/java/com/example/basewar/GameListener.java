package com.example.basewar;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(GameManager.HARDCORE_REVIVE_GUI_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == org.bukkit.Material.BLACK_STAINED_GLASS_PANE) return;
            gameManager.handleHardcoreReviveClick((Player) event.getWhoClicked(), clicked);
            return;
        }

        if (!event.getView().getTitle().equals(GameManager.GAMEMODE_GUI_TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        GameModeType mode = GameModeType.fromSlot(slot);
        if (mode == null || mode == gameManager.getCurrentGameMode()) return;

        gameManager.setCurrentGameMode(mode);
        Player player = (Player) event.getWhoClicked();
        // 선택 후 GUI 갱신
        gameManager.openGameModeGui(player);
        player.sendMessage("§a게임모드가 §f" + mode.getDisplayName() + "§a으로 설정되었습니다.");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(GameManager.KIT_GUI_TITLE)) {
            Player player = (Player) event.getPlayer();
            if (player.hasPermission("basewar.admin")) {
                gameManager.saveKit(event.getInventory());
                player.sendMessage("§a기본 아이템 설정이 저장되었습니다.");
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!gameManager.isGameInProgress()) return;

        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.SPECTATOR) {
            event.setFormat("§7[관전] §f%1$s§f: %2$s");
            return;
        }

        Team team = gameManager.getPlayerTeam(player);

        if (gameManager.isTeamChatEnabled(player.getUniqueId())) {
            if (team == null) return;

            event.setCancelled(true);

            String teamMessage = team.getChatColor() + "[팀] " + player.getName() + "§f: " + event.getMessage();

            Set<UUID> teamMembers = gameManager.getTeamMembers(team);
            for (UUID memberUUID : teamMembers) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    member.sendMessage(teamMessage);
                }
            }
        } else {
            // 일반 채팅
            String format;
            if (team != null) {
                format = team.getChatColor() + "%1$s§f: %2$s"; // 팀 색상 적용
            } else {
                format = "§f%1$s§f: %2$s"; // 팀이 없으면 기본 흰색
            }
            event.setFormat(format);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Team team = gameManager.getPlayerTeam(player);
        if (team != null) {
            gameManager.addPlayerToTeam(player, team);
        }

        // 재접속 시 무적 보스바에 다시 추가
        if (gameManager.isGameInProgress() && gameManager.isInvincible() && gameManager.getInvincibilityBossBar() != null) {
            gameManager.getInvincibilityBossBar().addPlayer(player);
        }
        // 재접속 시 점령전 보스바에 추가
        if (gameManager.isGameInProgress() && gameManager.getCurrentGameMode() == GameModeType.CONQUEST) {
            gameManager.addPlayerToConquestBossBars(player);
        }
        // 재접속 시 하드코어 사망 상태 복원
        if (gameManager.isGameInProgress() && gameManager.getCurrentGameMode() == GameModeType.HARDCORE) {
            gameManager.restoreHardcoreDeadState(player);
        }
        // 재접속 시 게임 관전 상태 복원 (회색 이름표)
        if (gameManager.isGameInProgress() && gameManager.isGameSpectator(player.getUniqueId())) {
            player.setDisplayName(org.bukkit.ChatColor.GRAY + player.getName() + org.bukkit.ChatColor.RESET);
            player.setPlayerListName(org.bukkit.ChatColor.GRAY + "[관전] " + player.getName() + org.bukkit.ChatColor.RESET);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameManager.isGameInProgress()) return;
        if (gameManager.getCurrentGameMode() != GameModeType.HARDCORE) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEACON) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        Team playerTeam = gameManager.getPlayerTeam(player);
        if (playerTeam == null) return;

        Location beaconLoc = gameManager.getBeaconLocations().get(playerTeam);
        if (beaconLoc == null || !beaconLoc.equals(block.getLocation())) return;

        event.setCancelled(true);
        gameManager.openHardcoreReviveGui(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 플레이어가 나가면 채굴 피로 효과 제거
        gameManager.stopBeaconMining(event.getPlayer());
    }


    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Player player = event.getEntity();

        // 하드코어: 신호기가 살아있으면 아이템 드롭 방지 (인벤 보호)
        if (gameManager.getCurrentGameMode() == GameModeType.HARDCORE) {
            Team team = gameManager.getPlayerTeam(player);
            if (team != null && gameManager.getBeaconStatus().get(team)) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
        }

        gameManager.handlePlayerDeath(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Player player = event.getPlayer();
        Team team = gameManager.getPlayerTeam(player);

        // 하드코어: 부활 대기 상태이면 글로벌 스폰으로 보내고 관전 전환 예약
        if (gameManager.getCurrentGameMode() == GameModeType.HARDCORE) {
            if (gameManager.isHardcoreDead(player.getUniqueId())) {
                Location globalSpawn = gameManager.getGlobalSpawnLocation();
                if (globalSpawn != null) {
                    event.setRespawnLocation(globalSpawn);
                } else {
                    event.setRespawnLocation(player.getWorld().getSpawnLocation());
                }
                gameManager.scheduleHardcoreSpectator(player);
                return;
            }
            // 신호기 미설치 시: 글로벌 스폰으로 리스폰
            Location globalSpawn = gameManager.getGlobalSpawnLocation();
            if (globalSpawn != null) {
                event.setRespawnLocation(globalSpawn);
            } else {
                event.setRespawnLocation(player.getWorld().getSpawnLocation());
            }
            return;
        }

        // 비콘이 살아있으면 리스폰 대기시간 시작
        if (team != null && gameManager.getBeaconStatus().get(team)) {
            // 즉시 리스폰되는 것을 막기 위해 현재 위치로 리스폰 위치 설정
            event.setRespawnLocation(player.getLocation());
            gameManager.startRespawnCountdown(player);
            return;
        }

        // 그 외의 경우 (팀이 없거나, 비콘이 없거나, 비콘이 파괴되었지만 아직 최종 탈락은 아닌 경우)
        // 최종 사망 처리는 handlePlayerDeath에서 하므로, 여기서는 리스폰 위치만 지정
        if (player.getGameMode() != GameMode.SPECTATOR) {
            Location globalSpawn = gameManager.getGlobalSpawnLocation();
            if (globalSpawn != null) {
                event.setRespawnLocation(globalSpawn);
            } else {
                event.setRespawnLocation(player.getWorld().getSpawnLocation());
            }
        }
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        if (!gameManager.isGameInProgress()) return;
        if (event.getBlock().getType() != Material.BEACON) {
            // 다른 블록을 캐기 시작하면 이전의 채굴 피로 효과 제거
            gameManager.stopBeaconMining(event.getPlayer());
            return;
        }

        // 무적 시간에는 비콘 손상 불가
        if (gameManager.isInvincible()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        Team playerTeam = gameManager.getPlayerTeam(player);
        Team beaconTeam = null;
        for (Team team : gameManager.getBeaconLocations().keySet()) {
            if (event.getBlock().getLocation().equals(gameManager.getBeaconLocations().get(team))) {
                beaconTeam = team;
                break;
            }
        }

        // 자신의 팀 비콘은 해당 없음
        if (beaconTeam == null || playerTeam == beaconTeam) {
            gameManager.stopBeaconMining(player);
            return;
        }

        // 적 팀 비콘을 캘 때 효과 시작
        gameManager.startBeaconMining(player, event.getBlock());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 비콘을 파괴하면 채굴 피로 효과 즉시 중지
        if (block.getType() == Material.BEACON) {
            if (gameManager.isInvincible()) {
                player.sendMessage("§c무적 시간에는 신호기를 파괴할 수 없습니다.");
                event.setCancelled(true);
                return;
            }

            gameManager.stopBeaconMining(player);

            // 성급함 버프 적용 (1.3초, 1단계, 파티클 없음)
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 0, true, false));

            // 신호기 아이템 드롭 방지
            event.setDropItems(false);
        }

        // 신호기 기반(철 블록) 보호
        if (block.getType() == Material.IRON_BLOCK) {
            Set<Location> protectedBaseLocations = new HashSet<>();
            for (Location beaconLoc : gameManager.getBeaconLocations().values()) {
                // Check if a beacon actually exists at the stored location before protecting its base
                if (beaconLoc != null && beaconLoc.getBlock().getType() == Material.BEACON) {
                    int baseX = beaconLoc.getBlockX();
                    int baseY = beaconLoc.getBlockY() - 1;
                    int baseZ = beaconLoc.getBlockZ();

                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            protectedBaseLocations.add(new Location(beaconLoc.getWorld(), baseX + x, baseY, baseZ + z));
                        }
                    }
                }
            }

            if (protectedBaseLocations.contains(block.getLocation())) {
                player.sendMessage("§c신호기 기반 블록은 파괴할 수 없습니다.");
                event.setCancelled(true);
                return;
            }
        }

        if (block.getType() == Material.BEACON) {
            Team brokenTeam = null;
            for (Team team : gameManager.getBeaconLocations().keySet()) {
                Location beaconLoc = gameManager.getBeaconLocations().get(team);
                if (beaconLoc != null && beaconLoc.equals(block.getLocation())) {
                    brokenTeam = team;
                    break;
                }
            }

            if (brokenTeam != null) {
                Team playerTeam = gameManager.getPlayerTeam(player);
                if (playerTeam == brokenTeam) {
                    player.sendMessage("§c자신의 팀 신호기는 파괴할 수 없습니다.");
                    event.setCancelled(true);
                    return;
                }
                gameManager.handleBeaconBreak(block, player);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() != Material.BEACON) return;

        Team placerTeam = gameManager.getPlayerTeam(player);
        if (placerTeam == null) return;

        if (gameManager.getBeaconStatus().get(placerTeam)) {
            player.sendMessage("§c이미 당신의 팀 신호기가 설치되어 있습니다.");
            event.setCancelled(true);
            return;
        }

        if (!gameManager.isBeaconPlacementAllowed(block.getLocation())) {
            player.sendMessage("§c이곳에는 신호기를 설치할 수 없습니다. (X: " + gameManager.getMinX() + "~" + gameManager.getMaxX() +
                               ", Y: " + gameManager.getMinY() + "~" + gameManager.getMaxY() +
                               ", Z: " + gameManager.getMinZ() + "~" + gameManager.getMaxZ() + ")");
            event.setCancelled(true);
            return;
        }

        gameManager.handleBeaconPlace(block, player);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!gameManager.isGameInProgress() || !(event.getEntity() instanceof Player)) {
            return;
        }

        // 무적 시간에는 모든 데미지 방지
        if (gameManager.isInvincible()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!gameManager.isGameInProgress()) return;

        // 무적 시간에는 플레이어에게 가해지는 데미지만 방지
        if (gameManager.isInvincible()) {
            if (event.getEntity() instanceof Player) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = null;

        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            attacker = (Player) damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // 아군 공격 방지
        if (attacker != null) {
            // Don't cancel if it's self-inflicted damage (e.g. shooting arrow up and it falls on you)
            if (attacker.equals(victim)) {
                return;
            }

            Team attackerTeam = gameManager.getPlayerTeam(attacker);
            Team victimTeam = gameManager.getPlayerTeam(victim);

            if (attackerTeam != null && attackerTeam == victimTeam) {
                event.setCancelled(true);
            }
        }
    }

    // New explosion handlers
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!gameManager.isGameInProgress()) return;
        removeBeaconsFromExplosion(event.blockList());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!gameManager.isGameInProgress()) return;
        removeBeaconsFromExplosion(event.blockList());
    }

    private void removeBeaconsFromExplosion(List<Block> blockList) {
        Set<Location> protectedLocations = new HashSet<>();

        // Add all registered beacon locations and their 3x3 iron bases to the protected set
        for (Location beaconLoc : gameManager.getBeaconLocations().values()) {
            if (beaconLoc != null) {
                protectedLocations.add(beaconLoc); // Protect the beacon itself

                // Protect the 3x3 iron block base below the beacon
                int baseX = beaconLoc.getBlockX();
                int baseY = beaconLoc.getBlockY() - 1;
                int baseZ = beaconLoc.getBlockZ();

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        protectedLocations.add(new Location(beaconLoc.getWorld(), baseX + x, baseY, baseZ + z));
                    }
                }
            }
        }

        // Remove protected blocks from the explosion list
        blockList.removeIf(block -> protectedLocations.contains(block.getLocation()));
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!gameManager.isGameInProgress()) return;
        Player player = event.getPlayer();

        // 탈락 관전자 이동 제한 (게임 관전자는 자유롭게 이동 허용)
        if (player.getGameMode() == GameMode.SPECTATOR
                && !gameManager.isGameSpectator(player.getUniqueId())) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
            }
        }
    }
}
