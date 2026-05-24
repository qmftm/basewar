package com.example.basewar;

import org.bukkit.plugin.java.JavaPlugin;

public class BaseWarPlugin extends JavaPlugin {

    private GameManager gameManager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        // Load and save config
        saveDefaultConfig();

        this.gameManager = new GameManager(this);
        this.dataManager = new DataManager(this);

        // Load data after initializing gameManager
        dataManager.loadData(gameManager);

        // Register Commands
        BwCommand bwCommandExecutor = new BwCommand(gameManager);
        this.getCommand("bw").setExecutor(bwCommandExecutor);
        this.getCommand("bw").setTabCompleter(bwCommandExecutor);

        // 팀 채팅 단축 명령어
        for (String alias : new String[]{"팀채팅", "팀챗"}) {
            org.bukkit.command.PluginCommand cmd = this.getCommand(alias);
            if (cmd != null) cmd.setExecutor(bwCommandExecutor);
        }

        // Register Event Listeners
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);

        getLogger().info("기지전쟁 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            dataManager.saveData(gameManager);
        }
        getLogger().info("기지전쟁 플러그인이 비활성화되었습니다.");
    }
}
