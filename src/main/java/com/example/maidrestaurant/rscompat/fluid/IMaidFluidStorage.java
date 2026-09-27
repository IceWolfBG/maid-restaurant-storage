package com.example.maidrestaurant.rscompat.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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

    default int getFluidAmount(Level level, BlockPos pos, FluidStack fluid) {
        IFluidHandler handler = getHandler(level, pos);
        if (handler == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stored = handler.getFluidInTank(i);
            if (!stored.isEmpty() && stored.getFluid() == fluid.getFluid()) {
                total += stored.getAmount();
            }
        }
        return total;
    }

    /**
     * 通过“空容器右键舀取”的方式装填（单份流体容器：炼药锅 / 水槽 / 流体源）。
     *
     * @param emptyContainer 女仆背包中的空容器（桶 / 玻璃瓶）
     * @return 装好流体的容器；返回空表示该存储不支持交互，调用方回退到 mB 抽取路径
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
}
