package com.example.maidrestaurant.rscompat.mixin;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原生物品存储注册表 {@link MaidStorages} 的黑名单闸门。
 *
 * 原生 CommonStorage 注册在最前、只认 storage_block 标签，不了解存储附属黑名单；
 * 存储附属的适配器都追加在其后、无法抢先匹配。因此在注册表两个查询方法的 HEAD 注入，
 * 让黑名单方块在任何适配器匹配之前就被统一判为不可用（黑名单最高优先），
 * 与流体注册表 {@code MaidFluidStorages.tryGetType} 的闸门保持同源一致。
 */
@Mixin(MaidStorages.class)
public class MaidStoragesMixin {

    @Inject(method = "tryGetHandler", at = @At("HEAD"), cancellable = true)
    private static void rsBlacklistGuardHandler(Level level, BlockPos pos, CallbackInfoReturnable<?> cir) {
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "tryGetType", at = @At("HEAD"), cancellable = true)
    private static void rsBlacklistGuardType(Level level, BlockPos pos, CallbackInfoReturnable<?> cir) {
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) {
            cir.setReturnValue(null);
        }
    }
}
