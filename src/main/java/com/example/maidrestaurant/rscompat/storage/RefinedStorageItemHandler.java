package com.example.maidrestaurant.rscompat.storage;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.TrackedResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 将精致存储（Refined Storage）网络的物品存储包装为 IItemHandler 接口。
 * RS 存储是无槽位的统一存储，这里将物品列表映射为虚拟槽位以供女仆餐厅检索。
 */
public class RefinedStorageItemHandler implements IItemHandler {

    private final Network network;

    public RefinedStorageItemHandler(Network network) {
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
        try {
            StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            if (storage == null) return result;
            List<TrackedResourceAmount> resources = storage.getResources(Actor.class);
            for (TrackedResourceAmount tracked : resources) {
                if (tracked.resourceAmount().resource() instanceof ItemResource itemResource) {
                    result.add(itemResource.toItemStack(tracked.resourceAmount().amount()));
                }
            }
        } catch (Exception e) {
            // 忽略异常，返回空列表
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
        try {
            StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            if (storage == null) return stack;
            ItemResource resource = ItemResource.ofItemStack(stack);
            long inserted = storage.insert(resource, stack.getCount(), simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
            if (inserted >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = stack.copy();
            remainder.setCount((int) (stack.getCount() - inserted));
            return remainder;
        } catch (Exception e) {
            return stack;
        }
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
        try {
            StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            if (storage == null) return ItemStack.EMPTY;
            ItemResource resource = ItemResource.ofItemStack(type);
            long extracted = storage.extract(resource, amount, simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
            if (extracted <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack result = type.copy();
            result.setCount((int) extracted);
            return result;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
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
