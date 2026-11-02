# 变更记录

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
