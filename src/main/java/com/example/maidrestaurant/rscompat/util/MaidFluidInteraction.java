package com.example.maidrestaurant.rscompat.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * 通过模拟玩家右键交互，从“单份流体容器”舀取流体。
 *
 * 两种方式：
 * 1. 方块实现 BucketPickup（流体源）：直接调用 takeLiquid 得到水桶/岩浆桶；
 * 2. 其它（炼药锅、水槽等）：用 FakePlayer 手持空容器，模拟对方块 UP 面右键。
 */
public final class MaidFluidInteraction {

    private MaidFluidInteraction() {
    }

    /**
     * 让女仆在指定方块处用空容器舀取一份流体。
     *
     * @param emptyContainer 女仆背包里的空容器（桶 / 玻璃瓶）
     * @return 装好流体的容器；失败返回空
     */
    public static ItemStack scoop(ServerLevel level, BlockPos pos, ItemStack emptyContainer) {
        BlockState state = level.getBlockState(pos);

        // 1. BucketPickup（流体源），仅桶可直接取
        if (state.getBlock() instanceof BucketPickup pickup
                && emptyContainer.getItem() == Items.BUCKET) {
            FakePlayer fake = MaidFakePlayer.get(level, pos);
            ItemStack result = pickup.pickupBlock(fake, level, pos, state);
            if (!result.isEmpty() && FluidContainerUtils.isFluidContainer(result)) {
                return result;
            }
            return ItemStack.EMPTY;
        }

        // 2. FakePlayer 模拟右键（炼药锅 / 水槽等）
        try {
            FakePlayer fake = MaidFakePlayer.get(level, pos);
            fake.getInventory().clearContent();

            ItemStack held = emptyContainer.copy();
            held.setCount(1);
            fake.setItemInHand(InteractionHand.MAIN_HAND, held);

            Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);

            // 持空容器右键方块，走 BlockState 的带物品交互（炼药锅 / 水槽装填逻辑）
            ItemInteractionResult result =
                    state.useItemOn(held, level, fake, InteractionHand.MAIN_HAND, hit);
            if (!result.consumesAction()) {
                return ItemStack.EMPTY;
            }

            // 从 FakePlayer 背包取出装填结果
            for (int i = 0; i < fake.getInventory().getContainerSize(); i++) {
                ItemStack stack = fake.getInventory().getItem(i);
                if (!stack.isEmpty() && FluidContainerUtils.isFluidContainer(stack)) {
                    ItemStack resultStack = stack.copy();
                    fake.getInventory().setItem(i, ItemStack.EMPTY);
                    return resultStack;
                }
            }
            return ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}
