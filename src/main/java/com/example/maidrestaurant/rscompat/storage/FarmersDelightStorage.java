package com.example.maidrestaurant.rscompat.storage;

import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.block.entity.CabinetBlockEntity;

/**
 * 女仆餐厅的存储实现，处理农夫乐事（Farmer's Delight）的橱柜。
 * 橱柜有27个槽位，继承自 RandomizableContainerBlockEntity，自动支持 ITEM_HANDLER capability。
 */
public class FarmersDelightStorage implements IMaidStorage {

    public static final String UID = "FarmersDelightStorage";

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.BARREL);
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        if (com.example.maidrestaurant.rscompat.config.CompatConfig.isBlacklisted(level.getBlockState(pos))) return false;
        return getHandler(level, pos) != null;
    }

    @Override
    @Nullable
    public IItemHandler getHandler(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CabinetBlockEntity) {
            return be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
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
