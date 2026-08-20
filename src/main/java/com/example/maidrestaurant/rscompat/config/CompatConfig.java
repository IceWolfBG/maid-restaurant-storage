package com.example.maidrestaurant.rscompat.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模组配置类。
 * 匹配优先级：黑名单 > 白名单
 */
public class CompatConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue RS_COMPAT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FLUID_SEARCH_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RS_DISK_DRIVE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue AE2_DISK_DRIVE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue INTERACTIVE_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONTAINER_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("compat");

        RS_COMPAT_ENABLED = builder
                .comment("是否启用让女仆通过精致厨房的厨房站检索精致存储(RS)网络中的物品和流体",
                        "默认: true")
                .define("rs_compat", true);

        FLUID_SEARCH_ENABLED = builder
                .comment("是否启用流体检索：女仆需要水桶/熔岩桶/水瓶等流体容器时，",
                        "若背包有空容器且周围有流体存储，会自动前往接取流体",
                        "默认: true")
                .define("fluid_search", true);

        RS_DISK_DRIVE_ENABLED = builder
                .comment("是否启用读取精致存储(RS)磁盘管理器中的物品",
                        "无论磁盘管理器是否连接RS网络均可读取",
                        "默认: false")
                .define("rs_disk_drive", false);

        AE2_DISK_DRIVE_ENABLED = builder
                .comment("是否启用读取应用能源2(AE2)磁盘管理器中的磁盘",
                        "启用后女仆可直接读取AE2磁盘管理器中的物品",
                        "默认: false")
                .define("ae2_disk_drive", false);

        INTERACTIVE_WHITELIST = builder
                .comment("是否启用手持女仆餐厅菜单潜行+右键管理白名单/黑名单",
                        "默认: true")
                .define("interactive_whitelist", true);

        CONTAINER_WHITELIST = builder
                .comment("容器白名单：填入方块ID，女仆可以调用这些储存容器中的物品和流体",
                        "格式：[\"命名空间:方块名\"]",
                        "默认: 空列表")
                .defineList("container_whitelist",
                        Collections.emptyList(),
                        obj -> obj instanceof String && ((String) obj).contains(":"));

        BLACKLIST = builder
                .comment("容器黑名单：填入方块ID，女仆不会使用这些容器",
                        "优先级高于白名单",
                        "格式：[\"命名空间:方块名\"]",
                        "默认: 空列表")
                .defineList("blacklist",
                        Collections.emptyList(),
                        obj -> obj instanceof String && ((String) obj).contains(":"));

        builder.pop();

        SPEC = builder.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    public static boolean isRsCompatEnabled() {
        return RS_COMPAT_ENABLED.get();
    }

    public static boolean isFluidSearchEnabled() {
        return FLUID_SEARCH_ENABLED.get();
    }

    public static boolean isRsDiskDriveEnabled() {
        return RS_DISK_DRIVE_ENABLED.get();
    }

    public static boolean isAe2DiskDriveEnabled() {
        return AE2_DISK_DRIVE_ENABLED.get();
    }

    public static boolean isInteractiveWhitelistEnabled() {
        return INTERACTIVE_WHITELIST.get();
    }

    // === 白名单 ===

    public static boolean isInWhitelist(BlockState state) {
        return isInWhitelist(state.getBlock());
    }

    public static boolean isInWhitelist(Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key != null && isInWhitelist(key.toString());
    }

    public static boolean isInWhitelist(String blockId) {
        return CONTAINER_WHITELIST.get().contains(blockId);
    }

    // === 黑名单 ===

    public static boolean isBlacklisted(BlockState state) {
        return isBlacklisted(state.getBlock());
    }

    public static boolean isBlacklisted(Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key != null && isBlacklisted(key.toString());
    }

    public static boolean isBlacklisted(String blockId) {
        return BLACKLIST.get().contains(blockId);
    }

    public static boolean isContainerAllowed(BlockState state) {
        return isContainerAllowed(state.getBlock());
    }

    public static boolean isContainerAllowed(Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key != null && isContainerAllowed(key.toString());
    }

    public static boolean isContainerAllowed(String blockId) {
        return !isBlacklisted(blockId) && isInWhitelist(blockId);
    }

    // === 运行时修改 ===

    @SuppressWarnings("unchecked")
    public static boolean addToWhitelist(String blockId) {
        List<String> list = new ArrayList<>((List<String>) CONTAINER_WHITELIST.get());
        if (list.contains(blockId)) return false;
        list.add(blockId);
        CONTAINER_WHITELIST.set(list);
        SPEC.save();
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean removeFromWhitelist(String blockId) {
        List<String> list = new ArrayList<>((List<String>) CONTAINER_WHITELIST.get());
        if (!list.remove(blockId)) return false;
        CONTAINER_WHITELIST.set(list);
        SPEC.save();
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean addToBlacklist(String blockId) {
        List<String> list = new ArrayList<>((List<String>) BLACKLIST.get());
        if (list.contains(blockId)) return false;
        list.add(blockId);
        BLACKLIST.set(list);
        SPEC.save();
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean removeFromBlacklist(String blockId) {
        List<String> list = new ArrayList<>((List<String>) BLACKLIST.get());
        if (!list.remove(blockId)) return false;
        BLACKLIST.set(list);
        SPEC.save();
        return true;
    }
}
