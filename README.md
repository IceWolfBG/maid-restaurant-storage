# 女仆餐厅：存储 (Maid Restaurant: Storage)

女仆餐厅（Maid Restaurant）的附属模组，扩展女仆的存储检索能力。

## 功能

- **RS 兼容**：女仆可通过精致厨房（Refined Cooking）的厨房终端检索精致存储（Refined Storage）网络中的物品和流体
- **更多箱子兼容**：女仆可检索 Iron Chests 模组中的各种箱子
- **农夫乐事兼容**：女仆可检索农夫乐事（Farmer's Delight）的橱柜
- **容器白名单**：可配置女仆可检索的容器白名单，支持游戏内手持女仆餐厅菜单潜行+右键一键添加
- **容器黑名单**：优先级高于白名单，可排除特定容器
- **流体容器自动填充**：女仆需要水桶/水瓶/熔岩桶/奶桶时，自动从流体存储填充
- **嵌套容器检索**：支持检索容器内嵌套容器（如箱子里的背包），最多一层
- **RS/AE2 磁盘管理器支持**：可直接读取 RS/AE2 磁盘管理器中的磁盘（可配置，默认关闭）
- **对外 API**：允许其他附属模组注册自定义容器的检索逻辑

## 依赖

- Minecraft 1.20.1
- Forge 47.2.0+
- 车万女仆 (Touhou Little Maid) 1.5.3+
- 女仆餐厅 (Maid Restaurant) 0.2.9+

## 可选依赖

- 精致存储 (Refined Storage) + 精致厨房 (Refined Cooking) — RS 兼容
- 更多箱子 (Iron Chests) — 更多箱子兼容
- 农夫乐事 (Farmer's Delight) — 农夫乐事兼容
- 应用能源2 (AE2) — AE2 磁盘管理器支持

## 配置

配置文件：`config/maid_restaurant_storage-common.toml`

- `rs_compat` (默认 true)：启用 RS 兼容
- `fluid_search` (默认 true)：启用流体容器自动填充
- `interactive_whitelist` (默认 true)：启用手持菜单潜行+右键管理白名单
- `rs_disk_drive` (默认 false)：启用 RS 磁盘管理器读取
- `ae2_disk_drive` (默认 false)：启用 AE2 磁盘管理器读取
- `container_whitelist`：容器白名单列表
- `blacklist`：容器黑名单列表

## 服务器兼容性

本模组支持服务器使用。Mixin 配置为非必需模式，即使在服务器环境下 Mixin 应用失败也不会导致崩溃。

## 许可

GPL-3.0
