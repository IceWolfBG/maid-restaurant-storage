package com.example.maidrestaurant.rscompat.storage;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 包装 IItemHandler，额外暴露容器内嵌套容器（如箱子里的背包）中的物品。
 * 最多检索一层嵌套，不递归。
 *
 * 虚拟槽位映射：
 * - [0, baseSlots) 对应原始 handler 的槽位
 * - [baseSlots, baseSlots+nestedSlots) 对应嵌套容器中的槽位
 */
public class NestedItemHandler implements IItemHandler {

    private final IItemHandler base;

    public NestedItemHandler(IItemHandler base) {
        this.base = base;
    }

    /** 获取原始 handler */
    public IItemHandler getBase() {
        return base;
    }

    /**
     * 扫描所有槽位，找到有物品处理能力的嵌套容器。
     * 返回 (baseSlot, nestedHandler) 列表。
     */
    private List<NestedEntry> findNestedHandlers() {
        List<NestedEntry> entries = new ArrayList<>();
        int slots = base.getSlots();
        for (int i = 0; i < slots; i++) {
            final int slotIndex = i;
            ItemStack stack = base.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            // 检查物品是否有物品处理能力（背包类物品）
            stack.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handler -> {
                entries.add(new NestedEntry(slotIndex, handler));
            });
        }
        return entries;
    }

    @Override
    public int getSlots() {
        int total = base.getSlots();
        for (NestedEntry entry : findNestedHandlers()) {
            total += entry.handler.getSlots();
        }
        return total;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        int baseSlots = base.getSlots();
        if (slot < baseSlots) {
            return base.getStackInSlot(slot);
        }
        // 嵌套槽位
        int offset = baseSlots;
        for (NestedEntry entry : findNestedHandlers()) {
            int nestedSlots = entry.handler.getSlots();
            if (slot < offset + nestedSlots) {
                return entry.handler.getStackInSlot(slot - offset);
            }
            offset += nestedSlots;
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        int baseSlots = base.getSlots();
        if (slot < baseSlots) {
            return base.insertItem(slot, stack, simulate);
        }
        // 不允许直接插入嵌套容器
        return stack;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int baseSlots = base.getSlots();
        if (slot < baseSlots) {
            return base.extractItem(slot, amount, simulate);
        }
        // 从嵌套容器提取
        int offset = baseSlots;
        for (NestedEntry entry : findNestedHandlers()) {
            int nestedSlots = entry.handler.getSlots();
            if (slot < offset + nestedSlots) {
                ItemStack extracted = entry.handler.extractItem(slot - offset, amount, simulate);
                // 如果提取成功且不是模拟，需要同步嵌套容器的 ItemStack NBT
                if (!simulate && !extracted.isEmpty()) {
                    syncNestedStack(entry);
                }
                return extracted;
            }
            offset += nestedSlots;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        int baseSlots = base.getSlots();
        if (slot < baseSlots) {
            return base.getSlotLimit(slot);
        }
        int offset = baseSlots;
        for (NestedEntry entry : findNestedHandlers()) {
            int nestedSlots = entry.handler.getSlots();
            if (slot < offset + nestedSlots) {
                return entry.handler.getSlotLimit(slot - offset);
            }
            offset += nestedSlots;
        }
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        int baseSlots = base.getSlots();
        if (slot < baseSlots) {
            return base.isItemValid(slot, stack);
        }
        return false;
    }

    /**
     * 从嵌套 handler 提取物品后，需要将修改后的 handler 状态同步回 ItemStack。
     * 大多数背包物品的 IItemHandler 实现直接操作 ItemStack 的 NBT，
     * 但有些实现需要手动保存。
     */
    private void syncNestedStack(NestedEntry entry) {
        ItemStack stack = base.getStackInSlot(entry.baseSlot);
        if (stack.isEmpty()) return;
        // 如果嵌套 handler 实现了 IItemHandlerModifiable，它通常直接操作 NBT
        // 对于需要手动保存的实现，这里可以通过 NBT 同步
        if (entry.handler instanceof IItemHandlerModifiable) {
            // 大多数背包实现直接修改 NBT，不需要额外操作
            return;
        }
        // 尝试通过 Capability 系统保存状态
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("NestedInventoryChanged", true);
    }

    private record NestedEntry(int baseSlot, IItemHandler handler) {}
}
