package com.example.maidrestaurant.rscompat.storage;

import com.example.maidrestaurant.rscompat.config.CompatConfig;
import com.mastermarisa.maid_restaurant.api.IMaidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 女仆餐厅的存储实现，处理应用能源2（AE2）的磁盘管理器。
 *
 * 通过反射访问AE2 API，避免编译时硬依赖。
 * 支持读取已连接ME网络或独立放置的磁盘管理器中的物品。
 */
public class AE2DiskDriveStorage implements IMaidStorage {

    public static final String UID = "AE2DiskDriveStorage";
    private static final String DISK_DRIVE_CLASS = "appeng.blockentity.storage.DiskDriveBlockEntity";

    private static boolean apiChecked = false;
    private static boolean apiAvailable = false;
    private static Class<?> diskDriveClass;
    private static Method getInventoryMethod;

    private static void checkApi() {
        if (apiChecked) return;
        apiChecked = true;
        try {
            diskDriveClass = Class.forName(DISK_DRIVE_CLASS);
            // 尝试获取 getInventory 方法（返回 IItemList）
            for (Method m : diskDriveClass.getMethods()) {
                if (m.getName().equals("getInventory") && m.getParameterCount() == 0) {
                    getInventoryMethod = m;
                    break;
                }
            }
            apiAvailable = getInventoryMethod != null;
        } catch (ClassNotFoundException e) {
            apiAvailable = false;
        }
    }

    public static boolean isApiAvailable() {
        checkApi();
        return apiAvailable;
    }

    @Override
    public String getUID() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack((ItemLike) Blocks.CHEST);
    }

    @Override
    public boolean isValid(Level level, BlockPos pos) {
        if (!CompatConfig.isAe2DiskDriveEnabled()) return false;
        if (!isApiAvailable()) return false;
        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && diskDriveClass.isInstance(be);
    }

    @Override
    @Nullable
    public IItemHandler getHandler(Level level, BlockPos pos) {
        if (!CompatConfig.isAe2DiskDriveEnabled() || !isApiAvailable()) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !diskDriveClass.isInstance(be)) return null;

        try {
            Object inventory = getInventoryMethod.invoke(be);
            if (inventory == null) return null;

            // inventory 是 appeng.api.storage.data.IItemList<IAEItemStack>
            // 反射遍历获取物品列表
            List<ItemStack> stacks = extractStacks(inventory);
            if (stacks.isEmpty()) return null;

            return new AE2ItemHandler(be, inventory, stacks);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 AE2 IItemList 中提取 ItemStack 列表。
     */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> extractStacks(Object itemList) {
        List<ItemStack> result = new ArrayList<>();
        try {
            // IItemList extends Iterable<IAEItemStack>
            if (itemList instanceof Iterable<?> iterable) {
                for (Object aeStack : iterable) {
                    if (aeStack == null) continue;
                    // IAEItemStack 有 getCachedItemStack() 或 createItemStack() 方法
                    ItemStack stack = getAeStackItem(aeStack);
                    if (!stack.isEmpty()) {
                        result.add(stack);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static ItemStack getAeStackItem(Object aeStack) {
        try {
            // 尝试 getCachedItemStack()
            Method m = aeStack.getClass().getMethod("getCachedItemStack");
            Object result = m.invoke(aeStack);
            if (result instanceof ItemStack stack) return stack;
        } catch (Exception ignored) {
        }
        try {
            // 尝试 createItemStack()
            Method m = aeStack.getClass().getMethod("createItemStack");
            Object result = m.invoke(aeStack);
            if (result instanceof ItemStack stack) return stack;
        } catch (Exception ignored) {
        }
        try {
            // 尝试 getItemStack()
            Method m = aeStack.getClass().getMethod("getItemStack");
            Object result = m.invoke(aeStack);
            if (result instanceof ItemStack stack) return stack;
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, int slot, int amount, boolean simulate) {
        IItemHandler handler = getHandler(level, pos);
        if (handler == null) return ItemStack.EMPTY;
        return handler.extractItem(slot, amount, simulate);
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, ItemStack stack, boolean simulate) {
        // AE2 插入需要通过网络 API，反射实现较复杂，暂不支持
        return stack;
    }

    /**
     * AE2 物品列表的 IItemHandler 包装。
     * 提取通过反射调用 AE2 的注入 API。
     */
    private static class AE2ItemHandler implements IItemHandler {
        private final BlockEntity blockEntity;
        private final Object itemList;
        private List<ItemStack> cachedStacks;

        AE2ItemHandler(BlockEntity blockEntity, Object itemList, List<ItemStack> cachedStacks) {
            this.blockEntity = blockEntity;
            this.itemList = itemList;
            this.cachedStacks = cachedStacks;
        }

        @Override
        public int getSlots() {
            return cachedStacks.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= cachedStacks.size()) return ItemStack.EMPTY;
            return cachedStacks.get(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= cachedStacks.size()) return ItemStack.EMPTY;
            ItemStack type = cachedStacks.get(slot);
            if (type.isEmpty()) return ItemStack.EMPTY;

            try {
                // 尝试通过 IStorageChannel 提取
                // AE2 的提取需要通过 IStorageChannel.getCellInventory 或网络
                // 这里用反射尝试调用 IItemList 的方法
                ItemStack extracted = tryExtract(type, amount, simulate);
                if (!extracted.isEmpty() && !simulate) {
                    // 刷新缓存
                    Object newList = getInventoryMethod.invoke(blockEntity);
                    if (newList != null) {
                        cachedStacks = extractStacks(newList);
                    }
                }
                return extracted;
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }

        private ItemStack tryExtract(ItemStack type, int amount, boolean simulate) {
            try {
                // 尝试通过反射调用 IStorageChannel.extractItems
                Class<?> storageChannelClass = Class.forName("appeng.api.storage.StorageChannels");
                Method itemsMethod = storageChannelClass.getMethod("items");
                Object channel = itemsMethod.invoke(null);

                // 从磁盘驱动器获取 storage
                Method getStorageMethod = diskDriveClass.getMethod("getInventory");
                Object inventory = getStorageMethod.invoke(blockEntity);

                // 尝试通过 IMEInventory 提取
                Method extractMethod = inventory.getClass().getMethod("extractItems",
                        Class.forName("appeng.api.storage.data.IAEItemStack"),
                        Class.forName("appeng.config.IActionSource"));

                // 创建 IAEItemStack
                Class<?> aeItemStackClass = Class.forName("appeng.api.storage.data.IAEItemStack");
                Class<?> storageChannelClass2 = Class.forName("appeng.api.storage.IStorageChannel");
                // 通过 channel.createStack 创建
                Method createStackMethod = channel.getClass().getMethod("createStack", ItemStack.class);
                Object aeStack = createStackMethod.invoke(channel, type);

                // 设置数量
                Method setStackSizeMethod = aeStack.getClass().getMethod("setStackSize", long.class);
                setStackSizeMethod.invoke(aeStack, (long) amount);

                // ActionSource
                Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
                // 用机器源
                Class<?> machineSourceClass = Class.forName("appeng.api.networking.security.BaseActionSource");
                Object actionSource = machineSourceClass.getDeclaredConstructor().newInstance();

                Object result = extractMethod.invoke(inventory, aeStack, actionSource);
                if (result != null) {
                    return getAeStackItem(result);
                }
            } catch (Exception ignored) {
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
