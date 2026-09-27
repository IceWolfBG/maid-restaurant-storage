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
    public static final ForgeConfigSpec.BooleanValue WORLD_FLUID_SOURCES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue AUTO_FILL_SINK;
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

        WORLD_FLUID_SOURCES_ENABLED = builder
                .comment("是否允许女仆直接从世界中的流体源方块（河水/湖水/天然岩浆等）取水或岩浆",
                        "关闭时只使用玩家放置的炼药锅、水槽等容器，可避免女仆跑到河里取水淹死",
                        "默认: false")
                .define("allow_world_fluid_sources", false);

        AUTO_FILL_SINK = builder
                .comment("是否允许女仆在水槽为空时，模拟玩家空手右键直接把水槽注满水，然后再舀水",
                        "仅对烛火晚宴/馥郁烘焙等支持空手注水的水槽生效，且只产生水",
                        "默认: true")
                .define("auto_fill_sink", true);

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

    public static boolean isWorldFluidSourcesEnabled() {
        return WORLD_FLUID_SOURCES_ENABLED.get();
    }

    public static boolean isAutoFillSinkEnabled() {
        return AUTO_FILL_SINK.get();
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

    /**
     * 交互专用：配置处于重载窗口时 get() 可能抛异常，此时按默认 true 处理，
     * 避免交互处理器提前 return、事件未取消而被女仆餐厅本体误设为上餐点。
     */
    public static boolean isInteractiveWhitelistEnabledSafe() {
        try {
            return INTERACTIVE_WHITELIST.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /** 安全快照：配置未加载/重载中时返回空列表而非抛异常。 */
    private static List<String> safeList(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        List<String> out = new ArrayList<>();
        try {
            List<? extends String> v = value.get();
            if (v != null) {
                for (String s : v) if (s != null) out.add(s);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public static boolean isInWhitelistSafe(String blockId) {
        return safeList(CONTAINER_WHITELIST).contains(blockId);
    }

    public static boolean isBlacklistedSafe(String blockId) {
        return safeList(BLACKLIST).contains(blockId);
    }

    /** 保存失败不应中断交互（内存值已更新，世界卸载时仍会落盘）。 */
    private static void safeSave() {
        try {
            safeSave();
        } catch (Throwable ignored) { }
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
        safeSave();
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean removeFromWhitelist(String blockId) {
        List<String> list = new ArrayList<>((List<String>) CONTAINER_WHITELIST.get());
        if (!list.remove(blockId)) return false;
        CONTAINER_WHITELIST.set(list);
        safeSave();
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean addToBlacklist(String blockId) {
        List<String> list = new ArrayList<>((List<String>) BLACKLIST.get());
        if (list.contains(blockId)) return false;
        list.add(blockId);
        BLACKLIST.set(list);
        safeSave();
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean removeFromBlacklist(String blockId) {
        List<String> list = new ArrayList<>((List<String>) BLACKLIST.get());
        if (!list.remove(blockId)) return false;
        BLACKLIST.set(list);
        safeSave();
        return true;
    }
}
