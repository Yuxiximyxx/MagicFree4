# 变更记录

## 2.4.2 — 去掉重复标题（ActionBar）

真机视图树 dump 发现页面顶部有两个同名文本节点：一个是我们的顶部栏，另一个是
`Theme.DeviceDefault.DayNight` 自带的 **ActionBar**（显示 Activity 的 `android:label`）。
改为 `Theme.DeviceDefault.NoActionBar` 后只剩一个标题，与系统设置一致。

## 2.4.1 — 顶部结构收敛

内容区不再重复渲染大标题（标题只在顶部栏），避免与顶部栏标题重叠。


## 2.4.0 — 详情页改为系统设置风格（渐变模糊顶部 + 子页面）

- **顶部渐变模糊遮罩**：内容从标题下方滚过时向上渐隐（`Brush.verticalGradient`，纯黑→透明三段过渡）
- **圆形返回键 + 大标题**：44dp 圆形按钮（`#2C2C2E`）+ 28sp Bold 标题，仿系统设置
- 色板全部改为系统深色取值：纯黑 `#000000` 底、`#1C1C1E` 卡片、`#38383A` 分隔线、
  `#8E8E93` 次要文字、`#0A84FF` 主操作色、`#32D74B` 成功色
- 卡片圆角 20dp、行高 ≥56dp、水平边距 16dp、区块间距 24dp
- 新增两个子页面：
  - **均衡器页**：四组预设（至臻原音/纯享人声/澎湃低音/活力动感），当前项打勾
  - **耳机设置页**：佩戴检测开关、查找耳机（开关）、提示音音量、固件版本、蓝牙设置；
    手势入口标注「待实现」


## 2.3.2 — 详情页按 MagicOS 设计规范重做

依据《Honor MagicOS 设计规范》（深色模式）：

| 维度 | 采用值 |
|---|---|
| 背景 / 卡片 / 三级背景 | `#111827` / `#1F2937` / `#374151` |
| 分隔线 / 主文字 / 次要文字 | `#4B5563` / `#F3F4F6` / `#8A919A` |
| 主操作色 / 成功色 / 错误色 | `#4D94FF` / `#5CAA7E` / `#E07065` |
| 字号 | 标题 24sp Bold、区块标题 15sp Medium、正文 15sp、说明 12sp |
| 圆角 | 卡片 16dp、弹窗 20dp、次按钮 12dp |
| 间距 | 8dp 栅格、水平 16dp、区块 24dp、列表行 ≥56dp |
| 动效/交互 | 触控区域 ≥48dp |

同时：
- 噪声控制四档（**新增自适应**，映射欢律 bit 11 / 内部 status 7）
- 「响铃查找」改为**开关**：点一下高亮+响，再点熄灭+停

## 2.3.1 — 响铃高亮修复

高亮走 `DeviceServiceCardDataMgr.notifyStatusChangeWithSpecialDev(adapter, "DccFindIotDevice", isSrc, highlight, localTag)`。
首次实现用了不存在的 `getInstance`（该类单例是私有字段 `sInstance`）→ `NoSuchMethodException`。
改为使用 hook 里的 `chain.thisObject` 作为 manager 实例，并从 `DeviceInfo.getBasicDeviceBallViewAdapter()` 兜底取 adapter。


## 2.2.4 — 修正接收器 IntentFilter（功能开关/EQ/音量真正生效）+ 自动回连

**关键 bug**：新增的 `ACTION_FEATURE_SET` / `ACTION_EQ_PRESET` / `ACTION_ALERT_VOLUME` 三个广播
只加进了 `when` 分支，**没加进 `IntentFilter` 列表** → 广播匹配不上，永远不触发。
ANC 能通是因为它本来就在列表里。

修复后真机实测（`magicorig_diag_*` 回执）：

| 动作 | 回执 | 结果 |
|---|---|---|
| 功能开关 id=6（低延迟） | `featset = id=6 on=true socket=true` | `magicorig_features` → `6=1` |
| EQ 预设 26 | `eqset = id=26 socket=true` | `magicorig_eq = 26` |
| 提示音音量 8 | `volset = level=8 socket=true` | `magicorig_alert_vol = 8` |
| 响铃 | `findtx = sent=true hex=AA 08 00 00 00 04 2C 01 00 01` | 指令已下发 |

**解析修正**（对照 QuickBuds `Decode.kt`）：

