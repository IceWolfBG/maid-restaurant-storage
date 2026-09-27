package com.example.maidrestaurant.rscompat.mixin;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.example.maidrestaurant.rscompat.util.FluidReservationManager;
import com.example.maidrestaurant.rscompat.util.FluidSearchHelper;
import com.example.maidrestaurant.rscompat.util.MaidReflectionUtils;
import com.example.maidrestaurant.rscompat.util.PhantomContainerTracker;
import com.example.maidrestaurant.rscompat.util.StorageSearchCache;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.StepResult;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidApproachCookBlockTask;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidGetFromStorageTask;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import com.mastermarisa.maid_restaurant.utils.CheckRateManager;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;
import com.mastermarisa.maid_restaurant.utils.SearchUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mixin 注入到 MaidGetFromStorageTask。
 *
 * 功能：
 * 1. 搜索结果缓存（方案四）：缓存最近一次搜索找到的容器位置，10 tick 内重复搜索直接返回缓存。
 * 2. containsRequired 结果缓存（方案二）：缓存容器检查结果，避免重复遍历所有 slot。
 * 3. 流体优先逻辑：如果女仆需要流体容器且背包有空容器，且周围有流体存储，则将目标覆盖为流体存储位置。
 */
@Mixin(MaidGetFromStorageTask.class)
public abstract class MaidGetFromStorageTaskMixin {

    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantStorage");

    @Shadow
    protected abstract boolean containsRequired(ServerLevel level, EntityMaid maid, IItemHandler itemHandler);

    @Shadow
    @Final
    private int verticalSearchRange;

    /** accept 前快照：女仆 -> 取物前背包总物品数。 */
    @Unique
    private static final Map<UUID, Integer> CAPTURE_COUNT = new HashMap<>();
    /** accept 前快照：女仆 -> 目标坐标（accept 本体里会 erase 目标）。 */
    @Unique
    private static final Map<UUID, Long> CAPTURE_POS = new HashMap<>();

