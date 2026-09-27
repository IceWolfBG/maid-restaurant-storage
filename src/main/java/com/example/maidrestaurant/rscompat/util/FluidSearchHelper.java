package com.example.maidrestaurant.rscompat.util;

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
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 流体搜索与填充工具类。
 *
 * 这是一个普通类（非 Mixin），reobfJar 能正确映射其中的 Minecraft 原版方法调用。
 * Mixin 类通过委托调用本类的方法，避免 Mixin 注入到目标类后原版方法未被映射的问题。
 *
 * 空水槽流程为两段式：注水 -&gt; 停留 {@link #FILL_TO_SCOOP_DELAY_TICKS}（让玩家看到水槽变满）-&gt; 舀水；
 * 通过 {@link FluidReservationManager} 预留水槽，避免多女仆冲突。
 */
public class FluidSearchHelper {

    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantStorage");

    /** 注水后到舀水之间的停留时间（tick），让玩家能清楚看到水槽被注满。 */
    private static final int FILL_TO_SCOOP_DELAY_TICKS = 24;

    /** 填充流程的结果状态。 */
    public static final class FillResult {
        public enum Status { DONE, WAITING, RETRY, NONE }

        public final Status status;
        public final long readyAt;

        private FillResult(Status status, long readyAt) {
            this.status = status;
            this.readyAt = readyAt;
        }

        public static final FillResult DONE = new FillResult(Status.DONE, -1L);
        public static final FillResult RETRY = new FillResult(Status.RETRY, -1L);
        public static final FillResult NONE = new FillResult(Status.NONE, -1L);

        public static FillResult waiting(long readyAt) {
            return new FillResult(Status.WAITING, readyAt);
        }
    }

    /**
     * 查找最近的可用流体存储位置，并立即为该女仆预留。
     *
     * @return 流体存储位置，null 表示不满足流体优先条件
     */
    public static BlockPos findNearestFluidStorage(ServerLevel level, EntityMaid maid) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, 0);
            if (request == null) return null;

            ICookTask iCookTask = CookTasks.getTask(request.type);
            RecipeHolder<?> recipeHolder = level.getRecipeManager().byKey(request.id).orElse(null);
            if (recipeHolder == null) return null;

            List<StackPredicate> required = iCookTask.getIngredients(recipeHolder, level);
            IItemHandler maidInv = MaidReflectionUtils.getAvailableInv(maid);
            if (maidInv == null) return null;

            // 强制转换为 LivingEntity，确保 reobfJar 正确映射 blockPosition() 为 m_20183_()
            BlockPos maidPos = ((net.minecraft.world.entity.LivingEntity) maid).blockPosition();
            UUID maidUuid = maid.getUUID();

            for (StackPredicate predicate : required) {
                if (hasItem(maidInv, predicate)) continue;

                ItemStack sample = findSampleForPredicate(predicate);
                if (sample == null || !FluidContainerUtils.isFluidContainer(sample)) continue;

                Item emptyContainer = FluidContainerUtils.getEmptyContainer(sample);
                if (emptyContainer == null) continue;

                // 严格检查：如果 predicate 也匹配空容器，说明不是专门需要流体容器，跳过
                if (predicate.test(new ItemStack(emptyContainer))) {
                    continue;
                }

                if (!hasItem(maidInv, emptyContainer)) {
                    continue;
                }

                FluidStack requiredFluid = FluidContainerUtils.getRequiredFluid(sample);
                if (requiredFluid == null) continue;

                int requiredAmount = FluidContainerUtils.getRequiredFluidAmount(sample);

                BlockPos center = BehaviorUtils.getSearchPos(maid);
                int searchRange = Math.max((int) MaidReflectionUtils.getPerceptionRange(maid), 8);

                List<BlockPos> foundFluidStorages = SearchUtils.search(center, searchRange, 4,
                        pos -> isFluidStorageAvailable(level, pos, requiredFluid, requiredAmount, maidUuid));

                if (foundFluidStorages.isEmpty()) {
                    continue;
                }

                // 按距离排序，依次尝试预留：归本女仆或空闲的水槽才采用
                List<BlockPos> ordered = new ArrayList<>(foundFluidStorages);
                ordered.sort(Comparator.comparingDouble(p -> p.distSqr(maidPos)));
                for (BlockPos candidate : ordered) {
                    if (FluidReservationManager.reserve(level, candidate, maidUuid)) {
                        return candidate;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Fluid search error", t);
        }
        return null;
    }

    /**
     * 在指定流体存储位置，按“注水 -&gt; 停留 -&gt; 舀水”的两段式流程填充女仆背包空容器。
     *
     * @return 填充结果（DONE / WAITING / RETRY / NONE）
     */
    public static FillResult fillFluidContainers(ServerLevel level, EntityMaid maid, BlockPos targetPos) {
        try {
            UUID maidUuid = maid.getUUID();
            long now = level.getGameTime();

            IItemHandler maidInv = MaidReflectionUtils.getAvailableInv(maid);
            if (maidInv == null) return FillResult.NONE;

            // 确认目标预留归属；不在名下则尝试当场预留，被别人占则重新搜索
            FluidReservationManager.Reservation reservation = FluidReservationManager.getByMaid(maidUuid);
            boolean ours = reservation != null
                    && reservation.key.equals(FluidReservationManager.key(level, targetPos));
            if (!ours) {
                if (!FluidReservationManager.reserve(level, targetPos, maidUuid)) {
                    return FillResult.RETRY;
                }
                reservation = FluidReservationManager.getByMaid(maidUuid);
            }

            CookRequest request = (CookRequest) RequestManager.peek(maid, 0);
            if (request == null) {
                FluidReservationManager.release(maidUuid);
                return FillResult.NONE;
            }

            ICookTask iCookTask = CookTasks.getTask(request.type);
            RecipeHolder<?> recipeHolder = level.getRecipeManager().byKey(request.id).orElse(null);
            if (recipeHolder == null) {
                FluidReservationManager.release(maidUuid);
                return FillResult.NONE;
            }

            List<StackPredicate> required = iCookTask.getIngredients(recipeHolder, level);

            // 找到该目标能满足的那个流体需求
            FluidStack needFluid = null;
            int needAmount = 0;
            Item emptyItem = null;
            int emptySlot = -1;
            IMaidFluidStorage adapter = null;

            for (StackPredicate predicate : required) {
                if (hasItem(maidInv, predicate)) continue;

                ItemStack candidateSample = findSampleForPredicate(predicate);
                if (candidateSample == null || !FluidContainerUtils.isFluidContainer(candidateSample)) continue;

                FluidStack fluid = FluidContainerUtils.getRequiredFluid(candidateSample);
                if (fluid == null) continue;

                Item item = FluidContainerUtils.getEmptyContainer(candidateSample);
                if (item == null || predicate.test(new ItemStack(item))) continue;

                int slot = findItemSlot(maidInv, item);
                if (slot == -1) continue;

                IMaidFluidStorage candidateAdapter = MaidFluidStorages.tryGetUsable(level, targetPos, fluid);
                if (candidateAdapter == null) continue;

                adapter = candidateAdapter;
                needFluid = fluid;
                needAmount = FluidContainerUtils.getRequiredFluidAmount(candidateSample);
                emptyItem = item;
                emptySlot = slot;
                break;
            }

            if (needFluid == null || adapter == null) {
                // 目标已无法满足任何需求（需求变化 / 已被处理）；释放并重新搜索
                FluidReservationManager.release(maidUuid);
                return FillResult.RETRY;
            }

            int have = adapter.getFluidAmount(level, targetPos, needFluid);

            // 已由我们注水：停留结束则舀水，否则继续等待
            if (reservation.isFilled()) {
                if (now < reservation.getReadyAt()) {
                    return FillResult.waiting(reservation.getReadyAt());
                }
                if (have >= needAmount
                        && performScoop(level, maid, maidInv, targetPos, adapter, emptyItem,
                        emptySlot, needFluid, needAmount)) {
                    FluidReservationManager.release(maidUuid);
                    return FillResult.DONE;
                }
                FluidReservationManager.release(maidUuid);
                return FillResult.RETRY;
            }

            // 尚未注水：空且可注满 -&gt; 注水，进入停留阶段
            if (have < needAmount && adapter.canBeFilled(level, targetPos, needFluid)) {
                if (adapter.ensureFilled(level, targetPos)) {
                    maid.swing(InteractionHand.MAIN_HAND);
                    long readyAt = now + FILL_TO_SCOOP_DELAY_TICKS;
                    FluidReservationManager.markFilled(level, targetPos, readyAt);
                    return FillResult.waiting(readyAt);
                }
                FluidReservationManager.release(maidUuid);
                return FillResult.RETRY;
            }

            // 已装水的水槽 / 多容量储罐：直接舀取
            if (have >= needAmount
                    && performScoop(level, maid, maidInv, targetPos, adapter, emptyItem,
                    emptySlot, needFluid, needAmount)) {
                FluidReservationManager.release(maidUuid);
                return FillResult.DONE;
            }

            FluidReservationManager.release(maidUuid);
            return FillResult.RETRY;
        } catch (Throwable t) {
            LOGGER.error("Fluid fill error", t);
            return FillResult.NONE;
        }
    }

    /**
     * 实际舀取一份流体到女仆背包：优先交互舀取，失败再走 extract（多容量储罐）。
     *
     * @return true 已把装好流体的容器放入女仆背包
     */
    private static boolean performScoop(ServerLevel level, EntityMaid maid, IItemHandler maidInv,
                                         BlockPos targetPos, IMaidFluidStorage adapter, Item emptyItem,
                                         int emptySlot, FluidStack needFluid, int needAmount) {
        // 1. 交互舀取（炼药锅 / 水槽 / 流体源）
        ItemStack interactResult =
                adapter.fillContainerByInteraction(level, targetPos, new ItemStack(emptyItem));
        if (!interactResult.isEmpty()) {
            FluidStack resultFluid = FluidContainerUtils.getRequiredFluid(interactResult);
            if (resultFluid != null && resultFluid.getFluid() == needFluid.getFluid()) {
                ItemStack removedEmpty = maidInv.extractItem(emptySlot, 1, false);
                if (removedEmpty.isEmpty()) return false;
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(maidInv, interactResult, false);
                if (!remainder.isEmpty()) {
                    MaidReflectionUtils.spawnAtLocation(maid, remainder);
                }
                maid.swing(InteractionHand.OFF_HAND);
                return true;
            }
        }

        // 2. extract（多容量储罐）
        FluidStack extracted = adapter.extract(level, targetPos, needFluid, needAmount, false);
        if (extracted.isEmpty() || extracted.getAmount() < needAmount) return false;

        ItemStack emptyContainer = maidInv.extractItem(emptySlot, 1, false);
        if (emptyContainer.isEmpty()) return false;

        ItemStack filled = FluidContainerUtils.fillContainer(emptyContainer, extracted);
        if (filled.isEmpty()) {
            ItemHandlerHelper.insertItemStacked(maidInv, emptyContainer, false);
            return false;
        }

        ItemStack remainder = ItemHandlerHelper.insertItemStacked(maidInv, filled, false);
        if (!remainder.isEmpty()) {
            MaidReflectionUtils.spawnAtLocation(maid, remainder);
        }
        maid.swing(InteractionHand.OFF_HAND);
        return true;
    }

    private static boolean isFluidStorageAvailable(ServerLevel level, BlockPos pos,
                                                     FluidStack requiredFluid, int requiredAmount,
                                                     UUID self) {
        return MaidFluidStorages.isAvailableOrFillable(level, pos, requiredFluid, requiredAmount, self);
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
        ItemStack waterPotion = new ItemStack(Items.POTION);
        waterPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        ItemStack[] candidates = {
                new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.LAVA_BUCKET),
                waterPotion
        };
        for (ItemStack candidate : candidates) {
            if (predicate.test(candidate)) return candidate;
        }
        return null;
    }
}