- `0x8105` 固件：`00 <count>` + UTF-8 三元组 → 正确显示 **138.138.105**（之前是一串 ASCII 码）
- `0x810D` 功能状态：兼容带状态字节的固件，正确解析 `4=1,11=1,17=1,24=1,6=1`
- `0x8130` 提示音音量：兼容 `00 <level>`
- `0x810F` EQ 回复不再覆盖本地值（编号空间与本模块预设不同）

**新增自动回连**：蓝牙栈重启后耳机常常不自动连回，`HeadsetStateDispatcher` 在 +5s/+20s
枚举已配对设备，A2DP 未连接则主动发起连接（隐藏 API 反射）。

**详情页**（2.2.0 起，本版真机可见）：三电量环、四档噪声控制、五个功能开关（不支持则置灰）、
均衡器/提示音音量/查找耳机/蓝牙设置/固件版本。


## 2.2.0 — 详情页对齐 QuickBuds + 功能开关/EQ/提示音/固件接入

### 链路争用（重要）

QuickBuds 与 MagicFree4 **不能同时持有 SPP**。实测：QuickBuds 连着时，设备中心里的响铃/降噪/断开全部空转（模块发不出帧），「更多设置」又优先跳 QuickBuds → 表现为「按钮全无效」。**要用模块就卸载/停用 QuickBuds。**

### 新增协议能力（`pods/hem`）

| 功能 | 命令 |
|---|---|
| 功能开关（低延迟/高音质/3D/佩戴检测/双设备） | `0x0403 [id, on]`，`0x010D` 查（payload 为 id 列表，Free4: `05 04 0B 11 18 06 1B 1C`） |
| 固件版本 | `0x0105` → `0x8105` |
| 均衡器 | `0x0406 [presetId]`，`0x010F` 查；Free4 预设 26/28/29/34 |
| 提示音音量 | `0x0427 [1..10]`，`0x0130` 查 |
| 自适应降噪 | `0x0404` bit 11（本模块扩展 status 7） |

新增跨进程桥：`magicorig_features`（`id=value,...`）、`magicorig_firmware`、`magicorig_alert_vol`、`magicorig_eq`、`magicorig_anc_raw`。

### 详情页重做（对齐 QuickBuds 版式）

- 顶栏：设备名 + 连接态 pill + 刷新/关闭
- 电量卡：左耳 / 右耳 / 充电仓三环
- 噪声控制：**关闭 / 降噪 / 自适应 / 通透** 四档（按原始位掩码高亮）
- 功能列表：低延迟、高音质、3D 音效、佩戴检测、双设备连接（开关，状态来自耳机回报）
- 设置入口：均衡器（预设对话框）、提示音音量（滑杆）、查找耳机、蓝牙连接设置、固件版本
- 不支持的功能自动置灰（靠 `magicorig_features` 是否含该 id 判断）

### 已知限制

- EQ 目前只做内置预设切换，自定义 EQ 曲线未实现
- 手势/贴合度检测/听力配置未实现（需要更多协议与 UI）
- 详情页未做真机验证（2.2.0 编译通过，待装机验证）


## 2.1.6 — 详情页重做 + 响铃/断开真正可用（真机验证）

### 真机定位出的关键教训

**LSPosed 模块更新后，蓝牙进程不会重启**。`com.android.bluetooth` 是个长寿进程，新广播 action 的接收端只有在它重启后才注册。前几版「响铃查找点了没反应、断开没反应」全部因此而来——直到 `svc bluetooth disable/enable` 之后才通。

排查手段：新增 `magicorig_diag_*` 诊断回读通道（写 Settings.Global，`adb settings get` 直读），绕开被 Honor 加密的 logcat。

### 这一轮的修复

| 问题 | 修法 | 验证 |
|---|---|---|
| 响铃查找没声音 | 接管 `AudioUtils.startFindDevice`（荣耀是把手机音频路由到耳机，虚拟设备没路由）→ 改发欢律 `0x0400`；蓝牙进程处理 `ACTION_FIND_BUDS` | ✅ 真机响了，发送回执 `AA 08 00 00 00 04 13 01 00 01` |
| 断开连接无效 | manager 层接管 `toDisConnectDev` → 自己驱动 A2DP/HFP（隐藏 API 用反射） | ✅ `bt_action: profile=1 disconnect → true`，connected→0 |
| 底部两按钮都变「断开连接」 | 上版 bug：把 `more_settings` 也一起改了；现在只改 `to_connect/disconnect` 两个 id | ✅ 「断开连接 + 更多设置」并存 |
| 响铃查找置灰 | 数据级：`ServiceCardInfo.setShowStatus(NORMAL)` | ✅ 可点 |
| 「更多设置」ActivityNotFound | `setClassName(ctx, ...)` 会拼宿主包名 → 改显式包名 | ✅ 能开 |
| 断开后球不消失/亮着 | 在线态改实时查 A2DP；`setIsOnline`/`updateDeviceOnlineStatus` 传真实值 | ✅ 变灰留存 |
| 详情页太简陋 | 重做为 tile 版式：连接态 pill + 三电量环（L/R/仓）+ 噪声控制 + 快捷操作（查找耳机/刷新/蓝牙设置）+ 深色状态栏 | ✅ 截图见 docs/evidence |

