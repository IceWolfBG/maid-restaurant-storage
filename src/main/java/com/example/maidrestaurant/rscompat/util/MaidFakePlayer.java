package com.example.maidrestaurant.rscompat.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 提供一个用于模拟“空桶/空瓶右键方块”的 FakePlayer。
 * FakePlayer 按 ServerLevel 缓存；其 awardStat 等方法在 Forge 中为空操作，可安全用于自动化交互。
 */
public final class MaidFakePlayer {

    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("maid_restaurant_fluid_filler".getBytes(StandardCharsets.UTF_8)),
            "MaidFluidFiller");

    private static FakePlayer fakePlayer;
    private static ServerLevel fakeLevel;

    private MaidFakePlayer() {
    }

    /**
     * 获取（或按维度重建）FakePlayer，并把它移动到指定坐标。
     */
    public static FakePlayer get(ServerLevel level, double x, double y, double z) {
        if (fakePlayer == null || fakeLevel != level) {
            fakePlayer = new FakePlayer(level, PROFILE);
            fakeLevel = level;
        }
        fakePlayer.moveTo(x, y, z, 0.0f, 0.0f);
        fakePlayer.setPos(x, y, z);
        fakePlayer.setYHeadRot(0.0f);
        return fakePlayer;
    }
}
