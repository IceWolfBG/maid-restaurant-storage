package com.example.maidrestaurant.rscompat.mixin;

import com.example.maidrestaurant.rscompat.storage.NestedItemHandler;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 包装 MaidStorages.tryGetHandler 返回的 IItemHandler，
 * 使女仆能够检索容器内嵌套容器（如箱子里的背包）中的物品。
 * 最多检索一层嵌套。
 */
@Mixin(value = MaidStorages.class, remap = false)
public class MaidStoragesMixin {

    @Inject(method = "tryGetHandler", at = @At("RETURN"), cancellable = true)
    private static void maidrestaurant_storage$wrapNestedHandler(
            Level level, BlockPos pos, CallbackInfoReturnable<IItemHandler> cir) {
        IItemHandler handler = cir.getReturnValue();
        if (handler != null && !(handler instanceof NestedItemHandler)) {
            cir.setReturnValue(new NestedItemHandler(handler));
        }
    }
}