### 已知限制

- 「更多设置」优先打开 **QuickBuds**（若已安装），否则打开本模块详情页。
- 详情页的 EQ / 手势 / 游戏模式入口尚未实现（需要补协议层）。
- 响铃依赖耳机固件支持 `0x0400`（Free4 实测支持）。

## 2.1.0 — 动作接管上移到 manager 层

`AudioUtils` 那层 hook 在本机不生效（点击根本没走到），而 `DeviceServiceCardDataMgr` 层稳定生效 → `toDisConnectDev` / `toConnectOfflineDev` / `findDevice` 全部在该层接管。

## 2.0.6 — 数据级修正（连接文案 / 响铃可用）

`genDeviceServiceInfo` 返回前遍历 sections：按真实连接态互换 `to_connect_device` ↔ `disconnect_device`（含文案）；`bsId` 含 Find 的 `ServiceCardInfo` 置 `NORMAL`。


## 2.0.5 — 服务卡为空：LinkDevice 注册表注入（本机构建 `b2.v` → `DeviceProductProfile`）**（已真机验证）**

**现象**：球出现了（2.0.3 修好），但点开显示「暂无服务可流转到其他设备」，没有降噪/电量卡。

**真机诊断**（2.0.4 内置的 `magicorig_diag_*` 回读通道，adb 直读，不依赖日志）：

```
ball     = ok=true prod=00VVD9            ← 球 OK
sections = n=null                         ← genDeviceServiceInfo 返回 null
section  = null                           ← 降噪/电量 section 从未构造
card     = ok=false retCls=null           ← 服务卡本体 null
```

**根因**（反编译本机 controlcenter 实证）：`DeviceServiceCardDataMgr.genDeviceServiceInfo` 第一行

```java
LinkDevice t8 = b2.v.s().t(str);
if (t8 == null) { P1.c.e(TAG, "genDeviceServiceInfo, linkDevice is null"); return null; }
```

本机构建的注册表类是 **`b2.v`**（上游写死的是 `c2.s`，已换名 → 上游那条兜底 hook 一直 CNFE、从未生效）。链路：

```
b2.v.s().t(id) → DeviceProductProfile.getInstance().getDeviceByUdid(id)
                → mOnLineLinkDevicesMap.get(id)     // 我们的设备从未进表 → null
→ genDeviceServiceInfo 返回 null → createDeviceServiceCard 返回 null → 「暂无服务可流转」
```

**修复**（`hook/ControlCenterNoiseHook.kt`）：hook `DeviceProductProfile.getDeviceByUdid(String)`，结果为 null 且 id 是我方 MAC 时：

1. 反射构造 `LinkDevice(mac, "OPPO Enco Free4", Third_EarPhone, mac, false, [], prodId)`
2. 塞进 `mOnLineLinkDevicesMap`（实测 mapSize 4→5）
3. 返回该对象，让 `genDeviceServiceInfo` 继续往下走

**真机验证结果**（Magic7 Pro / MagicOS 11.0.0.110 / Free4）：

| 项 | 结果 |
|---|---|
| 设备球 | ✅ 带名 + 电量图标 |
| 服务卡 | ✅ 打开即显示：电量 100% / L 100% / R 100% / 响铃查找 / 噪声控制 / 去连接 / 更多设置 |
| 降噪三档 | ✅ 点「关闭」→ `magicorig_current_anc=1`；「透传」→ `=2`；「降噪」→ `=5`（均为耳机**回读值**），UI 高亮同步 |
| sections | ✅ n=5，`createEarphoneNoiseSection → true`，`card → DeviceServiceCardView` |

**未验证**：QS 磁贴、SWS 通知电量、断开清理、丹拿版。


## 2.0.3 — 设备球建不出来：`isSelf` 传错导致被 SortUtils 过滤（真机反编译实证）

**现象**：日志 `▶ createDeviceBall(id=…) → NULL(失败)` ×14，两个会话无一成功；列表已注入、布局留位，但球视图工厂返回 null → 空白占位。

