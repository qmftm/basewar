package com.example.basewar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DataManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("data.yml 파일을 생성할 수 없습니다: " + e.getMessage());
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveData(GameManager gameManager) {
        dataConfig.set("game-in-progress", gameManager.isGameInProgress());

        for (Team team : Team.values()) {
            String path = "teams." + team.name();

            Set<UUID> members = gameManager.getTeams().get(team);
            if (members != null) {
                dataConfig.set(path + ".members", members.stream().map(UUID::toString).toList());
            }

            Boolean beaconStatus = gameManager.getBeaconStatus().get(team);
            dataConfig.set(path + ".beacon-active", beaconStatus != null && beaconStatus);

            Boolean everPlaced = gameManager.getBeaconEverPlaced().get(team);
            dataConfig.set(path + ".beacon-ever-placed", everPlaced != null && everPlaced);

            Location beaconLoc = gameManager.getBeaconLocations().get(team);
            if (beaconLoc != null && beaconLoc.getWorld() != null) {
                dataConfig.set(path + ".beacon.world", beaconLoc.getWorld().getName());
                dataConfig.set(path + ".beacon.x", beaconLoc.getX());
                dataConfig.set(path + ".beacon.y", beaconLoc.getY());
                dataConfig.set(path + ".beacon.z", beaconLoc.getZ());
            } else {
                dataConfig.set(path + ".beacon", null);
            }
        }

        // Save player-team mapping
        for (UUID uuid : gameManager.getPlayerTeams().keySet()) {
            Team team = gameManager.getPlayerTeams().get(uuid);
            dataConfig.set("player-teams." + uuid.toString(), team.name());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("데이터를 저장할 수 없습니다: " + e.getMessage());
        }
    }

    public void loadData(GameManager gameManager) {
        if (!dataFile.exists()) return;
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        boolean gameInProgress = dataConfig.getBoolean("game-in-progress", false);
        gameManager.setGameInProgress(gameInProgress);
        if (!gameInProgress) return;

        for (Team team : Team.values()) {
            String path = "teams." + team.name();

            // Load members
            var memberList = dataConfig.getStringList(path + ".members");
            Set<UUID> members = new HashSet<>();
            for (String uuidStr : memberList) {
                try {
                    members.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {}
            }
            gameManager.getTeams().put(team, members);

            // Load beacon status
            boolean beaconActive = dataConfig.getBoolean(path + ".beacon-active", false);
            gameManager.getBeaconStatus().put(team, beaconActive);

            boolean beaconEverPlaced = dataConfig.getBoolean(path + ".beacon-ever-placed", false);
            gameManager.getBeaconEverPlaced().put(team, beaconEverPlaced);

            // Load beacon location
            if (dataConfig.contains(path + ".beacon.world")) {
                String worldName = dataConfig.getString(path + ".beacon.world");
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    double x = dataConfig.getDouble(path + ".beacon.x");
                    double y = dataConfig.getDouble(path + ".beacon.y");
                    double z = dataConfig.getDouble(path + ".beacon.z");
                    gameManager.getBeaconLocations().put(team, new Location(world, x, y, z));
                }
            }
        }

        // Load player-team mapping
        var playerTeamSection = dataConfig.getConfigurationSection("player-teams");
        if (playerTeamSection != null) {
            for (String uuidStr : playerTeamSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String teamName = dataConfig.getString("player-teams." + uuidStr);
                    Team team = Team.valueOf(teamName);
                    gameManager.getPlayerTeams().put(uuid, team);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
