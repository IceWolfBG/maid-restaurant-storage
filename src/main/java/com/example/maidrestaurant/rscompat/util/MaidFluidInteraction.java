package com.example.maidrestaurant.rscompat.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

/**
 * 通用“单份流体”舀取工具。
 *
 * 对所有“空桶/空瓶右键即可取得一份流体”的方块生效，不硬编码任何方块 id：
 * 1. 实现 {@link BucketPickup} 的方块（如水/岩浆源）直接调用 takeLiquid；
 * 2. 炼药锅、水槽等通过 FakePlayer 模拟一次玩家右键交互，再从其背包取出装好流体的容器。
 */
public final class MaidFluidInteraction {

    private MaidFluidInteraction() {
    }

    /**
     * 用给定空容器从目标方块舀取一份流体，返回装好流体的物品栈；失败返回 EMPTY。
     * 该方法只操作空容器的副本，失败不会消耗女仆原本的空容器。
     */
    public static ItemStack scoop(ServerLevel level, BlockPos pos, ItemStack emptyContainer) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || emptyContainer == null || emptyContainer.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            // 1) BucketPickup（流体源方块），仅支持空桶
            if (state.getBlock() instanceof BucketPickup pickup
                    && emptyContainer.getItem() == Items.BUCKET) {
                ItemStack result = pickup.pickupBlock(level, pos, state);
                if (!result.isEmpty() && FluidContainerUtils.isFluidContainer(result)) {
                    return result;
                }
                return ItemStack.EMPTY;
            }
            // 2) 其余（炼药锅 / 水槽 等）走 FakePlayer 右键交互
            return interactFill(level, pos, emptyContainer);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack interactFill(ServerLevel level, BlockPos pos, ItemStack emptyContainer) {
        FakePlayer fake = MaidFakePlayer.get(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        fake.getInventory().clearContent();
        fake.setItemInHand(InteractionHand.MAIN_HAND, emptyContainer.copy());

        // 以方块顶面为点击面构造一次命中结果（炼药锅/水槽交互不依赖具体点击坐标）
        Vec3 hitLocation = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0);
        BlockHitResult hit = new BlockHitResult(hitLocation, Direction.UP, pos, false);

        BlockState state = level.getBlockState(pos);
        InteractionResult result = state.getBlock()
                .use(state, level, pos, fake, InteractionHand.MAIN_HAND, hit);

        ItemStack filled = findFilledContainer(fake);
        fake.getInventory().clearContent();
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        if (filled.isEmpty() || !FluidContainerUtils.isFluidContainer(filled)) {
            return ItemStack.EMPTY;
        }
        return filled;
    }

    private static ItemStack findFilledContainer(FakePlayer fake) {        for (int i = 0; i < fake.getInventory().getContainerSize(); i++) {
            ItemStack stack = fake.getInventory().getItem(i);
            if (!stack.isEmpty() && FluidContainerUtils.isFluidContainer(stack)) {
                fake.getInventory().setItem(i, ItemStack.EMPTY);
                return stack;
            }
        }
        ItemStack offhand = fake.getOffhandItem();
        if (!offhand.isEmpty() && FluidContainerUtils.isFluidContainer(offhand)) {
            fake.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            return offhand;
        }
        return ItemStack.EMPTY;
    }
}
