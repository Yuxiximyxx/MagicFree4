# FIX-001：设备中心出现空位但不显示设备（注入了幽灵 MAC）

> 仓库：https://github.com/Yuxiximyxx/MagicFree4 （基线 commit 3fac7bb / MagicFree4 2.0.0）
> 现象（机主真机，Magic7 Pro / MagicOS 11.0.0.110）：LSPosed 中模块已启用、5 个作用域全勾；连接 Free4 后打开设备中心，界面多出一个空位，但没有任何设备球渲染出来，也无法控制耳机。

## 根因（已定位，代码实证）

`ControlCenterNoiseHook.kt:661`：

```kotlin
private const val OUR_MAC = "18:5C:A1:52:10:36"   // ← 上游 MagicOriG 作者自己那副原道耳机的 MAC
private const val OUR_NAME = "OPPO Enco Free4"     // DSH 只改了名字
private const val OUR_PROD = "00VVD9"
```

上游 MagicOriG 把作者本人的耳机 MAC **写死**在设备中心注入链里，整个链以 MAC 为主键，`OUR_MAC` 共约 20 处引用，全部在 `ControlCenterNoiseHook.kt`：

| 行 | 用途 |
|---|---|
| 198 | 设备列表 ext 已含本设备判定 |
| 208–213 | 注入的 LinkDevice JSON：deviceId / deviceNodeId / macAddress / btMacAddress 全用 OUR_MAC |
| 250–255 | 球列表 DeviceInfo 构造 + setProdId/setBtMacAddress/setName |
| 271–281 | 球列表去重与注入 |
| 312–315 | getDeviceInfoById 兜底 |
| 341–360 | 在线态 setIsOnline / updateDeviceOnlineStatus |
| 458–463 | c2.s.o 注册表 LinkDevice 兜底注入 |
| 487–543 | 球 isOnline 断根、电量读取 |
| 572–578 | genDeviceServiceInfo 电量注入 |
| 606–610 | createDeviceCard 诊断（打印 `匹配OUR=`） |
| 644–648 | setBatteryLevel 兜底 |

后果：模块往设备中心注入的是一台 **MAC 为 18:5C:A1:52:10:36 的幽灵设备**——列表层注入成功，所以布局给它留了位置（机主看到的"空位"）；但这台设备在本机蓝牙里不存在、渲染与信息链拿不到真实数据，球显示不出来；机主真实的 Free4（MAC 完全不同）从头到尾没有被注入链认领，自然也无法控制。

这同时是上游的设计缺陷（写死作者 MAC）。DSH 的移植（三层中的协议层）没有问题，出问题的是身份层只改了名、没改 MAC 来源。

## 修复方案

把设备 MAC 从写死常量改为**运行时动态解析**，通道现成——模块已有 Settings.Global 跨进程桥（`magicorig_battery`、`magicorig_connected`、`magicorig_last_anc` 都是这么传的）：

1. **蓝牙进程写入**：`pods/RfcommController.kt` 在连接成功（现有写 `magicorig_connected` 的同一位置附近）增加：
   `Settings.Global.putString(cr, "magicorig_mac", mDevice.address)`；断开时**不清除**（保留上次连接值，供设备中心先行注入、连上后转在线）。
2. **设备中心进程解析**：`ControlCenterNoiseHook` 中新增 `ourMac()` 解析函数：优先读 `Settings.Global["magicorig_mac"]`；为空时回退到原常量并打一条警告日志。把上述约 20 处 `OUR_MAC` 引用全部改走 `ourMac()`（注意有几处在注入 JSON 字符串模板里，逐处替换，不要漏）。
3. **时机说明**：耳机未连接且从未连接过时 MAC 未知、不注入，是正确行为（原版设计也是连接后球才出现）。机主已连接过一次后 MAC 即被记住。
4. **顺手核对**：`BluetoothUpstreamHeadsetHook.isOriGAddress` / `HeadsetStateDispatcher` 的地址学习逻辑（knownAddresses）与新来源不冲突即可，不必大改；`OUR_NAME`、`OUR_PROD` 保持不变。

## 验证（日志驱动，原作者留有完整日志判据）

LSPosed 日志中过滤 `ControlCenterNoiseHook` 的 TAG，修复后应依次看到：

- `★ 设备列表已注入 OPPO Enco Free4` —— 且注入 JSON 里的 MAC == Free4 的真实 MAC（在手机 设置→蓝牙→OPPO Enco Free4 详情里核对，或 `adb shell settings get global magicorig_mac`）
- `★ 球列表已注入 OPPO Enco Free4`
- `▶ createDeviceCard ... 匹配OUR=true`
- `★ ballView.isOnline() false → true`

若 MAC 已正确但球出现却无图标：再查 prodId `00VVD9` 的图标资源在 Magic7 Pro 的 controlcenter 中是否存在（换 prodId 或另行注入图标），不要在未看日志前先动这块。

## 验收标准

- [ ] 设备中心出现带「OPPO Enco Free4」名称的设备球（不是空位）
- [ ] 点开服务卡，降噪三档可见且高亮为当前档
- [ ] 点按三档，耳机实际切换（通透能听到环境声、降噪有耳压变化），400ms 回读后高亮不跳错
- [ ] 服务卡显示左右耳电量，与欢律/耳机仓开盖显示一致（±5% 内）
- [ ] QS 磁贴可循环切档；断开耳机后球转离线/消失、SWS 通知自清
- [ ] LSPosed 日志无 hook 异常堆栈；5 个作用域进程无崩溃
