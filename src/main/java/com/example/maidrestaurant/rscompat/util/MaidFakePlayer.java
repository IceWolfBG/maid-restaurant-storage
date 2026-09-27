package com.example.maidrestaurant.rscompat.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

/**
 * 女仆流体检索专用的 FakePlayer，用于模拟“空桶/空瓶右键舀取”。
 * 每个 ServerLevel 缓存一个实例。
 */
public final class MaidFakePlayer {

    private static final GameProfile PROFILE =
            new GameProfile(UUID.nameUUIDFromBytes("maid_fluid_filler".getBytes()), "MaidFluidFiller");

    private static FakePlayer cached;
    private static ServerLevel cachedLevel;

    private MaidFakePlayer() {
    }

    public static FakePlayer get(ServerLevel level, BlockPos pos) {
        if (cached == null || cachedLevel != level) {
            cached = new FakePlayer(level, PROFILE);
            cachedLevel = level;
        }
        FakePlayer player = cached;
        player.setServerLevel(level);
        player.moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0F, 0.0F);
        return player;
    }
}
