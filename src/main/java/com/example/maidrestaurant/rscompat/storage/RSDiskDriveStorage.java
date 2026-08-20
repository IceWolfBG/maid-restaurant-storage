package com.example.maidrestaurant.rscompat.storage;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.storage.IStorage;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.network.node.diskdrive.DiskDriveNetworkNode;
import com.refinedmods.refinedstorage.blockentity.DiskDriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 女仆餐厅的存储实现，处理精致存储（Refined Storage）的磁盘管理器。
 *
 * 当磁盘管理器连接到RS网络时，通过网络读取物品。
 * 当磁盘管理器未连接网络时，直接读取节点已加载的磁盘缓存。
 */
public class RSDiskDriveStorage implements IMaidStorage {

    public static final String UID = "RSDiskDriveStorage";

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack((ItemLike) Blocks.CHEST);
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        if (!CompatConfig.isRsDiskDriveEnabled()) return false;
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof DiskDriveBlockEntity;
    }

    @Override
    @Nullable
    public IItemHandler getHandler(Level level, BlockPos pos) {
        if (!CompatConfig.isRsDiskDriveEnabled()) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DiskDriveBlockEntity drive)) return null;

        DiskDriveNetworkNode node = drive.getNode();
        if (node == null) return null;

        // 如果连接到网络，通过网络读取
        INetwork network = node.getNetwork();
        if (network != null) {
            return new RefinedStorageItemHandler(network);
        }

        // 未联网，直接读取节点已加载的磁盘缓存
        IStorageDisk<ItemStack>[] cachedDisks = node.getItemDisks();
        if (cachedDisks == null || cachedDisks.length == 0) return null;

        List<IStorage<ItemStack>> storages = new ArrayList<>();
        for (IStorageDisk<ItemStack> disk : cachedDisks) {
            if (disk != null) storages.add(disk);
        }
        if (storages.isEmpty()) return null;

        return new DiskItemHandler(storages);
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, int slot, int amount, boolean simulate) {
        IItemHandler handler = getHandler(level, pos);
        if (handler == null) return ItemStack.EMPTY;
        return handler.extractItem(slot, amount, simulate);
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, ItemStack stack, boolean simulate) {
        IItemHandler handler = getHandler(level, pos);
        if (handler == null) return stack;
        return handler.insertItem(0, stack, simulate);
    }

    /**
     * 将多个 IStorage 磁盘包装为 IItemHandler。
     * 使用 dirty 标志 + 时间缓存，避免每次调用都全量遍历磁盘。
     */
    private static class DiskItemHandler implements IItemHandler {
        private static final long CACHE_TTL_MS = 2000L;
        private final List<IStorage<ItemStack>> storages;
        private final List<ItemStack> cachedStacks = new ArrayList<>();
        private boolean dirty = true;
        private long lastCacheTime = 0;

        DiskItemHandler(List<IStorage<ItemStack>> storages) {
            this.storages = storages;
        }

        private void rebuildCache() {
            long now = System.currentTimeMillis();
            if (!dirty && (now - lastCacheTime) < CACHE_TTL_MS) return;
            cachedStacks.clear();
            for (IStorage<ItemStack> s : storages) {
                Collection<ItemStack> stacks = s.getStacks();
                if (stacks != null) {
                    for (ItemStack stack : stacks) {
                        if (!stack.isEmpty()) cachedStacks.add(stack.copy());
                    }
                }
            }
            dirty = false;
            lastCacheTime = now;
        }

        @Override
        public int getSlots() {
            rebuildCache();
            return cachedStacks.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            rebuildCache();
            if (slot < 0 || slot >= cachedStacks.size()) return ItemStack.EMPTY;
            return cachedStacks.get(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            ItemStack remainder = stack;
            for (IStorage<ItemStack> s : storages) {
                remainder = s.insert(remainder, remainder.getCount(), simulate ? Action.SIMULATE : Action.PERFORM);
                if (remainder.isEmpty()) {
                    if (!simulate) dirty = true;
                    return ItemStack.EMPTY;
                }
            }
            if (!simulate) dirty = true;
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            rebuildCache();
            if (slot < 0 || slot >= cachedStacks.size()) return ItemStack.EMPTY;
            ItemStack type = cachedStacks.get(slot);
            if (type.isEmpty()) return ItemStack.EMPTY;
            for (IStorage<ItemStack> s : storages) {
                ItemStack extracted = s.extract(type, amount, 0, simulate ? Action.SIMULATE : Action.PERFORM);
                if (!extracted.isEmpty()) {
                    if (!simulate) dirty = true;
                    return extracted;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
