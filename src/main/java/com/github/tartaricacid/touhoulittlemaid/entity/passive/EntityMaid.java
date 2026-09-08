package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

/**
 * 编译用 stub 类，仅用于编译期类型引用。
 * 运行时由车万女仆模组的真实 EntityMaid 类替代。
 * 此类不会被打包到最终 jar 中。
 */
public abstract class EntityMaid extends Mob {

    public EntityMaid(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    /**
     * 获取女仆可用物品栏。
     */
    public abstract IItemHandler getAvailableInv(boolean includeBackpack);

    /**
     * 获取女仆感知范围。
     */
    public abstract float getPerceptionRange();
}
