package com.example.maidrestaurant.rscompat.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 流体水槽的“占用预留”状态机（仅服务端、运行时内存，不持久化）。
 *
 * 女仆在发现水槽时即预留，防止多女仆同时走向 / 舀取同一水槽；
 * 空水槽经历“注水 -&gt; 停留（让玩家看到水槽被注满）-&gt; 舀水”，完成后释放。
 *
 * 预留带 TTL 自愈：女仆死亡、区块卸载或任务中断时，超时自动释放，避免水槽被永久锁死。
 */
public final class FluidReservationManager {

    /** 预留最长存活时间（tick），超过自动释放（覆盖正常的寻路 + 注水等待）。 */
    private static final long TTL_TICKS = 600L;

    public static final class Reservation {
        final UUID maid;
        final String key;
        final long createdAt;
        long readyAt = -1L;   // 已注水、允许舀水的游戏时刻；-1 表示尚未注水
        boolean filled = false;

        Reservation(UUID maid, String key, long createdAt) {
            this.maid = maid;
            this.key = key;
            this.createdAt = createdAt;
        }

        public boolean isFilled() {
            return filled;
        }

        public long getReadyAt() {
            return readyAt;
        }
    }

    private static final Map<String, Reservation> BY_KEY = new HashMap<>();
    private static final Map<UUID, String> BY_MAID = new HashMap<>();

    private FluidReservationManager() {
    }

    public static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location().toString() + "@" + pos.asLong();
    }

    /** 清理过期预留（含游戏时间回退，如重进会话）。 */
    public static void prune(long now) {
        BY_KEY.entrySet().removeIf(e -> {
            Reservation r = e.getValue();
            boolean stale = now < r.createdAt || now - r.createdAt > TTL_TICKS;
            if (stale) {
                BY_MAID.remove(r.maid);
            }
            return stale;
        });
    }

    /** 该位置是否被别的女仆预留。 */
    public static boolean isReservedByOther(ServerLevel level, BlockPos pos, UUID self) {
        prune(level.getGameTime());
        Reservation r = BY_KEY.get(key(level, pos));
        return r != null && !r.maid.equals(self);
    }

    /**
     * 为女仆预留某水槽（发现期）。同一女仆 + 同一位置重复预留幂等。
     *
     * @return true 预留成功（含原本就归该女仆）；false 已被别的女仆占用
     */
    public static boolean reserve(ServerLevel level, BlockPos pos, UUID maid) {
        long now = level.getGameTime();
        prune(now);
        String k = key(level, pos);
        Reservation r = BY_KEY.get(k);
        if (r != null) {
            return r.maid.equals(maid);
        }
        String prior = BY_MAID.get(maid);
        if (prior != null) {
            BY_KEY.remove(prior);
        }
        r = new Reservation(maid, k, now);
        BY_KEY.put(k, r);
        BY_MAID.put(maid, k);
        return true;
    }

    @Nullable
    public static Reservation getByMaid(UUID maid) {
        String k = BY_MAID.get(maid);
        return k == null ? null : BY_KEY.get(k);
    }

    /** 获取女仆在该维度当前预留的水槽坐标；没有或跨维度则 null。会先清理过期预留。 */
    @Nullable
    public static BlockPos getReservedPos(ServerLevel level, UUID maid) {
        prune(level.getGameTime());
        String k = BY_MAID.get(maid);
        if (k == null) return null;
        String prefix = level.dimension().location().toString() + "@";
        if (!k.startsWith(prefix)) return null;
        try {
            return BlockPos.of(Long.parseLong(k.substring(prefix.length())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 标记已注水、设定可舀水时刻。 */
    public static void markFilled(ServerLevel level, BlockPos pos, long readyAt) {
        Reservation r = BY_KEY.get(key(level, pos));
        if (r != null) {
            r.filled = true;
            r.readyAt = readyAt;
        }
    }

    /** 释放某女仆的预留。 */
    public static void release(UUID maid) {
        String k = BY_MAID.remove(maid);
        if (k != null) {
            BY_KEY.remove(k);
        }
    }
}
