# FIX-002：触发链双路皆断——广播 action 写错（上游 bug）+ MAC 发布点后移

> 仓库：https://github.com/Yuxiximyxx/MagicFree4 （基线 ab619bd / 2.0.1）
> 机主真机日志实证（Magic7 Pro / MagicOS 11.0.0.110，2026-11-02）：
> - 蓝牙进程重启后 `★ A2DP 系统广播 receiver 已注册` 出现（12:23:14），但 `A2DP 广播 state=` 一条都没有（接收器从未收到任何广播）
> - `magicorig_mac` 至今为空：connectPod 从未被触发，设备中心只能回落幽灵 MAC，球依旧是空白占位
> - `跳过注入` 无记录，排除缓存误判

## 根因 1（确诊）：兜底广播的 action 字符串写错——上游 bug

`HeadsetStateDispatcher.kt:63` 注册的 IntentFilter 用的是字面量：

```
"android.bluetooth.a2dp.profile.connection.action.CONNECTION_STATE_CHANGED"   ← 多了 .connection
```

Android SDK 真值（已对本机 android-36 android.jar 用 javap 核验）：

```
BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
  = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
```

模块注册的是一个**不存在的 action**，接收器永远收不到广播。这是上游 MagicOriG 就有的 bug（作者真机上主 hook 正常工作，所以兜底从未被真正用过，没人发现）。

**修法**：不要再写字面量，直接用常量 `android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` 构造 IntentFilter。

## 根因 2（未确诊但必须绕开）：主 hook 触发在本机无任何 Log.i 级证据

`A2dpService.handleConnectionStateChanged` 的安装日志是 Log.d（LSPosed 日志默认过滤 debug，不可见），失败日志 Log.e 未搜到——大概率装上了；但其回调里的 Log.i 判据（`stateChange state=`、`connected=1`）从未被检索确认。在荣耀深度定制的蓝牙栈（`com.android.bluetooth.ble.app.*` 一系）上，该方法是否还走连接状态分发并不确定。**不要继续押注单点触发**，按下面第 2、3 条把触发做成多源冗余。

## 修复清单

1. **修正广播 action**（根因 1）：`HeadsetStateDispatcher` 注册处改用 `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` 常量。改完后接收器应能收到广播，其既有日志 `A2DP 广播 state=$state dev=$addr` 与 `★ A2DP 广播 CONNECTED → 触发 connectPod` 即为判据。

2. **MAC 发布前移**：`magicorig_mac` 的写入从 `RfcommController.connectPod` 内部，前移到**设备识别命中点**：`HeadsetStateDispatcher.isOriGPod(device)` 与 `BluetoothUpstreamHeadsetHook.isOriGPod/isOriGAddress` 任一判定为真时，立即 `Settings.Global.putString(cr, "magicorig_mac", device.address)`（同值重复写无副作用，加一层内存去重即可）。这样设备身份的发布不再依赖 SPP 连接流程是否跑通；connectPod 内的原有写入保留作双保险。

3. **已连接回补（新增第三触发源）**：`HeadsetStateDispatcher.onHook` 完成初始化后，延迟（如 +5s 与 +20s 各一次）在蓝牙进程内枚举 `BluetoothAdapter.bondedDevices`，对 isOriGPod 命中的设备查询 A2DP 连接状态（`BluetoothManager` 取 A2DP profile proxy 的 `getConnectionState(device)`，或反射 A2dpService 同名方法），若为 STATE_CONNECTED 则直接走现有 `safeConnect(context, device)`。覆盖「耳机在 hook 就绪前已连上、广播与回调都错过」的场景（本次故障正是此类）。

4. **触发链日志升格**：下列日志由 Log.d 改 Log.i，保证 LSPosed 日志（默认级别）可见：
   - `A2dpService.handleConnectionStateChanged hook installed`
   - `A2DP state: $currState ...`（safeHandleConnectionStateChanged 内）
   - 广播接收器内 isOriGPod 判定为假时，补一条 Log.i：`A2DP 广播忽略 dev=$addr name=$name state=$state`（含设备名，便于判断是名字没对上还是设备不对）

5. **降噪音（顺手）**：`ControlCenterNoiseHook` 的 createDeviceCard 观察钩子在 SystemUI 进程抛 `ClassNotFoundException: com.hihonor.controlviewnew.deviceservic...`——改为 findClassOrNull 找不到就静默跳过，不再打 W 级日志。

## 验证（机主真机，按序）

1. 装新版 → 开关一次蓝牙（让蓝牙进程加载新代码）→ 耳机入仓关盖 10 秒 → 开盖重连
2. LSPosed 日志依次应出现：
   - `A2DP 广播 state=1/2 dev=<Free4 MAC>`（广播链复活）
   - `★ A2DP 广播 CONNECTED → 触发 connectPod` 或回补路径的 `RFCOMM connect initiated`
   - `已写入 magicorig_mac=<Free4 真实 MAC>`（TAG MagicOriG-Rfcomm；若是前移点写入，打同名日志）
3. 打开设备中心：出现带名设备球（验收标准同 FIX-001）
4. 若广播仍无 state 行：导出蓝牙进程（com.android.bluetooth）的完整模块日志回传，不要再逐词截图

## 备注

- 本单不改协议层（pods/hem）与设备中心注入链（FIX-001 的 ourMac 动态读取保持不变）
- 第 3 条回补若与广播/主 hook 同时触发，现有 isConnecting 原子门已能防重复连接，不必另加锁
