package com.example.maidrestaurant.rscompat.mixin;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.example.maidrestaurant.rscompat.util.FluidSearchHelper;
import com.example.maidrestaurant.rscompat.util.MaidReflectionUtils;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.StepResult;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidGetFromStorageTask;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin 注入到 MaidGetFromStorageTask。
 *
 * 流体优先逻辑（安全方式）：
 * 1. search() RETURN：原 search() 正常执行后，如果女仆需要流体容器且背包有空容器，
 *    且周围有流体存储，则将目标覆盖为流体存储位置。不阻止原方法执行。
 * 2. accept() HEAD：女仆到达流体存储后，执行流体填充并取消原 accept()。
 */
@Mixin(MaidGetFromStorageTask.class)
public abstract class MaidGetFromStorageTaskMixin {

    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantStorage");

    @Inject(method = "search", at = @At("RETURN"), cancellable = true)
    private void maidrestaurant_storage$onSearchReturn(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        if (!CompatConfig.isFluidSearchEnabled()) return;
        try {
            BlockPos fluidPos = FluidSearchHelper.findNearestFluidStorage(level, maid);
            if (fluidPos == null) return;

            LOGGER.info("Fluid search (priority): overriding target to fluid storage at {}", fluidPos);
            BehaviorUtils.setTargetPos(maid, new BlockPosTracker(fluidPos), 0);
            BehaviorUtils.setWalkAndLookTargetMemories(maid, fluidPos, fluidPos, 0.5f, 0);
            cir.setReturnValue(true);
        } catch (Throwable t) {
            LOGGER.error("Fluid search (priority) error", t);
        }
    }

    @Inject(method = "accept", at = @At("HEAD"), cancellable = true)
    private void maidrestaurant_storage$onAcceptHead(ServerLevel level, EntityMaid maid, StepResult result, CallbackInfo ci) {
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
