package com.example.maidrestaurant.rscompat.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆餐厅的流体存储接口。
 */
public interface IMaidFluidStorage {

    String getUID();

    boolean isValid(Level level, BlockPos pos);

    @Nullable
    IFluidHandler getHandler(Level level, BlockPos pos);

    FluidStack extract(Level level, BlockPos pos, FluidStack fluid, int amount, boolean simulate);

    /**
     * 直接通过“空容器右键交互”装填女仆手中的空桶/空瓶，返回装好流体的物品栈。
     * 适用于炼药锅、水槽等按“次/桶”取水、不暴露 mB 流体能力的单份容器。
     * 返回 {@link ItemStack#EMPTY} 表示该存储走 mB {@link #extract} 路径。
     */
    default ItemStack fillContainerByInteraction(ServerLevel level, BlockPos pos, ItemStack emptyContainer) {
        return ItemStack.EMPTY;
    }

    /**
     * 该位置是否为“空、但可通过空手右键交互直接注满”的水槽。
     * 用于发现期把“空水槽”也列为可前往的目标（仅水）。
     */
    default boolean canBeFilled(Level level, BlockPos pos, FluidStack fluid) {
        return false;
    }

    /**
     * 模拟玩家空手右键，把空水槽注满，成功返回 true。
     */
    default boolean ensureFilled(ServerLevel level, BlockPos pos) {
        return false;
    }

    default int getFluidAmount(Level level, BlockPos pos, FluidStack fluid) {
        IFluidHandler handler = getHandler(level, pos);
        if (handler == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stored = handler.getFluidInTank(i);
            if (stored.isFluidEqual(fluid)) {
                total += stored.getAmount();
            }
        }
        return total;
    }
}
