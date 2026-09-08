package com.example.maidrestaurant.rscompat.fluid;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IStackList;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import dev.smolinacadena.refinedcooking.blockentity.KitchenStationBlockEntity;
import dev.smolinacadena.refinedcooking.network.KitchenStationNetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

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
        INetwork network = getNetwork(level, pos);
        return network != null ? new NetworkFluidHandler(network) : null;
    }

    @Override
    public FluidStack extract(Level level, BlockPos pos, FluidStack fluid, int amount, boolean simulate) {
        INetwork network = getNetwork(level, pos);
        if (network != null) {
            return network.extractFluid(fluid, amount, simulate ? Action.SIMULATE : Action.PERFORM);
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getFluidAmount(Level level, BlockPos pos, FluidStack fluid) {
        INetwork network = getNetwork(level, pos);
        if (network == null) return 0;
        IStackList<FluidStack> list = network.getFluidStorageCache().getList();
        int total = 0;
        for (StackListEntry<FluidStack> entry : list.getStacks()) {
            if (entry.getStack().isFluidEqual(fluid)) {
                total += entry.getStack().getAmount();
            }
        }
        return total;
    }

    @Nullable
    private INetwork getNetwork(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KitchenStationBlockEntity station) {
            KitchenStationNetworkNode node = station.getNode();
            if (node != null) return node.getNetwork();
        }
        return null;
    }

    private static class NetworkFluidHandler implements IFluidHandler {
        private final INetwork network;

        NetworkFluidHandler(INetwork network) {
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
            FluidStack remainder = network.insertFluid(resource, resource.getAmount(),
                    action == FluidAction.SIMULATE ? Action.SIMULATE : Action.PERFORM);
            return resource.getAmount() - remainder.getAmount();
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return network.extractFluid(resource, resource.getAmount(),
                    action == FluidAction.SIMULATE ? Action.SIMULATE : Action.PERFORM);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
