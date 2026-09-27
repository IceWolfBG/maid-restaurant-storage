package com.example.maidrestaurant.rscompat.fluid;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.example.maidrestaurant.rscompat.util.FluidReservationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆餐厅流体存储注册表。
 */
public class MaidFluidStorages {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("MaidRestaurantStorage");
    private static final List<IMaidFluidStorage> STORAGES = new ArrayList<>();

    public static void register(IMaidFluidStorage storage) {
        STORAGES.add(storage);
    }

    @Nullable
    public static IMaidFluidStorage tryGetType(Level level, BlockPos pos) {
        // 注册表级黑名单闸门：黑名单最高优先，统一拦截所有流体适配器（含结构检测的水槽/炼药锅）
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) {
            return null;
        }
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

    /**
     * 发现期统一判定：该位置要么现有流体已满足需求，要么是“空但可直接注满”的水槽（仅水）。
     * 不先按 isValid 过滤，确保空水槽也能被发现；黑名单最高优先；被别的女仆预留的水槽排除。
     */
    public static boolean isAvailableOrFillable(Level level, BlockPos pos, FluidStack fluid, int required, java.util.UUID self) {
        net.minecraft.world.level.block.state.BlockState pState = level.getBlockState(pos);
        if (CompatConfig.isBlacklisted(pState)) {
            return false;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && FluidReservationManager.isReservedByOther(serverLevel, pos, self)) {
            return false;
        }
        for (IMaidFluidStorage storage : STORAGES) {
            boolean valid = storage.isValid(level, pos);
            int amount = storage.getFluidAmount(level, pos, fluid);
            boolean canFill = storage.canBeFilled(level, pos, fluid);
            if (valid && amount >= required) {
                return true;
            }
            if (canFill) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行期解析：返回当前能提供目标流体、或可先注满再提供的适配器；黑名单最高优先。
     */
    @Nullable
    public static IMaidFluidStorage tryGetUsable(Level level, BlockPos pos, FluidStack fluid) {
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) {
            return null;
        }
        for (IMaidFluidStorage storage : STORAGES) {
            if (storage.isValid(level, pos)
                    && storage.getFluidAmount(level, pos, fluid) > 0) {
                return storage;
            }
        }
        for (IMaidFluidStorage storage : STORAGES) {
            if (storage.canBeFilled(level, pos, fluid)) {
                return storage;
            }
        }
        return null;
    }
}