**取证**（`adb pull` 本机 `/system/priv-app/DeviceControlCenter/DeviceControlCenter.apk`，jadx 反编译 2743 个类）：

`DeviceBallLayoutManager.createDeviceBall(DeviceInfo, long)` 只有两个 null 出口：

```java
if (!this.gridIndexMap.containsKey(deviceInfo.getId())) return null;   // ① 无轨迹槽位
if (!deviceInfo.isShow()) { ...hidden...; return null; }               // ② 设备被隐藏
```

而 `gridIndexMap` 由 `relayoutRemainingRemoteDeviceBalls()` 重建：

```java
this.gridIndexMap.putAll(SortUtils.DeviceSortUtils(showRemoteDeviceInfoList));
```

`SortUtils.DeviceSortUtils` 的第 100 行：

```java
if (deviceInfo != null && !deviceInfo.isSelf()) { arrayList.add(deviceInfo); }   // 过滤 isSelf
```

**根因**：`DeviceInfo` 7 参构造签名是

```java
(String id, String name, HnDeviceProductType type, int shapeFeature,
 boolean isSelf, boolean isOnline, boolean isShow)     // ← 第 5 个是 isSelf
```

上游传的是 `(mac, name, third, 0, true, false, true)` → **`isSelf = true`** → 我们的条目被 `SortUtils` 当成「本机自己」过滤 → `gridIndexMap` 无槽位 → `createDeviceBall` 返回 null。**这与设备中心版本漂移无关，是参数语义错。**

**修复**（`hook/ControlCenterNoiseHook.kt`）：

1. 构造参数改为 `(mac, name, third, 0, **false**, true, true)`：isSelf=false、isOnline=true、isShow=true
2. 显式 `setIsShow(true)`，不依赖构造参数语义
3. **双保险**：`createDeviceBall` hook 在 `chain.proceed()` 前反射检查 `gridIndexMap`，缺槽位就按 `deviceInfo.getIndex()`（或 `map.size`）补进去，并打日志

**同批落地（FIX-003 其余任务）**：

| 任务 | 状态 |
|---|---|
| 任务1 字段 diff 取证埋点 | ✅ `magicorig_debug_dump=1` 时 dump 球列表每个 DeviceInfo 全字段、ext JSON、createDeviceBall 入参/返回值 |
| 任务1 prodId A/B | ✅ `magicorig_prodid` 可运行期覆盖 prodId（默认 00VVD9），不用改代码 |
| 任务3.1 `c2.s` CNFE | ✅ 降为 debug 日志（sections=5 链路已实证可通，该 hook 仅兜底） |
| 任务3.2 `getDeviceList` CNFE ×3 | ✅ 类缺失时静默跳过 |
| 任务3.3 `MbbApi.getSmartConnect` | ⏳ 留待 FIX-004（详情页链路，次优先） |

**验证（真机）**：装新版 → 强停 controlcenter（或重启进程）→ 打开设备中心。日志判据：

```
★ gridIndexMap 补槽位 id=<MAC> → index=…      # 双保险生效时会打印
▶ createDeviceBall(id=<MAC>) → OK prodId=00VVD9
★ setIsOnline(true) on adapter
```

目视：球带名出现 → 点开出服务卡 → 降噪三档可切、电量 ≈100%。


## 2.0.2 — 触发链修复：广播 action 写错 + MAC 发布前移 + 第三触发源

**现象**（真机日志）：蓝牙进程重启后 `★ A2DP 系统广播 receiver 已注册` 出现，但 `A2DP 广播 state=` 一条都没有；`magicorig_mac` 始终为空 → 设备中心仍回落幽灵 MAC → 球空白。

### 根因 1（确诊，上游 bug）

`HeadsetStateDispatcher.kt` 注册 IntentFilter 时手写了字面量：

```
android.bluetooth.a2dp.profile.connection.action.CONNECTION_STATE_CHANGED   ← 多了 .connection
```

SDK 真值（`BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`）是：

```
android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED
```

模块注册的是一个**不存在的 action**，接收器永远收不到广播。上游作者真机上主 hook 一直正常，所以这个兜底从未被真正用过，bug 一直没被发现。

**修法**：改用 SDK 常量 `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`，不再手写字符串。

### 根因 2（未确诊，用冗余绕开）

`A2dpService.handleConnectionStateChanged` 的安装日志是 `Log.d`（LSPosed 默认过滤），其回调里的 `stateChange state=` 判据从未被确认。荣耀定制蓝牙栈上该方法是否还走连接状态分发不确定。**不再押注单点触发**，改为三源冗余。

