package com.example.maidrestaurant.rscompat.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 流体容器工具类。
 * 支持水桶、熔岩桶、水瓶的识别和填充。
 * 奶桶不支持流体填充（奶在原版中不是流体）。
 */
public class FluidContainerUtils {

    public static final int BUCKET_CAPACITY = FluidType.BUCKET_VOLUME;
    public static final int BOTTLE_CAPACITY = 333;

    public static boolean isFluidContainer(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.WATER_BUCKET
                || item == Items.LAVA_BUCKET
                || isWaterBottle(stack);
    }

    public static boolean isWaterBottle(ItemStack stack) {
        if (stack.getItem() != Items.POTION) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.potion().isPresent() && contents.potion().get() == Potions.WATER;
    }

    public static FluidStack getRequiredFluid(ItemStack container) {
        Item item = container.getItem();
        if (item == Items.WATER_BUCKET) {
            return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, BUCKET_CAPACITY);
        }
        if (item == Items.LAVA_BUCKET) {
            return new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, BUCKET_CAPACITY);
        }
        if (isWaterBottle(container)) {
            return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, BOTTLE_CAPACITY);
        }
        return null;
    }

    public static Item getEmptyContainer(ItemStack filledContainer) {
        Item item = filledContainer.getItem();
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            return Items.BUCKET;
        }
        if (isWaterBottle(filledContainer)) {
            return Items.GLASS_BOTTLE;
        }
        return null;
    }

    public static ItemStack fillContainer(ItemStack emptyContainer, FluidStack fluid) {
        Item item = emptyContainer.getItem();
        if (item == Items.BUCKET) {
            if (fluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER) {
                return new ItemStack(Items.WATER_BUCKET);
            }
            if (fluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA) {
                return new ItemStack(Items.LAVA_BUCKET);
            }
        }
        if (item == Items.GLASS_BOTTLE) {
            if (fluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER) {
                ItemStack potion = new ItemStack(Items.POTION);
                potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
                return potion;
            }
        }
        return ItemStack.EMPTY;
    }

    public static int getRequiredFluidAmount(ItemStack container) {
        Item item = container.getItem();
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            return BUCKET_CAPACITY;
        }
        if (isWaterBottle(container)) {
            return BOTTLE_CAPACITY;
        }
        return 0;
    }
}
