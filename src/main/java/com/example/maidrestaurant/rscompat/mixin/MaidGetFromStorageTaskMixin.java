package com.example.maidrestaurant.rscompat.mixin;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.example.maidrestaurant.rscompat.util.FluidSearchHelper;
import com.example.maidrestaurant.rscompat.util.MaidReflectionUtils;
import com.example.maidrestaurant.rscompat.util.StorageSearchCache;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.StepResult;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidGetFromStorageTask;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Mixin 注入到 MaidGetFromStorageTask。
 *
 * 性能优化：
 * 1. 搜索结果缓存（方案四）：缓存最近一次搜索找到的容器位置，10 tick 内重复搜索直接返回缓存。
 * 2. containsRequired 结果缓存（方案二）：缓存容器检查结果，避免重复遍历所有 slot。
 * 3. 流体优先逻辑：如果女仆需要流体容器且背包有空容器，且周围有流体存储，则将目标覆盖为流体存储位置。
 */
@Mixin(MaidGetFromStorageTask.class)
public abstract class MaidGetFromStorageTaskMixin {

    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantStorage");

    /**
     * 搜索结果缓存（方案四）。
     * 在 search() HEAD 点检查缓存，如果缓存命中就直接设置目标位置并返回 true。
     */
    @Inject(method = "search", at = @At("HEAD"), cancellable = true)
    private void maidrestaurant_storage$onSearchHead(ServerLevel level, EntityMaid maid,
                                                       CallbackInfoReturnable<Boolean> cir) {
        try {
            long currentTick = level.getGameTime();
            UUID maidUuid = maid.getUUID();

            // 检查搜索结果缓存
            BlockPos cachedPos = StorageSearchCache.getSearchResult(maidUuid, currentTick);
            if (cachedPos != null) {
                // 缓存命中，直接设置目标位置并返回 true
                BehaviorUtils.setTargetPos(maid, new BlockPosTracker(cachedPos), 0);
                BehaviorUtils.setWalkAndLookTargetMemories(maid, cachedPos, cachedPos, 0.5f, 0);
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
     */
    @Inject(method = "search", at = @At("RETURN"))
    private void maidrestaurant_storage$onSearchReturn(ServerLevel level, EntityMaid maid,
                                                         CallbackInfoReturnable<Boolean> cir) {
        try {
            // 流体优先逻辑（保留原有功能）
            if (!CompatConfig.isFluidSearchEnabled()) return;
            if (!cir.getReturnValue()) return; // 搜索失败时不覆盖

            BlockPos fluidPos = FluidSearchHelper.findNearestFluidStorage(level, maid);
            if (fluidPos == null) return;

            LOGGER.info("Fluid search (priority): overriding target to fluid storage at {}", fluidPos);
            BehaviorUtils.setTargetPos(maid, new BlockPosTracker(fluidPos), 0);
            BehaviorUtils.setWalkAndLookTargetMemories(maid, fluidPos, fluidPos, 0.5f, 0);

            // 更新搜索结果缓存为流体存储位置
            StorageSearchCache.setSearchResult(maid.getUUID(), fluidPos, level.getGameTime());
        } catch (Throwable t) {
            LOGGER.error("Fluid search (priority) error", t);
        }

        try {
            // 记录搜索结果缓存
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
        } catch (Throwable t) {
            LOGGER.debug("Cache invalidation error", t);
        }
    }

    /**
     * 流体填充逻辑（保留原有功能）。
     */
    @Inject(method = "accept", at = @At("HEAD"), cancellable = true)
    private void maidrestaurant_storage$onAcceptHead(ServerLevel level, EntityMaid maid, StepResult result,
                                                       CallbackInfo ci) {
        if (result != StepResult.SUCCESS) return;
        if (!CompatConfig.isFluidSearchEnabled()) return;

        try {
            BlockPos targetPos = MaidReflectionUtils.getTargetPos(maid);
            if (targetPos == null) return;

            boolean filled = FluidSearchHelper.fillFluidContainers(level, maid, targetPos);
            if (!filled) return;

            LOGGER.info("Fluid fill (priority): success at {}", targetPos);
            BehaviorUtils.eraseTargetPos(maid);
            ci.cancel();
        } catch (Throwable t) {
            LOGGER.error("Fluid fill (priority) error", t);
        }
    }
}
