package com.example.maidrestaurant.rscompat.fluid;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.example.maidrestaurant.rscompat.util.FluidContainerUtils;
import com.example.maidrestaurant.rscompat.util.MaidFluidInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LavaCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 单份流体存储适配器：兼容“空桶/空瓶右键取一份流体”的方块，不硬编码任何方块 id。
 *
 * 覆盖三类基于原版类/属性约定的结构（模组子类同样生效）：
 * 1. 实现 BucketPickup 的流体源方块（水/岩浆源），按世界 FluidState 判断；
 * 2. 炼药锅：{@link LayeredCauldronBlock}（水，1~3 份）、{@link LavaCauldronBlock}（岩浆，1 份）；
 * 3. 双半水槽：下半方块带有表示“已装水”的布尔属性（如 Let's Do 系列水槽的 filled）。
 *
 * 实际装填通过 {@link MaidFluidInteraction} 模拟玩家右键完成。
 */
public class SingleServingFluidStorage implements IMaidFluidStorage {

    public static final String UID = "SingleServingFluidStorage";

    /**
     * 表示水槽“已装水”的布尔属性名约定（非方块 id，跨水槽模组通用）。
     */
    private static final Set<String> WATER_FILL_BOOLEANS = Set.of("filled", "full", "has_water", "water");

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof BucketPickup) {
            return CompatConfig.isWorldFluidSourcesEnabled() && level.getFluidState(pos).isSource();
        }
        if (block instanceof LavaCauldronBlock) {
            return true;
        }
        if (block instanceof LayeredCauldronBlock) {
            // 用 instanceof 而非 getClass()==：装水后 ID 变为子类炼药锅时也能识别
            return state.hasProperty(LayeredCauldronBlock.LEVEL)
                    && state.getValue(LayeredCauldronBlock.LEVEL) >= 1;
        }
        return isFilledSinkLower(state);
    }

    @Nullable
    @Override
    public IFluidHandler getHandler(Level level, BlockPos pos) {
        // 单份容器不暴露 mB 流体能力
        return null;
    }

    @Override
    public int getFluidAmount(Level level, BlockPos pos, FluidStack fluid) {
        Fluid want = fluid.getFluid();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof BucketPickup) {
            if (!CompatConfig.isWorldFluidSourcesEnabled()) return 0;
            FluidState fluidState = level.getFluidState(pos);
            return (fluidState.isSource() && fluidState.getType() == want)
                    ? FluidContainerUtils.BUCKET_CAPACITY : 0;
        }
        if (block instanceof LavaCauldronBlock) {
            return want == Fluids.LAVA ? FluidContainerUtils.BUCKET_CAPACITY : 0;
        }
        if (block instanceof LayeredCauldronBlock) {
            if (want != Fluids.WATER) {
                return 0;
            }
            // 原版机制：空桶只能从满锅(3层)接出一桶；玻璃瓶每层一瓶(333)。
            // 满锅报1000（满足1桶或3瓶），1/2层报 level*333（仅够对应瓶数、不够一桶）。
            // 子类炼药锅若无 LEVEL 属性则保守返回 0，交由失败诊断日志标识。
            if (!state.hasProperty(LayeredCauldronBlock.LEVEL)) {
                return 0;
            }
            int cauldronLevel = state.getValue(LayeredCauldronBlock.LEVEL);
            return cauldronLevel >= 3 ? FluidContainerUtils.BUCKET_CAPACITY
                    : cauldronLevel * FluidContainerUtils.BOTTLE_CAPACITY;
        }
        if (isFilledSinkLower(state)) {
            return want == Fluids.WATER ? FluidContainerUtils.BUCKET_CAPACITY : 0;
        }
        return 0;
    }

    @Override
    public FluidStack extract(Level level, BlockPos pos, FluidStack fluid, int amount, boolean simulate) {
        // 实际取水走交互路径，这里仅提供容量查询语义
        if (!simulate) {
            return FluidStack.EMPTY;
        }
        int available = getFluidAmount(level, pos, fluid);
        if (available <= 0) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluid.getFluid(), Math.min(amount, available));
    }

    @Override
    public ItemStack fillContainerByInteraction(ServerLevel level, BlockPos pos, ItemStack emptyContainer) {
        return MaidFluidInteraction.scoop(level, pos, emptyContainer);
    }

    @Override
    public boolean canBeFilled(Level level, BlockPos pos, FluidStack want) {
        if (!CompatConfig.isAutoFillSinkEnabled()) {
            return false;
        }
        if (want == null || want.getFluid() != Fluids.WATER) {
            return false;
        }
        return isEmptySinkLower(level.getBlockState(pos));
    }

    @Override
    public boolean ensureFilled(ServerLevel level, BlockPos pos) {
        if (!CompatConfig.isAutoFillSinkEnabled()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!isEmptySinkLower(state)) {
            return false;
        }
        // 直接把装水布尔属性置为 true：不依赖女仆空手，也不播放倒水音效
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty booleanProperty
                    && WATER_FILL_BOOLEANS.contains(booleanProperty.getName())
                    && !state.getValue(booleanProperty)) {
                level.setBlock(pos, state.setValue(booleanProperty, true), 3);
                return isFilledSinkLower(level.getBlockState(pos));
            }
        }
        return false;
    }

    /**
     * 判断是否为“已装水的双半水槽下半部分”。
     * 要求存在 DoubleBlockHalf 枚举属性且值为 LOWER，并存在表示装水且为 true 的布尔属性。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isFilledSinkLower(BlockState state) {
        boolean foundHalf = false;
        for (Property property : state.getProperties()) {
            if (property instanceof EnumProperty<?> enumProperty
                    && isDoubleBlockHalfProperty(enumProperty)) {
                foundHalf = true;
                if (state.getValue(property) != DoubleBlockHalf.LOWER) {
                    return false;
                }
            }
        }
        if (!foundHalf) {
            return false;
        }
        for (Property property : state.getProperties()) {
            if (property instanceof BooleanProperty booleanProperty
                    && WATER_FILL_BOOLEANS.contains(booleanProperty.getName())
                    && state.getValue(booleanProperty)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为“空、但带装水布尔属性的双半水槽下半部分”。
     * 即存在 DoubleBlockHalf 且值为 LOWER，并存在装水布尔属性且当前为 false。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isEmptySinkLower(BlockState state) {
        boolean foundHalf = false;
        boolean hasWaterBoolean = false;
        boolean waterValue = false;
        for (Property property : state.getProperties()) {
            if (property instanceof EnumProperty<?> enumProperty
                    && isDoubleBlockHalfProperty(enumProperty)) {
                foundHalf = true;
                if (state.getValue(property) != DoubleBlockHalf.LOWER) {
                    return false;
                }
            }
            if (property instanceof BooleanProperty booleanProperty
                    && WATER_FILL_BOOLEANS.contains(booleanProperty.getName())) {
                hasWaterBoolean = true;
                waterValue = state.getValue(booleanProperty);
            }
        }
        return foundHalf && hasWaterBoolean && !waterValue;
    }

    private static boolean isDoubleBlockHalfProperty(EnumProperty<?> enumProperty) {
        return !enumProperty.getPossibleValues().isEmpty()
                && enumProperty.getPossibleValues().iterator().next() instanceof DoubleBlockHalf;
    }
}
