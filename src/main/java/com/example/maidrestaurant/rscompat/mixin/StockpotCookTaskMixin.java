package com.example.maidrestaurant.rscompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.mastermarisa.maid_restaurant.cooktask.StockpotCookTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 与“营业中”同源的修复，移植到存储附属：
 * 汤锅初始 / 重置后 status=PUT_SOUP_BASE、soupBaseId 默认 WATER，
 * 原 getCurrentInput 仅凭 getSoupBase()!=null 就把默认汤底类型误判成已加入汤底，
 * 导致女仆没取水也判 COOK。此状态锅内全空，返回空列表。
 *
 * 该修复幂等：当营业中与存储附属同时安装时，两边的 HEAD 回调都会执行，
 * 返回值同为空列表，效果等价、只算一次生效，不会冲突。
 * 两桶水的背包内无限水是独立机制，此处不改动。
 */
@Mixin(StockpotCookTask.class)
public class StockpotCookTaskMixin {
    @Inject(method = "getCurrentInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void storage$fixEmptyPotSoupBase(Level level, BlockPos pos, EntityMaid maid,
                                             CallbackInfoReturnable<List<ItemStack>> cir) {
        if (level.getBlockEntity(pos) instanceof IStockpot stockpot
                && stockpot.getStatus() == IStockpot.PUT_SOUP_BASE) {
            cir.setReturnValue(new ArrayList<>());
        }
    }
}
