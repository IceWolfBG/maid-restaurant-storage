package com.example.maidrestaurant.rscompat.util;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.example.maidrestaurant.rscompat.fluid.IMaidFluidStorage;
import com.example.maidrestaurant.rscompat.fluid.MaidFluidStorages;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import com.mastermarisa.maid_restaurant.utils.CookTasks;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import com.mastermarisa.maid_restaurant.utils.SearchUtils;
import com.mastermarisa.maid_restaurant.utils.component.StackPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * 流体搜索与填充工具类。
 *
 * 这是一个普通类（非 Mixin），reobfJar 能正确映射其中的 Minecraft 原版方法调用。
 * Mixin 类通过委托调用本类的方法，避免 Mixin 注入到目标类后原版方法未被映射的问题。
 */
public class FluidSearchHelper {

    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantStorage");

    /**
     * 查找最近的可用流体存储位置。
     * @return 流体存储位置，null 表示不满足流体优先条件
     */
    public static BlockPos findNearestFluidStorage(ServerLevel level, EntityMaid maid) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, 0);
            if (request == null) return null;

            ICookTask iCookTask = CookTasks.getTask(request.type);
            Recipe<?> recipe = level.getRecipeManager().byKey(request.id).orElse(null);
            if (recipe == null) return null;

            List<StackPredicate> required = iCookTask.getIngredients(recipe, level);
            IItemHandler maidInv = MaidReflectionUtils.getAvailableInv(maid);
            if (maidInv == null) return null;

            // 强制转换为 LivingEntity，确保 reobfJar 正确映射 blockPosition() 为 m_20183_()
            BlockPos maidPos = ((net.minecraft.world.entity.LivingEntity) maid).blockPosition();

            for (StackPredicate predicate : required) {
                if (hasItem(maidInv, predicate)) continue;

                ItemStack sample = findSampleForPredicate(predicate);
                if (sample == null || !FluidContainerUtils.isFluidContainer(sample)) continue;

                Item emptyContainer = FluidContainerUtils.getEmptyContainer(sample);
                if (emptyContainer == null) continue;

                // 严格检查：如果 predicate 也匹配空容器，说明不是专门需要流体容器，跳过
                // 避免 predicate 匹配范围过广时误拦截原物品搜索
                if (predicate.test(new ItemStack(emptyContainer))) {
                    LOGGER.info("Fluid search: predicate also matches empty container, skipping");
                    continue;
                }

                if (!hasItem(maidInv, emptyContainer)) {
                    LOGGER.info("Fluid search: maid needs {} but has no empty {}", sample.getItem(), emptyContainer);
                    continue;
                }

                FluidStack requiredFluid = FluidContainerUtils.getRequiredFluid(sample);
                if (requiredFluid == null) continue;

                int requiredAmount = FluidContainerUtils.getRequiredFluidAmount(sample);

                BlockPos center = BehaviorUtils.getSearchPos(maid);
                int searchRange = Math.max((int) MaidReflectionUtils.getPerceptionRange(maid), 8);

                List<BlockPos> foundFluidStorages = SearchUtils.search(center, searchRange, 4,
                        pos -> isFluidStorageAvailable(level, pos, requiredFluid, requiredAmount));

                if (foundFluidStorages.isEmpty()) {
                    LOGGER.info("Fluid search: no fluid storage with enough {} nearby", requiredFluid.getFluid());
                    continue;
                }

                BlockPos nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (BlockPos p : foundFluidStorages) {
                    double dist = p.distSqr(maidPos);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = p;
                    }
                }

                if (nearest != null) {
                    return nearest;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Fluid search error", t);
        }
        return null;
    }

    /**
     * 在指定流体存储位置填充女仆背包中的空容器。
     * @return true 如果执行了填充
     */
    public static boolean fillFluidContainers(ServerLevel level, EntityMaid maid, BlockPos fluidPos) {
        try {
            IMaidFluidStorage fluidStorage = MaidFluidStorages.tryGetType(level, fluidPos);
            if (fluidStorage == null) return false;

            IItemHandler maidInv = MaidReflectionUtils.getAvailableInv(maid);
            if (maidInv == null) return false;

            CookRequest request = (CookRequest) RequestManager.peek(maid, 0);
            if (request == null) return false;

            ICookTask iCookTask = CookTasks.getTask(request.type);
            Recipe<?> recipe = level.getRecipeManager().byKey(request.id).orElse(null);
            if (recipe == null) return false;

            List<StackPredicate> required = iCookTask.getIngredients(recipe, level);
            boolean filledAny = false;

            for (StackPredicate predicate : required) {
                if (hasItem(maidInv, predicate)) continue;

                ItemStack sample = findSampleForPredicate(predicate);
                if (sample == null || !FluidContainerUtils.isFluidContainer(sample)) continue;

                FluidStack requiredFluid = FluidContainerUtils.getRequiredFluid(sample);
                if (requiredFluid == null) continue;

                int requiredAmount = FluidContainerUtils.getRequiredFluidAmount(sample);
                Item emptyItem = FluidContainerUtils.getEmptyContainer(sample);
                if (emptyItem == null) continue;

                // 严格检查：predicate 不应该匹配空容器
                if (predicate.test(new ItemStack(emptyItem))) continue;

                int emptySlot = findItemSlot(maidInv, emptyItem);
                if (emptySlot == -1) continue;

                int available = fluidStorage.getFluidAmount(level, fluidPos, requiredFluid);
                LOGGER.info("Fluid fill: need {} mB, have {} mB", requiredAmount, available);
                if (available < requiredAmount) continue;

                FluidStack extracted = fluidStorage.extract(level, fluidPos, requiredFluid, requiredAmount, false);
                if (extracted.isEmpty() || extracted.getAmount() < requiredAmount) continue;

                ItemStack emptyContainer = maidInv.extractItem(emptySlot, 1, false);
                if (emptyContainer.isEmpty()) continue;

                ItemStack filled = FluidContainerUtils.fillContainer(emptyContainer, extracted);
                if (filled.isEmpty()) {
                    ItemHandlerHelper.insertItemStacked(maidInv, emptyContainer, false);
                    continue;
                }

                ItemStack remainder = ItemHandlerHelper.insertItemStacked(maidInv, filled, false);
                if (!remainder.isEmpty()) {
                    MaidReflectionUtils.spawnAtLocation(maid, remainder);
                }
                LOGGER.info("Fluid fill: success {}", filled.getItem());
                // 触发手臂摆动动画，让玩家知道女仆正在取水（与女仆餐厅拿取食材时使用相同的动画）
                maid.swing(InteractionHand.OFF_HAND);
                filledAny = true;
            }
            return filledAny;
        } catch (Throwable t) {
            LOGGER.error("Fluid fill error", t);
            return false;
        }
    }

    private static boolean isFluidStorageAvailable(ServerLevel level, BlockPos pos, FluidStack requiredFluid, int requiredAmount) {
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) return false;
        IMaidFluidStorage storage = MaidFluidStorages.tryGetType(level, pos);
        return storage != null && storage.getFluidAmount(level, pos, requiredFluid) >= requiredAmount;
    }

    private static boolean hasItem(IItemHandler handler, StackPredicate predicate) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (predicate.test(handler.getStackInSlot(i))) return true;
        }
        return false;
    }

    private static boolean hasItem(IItemHandler handler, Item item) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() == item) return true;
        }
        return false;
    }

    private static int findItemSlot(IItemHandler handler, Item item) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() == item) return i;
        }
        return -1;
    }

    private static ItemStack findSampleForPredicate(StackPredicate predicate) {
        ItemStack[] candidates = {
                new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.LAVA_BUCKET),
                PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER)
        };
        for (ItemStack candidate : candidates) {
            if (predicate.test(candidate)) return candidate;
        }
        return null;
    }
}
