package com.example.maidrestaurant.rscompat.api;

import com.example.maidrestaurant.rscompat.fluid.IMaidFluidStorage;
import com.example.maidrestaurant.rscompat.fluid.MaidFluidStorages;
import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;

/**
 * 女仆餐厅：储存 —— 对外 API。
 *
 * 其他附属模组可通过本 API 注册自定义容器的检索逻辑：
 * 1. 注册自定义物品存储（IMaidStorage）
 * 2. 注册自定义流体存储（IMaidFluidStorage）
 *
 * 使用示例：
 * <pre>
 * MaidStorageAPI.registerItemStorage(new MyCustomStorage());
 * MaidStorageAPI.registerFluidStorage(new MyCustomFluidStorage());
 * </pre>
 */
public final class MaidStorageAPI {

    private MaidStorageAPI() {}

    /**
     * 注册自定义物品存储。
     */
    public static void registerItemStorage(IMaidStorage storage) {
        MaidStorages.register(storage);
    }

    /**
     * 注册自定义流体存储。
     */
    public static void registerFluidStorage(IMaidFluidStorage storage) {
        MaidFluidStorages.register(storage);
    }
}
