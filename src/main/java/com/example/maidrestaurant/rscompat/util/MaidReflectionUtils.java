package com.example.maidrestaurant.rscompat.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 反射工具类，用于调用 EntityMaid 的方法。
 * 避免编译时签名与运行时真实类不匹配导致 NoSuchMethodError。
 */
public class MaidReflectionUtils {
    private static Method getAvailableInvMethod;
    private static Method getPerceptionRangeMethod;
    private static boolean initialized = false;

    private static void init(LivingEntity maid) {
        if (initialized) return;
        initialized = true;
        try {
            for (Method m : maid.getClass().getMethods()) {
                if (m.getName().equals("getAvailableInv") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == boolean.class) {
                    getAvailableInvMethod = m;
                    break;
                }
            }
        } catch (Exception ignored) {}
        try {
            getPerceptionRangeMethod = maid.getClass().getMethod("getPerceptionRange");
        } catch (NoSuchMethodException ignored) {}
    }

    /**
     * 获取女仆可用物品栏。
     */
    public static IItemHandler getAvailableInv(LivingEntity maid) {
        init(maid);
        if (getAvailableInvMethod != null) {
            try {
                Object result = getAvailableInvMethod.invoke(maid, false);
                if (result instanceof IItemHandler) {
                    return (IItemHandler) result;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 获取女仆感知范围。
     */
    public static float getPerceptionRange(LivingEntity maid) {
        init(maid);
        if (getPerceptionRangeMethod != null) {
            try {
                return (float) getPerceptionRangeMethod.invoke(maid);
            } catch (Exception ignored) {}
        }
        return 8.0f;
    }

    /**
     * 获取女仆当前目标位置（通过反射读取 brain 中的 TARGET_POS 记忆）。
     */
    @SuppressWarnings("unchecked")
    public static BlockPos getTargetPos(LivingEntity maid) {
        try {
            // 反射获取 Brain 对象（兼容 SRG 和 official 方法名）
            Object brain = null;
            for (Method m : maid.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("Brain")) {
                    m.setAccessible(true);
                    brain = m.invoke(maid);
                    break;
                }
            }
            if (brain == null) return null;

            // 获取 ModEntities.TARGET_POS
            Class<?> modEntitiesClass = Class.forName("com.mastermarisa.maid_restaurant.init.ModEntities");
            Object supplier = modEntitiesClass.getField("TARGET_POS").get(null);
            Object memoryType = ((java.util.function.Supplier<?>) supplier).get();

            // 反射调用 brain.getMemory(memoryType)
            Object optional = null;
            for (Method m : brain.getClass().getMethods()) {
                if (m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == net.minecraft.world.entity.ai.memory.MemoryModuleType.class
                        && m.getReturnType() == Optional.class) {
                    m.setAccessible(true);
                    optional = m.invoke(brain, memoryType);
                    break;
                }
            }
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) return null;

            // 从 PositionTracker 获取位置
            Object tracker = opt.get();
            for (Method m : tracker.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == Vec3.class) {
                    m.setAccessible(true);
                    Vec3 vec = (Vec3) m.invoke(tracker);
                    return BlockPos.containing(vec);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 在实体位置生成掉落物（通过反射调用 spawnAtLocation）。
     */
    public static void spawnAtLocation(LivingEntity entity, ItemStack stack) {
        try {
            // 遍历查找 spawnAtLocation(ItemStack) 方法（兼容 SRG 和 official 名称）
            for (Method m : entity.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == ItemStack.class
                        && m.getReturnType() == ItemStack.class) {
                    // spawnAtLocation 返回 ItemStack，检查方法名
                    String name = m.getName();
                    if (name.equals("spawnAtLocation") || name.equals("m_19983_")) {
                        m.invoke(entity, stack);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
