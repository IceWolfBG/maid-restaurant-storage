package com.example.maidrestaurant.rscompat.util;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储搜索缓存管理器。
 *
 * 包含两层缓存：
 * 1. 搜索结果缓存：缓存最近一次搜索找到的容器位置，10 tick 内重复搜索直接返回缓存。
 * 2. containsRequired 结果缓存：缓存容器检查结果，避免重复遍历所有 slot。
 *
 * 缓存设计原则：
 * - 缓存时间短（10 tick = 0.5 秒），避免数据不一致
 * - 线程安全，使用 ConcurrentHashMap
 * - 缓存失效时自动回退到原逻辑，不影响功能
 */
public class StorageSearchCache {

    /** 搜索结果缓存有效期（tick） */
    private static final long SEARCH_CACHE_TICKS = 10;

    /**
     * 搜索结果缓存。
     * key: 女仆 UUID
     * value: SearchResult（找到的容器位置 + 过期时间）
     */
    private static final Map<UUID, SearchResult> searchCache = new ConcurrentHashMap<>();

    /**
     * containsRequired 结果缓存。
     * key: 女仆 UUID + ":" + System.identityHashCode(handler)
     * value: ContainsRequiredCacheEntry（结果 + 过期时间）
     */
    private static final Map<String, ContainsRequiredCacheEntry> containsRequiredCache = new ConcurrentHashMap<>();

    /** containsRequired 缓存有效期（tick） */
    private static final long CONTAINS_REQUIRED_CACHE_TICKS = 20;

    /**
     * 搜索结果缓存条目。
     */
    private static class SearchResult {
        final BlockPos pos;
        final long expireTick;

        SearchResult(BlockPos pos, long currentTick) {
            this.pos = pos;
            this.expireTick = currentTick + SEARCH_CACHE_TICKS;
        }

        boolean isValid(long currentTick) {
            return currentTick < expireTick;
        }
    }

    /**
     * containsRequired 缓存条目。
     */
    private static class ContainsRequiredCacheEntry {
        final boolean result;
        final long expireTick;

        ContainsRequiredCacheEntry(boolean result, long currentTick) {
            this.result = result;
            this.expireTick = currentTick + CONTAINS_REQUIRED_CACHE_TICKS;
        }

        boolean isValid(long currentTick) {
            return currentTick < expireTick;
        }
    }

    /**
     * 获取搜索结果缓存。
     *
     * @param maidUuid 女仆 UUID
     * @param currentTick 当前游戏 tick
     * @return 缓存的容器位置，如果缓存不存在或已过期则返回 null
     */
    public static BlockPos getSearchResult(UUID maidUuid, long currentTick) {
        SearchResult result = searchCache.get(maidUuid);
        if (result != null && result.isValid(currentTick)) {
            return result.pos;
        }
        // 缓存过期，移除
        if (result != null) {
            searchCache.remove(maidUuid);
        }
        return null;
    }

    /**
     * 设置搜索结果缓存。
     *
     * @param maidUuid 女仆 UUID
     * @param pos 找到的容器位置
     * @param currentTick 当前游戏 tick
     */
    public static void setSearchResult(UUID maidUuid, BlockPos pos, long currentTick) {
        searchCache.put(maidUuid, new SearchResult(pos, currentTick));
    }

    /**
     * 使搜索结果缓存失效。
     * 当女仆取走物品后，应该调用此方法，避免女仆去一个已经没有所需物品的容器。
     *
     * @param maidUuid 女仆 UUID
     */
    public static void invalidateSearchResult(UUID maidUuid) {
        searchCache.remove(maidUuid);
    }

    /**
     * 获取 containsRequired 结果缓存。
     *
     * @param maidUuid 女仆 UUID
     * @param handlerIdentity IItemHandler 的身份哈希（System.identityHashCode）
     * @param currentTick 当前游戏 tick
     * @return 缓存的结果（true/false），如果缓存不存在或已过期则返回 null
     */
    public static Boolean getContainsRequiredResult(UUID maidUuid, int handlerIdentity, long currentTick) {
        String key = maidUuid.toString() + ":" + handlerIdentity;
        ContainsRequiredCacheEntry entry = containsRequiredCache.get(key);
        if (entry != null && entry.isValid(currentTick)) {
            return entry.result;
        }
        // 缓存过期，移除
        if (entry != null) {
            containsRequiredCache.remove(key);
        }
        return null;
    }

    /**
     * 设置 containsRequired 结果缓存。
     *
     * @param maidUuid 女仆 UUID
     * @param handlerIdentity IItemHandler 的身份哈希（System.identityHashCode）
     * @param result 检查结果
     * @param currentTick 当前游戏 tick
     */
    public static void setContainsRequiredResult(UUID maidUuid, int handlerIdentity, boolean result, long currentTick) {
        String key = maidUuid.toString() + ":" + handlerIdentity;
        containsRequiredCache.put(key, new ContainsRequiredCacheEntry(result, currentTick));
    }

    /**
     * 使指定女仆的 containsRequired 缓存失效。
     *
     * @param maidUuid 女仆 UUID
     */
    public static void invalidateContainsRequiredCache(UUID maidUuid) {
        String prefix = maidUuid.toString() + ":";
        containsRequiredCache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    /**
     * 清理所有缓存。
     * 通常在服务器关闭时调用。
     */
    public static void clearAll() {
        searchCache.clear();
        containsRequiredCache.clear();
    }

    /**
     * 获取缓存大小（用于调试）。
     */
    public static int getSearchCacheSize() {
        return searchCache.size();
    }
}
