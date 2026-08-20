package com.example.maidrestaurant.rscompat.storage;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IStackList;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 将精致存储（Refined Storage）网络的物品存储包装为 IItemHandler 接口。
 * RS 存储是无槽位的统一存储，这里将物品列表映射为虚拟槽位以供女仆餐厅检索。
 */
public class RefinedStorageItemHandler implements IItemHandler {

    private final INetwork network;

    public RefinedStorageItemHandler(INetwork network) {
        this.network = network;
    }

    /**
     * 从 RS 网络实时获取物品列表，映射为虚拟槽位。
     */
    private List<ItemStack> getCachedStacks() {
        List<ItemStack> result = new ArrayList<>();
        if (network == null) {
            return result;
        }
        IStackList<ItemStack> list = network.getItemStorageCache().getList();
        for (StackListEntry<ItemStack> entry : list.getStacks()) {
            result.add(entry.getStack().copy());
        }
        return result;
    }

    @Override
    public int getSlots() {
        return getCachedStacks().size();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        List<ItemStack> stacks = getCachedStacks();
        if (slot < 0 || slot >= stacks.size()) {
            return ItemStack.EMPTY;
        }
        return stacks.get(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || network == null) {
            return stack;
        }
        // RS 是统一存储，忽略槽位参数，直接插入网络
        return network.insertItem(stack, stack.getCount(), simulate ? Action.SIMULATE : Action.PERFORM);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || network == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> stacks = getCachedStacks();
        if (slot < 0 || slot >= stacks.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack type = stacks.get(slot);
        if (type.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return network.extractItem(type, amount, simulate ? Action.SIMULATE : Action.PERFORM);
    }

    @Override
    public int getSlotLimit(int slot) {
        // RS 没有单槽位上限，返回一个较大的值
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return true;
    }
}
