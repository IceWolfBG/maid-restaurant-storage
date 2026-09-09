package com.example.maidrestaurant.rscompat.storage;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 白名单物品存储：女仆可检索白名单中且不在黑名单中的容器。
 */
public class WhitelistStorage implements IMaidStorage {
    public static final String UID = "WhitelistStorage";

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.HOPPER);
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!CompatConfig.isInWhitelist(state)) return false;
        if (CompatConfig.isBlacklisted(state)) return false;
        return getHandler(level, pos) != null;
    }

    @Nullable
    @Override
    public IItemHandler getHandler(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!CompatConfig.isInWhitelist(state)) return null;
        if (CompatConfig.isBlacklisted(state)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, null);
        }
        return null;
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, int slot, int amount, boolean simulate) {
        IItemHandler handler = getHandler(level, pos);
        if (handler != null) {
            return handler.extractItem(slot, amount, simulate);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, ItemStack stack, boolean simulate) {
        IItemHandler handler = getHandler(level, pos);
        if (handler != null) {
            return ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
        }
        return stack;
    }
}
