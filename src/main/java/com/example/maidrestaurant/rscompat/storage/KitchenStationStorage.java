package com.example.maidrestaurant.rscompat.storage;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.TrackedResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import sebastrn.refinedcooking.RefinedCookingBlocks;
import sebastrn.refinedcooking.blockentity.KitchenStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆餐厅的存储实现，专门处理精致厨房（Refined Cooking）的厨房站。
 * 通过厨房站连接的精致存储（Refined Storage）网络来存取物品。
 */
public class KitchenStationStorage implements IMaidStorage {

    public static final String UID = "KitchenStationStorage";

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        try {
            return new ItemStack(RefinedCookingBlocks.KITCHEN_STATION.get());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        if (!CompatConfig.isRsCompatEnabled()) {
            return false;
        }
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) return false;
        return getHandler(level, pos) != null;
    }

    @Override
    @Nullable
    public IItemHandler getHandler(Level level, BlockPos pos) {
        if (!CompatConfig.isRsCompatEnabled()) {
            return null;
        }
        Network network = getNetwork(level, pos);
        if (network != null) {
            return new RefinedStorageItemHandler(network);
        }
        return null;
    }

    /**
     * 从厨房站获取已连接的 RS 网络。
     */
    @Nullable
    private Network getNetwork(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KitchenStationBlockEntity station) {
            return station.getNetwork();
        }
        return null;
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, int slot, int amount, boolean simulate) {
        Network network = getNetwork(level, pos);
        if (network == null) {
            return ItemStack.EMPTY;
        }
        try {
            StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            if (storage == null) return ItemStack.EMPTY;
            var resources = storage.getResources(Actor.class);
            int index = 0;
            for (TrackedResourceAmount tracked : resources) {
                if (index == slot) {
                    if (tracked.resourceAmount().resource() instanceof ItemResource itemResource) {
                        long extracted = storage.extract(itemResource, amount, simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
                        if (extracted > 0) {
                            return itemResource.toItemStack(extracted);
                        }
                    }
                    return ItemStack.EMPTY;
                }
                index++;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, ItemStack stack, boolean simulate) {
        Network network = getNetwork(level, pos);
        if (network != null) {
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
        return stack;
    }
}
