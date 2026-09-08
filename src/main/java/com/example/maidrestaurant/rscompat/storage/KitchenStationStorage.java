package com.example.maidrestaurant.rscompat.storage;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IStackList;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import dev.smolinacadena.refinedcooking.RefinedCookingBlocks;
import dev.smolinacadena.refinedcooking.blockentity.KitchenStationBlockEntity;
import dev.smolinacadena.refinedcooking.network.KitchenStationNetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
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
        return new ItemStack((ItemLike) RefinedCookingBlocks.KITCHEN_STATION.get());
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
        INetwork network = getNetwork(level, pos);
        if (network != null) {
            return new RefinedStorageItemHandler(network);
        }
        return null;
    }

    /**
     * 从厨房站获取已连接的 RS 网络。
     */
    @Nullable
    private INetwork getNetwork(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KitchenStationBlockEntity station) {
            KitchenStationNetworkNode node = station.getNode();
            if (node != null) {
                return node.getNetwork();
            }
        }
        return null;
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, int slot, int amount, boolean simulate) {
        INetwork network = getNetwork(level, pos);
        if (network == null) {
            return ItemStack.EMPTY;
        }
        // 直接从 RS 网络按槽位提取物品（实时获取列表，确保槽位对应正确）
        IStackList<ItemStack> list = network.getItemStorageCache().getList();
        int index = 0;
        for (StackListEntry<ItemStack> entry : list.getStacks()) {
            if (index == slot) {
                return network.extractItem(entry.getStack(), amount, simulate ? Action.SIMULATE : Action.PERFORM);
            }
            index++;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, ItemStack stack, boolean simulate) {
        INetwork network = getNetwork(level, pos);
        if (network != null) {
            ItemStack remainder = network.insertItem(stack, stack.getCount(), simulate ? Action.SIMULATE : Action.PERFORM);
            return remainder;
        }
        return stack;
    }
}
