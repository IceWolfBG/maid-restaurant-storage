package com.example.maidrestaurant.rscompat;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.example.maidrestaurant.rscompat.fluid.BalmFluidStorage;
import com.example.maidrestaurant.rscompat.fluid.MaidFluidStorages;
import com.example.maidrestaurant.rscompat.fluid.RefinedStorageFluidStorage;
import com.example.maidrestaurant.rscompat.fluid.WhitelistFluidStorage;
import com.example.maidrestaurant.rscompat.storage.AE2DiskDriveStorage;
import com.example.maidrestaurant.rscompat.storage.FarmersDelightStorage;
import com.example.maidrestaurant.rscompat.storage.IronChestStorage;
import com.example.maidrestaurant.rscompat.storage.KitchenStationStorage;
import com.example.maidrestaurant.rscompat.storage.RSDiskDriveStorage;
import com.example.maidrestaurant.rscompat.storage.WhitelistStorage;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 女仆餐厅：储存（Maid Restaurant: Storage）
 *
 * 功能：
 * 1. RS兼容（可配置）：女仆通过精致厨房厨房站访问RS物品和流体网络
 * 2. 更多箱子兼容：女仆可检索更多箱子中的物品
 * 3. 农夫乐事兼容：女仆可检索农夫乐事橱柜
 * 4. 统一容器白名单（可配置，支持游戏内Shift+右键添加）
 * 5. 容器黑名单（可配置，优先级高于白名单）
 * 6. 流体容器自动填充：女仆需要水桶/水瓶/熔岩桶时自动从流体存储填充
 * 7. 对外API：允许其他附属模组注册自定义容器
 * 8. 命令支持：/maidstorage 管理白名单/黑名单
 */
@Mod(MaidRestaurantRSCompat.MOD_ID)
public class MaidRestaurantRSCompat {

    public static final String MOD_ID = "maid_restaurant_storage";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public MaidRestaurantRSCompat(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        CompatConfig.register();
        LOGGER.info("MaidRestaurantStorage initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // === 物品存储 ===
            if (ModList.get().isLoaded("refinedstorage") && ModList.get().isLoaded("refinedcooking")) {
                try {
                    MaidStorages.register(new KitchenStationStorage());
                    LOGGER.info("Registered KitchenStationStorage (RS item compat)");
                } catch (Throwable t) {
                    LOGGER.error("Failed to register KitchenStationStorage", t);
                }
            }

            // RS磁盘管理器支持（仅需RS，不需要精致厨房，默认关闭）
            if (ModList.get().isLoaded("refinedstorage")) {
                try {
                    MaidStorages.register(new RSDiskDriveStorage());
                    LOGGER.info("Registered RSDiskDriveStorage");
                } catch (Throwable t) {
                    LOGGER.error("Failed to register RSDiskDriveStorage", t);
                }
            }

            // AE2磁盘管理器支持（反射，默认关闭）
            if (ModList.get().isLoaded("ae2")) {
                try {
                    MaidStorages.register(new AE2DiskDriveStorage());
                    LOGGER.info("Registered AE2DiskDriveStorage");
                } catch (Throwable t) {
                    LOGGER.error("Failed to register AE2DiskDriveStorage", t);
                }
            }

            if (ModList.get().isLoaded("ironchest")) {
                try {
                    MaidStorages.register(new IronChestStorage());
                    LOGGER.info("Registered IronChestStorage");
                } catch (Throwable t) {
                    LOGGER.error("Failed to register IronChestStorage", t);
                }
            }

            if (ModList.get().isLoaded("farmersdelight")) {
                try {
                    MaidStorages.register(new FarmersDelightStorage());
                    LOGGER.info("Registered FarmersDelightStorage");
                } catch (Throwable t) {
                    LOGGER.error("Failed to register FarmersDelightStorage", t);
                }
            }

            try {
                MaidStorages.register(new WhitelistStorage());
                LOGGER.info("Registered WhitelistStorage");
            } catch (Throwable t) {
                LOGGER.error("Failed to register WhitelistStorage", t);
            }

            // === 流体存储 ===
            if (ModList.get().isLoaded("refinedstorage") && ModList.get().isLoaded("refinedcooking")) {
                try {
                    MaidFluidStorages.register(new RefinedStorageFluidStorage());
                    LOGGER.info("Registered RefinedStorageFluidStorage (RS fluid compat)");
                } catch (Throwable t) {
                    LOGGER.error("Failed to register RefinedStorageFluidStorage", t);
                }
            }

            try {
                MaidFluidStorages.register(new WhitelistFluidStorage());
                LOGGER.info("Registered WhitelistFluidStorage");
            } catch (Throwable t) {
                LOGGER.error("Failed to register WhitelistFluidStorage", t);
            }

            try {
                MaidFluidStorages.register(new BalmFluidStorage());
                LOGGER.info("Registered BalmFluidStorage (Cooking for Blockheads sink etc.)");
            } catch (Throwable t) {
                LOGGER.error("Failed to register BalmFluidStorage", t);
            }

            LOGGER.info("MaidRestaurantStorage setup complete");
        });
    }
}
