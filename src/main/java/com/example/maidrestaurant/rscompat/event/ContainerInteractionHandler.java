package com.example.maidrestaurant.rscompat.event;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * 玩家交互事件监听器：
 * 1. 手持容器方块 Shift+右键：一键加入/移出白名单
 * 2. 命令：/maidstorage 管理白名单/黑名单
 */
@EventBusSubscriber(modid = "maid_restaurant_storage", bus = EventBusSubscriber.Bus.GAME)
public class ContainerInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        if (!CompatConfig.isInteractiveWhitelistEnabled()) return;

        ItemStack held = event.getItemStack();
        // 仅手持女仆餐厅菜单（maid_restaurant:order_menu）时触发白名单管理
        if (held.isEmpty() || !held.is(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("maid_restaurant", "order_menu")))) return;

        // 获取玩家右键的方块
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return;

        String blockIdStr = blockId.toString();

        // 三态循环：无 → 白名单 → 黑名单 → 无
        if (CompatConfig.isBlacklisted(blockIdStr)) {
            // 在黑名单中，移出黑名单（回到无状态）
            CompatConfig.removeFromBlacklist(blockIdStr);
            player.displayClientMessage(Component.literal("[储存] 已从黑名单移除: " + blockIdStr)
                    .withStyle(ChatFormatting.YELLOW), false);
        } else if (CompatConfig.isInWhitelist(blockIdStr)) {
            // 在白名单中，移到黑名单
            CompatConfig.removeFromWhitelist(blockIdStr);
            CompatConfig.addToBlacklist(blockIdStr);
            player.displayClientMessage(Component.literal("[储存] 已移至黑名单: " + blockIdStr)
                    .withStyle(ChatFormatting.RED), false);
        } else {
            // 不在任何名单中，加入白名单
            CompatConfig.addToWhitelist(blockIdStr);
            player.displayClientMessage(Component.literal("[储存] 已加入白名单: " + blockIdStr)
                    .withStyle(ChatFormatting.GREEN), false);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("maidstorage")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("whitelist")
                        .then(Commands.literal("add")
                                .then(Commands.argument("block", StringArgumentType.greedyString())
                                        .executes(ctx -> addToWhitelist(ctx.getSource(), StringArgumentType.getString(ctx, "block")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("block", StringArgumentType.greedyString())
                                        .executes(ctx -> removeFromWhitelist(ctx.getSource(), StringArgumentType.getString(ctx, "block")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listWhitelist(ctx.getSource()))))
                .then(Commands.literal("blacklist")
                        .then(Commands.literal("add")
                                .then(Commands.argument("block", StringArgumentType.greedyString())
                                        .executes(ctx -> addToBlacklist(ctx.getSource(), StringArgumentType.getString(ctx, "block")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("block", StringArgumentType.greedyString())
                                        .executes(ctx -> removeFromBlacklist(ctx.getSource(), StringArgumentType.getString(ctx, "block")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listBlacklist(ctx.getSource()))))
                .then(Commands.literal("addchunk")
                        .executes(ctx -> addChunkContainers(ctx.getSource())))
        );
    }

    private static int addToWhitelist(CommandSourceStack src, String blockId) {
        if (CompatConfig.addToWhitelist(blockId)) {
            src.sendSuccess(() -> Component.literal("已加入白名单: " + blockId).withStyle(ChatFormatting.GREEN), false);
        } else {
            src.sendFailure(Component.literal("已在白名单中: " + blockId));
        }
        return 1;
    }

    private static int removeFromWhitelist(CommandSourceStack src, String blockId) {
        if (CompatConfig.removeFromWhitelist(blockId)) {
            src.sendSuccess(() -> Component.literal("已从白名单移除: " + blockId).withStyle(ChatFormatting.YELLOW), false);
        } else {
            src.sendFailure(Component.literal("不在白名单中: " + blockId));
        }
        return 1;
    }

    private static int listWhitelist(CommandSourceStack src) {
        var list = CompatConfig.CONTAINER_WHITELIST.get();
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.literal("白名单为空").withStyle(ChatFormatting.GRAY), false);
        } else {
            src.sendSuccess(() -> Component.literal("白名单 (" + list.size() + "):").withStyle(ChatFormatting.GREEN), false);
            for (Object id : list) {
                src.sendSuccess(() -> Component.literal("  " + id), false);
            }
        }
        return 1;
    }

    private static int addToBlacklist(CommandSourceStack src, String blockId) {
        if (CompatConfig.addToBlacklist(blockId)) {
            src.sendSuccess(() -> Component.literal("已加入黑名单: " + blockId).withStyle(ChatFormatting.RED), false);
        } else {
            src.sendFailure(Component.literal("已在黑名单中: " + blockId));
        }
        return 1;
    }

    private static int removeFromBlacklist(CommandSourceStack src, String blockId) {
        if (CompatConfig.removeFromBlacklist(blockId)) {
            src.sendSuccess(() -> Component.literal("已从黑名单移除: " + blockId).withStyle(ChatFormatting.YELLOW), false);
        } else {
            src.sendFailure(Component.literal("不在黑名单中: " + blockId));
        }
        return 1;
    }

    private static int listBlacklist(CommandSourceStack src) {
        var list = CompatConfig.BLACKLIST.get();
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.literal("黑名单为空").withStyle(ChatFormatting.GRAY), false);
        } else {
            src.sendSuccess(() -> Component.literal("黑名单 (" + list.size() + "):").withStyle(ChatFormatting.RED), false);
            for (Object id : list) {
                src.sendSuccess(() -> Component.literal("  " + id), false);
            }
        }
        return 1;
    }

    /**
     * 一键添加当前区块所有拥有物品或流体存储能力的容器到白名单。
     */
    private static int addChunkContainers(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();
        int minX = (playerPos.getX() >> 4) << 4;
        int minZ = (playerPos.getZ() >> 4) << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        java.util.Set<String> added = new java.util.HashSet<>();
        int scanned = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    // 跳过空气和流体
                    if (state.isAir()) continue;
                    scanned++;

                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (blockId == null) continue;
                    String blockIdStr = blockId.toString();
                    if (CompatConfig.isInWhitelist(blockIdStr) || CompatConfig.isBlacklisted(blockIdStr)) continue;
                    if (added.contains(blockIdStr)) continue;

                    // 检查是否有物品或流体存储能力
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
                    if (be == null) continue;

                    boolean hasItemHandler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, state, be, null) != null;
                    boolean hasFluidHandler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, state, be, null) != null;

                    if (hasItemHandler || hasFluidHandler) {
                        CompatConfig.addToWhitelist(blockIdStr);
                        added.add(blockIdStr);
                    }
                }
            }
        }

        int finalAdded = added.size();
        int finalScanned = scanned;
        src.sendSuccess(() -> Component.literal("扫描 " + finalScanned + " 个方块，已添加 " + finalAdded + " 种容器到白名单").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
