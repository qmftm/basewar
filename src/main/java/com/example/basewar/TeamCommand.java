package com.example.basewar;

import org.bukkit.Bukkit;
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

public class
TeamCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;

    public TeamCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("basewar.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§e사용법: /team <set|random> <player> [팀이름]");
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                sender.sendMessage("§e사용법: /team set <player> <" + teamListString() + ">");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c플레이어 '" + args[1] + "'를 찾을 수 없습니다.");
                return true;
            }
            try {
                Team team = Team.valueOf(args[2].toUpperCase());
                gameManager.addPlayerToTeam(target, team);
                sender.sendMessage("§a" + target.getName() + "을(를) " + team.getChatColor() + team.getDisplayName() + " §a팀에 배정했습니다.");
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§c존재하지 않는 팀입니다. (" + teamListString() + ")");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("random")) {
            if (args.length < 2) {
                sender.sendMessage("§e사용법: /team random <팀 개수(1~" + Team.values().length + ")>");
                return true;
            }
            int count;
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c팀 개수는 숫자로 입력해주세요.");
                return true;
            }
            Team[] allTeams = Team.values();
            if (count < 1 || count > allTeams.length) {
                sender.sendMessage("§c팀 개수는 1~" + allTeams.length + " 사이여야 합니다.");
                return true;
            }
            Team[] usedTeams = Arrays.copyOf(allTeams, count);
            Random random = new Random();
            for (Player p : Bukkit.getOnlinePlayers()) {
                Team randomTeam = usedTeams[random.nextInt(count)];
                gameManager.addPlayerToTeam(p, randomTeam);
            }
            String teamNames = Arrays.stream(usedTeams)
                    .map(t -> t.getChatColor() + t.getDisplayName())
                    .collect(Collectors.joining("§f, "));
            sender.sendMessage("§a모든 플레이어를 " + count + "개 팀(" + teamNames + "§a)에 무작위 배정했습니다.");
            return true;
        }

        sender.sendMessage("§e사용법: /team <set|random> <player> [팀이름]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("basewar.admin")) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("set", "random").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("random")) {
                return Arrays.stream(Team.values())
                        .map(t -> String.valueOf(Arrays.asList(Team.values()).indexOf(t) + 1))
                        .filter(n -> n.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.stream(Team.values())
                    .map(t -> t.name().toLowerCase())
                    .filter(t -> t.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private String teamListString() {
        return Arrays.stream(Team.values())
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.joining("|"));
    }
}