### 修复清单

| # | 改动 | 文件 |
|---|---|---|
| 1 | 广播 action 改用 SDK 常量 | `hook/HeadsetStateDispatcher.kt` |
| 2 | 新增 `DeviceIdentity.publish()`：**设备识别命中即发布真实 MAC**（`isOriGPod`/`isOriGAddress` 三处调用），不再依赖 SPP 连接成功；同值内存去重 | `utils/DeviceIdentity.kt`（新增）、`hook/HeadsetStateDispatcher.kt`、`hook/BluetoothUpstreamHeadsetHook.kt` |
| 3 | **第三触发源**：hook 注册后 +5s / +20s 枚举 `bondedDevices`，命中且 A2DP 已连接则补一次 `connectPod` | `hook/HeadsetStateDispatcher.kt` |
| 4 | 日志升格：`hook installed`、`A2DP state:` 由 `Log.d` → `Log.i`；广播命中失败时补 `A2DP 广播忽略 dev=... name=...` | 同上 |
| 5 | `createDeviceServiceCard` 观察钩子在无此类进程（SystemUI）**静默跳过**，不再打 CNFE 的 W 日志 | `hook/ControlCenterNoiseHook.kt` |

`connectPod` 内的 `magicorig_mac` 写入保留，作为双保险。

### 验证（真机，按序）

1. 装新版 → 开关一次蓝牙（让蓝牙进程加载新代码）→ 耳机入仓关盖 10 秒 → 开盖重连
2. LSPosed 日志应依次出现：
   - `A2DP 广播 state=1/2 dev=<MAC>`
   - `★ A2DP 广播 CONNECTED → 触发 connectPod` 或 `★ 回补触发 connectPod`
   - `已发布 magicorig_mac=<MAC>` / `已写入 magicorig_mac=<MAC>`
3. 打开设备中心：出现带名设备球
4. 若广播仍无 state 行：导出 `com.android.bluetooth` 进程完整模块日志回传

**未真机验证**：本版仅编译 + 14 条单测通过。


## 2.0.1 — 修复「设备中心空位但不显示设备」（幽灵 MAC）

**现象**：LSPosed 已启用、5 个作用域全勾，连上 Free4 后设备中心多出一个**空位**，没有任何设备球，也无法控制耳机。

**根因**：上游 MagicOriG 把作者自己那副耳机的 MAC 写死成常量 `18:5C:A1:52:10:36`，整条注入链以 MAC 为主键（`ControlCenterNoiseHook.kt` 里 22 处引用）。移植时只改了名字没改 MAC 来源，于是注入进设备中心的是一台**本机不存在的幽灵设备**：

- 列表层注入成功 → 布局给它留了位置 → 你看到的「空位」
- 这台 MAC 在本机蓝牙里不存在 → 渲染与信息链拿不到数据 → 球出不来
- 真实 Free4 的 MAC 完全不同 → 从没被注入链认领 → 也就无法控制

**修复**（沿用模块已有的 Settings.Global 跨进程桥）：

| 侧 | 改动 |
|---|---|
| 蓝牙进程 `pods/RfcommController.kt` | 连接时写 `Settings.Global["magicorig_mac"] = device.address`；断开**不清除**，保留上次值供设备中心先行注入 |
| 设备中心 `hook/ControlCenterNoiseHook.kt` | 新增 `ourMac()`：读 Global 值（5 s 缓存），为空才回落上游常量并告警一次；新增 `isOurMac()` 做大小写不敏感比较；22 处引用全部改走这两个函数 |

**行为说明**：耳机从未连接过时 `magicorig_mac` 为空 → 回落常量 → 与修复前一致（不会更坏）。**连接一次后自动纠正**为真实 MAC，无需手工操作。

**验证**（真机，日志驱动）：

```
adb shell settings get global magicorig_mac        # 期望 = 你耳机的真实 MAC
# LSPosed 日志过滤 ControlCenterNoiseHook：
★ 设备列表已注入 OPPO Enco Free4                     # 注入 JSON 里的 MAC 应等于上面的值
★ 球列表已注入 OPPO Enco Free4
▶ createDeviceCard ... 匹配OUR=true mac=XX:XX:...
★ ballView.isOnline() false → true
```

**已知未验证**：本修复只做了编译与单测，**没有真机确认**球是否真的渲染出来。

## 2.0.0

首次发布：MagicOriG fork + 欢律（HeyMelody）协议层移植（`pods/hem/`）、设备身份改名、启动图标、单元测试、文档。
