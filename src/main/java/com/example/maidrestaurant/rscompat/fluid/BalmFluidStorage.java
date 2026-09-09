package com.example.maidrestaurant.rscompat.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * 通用 Balm 流体存储实现。
 * 通过反射访问实现了 BalmFluidTankProvider 接口的方块实体（如懒人厨房的水槽）。
 * 不依赖 Balm 库进行编译，运行时通过反射调用。
 */
public class BalmFluidStorage implements IMaidFluidStorage {

    public static final String UID = "BalmFluidStorage";

    private static Class<?> balmProviderClass;
    private static Method getFluidTankMethod;
    private static Method fluidTankGetFluidMethod;
    private static Method fluidTankGetAmountMethod;
    private static Method fluidTankDrainMethod;
    private static boolean reflectionInitialized = false;
    private static boolean reflectionAvailable = false;

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            balmProviderClass = Class.forName("net.blay09.mods.balm.api.fluid.BalmFluidTankProvider");
            getFluidTankMethod = balmProviderClass.getMethod("getFluidTank");
            Class<?> fluidTankClass = Class.forName("net.blay09.mods.balm.api.fluid.FluidTank");
            fluidTankGetFluidMethod = fluidTankClass.getMethod("getFluid");
            fluidTankGetAmountMethod = fluidTankClass.getMethod("getAmount");
            fluidTankDrainMethod = fluidTankClass.getMethod("drain", Fluid.class, int.class, boolean.class);
            reflectionAvailable = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            reflectionAvailable = false;
        }
    }

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        if (com.example.maidrestaurant.rscompat.config.CompatConfig.isBlacklisted(level.getBlockState(pos))) return false;
        initReflection();
        if (!reflectionAvailable) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && balmProviderClass.isInstance(be);
    }

    @Override
    @Nullable
    public IFluidHandler getHandler(Level level, BlockPos pos) {
        // Balm 流体不直接实现 IFluidHandler，返回 null，使用 extract/getFluidAmount 方法
        return null;
    }

    @Override
    public FluidStack extract(Level level, BlockPos pos, FluidStack fluid, int amount, boolean simulate) {
        initReflection();
        if (!reflectionAvailable) return FluidStack.EMPTY;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !balmProviderClass.isInstance(be)) return FluidStack.EMPTY;

        try {
            Object tank = getFluidTankMethod.invoke(be);
            if (tank == null) return FluidStack.EMPTY;

            Fluid tankFluid = (Fluid) fluidTankGetFluidMethod.invoke(tank);
            if (tankFluid == null || !tankFluid.isSame(fluid.getFluid())) return FluidStack.EMPTY;

            int drained = (int) fluidTankDrainMethod.invoke(tank, fluid.getFluid(), amount, simulate);
            if (drained > 0) {
                return new FluidStack(fluid.getFluid(), drained);
            }
        } catch (Exception e) {
            // 静默失败
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getFluidAmount(Level level, BlockPos pos, FluidStack fluid) {
        initReflection();
        if (!reflectionAvailable) return 0;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !balmProviderClass.isInstance(be)) return 0;

        try {
            Object tank = getFluidTankMethod.invoke(be);
            if (tank == null) return 0;

            Fluid tankFluid = (Fluid) fluidTankGetFluidMethod.invoke(tank);
            if (tankFluid == null || !tankFluid.isSame(fluid.getFluid())) return 0;

            return (int) fluidTankGetAmountMethod.invoke(tank);
        } catch (Exception e) {
            return 0;
        }
    }
}