    @Unique
    private static int totalItems(IItemHandler handler) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            total += handler.getStackInSlot(i).getCount();
        }
        return total;
    }

    /** 排除拉黑位的物品容器搜索，返回最近一个；无则 null。 */
    @Unique
    private BlockPos maidrestaurant_storage$findItemStorageExcept(ServerLevel level, EntityMaid maid,
                                                                     Set<BlockPos> exclude) {
        BlockPos center = BehaviorUtils.getSearchPos(maid);
        int range = (int) maid.getRestrictRadius();
        List<BlockPos> found = SearchUtils.search(center, range, verticalSearchRange, pos -> {
            if (exclude.contains(pos)) return false;
            IItemHandler h = MaidStorages.tryGetHandler(level, pos);
            return h != null && containsRequired(level, maid, h);
        });
        return found.stream()
                .min(Comparator.comparingDouble(p -> p.distSqr(maid.blockPosition())))
                .orElse(null);
    }

    /**
     * accept HEAD 快照（非取消）：记录目标坐标与背包总物品数，供 RETURN 判定是否零收获。
     */
    @Inject(method = "accept", at = @At("HEAD"))
    private void maidrestaurant_storage$onAcceptCaptureHead(ServerLevel level, EntityMaid maid,
                                                               StepResult result, CallbackInfo ci) {
        if (result != StepResult.SUCCESS) return;
        try {
            BlockPos target = MaidReflectionUtils.getTargetPos(maid);
            IItemHandler inv = MaidReflectionUtils.getAvailableInv(maid);
            CAPTURE_POS.put(maid.getUUID(), target == null ? null : target.asLong());
            CAPTURE_COUNT.put(maid.getUUID(), inv == null ? 0 : totalItems(inv));
        } catch (Throwable t) {
            LOGGER.debug("accept capture error", t);
        }
    }

    /**
     * 搜索结果缓存（方案四）。
     * 在 search() HEAD 点检查缓存，如果缓存命中就直接设置目标位置并返回 true。
     */
    @Inject(method = "search", at = @At("HEAD"), cancellable = true)
    private void maidrestaurant_storage$onSearchHead(ServerLevel level, EntityMaid maid,
                                                       CallbackInfoReturnable<Boolean> cir) {
        try {
            // 进行中的注水预留：锁定到该水槽，避免等待期间被本体带去拿别的食材
            BlockPos reserved = FluidReservationManager.getReservedPos(level, maid.getUUID());
            if (reserved != null) {
                BehaviorUtils.setTargetPos(maid, new BlockPosTracker(reserved), 0);
                BehaviorUtils.setWalkAndLookTargetMemories(maid, reserved, reserved, 0.5f, 1);
                cir.setReturnValue(true);
                return;
            }

            long currentTick = level.getGameTime();
            UUID maidUuid = maid.getUUID();

            // 检查搜索结果缓存
            BlockPos cachedPos = StorageSearchCache.getSearchResult(maidUuid, currentTick);
            if (cachedPos != null) {
                // 缓存命中，直接设置目标位置并返回 true
                BehaviorUtils.setTargetPos(maid, new BlockPosTracker(cachedPos), 0);
                BehaviorUtils.setWalkAndLookTargetMemories(maid, cachedPos, cachedPos, 0.5f, 1);
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            // 缓存出错时不影响原逻辑
            LOGGER.debug("Search cache check error", t);
        }
    }

    /**
     * 搜索结果缓存（方案四）。
     * 在 search() RETURN 点记录结果。
     *
     * 流体优先：即使原 search() 没找到任何容器（返回 false），只要缺的是流体容器、
     * 背包有空容器、且附近有足量流体存储，也要把目标覆盖为流体存储，并把返回值翻为 true，
     * 否则“别的食材都齐、只缺水桶”时女仆完全不会去找水。
     */
    @Inject(method = "search", at = @At("RETURN"), cancellable = true)
    private void maidrestaurant_storage$onSearchReturn(ServerLevel level, EntityMaid maid,
                                                         CallbackInfoReturnable<Boolean> cir) {
        try {
            // 1. 进行中的注水预留：锁定到该水槽，忽略本体找到的非流体目标
            BlockPos reserved = FluidReservationManager.getReservedPos(level, maid.getUUID());
            if (reserved != null) {
                BehaviorUtils.setTargetPos(maid, new BlockPosTracker(reserved), 0);
                BehaviorUtils.setWalkAndLookTargetMemories(maid, reserved, reserved, 0.5f, 1);
                StorageSearchCache.setSearchResult(maid.getUUID(), reserved, level.getGameTime());
                cir.setReturnValue(true);
                return;
            }

            // 2. 本体没找到任何容器（缺的是流体、本体不认识水槽）：流体兜底
            BlockPos baseTarget = maid.getBrain().getMemory(com.mastermarisa.maid_restaurant.init.ModEntities.TARGET_POS.get())
                    .map(t -> t.currentBlockPosition()).orElse(null);

            // 2a. 本体锁到了幽灵容器（如 RS 磁盘驱动器）：排除拉黑位重新搜索物品容器
            if (cir.getReturnValue() && baseTarget != null
                    && PhantomContainerTracker.isBlacklisted(level, baseTarget)) {
                Set<BlockPos> exclude = PhantomContainerTracker.getBlacklistedPositions(level);
                BlockPos alt = maidrestaurant_storage$findItemStorageExcept(level, maid, exclude);
                if (alt != null) {
                    BehaviorUtils.setTargetPos(maid, new BlockPosTracker(alt), 0);
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, alt, alt, 0.5f, 1);
                    StorageSearchCache.setSearchResult(maid.getUUID(), alt, level.getGameTime());
                    baseTarget = alt;
                } else {
                    // 找不到其它物品容器：清目标、返回 false，交给流体兜底
                    BehaviorUtils.eraseTargetPos(maid);
                    StorageSearchCache.invalidateSearchResult(maid.getUUID());
                    cir.setReturnValue(false);
                }
            }

            if (!cir.getReturnValue() && CompatConfig.isFluidSearchEnabled()) {
                BlockPos fluidPos = FluidSearchHelper.findNearestFluidStorage(level, maid);
                if (fluidPos != null) {
                    BehaviorUtils.setTargetPos(maid, new BlockPosTracker(fluidPos), 0);
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, fluidPos, fluidPos, 0.5f, 1);
                    StorageSearchCache.setSearchResult(maid.getUUID(), fluidPos, level.getGameTime());
                    cir.setReturnValue(true);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Fluid search handling error", t);
        }

        try {
            // 3. 记录本体搜索结果缓存
            if (cir.getReturnValue()) {
                // 从 maid 的 brain 中获取目标位置
                maid.getBrain().getMemory(com.mastermarisa.maid_restaurant.init.ModEntities.TARGET_POS.get())
                        .ifPresent(tracker -> {
                            BlockPos pos = tracker.currentBlockPosition();
                            StorageSearchCache.setSearchResult(maid.getUUID(), pos, level.getGameTime());
                        });
            }
        } catch (Throwable t) {
            LOGGER.debug("Search cache record error", t);
        }
    }

    /**
     * containsRequired 结果缓存（方案二）。
     * 在 containsRequired() HEAD 点检查缓存，如果缓存命中就直接返回结果。
     */
    @Inject(method = "containsRequired", at = @At("HEAD"), cancellable = true)
    private void maidrestaurant_storage$onContainsRequiredHead(ServerLevel level, EntityMaid maid,
                                                                  IItemHandler itemHandler,
                                                                  CallbackInfoReturnable<Boolean> cir) {
        try {
            long currentTick = level.getGameTime();
            UUID maidUuid = maid.getUUID();
            int handlerIdentity = System.identityHashCode(itemHandler);

            // 检查 containsRequired 结果缓存
            Boolean cachedResult = StorageSearchCache.getContainsRequiredResult(maidUuid, handlerIdentity, currentTick);
            if (cachedResult != null) {
                // 缓存命中，直接返回结果
                cir.setReturnValue(cachedResult);
            }
        } catch (Throwable t) {
            // 缓存出错时不影响原逻辑
            LOGGER.debug("containsRequired cache check error", t);
        }
    }

    /**
     * containsRequired 结果缓存（方案二）。
     * 在 containsRequired() RETURN 点记录结果。
     */
    @Inject(method = "containsRequired", at = @At("RETURN"))
    private void maidrestaurant_storage$onContainsRequiredReturn(ServerLevel level, EntityMaid maid,
                                                                    IItemHandler itemHandler,
                                                                    CallbackInfoReturnable<Boolean> cir) {
        try {
            long currentTick = level.getGameTime();
            UUID maidUuid = maid.getUUID();
            int handlerIdentity = System.identityHashCode(itemHandler);

            // 记录 containsRequired 结果缓存
            StorageSearchCache.setContainsRequiredResult(maidUuid, handlerIdentity, cir.getReturnValue(), currentTick);
        } catch (Throwable t) {
            LOGGER.debug("containsRequired cache record error", t);
        }
    }

    /**
     * 女仆取走物品后，使缓存失效。
     * 在 accept() 方法返回后调用。
     */
    @Inject(method = "accept", at = @At("RETURN"))
    private void maidrestaurant_storage$onAcceptReturn(ServerLevel level, EntityMaid maid, StepResult result,
                                                         CallbackInfo ci) {
        try {
            UUID maidUuid = maid.getUUID();
            // 女仆取走物品后，使搜索结果缓存失效
            StorageSearchCache.invalidateSearchResult(maidUuid);
            // 同时使 containsRequired 缓存失效
            StorageSearchCache.invalidateContainsRequiredCache(maidUuid);

            // 零收获检测：背包总物品数未增长则计一次幽灵容器，增长则清零
            Integer before = CAPTURE_COUNT.remove(maidUuid);
            Long posLong = CAPTURE_POS.remove(maidUuid);
            if (before != null && posLong != null) {
                IItemHandler inv = MaidReflectionUtils.getAvailableInv(maid);
                int after = inv == null ? before : totalItems(inv);
                BlockPos target = BlockPos.of(posLong);
                if (after <= before) {
                    PhantomContainerTracker.recordZeroYield(level, maidUuid, target);
                } else {
                    PhantomContainerTracker.recordProgress(level, maidUuid, target);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Cache invalidation error", t);
        }
    }

    /**
     * 流体填充逻辑（两段式：注水 -&gt; 停留 -&gt; 舀水；含多女仆预留与重试）。
     */
    @Inject(method = "accept", at = @At("HEAD"), cancellable = true)
    private void maidrestaurant_storage$onAcceptHead(ServerLevel level, EntityMaid maid, StepResult result,
                                                       CallbackInfo ci) {
        if (result != StepResult.SUCCESS) return;
        if (!CompatConfig.isFluidSearchEnabled()) return;

        try {
            BlockPos targetPos = MaidReflectionUtils.getTargetPos(maid);
            if (targetPos == null) return;

            // 只有女仆确实持有匹配当前目标的流体预留时才接管 accept；
            // 否则这是普通（非流体）取物，交给本体完成，避免误取消导致食材永远拿不起来。
            BlockPos reservedPos = FluidReservationManager.getReservedPos(level, maid.getUUID());
            if (reservedPos == null) {
                return;
            }
            if (!reservedPos.equals(targetPos)) {
                // 预留目标与当前目标错配（残留预留）：释放它，让本体完成本次取物
                FluidReservationManager.release(maid.getUUID());
                return;
            }

            FluidSearchHelper.FillResult fillResult =
                    FluidSearchHelper.fillFluidContainers(level, maid, targetPos);
            long now = level.getGameTime();

            switch (fillResult.status) {
                case DONE:
                    BehaviorUtils.eraseTargetPos(maid);
                    // 清除 WALK_TARGET 和 LOOK_TARGET 记忆，避免女仆继续往水槽寻路 / 站到水槽上
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                    CheckRateManager.setNextCheckTick(maid.getUUID() + MaidGetFromStorageTask.UID, 5);
                    CheckRateManager.setNextCheckTick(maid.getUUID() + MaidApproachCookBlockTask.UID, 5);
                    ci.cancel();
                    break;
                case WAITING:
                    // 注水已完成：清除寻路记忆，停在水槽旁（保留 LOOK_TARGET 继续面向水槽）
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    // 注水后停留，让玩家看到水槽变满；保持站位，到点再舀水
                    long waitTicks = Math.max(1L, fillResult.readyAt - now);
                    CheckRateManager.setNextCheckTick(maid.getUUID() + MaidGetFromStorageTask.UID, (int) waitTicks);
                    CheckRateManager.setNextCheckTick(maid.getUUID() + MaidApproachCookBlockTask.UID, (int) waitTicks);
                    ci.cancel();
                    break;
                case RETRY:
                    // 目标被别的女仆占用 / 状态变化：失效缓存并清除目标，稍后重新搜索
                    StorageSearchCache.invalidateSearchResult(maid.getUUID());
                    StorageSearchCache.invalidateContainsRequiredCache(maid.getUUID());
                    BehaviorUtils.eraseTargetPos(maid);
                    CheckRateManager.setNextCheckTick(maid.getUUID() + MaidGetFromStorageTask.UID, 10);
                    CheckRateManager.setNextCheckTick(maid.getUUID() + MaidApproachCookBlockTask.UID, 10);
                    ci.cancel();
                    break;
                case NONE:
                default:
                    break;
            }
        } catch (Throwable t) {
            LOGGER.error("Fluid fill (priority) error", t);
        }
    }
}
