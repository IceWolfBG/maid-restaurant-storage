package com.example.maidrestaurant.rscompat.storage;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import com.refinedmods.refinedstorage.api.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆餐厅的存储实现，处理精致存储（Refined Storage）的磁盘管理器。
 *
 * 当磁盘管理器连接到RS网络时，通过网络读取物品。
 */
public class RSDiskDriveStorage implements IMaidStorage {

    public static final String UID = "RSDiskDriveStorage";

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Blocks.CHEST);
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        if (!CompatConfig.isRsDiskDriveEnabled()) return false;
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        // 通过类名检查，避免直接引用可能变化的类
        return be.getClass().getName().contains("DiskDriveBlockEntity");
    }

    @Override
    @Nullable
    public IItemHandler getHandler(Level level, BlockPos pos) {
        if (!CompatConfig.isRsDiskDriveEnabled()) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        try {
            // 通过反射获取节点和网络
            java.lang.reflect.Method getNodeMethod = be.getClass().getMethod("getNode");
            Object node = getNodeMethod.invoke(be);
            if (node == null) return null;

            java.lang.reflect.Method getNetworkMethod = node.getClass().getMethod("getNetwork");
            Network network = (Network) getNetworkMethod.invoke(node);
            if (network != null) {
                return new RefinedStorageItemHandler(network);
            }
        } catch (Exception e) {
            // 忽略异常，返回null
        }

        return null;
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
}
