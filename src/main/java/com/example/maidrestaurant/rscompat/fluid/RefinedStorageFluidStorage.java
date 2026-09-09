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

    @Nullable
    private Network getNetwork(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KitchenStationBlockEntity station) {
            return station.getNetwork();
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
