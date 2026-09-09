package com.example.maidrestaurant.rscompat.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆餐厅流体存储注册表。
 */
public class MaidFluidStorages {

    private static final List<IMaidFluidStorage> STORAGES = new ArrayList<>();

    public static void register(IMaidFluidStorage storage) {
        STORAGES.add(storage);
    }

    @Nullable
    public static IMaidFluidStorage tryGetType(Level level, BlockPos pos) {
        for (IMaidFluidStorage storage : STORAGES) {
            if (storage.isValid(level, pos)) {
                return storage;
            }
        }
        return null;
    }

    @Nullable
    public static IFluidHandler tryGetHandler(Level level, BlockPos pos) {
        IMaidFluidStorage storage = tryGetType(level, pos);
        if (storage != null) {
            return storage.getHandler(level, pos);
        }
        return null;
    }

    public static List<IMaidFluidStorage> getStorages() {
        return STORAGES;
    }

    public static boolean hasEnoughFluid(Level level, BlockPos pos, FluidStack fluid, int required) {
        IMaidFluidStorage storage = tryGetType(level, pos);
        if (storage == null) {
            return false;
        }
        return storage.getFluidAmount(level, pos, fluid) >= required;
    }
}
