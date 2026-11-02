# 变更记录

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
