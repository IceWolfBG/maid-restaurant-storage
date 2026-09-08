package com.example.maidrestaurant.rscompat.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储搜索缓存管理器。
 *
 * 包含两层缓存：
 * 1. 搜索结果缓存（方案四）：缓存最近一次搜索找到的容器位置，10 tick 内重复搜索直接返回缓存。
 * 2. Slot 缓存（方案二）：缓存容器中包含特定物品的 slot，避免每次都遍历所有 slot。
 *
 * 缓存设计原则：
 * - 缓存时间短（10 tick = 0.5 秒），避免数据不一致
 * - 线程安全，使用 ConcurrentHashMap
 * - 缓存失效时自动回退到原逻辑，不影响功能
 */
public class StorageSearchCache {

    /** 搜索结果缓存有效期（tick） */
    private static final long SEARCH_CACHE_TICKS = 10;

    /** Slot 缓存有效期（tick） */
    private static final long SLOT_CACHE_TICKS = 20;

    /**
     * 搜索结果缓存。
     * key: 女仆 UUID
     * value: SearchResult（找到的容器位置 + 过期时间）
     */
    private static final Map<UUID, SearchResult> searchCache = new ConcurrentHashMap<>();

    /**
     * Slot 缓存。
     * key: 女仆 UUID + 容器位置（组合 key）
     * value: SlotCacheEntry（找到的 slot + 物品类型 + 过期时间）
     */
    private static final Map<String, SlotCacheEntry> slotCache = new ConcurrentHashMap<>();

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
     * Slot 缓存条目。
     */
    private static class SlotCacheEntry {
        final int slot;
        final Item item;
        final long expireTick;

        SlotCacheEntry(int slot, Item item, long currentTick) {
            this.slot = slot;
            this.item = item;
            this.expireTick = currentTick + SLOT_CACHE_TICKS;
        }

        boolean isValid(Item expectedItem, long currentTick) {
            return currentTick < expireTick && item == expectedItem;
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
     * 获取 Slot 缓存。
     *
     * @param maidUuid 女仆 UUID
     * @param containerPos 容器位置
     * @param expectedItem 期望的物品类型
     * @param currentTick 当前游戏 tick
     * @return 缓存的 slot 索引，如果缓存不存在或已过期则返回 -1
     */
    public static int getSlot(UUID maidUuid, BlockPos containerPos, Item expectedItem, long currentTick) {
        String key = buildSlotKey(maidUuid, containerPos);
        SlotCacheEntry entry = slotCache.get(key);
        if (entry != null && entry.isValid(expectedItem, currentTick)) {
            return entry.slot;
        }
        // 缓存过期，移除
        if (entry != null) {
            slotCache.remove(key);
        }
        return -1;
    }

    /**
     * 设置 Slot 缓存。
     *
     * @param maidUuid 女仆 UUID
     * @param containerPos 容器位置
     * @param slot 找到的 slot 索引
     * @param item 物品类型
     * @param currentTick 当前游戏 tick
     */
    public static void setSlot(UUID maidUuid, BlockPos containerPos, int slot, Item item, long currentTick) {
        String key = buildSlotKey(maidUuid, containerPos);
        slotCache.put(key, new SlotCacheEntry(slot, item, currentTick));
    }

    /**
     * 检查 IItemHandler 中是否包含指定物品，优先使用 Slot 缓存。
     *
     * @param maidUuid 女仆 UUID
     * @param containerPos 容器位置
     * @param handler IItemHandler
     * @param expectedItem 期望的物品类型
     * @param currentTick 当前游戏 tick
     * @return 如果包含指定物品则返回 true
     */
    public static boolean containsItem(UUID maidUuid, BlockPos containerPos, IItemHandler handler,
                                        Item expectedItem, long currentTick) {
        // 先检查 Slot 缓存
        int cachedSlot = getSlot(maidUuid, containerPos, expectedItem, currentTick);
        if (cachedSlot >= 0 && cachedSlot < handler.getSlots()) {
            ItemStack stack = handler.getStackInSlot(cachedSlot);
            if (!stack.isEmpty() && stack.getItem() == expectedItem) {
                return true; // 缓存命中
            }
        }

        // 缓存未命中，遍历所有 slot
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == expectedItem) {
                // 找到物品，更新缓存
                setSlot(maidUuid, containerPos, i, expectedItem, currentTick);
                return true;
            }
        }
        return false;
    }

    /**
     * 构建 Slot 缓存的 key。
     */
    private static String buildSlotKey(UUID maidUuid, BlockPos containerPos) {
        return maidUuid.toString() + ":" + containerPos.asLong();
    }

    /**
     * 清理所有缓存。
     * 通常在服务器关闭时调用。
     */
    public static void clearAll() {
        searchCache.clear();
        slotCache.clear();
        containsRequiredCache.clear();
    }

    /**
     * 获取缓存大小（用于调试）。
     */
    public static int getSearchCacheSize() {
        return searchCache.size();
    }

    public static int getSlotCacheSize() {
        return slotCache.size();
    }
}
