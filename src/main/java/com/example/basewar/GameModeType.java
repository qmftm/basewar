package com.example.basewar;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

public enum GameModeType {
    DEFAULT("§b기본 게임모드", Material.GRASS_BLOCK, 9,
            "§7기지전쟁의 기본 규칙으로 진행됩니다.",
            "§7신호기를 설치하고 적 팀의 신호기를 파괴하세요.",
            "§7신호기가 파괴된 팀은 리스폰할 수 없습니다."),

    CONQUEST("§6\uD83D\uDEA9 점령전 \uD83D\uDEA9", Material.RED_BANNER, 10,
            "§7신호기 주변 구역을 점령하는 모드입니다.",
            "§7구역 안에 우리 팀 인원이 적보다 많으면",
            "§7점령이 진행되어 100%가 되면 신호기를 획득합니다.",
            "§7모든 신호기를 잃으면 멸망합니다."),

    HARDCORE("§4\uD83D\uDC80 하드코어 \uD83D\uDC80", Material.WITHER_SKELETON_SKULL, 11,
            "§7부활 쿨타임이 없고 인벤토리가 보호됩니다.",
            "§7단, 사망 시 팀원이 신호기를 우클릭하여",
            "§7다이아몬드 32개를 소모해야 부활할 수 있습니다.");

    private final String displayName;
    private final Material material;
    private final int slot;
    private final List<String> lore;

    GameModeType(String displayName, Material material, int slot, String... lore) {
        this.displayName = displayName;
        this.material = material;
        this.slot = slot;
        this.lore = Arrays.asList(lore);
    }

    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public int getSlot() { return slot; }
    public List<String> getLore() { return lore; }

    public static GameModeType fromSlot(int slot) {
        for (GameModeType mode : values()) {
            if (mode.slot == slot) return mode;
        }
        return null;
    }
}
