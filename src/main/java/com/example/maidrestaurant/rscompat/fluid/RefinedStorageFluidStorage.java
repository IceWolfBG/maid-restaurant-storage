package com.example.maidrestaurant.rscompat.fluid;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.TrackedResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import sebastrn.refinedcooking.blockentity.KitchenStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 通过精致厨房厨房站访问 RS 流体网络。
 */
public class RefinedStorageFluidStorage implements IMaidFluidStorage {

    public static final String UID = "RefinedStorageFluidStorage";

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        if (com.example.maidrestaurant.rscompat.config.CompatConfig.isBlacklisted(level.getBlockState(pos))) return false;
        return getNetwork(level, pos) != null;
    }

    @Override
    @Nullable
    public IFluidHandler getHandler(Level level, BlockPos pos) {
        Network network = getNetwork(level, pos);
        return network != null ? new NetworkFluidHandler(network) : null;
    }

    @Override
    public FluidStack extract(Level level, BlockPos pos, FluidStack fluid, int amount, boolean simulate) {
        Network network = getNetwork(level, pos);
        if (network != null) {
            try {
                StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
                if (storage == null) return FluidStack.EMPTY;
                FluidResource resource = new FluidResource(fluid.getFluid());
                long extracted = storage.extract(resource, amount, simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
                if (extracted > 0) {
                    return new FluidStack(fluid.getFluid(), (int) extracted);
                }
            } catch (Exception e) {
                // 忽略异常
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getFluidAmount(Level level, BlockPos pos, FluidStack fluid) {
        Network network = getNetwork(level, pos);
        if (network == null) return 0;
        try {
            StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            if (storage == null) return 0;
            FluidResource resource = new FluidResource(fluid.getFluid());
            return (int) storage.get(resource);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 解析目标方块所属的 RS 网络。
     * 1) 精致厨房厨房站；2) 任意 RS2 网络节点（流体网格/控制器/磁盘驱动器/线缆等）。
     *
     * RS2 节点 BE 的取网络方法 {@code getNetworkForItem()} 位于带 apiguardian 注解的
     * common 类上，直接引用会让编译期缺注解 jar；这里反射调用，方法名在非混淆的 mod
     * 类中稳定。
     */
    @Nullable
    private Network getNetwork(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        // 1) 精致厨房厨房站
        if (be instanceof KitchenStationBlockEntity station) {
            Network n = station.getNetwork();
            if (n != null) return n;
        }

        // 2) 任意 RS2 网络节点：反射调用 getNetworkForItem()
        try {
            Method m = findGetNetworkForItem(be.getClass());
            if (m != null) {
                Object n = m.invoke(be);
                if (n instanceof Network network) return network;
            }
        } catch (Throwable ignored) {
            // 无网络 / 不可访问时视为 null
        }
        return null;
    }

    /** 缓存：BE 类 -> getNetworkForItem 方法（找不到记 null 哨兵）。 */
    @Nullable
    private static Method findGetNetworkForItem(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("getNetworkForItem");
                if (Network.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // 继续往父类找
            }
        }
        return null;
    }

    private static class NetworkFluidHandler implements IFluidHandler {
        private final Network network;

        NetworkFluidHandler(Network network) {
            this.network = network;
        }

        @Override
        public int getTanks() { return 1; }

        @Override
        public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }

        @Override
        public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) { return true; }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            try {
                StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
                if (storage == null) return 0;
                FluidResource fluidResource = new FluidResource(resource.getFluid());
                long inserted = storage.insert(fluidResource, resource.getAmount(),
                        action == FluidAction.SIMULATE ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
                return (int) inserted;
            } catch (Exception e) {
                return 0;
            }
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            try {
                StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
                if (storage == null) return FluidStack.EMPTY;
                FluidResource fluidResource = new FluidResource(resource.getFluid());
                long extracted = storage.extract(fluidResource, resource.getAmount(),
                        action == FluidAction.SIMULATE ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
                if (extracted > 0) {
                    return new FluidStack(resource.getFluid(), (int) extracted);
                }
            } catch (Exception e) {
                // 忽略异常
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
