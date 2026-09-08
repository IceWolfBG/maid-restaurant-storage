package com.example.maidrestaurant.rscompat.storage;

import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import com.progwml6.ironchest.common.block.regular.entity.AbstractIronChestBlockEntity;
import com.progwml6.ironchest.common.block.trapped.entity.AbstractTrappedIronChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆餐厅的存储实现，处理更多箱子（Iron Chests）的所有箱子类型。
 * 包括普通箱子（铁、金、钻石、铜、水晶、黑曜石、泥土）和对应的陷阱箱子。
 * 直接通过 instanceof 检查 BlockEntity 类型，不依赖标签。
 */
public class IronChestStorage implements IMaidStorage {

    public static final String UID = "IronChestStorage";

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.CHEST);
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
        if (be instanceof AbstractIronChestBlockEntity || be instanceof AbstractTrappedIronChestBlockEntity) {
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
