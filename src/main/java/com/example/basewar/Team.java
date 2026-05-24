package com.example.basewar;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;

public enum Team {
    RED("Red",    ChatColor.RED,    Material.RED_STAINED_GLASS,    Color.RED,    BarColor.RED),
    BLUE("Blue",  ChatColor.BLUE,   Material.BLUE_STAINED_GLASS,   Color.BLUE,   BarColor.BLUE),
    YELLOW("Yellow", ChatColor.YELLOW, Material.YELLOW_STAINED_GLASS, Color.YELLOW, BarColor.YELLOW),
    GREEN("Green", ChatColor.GREEN,  Material.LIME_STAINED_GLASS,   Color.LIME,   BarColor.GREEN);

    private final String displayName;
    private final ChatColor chatColor;
    private final Material stainedGlass;
    private final Color particleColor;
    private final BarColor barColor;

    Team(String displayName, ChatColor chatColor, Material stainedGlass, Color particleColor, BarColor barColor) {
        this.displayName = displayName;
        this.chatColor = chatColor;
        this.stainedGlass = stainedGlass;
        this.particleColor = particleColor;
        this.barColor = barColor;
    }

    public String getDisplayName() { return displayName; }
    public ChatColor getChatColor() { return chatColor; }
    public Material getStainedGlass() { return stainedGlass; }
    public Color getParticleColor() { return particleColor; }
    public BarColor getBarColor() { return barColor; }
}