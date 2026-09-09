package com.example.maidrestaurant.rscompat.fluid;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 白名单流体存储：女仆可从白名单中且不在黑名单中的流体容器提取流体。
 */
public class WhitelistFluidStorage implements IMaidFluidStorage {
    public static final String UID = "WhitelistFluidStorage";

    @Override
    public String getUID() {
        return UID;
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
    public IFluidHandler getHandler(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!CompatConfig.isInWhitelist(state)) return null;
        if (CompatConfig.isBlacklisted(state)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
        }
        return null;
    }

    @Override
    public int getFluidAmount(Level level, BlockPos pos, FluidStack fluid) {
        IFluidHandler handler = getHandler(level, pos);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stored = handler.getFluidInTank(i);
            if (stored.getFluid() == fluid.getFluid()) {
                total += stored.getAmount();
            }
        }
        return total;
    }

    @Override
    public FluidStack extract(Level level, BlockPos pos, FluidStack fluid, int amount, boolean simulate) {
        IFluidHandler handler = getHandler(level, pos);
        if (handler == null) return FluidStack.EMPTY;
        FluidStack toExtract = new FluidStack(fluid.getFluid(), amount);
        return handler.drain(toExtract, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }
}
