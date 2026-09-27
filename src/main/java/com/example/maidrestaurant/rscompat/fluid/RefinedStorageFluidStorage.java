package com.example.maidrestaurant.rscompat.fluid;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IStackList;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability;
import dev.smolinacadena.refinedcooking.blockentity.KitchenStationBlockEntity;
import dev.smolinacadena.refinedcooking.network.KitchenStationNetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 通过 RS 网络访问流体：支持精致厨房厨房站，以及任意 RS 网络节点
 * （流体网格、控制器、磁盘驱动器、线缆等）。右键这些方块只会打开 GUI，
 * 女仆装桶走 {@link #extract}（network.extractFluid）路径。
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

    /**
     * 解析目标方块所属的 RS 网络。
     * 1) 精致厨房厨房站；2) 任意 RS 网络节点（流体网格/控制器/磁盘驱动器/线缆等）。
     */
    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private INetwork getNetwork(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        // 1) 精致厨房厨房站
        if (be instanceof KitchenStationBlockEntity station) {
            KitchenStationNetworkNode node = station.getNode();
            if (node != null && node.getNetwork() != null) return node.getNetwork();
        }

        // 2) 任意 RS 网络节点：经节点代理能力取 node.getNetwork()
        LazyOptional<INetworkNodeProxy> cap = be.getCapability(
                NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY, null);
        if (cap.isPresent()) {
            try {
                INetworkNodeProxy proxy = cap.orElse(null);
                if (proxy != null) {
                    INetworkNode node = proxy.getNode();
                    if (node != null && node.getNetwork() != null) return node.getNetwork();
                }
            } catch (Throwable ignored) {
                // getNode() 在无节点时按契约抛异常，视为无网络
            }
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
