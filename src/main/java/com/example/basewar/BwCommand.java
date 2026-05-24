package com.example.basewar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class BwCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;

    public BwCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName();
        if (cmdName.equals("팀채팅") || cmdName.equals("팀챗")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                return true;
            }
            gameManager.toggleTeamChat((Player) sender);
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e사용법: /bw <start|stop|reload|config|chat|team>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            gameManager.reloadPluginConfig();
            sender.sendMessage("§aconfig.yml을 리로드했습니다.");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            gameManager.startGame();
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            gameManager.stopGame(true);
            return true;
        }

        if (args[0].equalsIgnoreCase("chat")) {
            if (sender instanceof Player) {
                gameManager.toggleTeamChat((Player) sender);
            } else {
                sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("spectator")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§e사용법: /bw spectator <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c플레이어 '" + args[1] + "'를 찾을 수 없습니다.");
                return true;
            }
            if (gameManager.isGameInProgress() && gameManager.isGameSpectator(target.getUniqueId())) {
                // 관전 중 → 원래 팀으로 복귀 시도
                gameManager.restoreGameSpectator(target, sender);
            } else if (gameManager.isGameInProgress()) {
                // 게임 중 → 팀의 마지막 생존자면 관전 불가
                if (!gameManager.canSetGameSpectator(target)) {
                    sender.sendMessage("§c" + target.getName() + "은(는) 팀의 마지막 생존자라 관전 모드로 전환할 수 없습니다.");
                    return true;
                }
                gameManager.setGameSpectator(target);
                sender.sendMessage("§7" + target.getName() + "을(를) 관전 모드로 전환했습니다.");
            } else {
                // 게임 외 → 단순 관전 전환
                target.setGameMode(org.bukkit.GameMode.SPECTATOR);
                target.sendMessage("§7관전 모드로 전환되었습니다.");
                sender.sendMessage("§7" + target.getName() + "을(를) 관전 모드로 전환했습니다.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("team")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            return handleTeam(sender, args);
        }

        if (args[0].equalsIgnoreCase("config")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§e사용법: /bw config <setspawn|inv|kit|gamemode>");
                return true;
            }

            if (args[1].equalsIgnoreCase("setspawn")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    Location location = player.getLocation();
                    gameManager.setGlobalSpawn(location);
                    sender.sendMessage("§a게임 시작 스폰 지점을 현재 위치로 설정했습니다.");
                } else {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                }
                return true;
            }

            if (args[1].equalsIgnoreCase("inv")) {
                if (args.length < 3) {
                    sender.sendMessage("§e사용법: /bw config inv <시간(초)>");
                    return true;
                }
                try {
                    int duration = Integer.parseInt(args[2]);
                    if (duration < 0) {
                        sender.sendMessage("§c시간은 0 이상이어야 합니다.");
                        return true;
                    }
                    gameManager.setInvincibilityDuration(duration);
                    sender.sendMessage("§a무적 시간이 " + duration + "초로 설정되었습니다.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c유효한 숫자를 입력해주세요.");
                }
                return true;
            }

            if (args[1].equalsIgnoreCase("kit")) {
                if (sender instanceof Player) {
                    gameManager.openKitGui((Player) sender);
                } else {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                }
                return true;
            }

            if (args[1].equalsIgnoreCase("gamemode")) {
                if (sender instanceof Player) {
                    gameManager.openGameModeGui((Player) sender);
                } else {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                }
                return true;
            }

            sender.sendMessage("§e사용법: /bw config <setspawn|inv|kit|gamemode>");
            return true;
        }

        sender.sendMessage("§c알 수 없는 명령어입니다. 사용법: /bw <start|stop|reload|config|chat|team>");
        return true;
    }

    private boolean handleTeam(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e사용법: /bw team <set|random> ...");
            return true;
        }

        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 4) {
                sender.sendMessage("§e사용법: /bw team set <player> <" + teamListString() + ">");
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§c플레이어 '" + args[2] + "'를 찾을 수 없습니다.");
                return true;
            }
            try {
                Team team = Team.valueOf(args[3].toUpperCase());
                gameManager.addPlayerToTeam(target, team);
                sender.sendMessage("§a" + target.getName() + "을(를) " + team.getChatColor() + team.getDisplayName() + " §a팀에 배정했습니다.");
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§c존재하지 않는 팀입니다. (" + teamListString() + ")");
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("random")) {
            int count;
            if (args.length < 3) {
                count = 2;
            } else {
                try {
                    count = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c팀 개수는 숫자로 입력해주세요.");
                    return true;
                }
            }
            Team[] allTeams = Team.values();
            int onlineCount = Bukkit.getOnlinePlayers().size();
            int maxTeams = Math.min(allTeams.length, onlineCount);
            if (count < 2) {
                sender.sendMessage("§c팀은 최소 2개 이상이어야 합니다.");
                return true;
            }
            if (count > allTeams.length) {
                sender.sendMessage("§c팀 개수는 최대 " + allTeams.length + "개입니다.");
                return true;
            }
            if (count > onlineCount) {
                sender.sendMessage("§c온라인 플레이어(" + onlineCount + "명)보다 팀 개수가 많을 수 없습니다.");
                return true;
            }
            Team[] usedTeams = Arrays.copyOf(allTeams, count);
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            java.util.Collections.shuffle(players);
            for (int i = 0; i < players.size(); i++) {
                gameManager.addPlayerToTeam(players.get(i), usedTeams[i % count]);
            }
            String teamNames = Arrays.stream(usedTeams)
                    .map(t -> t.getChatColor() + t.getDisplayName())
                    .collect(Collectors.joining("§f, "));
            sender.sendMessage("§a모든 플레이어를 " + count + "개 팀(" + teamNames + "§a)에 무작위 배정했습니다.");
            return true;
        }

        sender.sendMessage("§e사용법: /bw team <set|random> ...");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("chat");
            if (sender.hasPermission("basewar.admin")) {
                subs.addAll(Arrays.asList("start", "stop", "reload", "config", "team", "spectator"));
            }
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // 이하 관리자 전용
        if (!sender.hasPermission("basewar.admin")) return new ArrayList<>();

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("config")) {
                return filterPrefix(Arrays.asList("setspawn", "inv", "kit", "gamemode"), args[1]);
            }
            if (args[0].equalsIgnoreCase("team")) {
                return filterPrefix(Arrays.asList("set", "random"), args[1]);
            }
            if (args[0].equalsIgnoreCase("spectator")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("inv")) {
                return List.of("900");
            }
            if (args[0].equalsIgnoreCase("team") && args[1].equalsIgnoreCase("set")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("team") && args[1].equalsIgnoreCase("random")) {
                int online = Bukkit.getOnlinePlayers().size();
                int max = Math.min(Team.values().length, online);
                List<String> nums = new ArrayList<>();
                for (int i = 2; i <= max; i++) nums.add(String.valueOf(i));
                return nums.stream().filter(n -> n.startsWith(args[2])).collect(Collectors.toList());
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("team") && args[1].equalsIgnoreCase("set")) {
            return Arrays.stream(Team.values())
                    .map(t -> t.name().toLowerCase())
                    .filter(t -> t.startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private String teamListString() {
        return Arrays.stream(Team.values())
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.joining("|"));
    }
}
