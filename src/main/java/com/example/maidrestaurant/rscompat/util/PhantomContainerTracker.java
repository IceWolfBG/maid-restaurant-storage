package com.example.maidrestaurant.rscompat.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 幽灵容器追踪器。
 *
 * 某些 mod 方块（如 Refined Storage 磁盘驱动器）在存储标签内、对外暴露的物品处理器
 * “报告”含有食材，但女仆实际交互/抽取时一无所获，导致本体搜索恒为 true、把女仆永久锁死，
 * 流体兜底（仅在本体搜索失败时运行）被饿死。
 *
 * 本追踪器记录女仆对某容器“连续零收获”的交互次数，达到阈值后将该位置临时拉黑（全局、带 TTL），
 * 让搜索跳过该容器，回落到其它容器或流体兜底；TTL 到期自动恢复，避免永久误判。
 *
 * 线程模型：仅在服务端主线程调用，时间用 level.getGameTime() 惰性比较，无需订阅 tick。
 */
public final class PhantomContainerTracker {

    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantStorage");

    /** 连续零收获多少次后临时拉黑。 */
    private static final int FAIL_THRESHOLD = 2;
    /** 拉黑持续时间（tick）。 */
    private static final long BLACKLIST_TTL = 600L;

    /** 全局拉黑记录：blockKey -> expiryTick（一个位置取不到，所有女仆都取不到）。 */
    private static final Map<String, Long> BLACKLIST = new HashMap<>();
    /** 连续零收获计数：maidKey|blockKey -> count（按女仆检测）。 */
    private static final Map<String, Integer> FAIL_COUNTS = new HashMap<>();

    private PhantomContainerTracker() {}

    private static String dim(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static String blockKey(ServerLevel level, BlockPos pos) {
        return dim(level) + "|" + pos.asLong();
    }

    private static String failKey(ServerLevel level, UUID maid, BlockPos pos) {
        return dim(level) + "|" + maid + "|" + pos.asLong();
    }

    /** 女仆在 targetPos 一次取物零收获：累加；达到阈值则全局临时拉黑。 */
    public static void recordZeroYield(ServerLevel level, UUID maid, BlockPos pos) {
        if (pos == null) return;
        String fk = failKey(level, maid, pos);
        int c = FAIL_COUNTS.getOrDefault(fk, 0) + 1;
        FAIL_COUNTS.put(fk, c);
        if (c >= FAIL_THRESHOLD) {
            BLACKLIST.put(blockKey(level, pos), level.getGameTime() + BLACKLIST_TTL);
        }
    }

    /** 女仆在 targetPos 取物有收获：清零该容器的失败计数。 */
    public static void recordProgress(ServerLevel level, UUID maid, BlockPos pos) {
        if (pos == null) return;
        FAIL_COUNTS.remove(failKey(level, maid, pos));
    }

    /** 该位置当前是否被临时拉黑。 */
    public static boolean isBlacklisted(ServerLevel level, BlockPos pos) {
        if (pos == null) return false;
        String bk = blockKey(level, pos);
        Long expiry = BLACKLIST.get(bk);
        if (expiry == null) return false;
        if (level.getGameTime() > expiry) {
            BLACKLIST.remove(bk);
            return false;
        }
        return true;
    }

    /** 取得当前维度所有仍在拉黑期内的方块坐标（用于排除式搜索）。 */
    public static Set<BlockPos> getBlacklistedPositions(ServerLevel level) {
        Set<BlockPos> result = new HashSet<>();
        String prefix = dim(level) + "|";
        long now = level.getGameTime();
        Iterator<Map.Entry<String, Long>> it = BLACKLIST.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now > e.getValue()) {
                it.remove();
                continue;
            }
            String key = e.getKey();
            if (key.startsWith(prefix)) {
                try {
                    result.add(BlockPos.of(Long.parseLong(key.substring(prefix.length()))));
                } catch (NumberFormatException ignored) {
                    // 非本维度/非纯坐标 key，跳过
                }
            }
        }
        return result;
    }
}
