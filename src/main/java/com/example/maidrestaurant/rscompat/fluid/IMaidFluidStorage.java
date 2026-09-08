package com.example.maidrestaurant.rscompat.fluid;

import net.minecraft.core.BlockPos;
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
