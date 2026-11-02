# MagicFree4 执行计划 v3（取证完成版）

> **状态**：两个参考仓库已克隆进工作区；欢律协议关键事实已从**源码**取证（不再是「待查」）。真机验证尚未开始——本机无 adb、无 Android SDK、无连接设备。

| 文档 | 作用 |
|---|---|
| `docs/protocol-code-facts.md` | 欢律协议**代码级**证据（`文件:行号`），本文 §5 的依据 |
| `docs/protocol-evidence.md` | `QuickBuds/docs/PROTOCOL.md` 文档侧取证 |
| `docs/magicos-recon.md` | `MagicOriG/docs/magicos11-integration.md` 注入点侦察 |
| `PLAN.md` | 本文件：目标、阶段、验收门槛 |

证据标记：`✅` 已核实（可复核）｜`🔍` 待真机｜`🧠` 设计推导

---

## 1. 目标

在荣耀 MagicOS 11 上，让 **OPPO Enco Free4** 获得荣耀自家耳机待遇：

- 设备中心出现球位 + 可打开服务卡
- 服务卡内切降噪（降噪 / 通透 / 关闭），点击即生效
- 左右耳独立电量（系统管线支持则加仓电量）
- 控制中心 QS 磁贴循环切档
- 音频切换（SWS）通知追加电量，断开自动清除

路线：fork **MagicOriG**（荣耀侧注入框架，已为原道耳机打通 11 环），把 `pods/` 协议层从原道私有协议换成**欢律（OPPO/HeyMelody）协议**，实现参照 **QuickBuds**。

---

## 2. 环境与基线

### 2.1 机主设备（固定）

| 项 | 值 |
|---|---|
| 手机 | 荣耀 Magic7 Pro |
| 系统 | MagicOS 11.0.0.110 |
| Root | 临时 root（重启失效），KernelSU 3.3.1 |
| 框架 | LSPosed 2.2.1（libxposed API 102） |
| 耳机 | OPPO Enco Free4（productId `068C10`）／丹拿版 `06C010` ✅ |
| 约束 | 测试期不要同时开欢律 / QuickBuds——RFCOMM 会抢链路 |

原作者基线是 HONOR AAK-AN00 / MagicOS 11.0.0.109，与目标机同为 11.0.0.x，hook 点预期成立但必须 Phase 0 实测。

### 2.2 开发机（本次实测）

| 能力 | 状态 |
|---|---|
| 参考仓库 | ✅ 已克隆到工作区（§3） |
| 出网 | ✅ 走系统代理 `127.0.0.1:7897` 可访问 GitHub；直连仍被 fake-IP（`198.18.0.0/15`）拦 |
| JDK | ✅ 21.0.5（`/Users/muxiaole/jdk-21.0.5+11`）— AGP 要求 21 工具链 |
| Android SDK | ✅ `platforms;android-34` + `build-tools;34.0.0` + `platform-tools`（`/Users/muxiaole/android-sdk`） |
| Gradle | ✅ 8.9（wrapper 自动下载），代理写在 `~/.gradle/gradle.properties` |
| 依赖源 | ✅ 阿里云镜像优先（`settings.gradle.kts`），规避国内 TLS 掐断 |
| 编译 | ✅ `assembleDebug` / `assembleRelease` 均通过 |
| 单测 | ✅ 14 条纯 JVM 测试全绿 |
| 连接设备 | ❌ 无 USB 调试通道 —— 真机验收仍未做 |

**结论**：代码、编译、单测全部打通，**APK 已产出**；只差真机验证。

---

## 3. 参考仓库（已就位）✅

| 仓库 | 本地路径 | commit | 许可证 |
|---|---|---|---|
| MagicOriG | `MagicOriG/` | `25780c8b006fec78bea75e90daa799702045e51c` | MIT |
| QuickBuds | `QuickBuds/` | `8e8d3e9309cfd4f648c4ffc8f8d0bd2b61d0cc8b` | GPL-3.0 |

克隆时的网络解法（记下来，下次直接用）：

```bash
export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897
git clone --depth 1 https://github.com/yingjfd/MagicOriG.git
git clone --depth 1 https://github.com/spizganed/QuickBuds.git
```

**许可证义务（硬约束）**：协议代码移植自 GPL-3.0 的 QuickBuds ⇒ 公开发布时整体 **GPL-3.0** + 标注出处；MagicOriG 的 MIT 声明保留。两者不冲突（GPL 兼容 MIT），但**不能声称「仅 MIT」**。

---

## 4. MagicOriG 结构（已核对真实代码）✅

实际 **5551 行 Kotlin**（原计划写「约 4350 行」是旧数字，仓库已到 v1.1.8）。

**作用域**（`app/src/main/resources/META-INF/xposed/scope.list`）：

```
com.android.bluetooth                 RFCOMM 链路宿主
com.android.systemui                  状态栏耳机图标
com.hihonor.audioaccessorymanager     耳机详情页 / 降噪面板
com.hihonor.imedia.sws                音频切换通知
com.hihonor.controlcenter             设备中心
```

**module.prop 的坑（已确认原文）**：`targetApiVersion=102` 必须显式声明——缺这个键时 LSPosed `extractIntPart(null)` 抛 NPE，异常逃出 `reloadInstalledModules()` 循环，表现为「LSPosed 模块列表一个都看不到」。另有 `minApiVersion=101`、`staticScope=true`、`autoHotReload=false`。**不要删任何键。**

**三层改造面**（行数/行号已实地核对）：

| 层 | 文件（行数） | 改造 |
|---|---|---|
| 设备中心注入（**68 条注入点，5 个进程**；文档并无「1–9 环」编号表，原简报的「11 环」是概括说法） | `hook/ControlCenterNoiseHook.kt`(736)、`hook/HonorEarphoneHook.kt`(423) | 保留，仅改身份参数 |
| 下发桥 | `hook/ControlCenterNoiseHook.kt:95`（`setNoiseCtrlMode` 拦截）、`:108`（映射日志）、`hook/QuickSettingsAncTile.kt`(71)、`utils/MagicOriGAction.kt` | 契约不动 |
| 协议层 | `pods/RfcommController.kt`(716)、`pods/Packets.kt`(204) | **整体替换** |
| 其余 hook | `BluetoothUpstreamHeadsetHook.kt`(453)、`HeadsetStateDispatcher.kt`(250)、`MediaSwsBatteryHook.kt`(53)、`SystemUIHeadsetIconHook.kt`(359)、`SettingsHeadsetHook.kt`(779) | 保留 + 改设备识别 |

**设备身份伪装（已确认行号）**：

- prodId `00VVD9`：`hook/ControlCenterNoiseHook.kt:214`（JSON 注入）、`:663`（`OUR_PROD` 常量）
- `hook/HonorEarphoneHook.kt:291` 写 `modelId = 00VVD9`、`:287` 写 `device_general_name = "YUANDAO OriG in"`
- 蓝牙名硬编码：`hook/BluetoothUpstreamHeadsetHook.kt:426-427`、`hook/HeadsetStateDispatcher.kt:242-244`（`YUANDAO` / `OriG` / `NiceHCK`）

**原协议（将被替换）**：帧头 `0x4E`，SPP UUID `0000a100-1000-8000-4e48-434b4354524c`，ANC_SET `0x0201`。

### 4.1 原作者踩坑记录（v1.1.1–v1.1.3，README 更新日志）✅

1. **「蓝牙页停留一会自动切降噪」**：三个源头——状态同步调用被误转发成真切换、面板高亮刷新连带真下发、「保护窗口」开关**只 set 从未 get**（拦截器从未通电）。修法：同步不转发、刷新不下发、窗口双向可读（实测静置 100 s 零下发）。
2. **双耳电量**：数据源是 `DeviceInfo.batteryData`，喂入点必须放在**每次取设备信息的出口**（球列表有缓存）。
3. **SWS 通知被还原**：SystemUI 是长寿进程，更新模块后不重启它就跑旧代码。
4. 项目自述「AI 生成 + 人工把关」，读它的代码要自己复核，别迷信注释。

### 4.2 荣耀侧 ANC 契约（从集成文档取证，Phase 2 直接要用）✅

来源：`docs/magicos-recon.md` ← `magicos11-integration.md`

| 环节 | 事实 | 行号 |
|---|---|---|
| 面板 UI → 内部 status | UI index `0/1/2` → status `5/2/1` | `:1259-1261` |
| status → `AncMode` | status 5 → `EXPERIMENT = 0x10`；status 2 → `TRANSPARENT = 0x01`；status 1 → `OFF = 0x00` | `:1259-1261` |
| 旧值 | 改动前 status `3` → `0x02` NORMAL | `:1263` |
| 下发入口 | `PropertyUtils.setNoiseCtrlMode(String,int,cb)`（static）；cb 是 `c2.s$b{onResult(String), onError(Exception), onResultWithKey(Map)}`；唯一调用方 `onNoiseModeClick(NoiseMode)` | `:1337-1338`、`:1636` |
| 广播契约 | action `io.github.yuxiximyxx.magicfree4.ACTION_ANC_SELECT`，extra 键名 **`status`**，**必须是非空 Int**（曾是 String，导致 `getIntExtra` 返 0、根本不发帧） | `:1237`、`:1251`、`:1579-1581` |
| 降噪行开关 | 最终生效开关 = `x7.a.r():Boolean` | `:1096`、`:1247` |

**⇒ Phase 2 的映射表实际是两跳**：`荣耀 status(5/2/1) → 欢律 bit`。
按 Free4 的 ANC 树（§5.2）：降噪 → Deep(4) 或 Medium(5) 或 Light(6)（**待定策略**）、通透 → Transparency(2)、关闭 → Off(0)。
**status 4 的语义文档未给出**（只在 `readLastAnc` 的 5/4/3 里出现）→ 🔍 需要在 Phase 0 用日志实勘。

**文档明确缺失（不是没找到）**：`NoiseControlViewModel` 的读写方法签名（已混淆成 `R(I)V`/`U(I)V`/`V(I)V`/`W(I)V`/`Y(...)`）、`DeviceInfo.batteryData` 的**字段级结构**、「每次取设备信息的出口」的确切方法名（最接近候选 `getDeviceInfoById`）、`setNoiseCtrlMode` 返回值类型。

> 另更正两处归属：`module.prop` 的 `targetApiVersion` 坑**不在**集成文档里（在 `module.prop` 自己的注释里，已直接核对）；「SystemUI 长寿进程」出自 README 更新日志，也不在集成文档。

---

## 5. 欢律协议（已从源码取证）✅

完整台账见 **`docs/protocol-code-facts.md`**。以下只列结论。

### 5.1 关键事实

| 项 | 结论 | 证据 |
|---|---|---|
| 帧头 | `0xAA`，长度 LEB128，**无校验字节** | `Wire.kt:16,19,26-41` |
| 帧体 | `AA len slice 00 cmd(2LE) seq payLen(2LE) payload` | `Wire.kt:46-81` |
| 分片 | `0`=整帧 / `1`=首 / `2`=中 / `3`=尾；预算 660 B，上限 2048 B | `Wire.kt:15,131-166` |
| SPP UUID | 主 `00001107-D102-11E1-9B23-00025B00A5A5`；备 `0000079A-D102-11E1-9B23-00025B00A5A5` | `Protocol.kt:33-34` |
| 连接顺序 | `079A` secure → `1107` secure → `079A` insecure → `1107` insecure → 反射 channel 15（共 5 次）；12 s 看门狗；掉线 8 次重连 | `BudsConnectionManager.kt:377-385`、`PROTOCOL.md:46-50` |
| 握手 | `0x0100` 空 payload，连上 300 ms 后 | `Commands.kt:13`、`BudsConnectionManager.kt:618` |
| 降噪写 | `0x0404` payload `01 01 <单 bit mask>` | `Commands.kt:87-95` |
| 降噪读 | `0x010C` `01 01`（当前）/ `02 <type>`（切换列表）/ `04 01`（Smart） | `Commands.kt:150-161` |
| 降噪上报 | `0x810C` 或 `0x0204` subType `0x03`，payload `03 01 01 <value LE>` | `Anc.kt:8-81` |
| **回声坑** | `0x0404` 写入本身会回声 `03 02 01 <mask>`，不是真事件；必须校验 `payload[1..2] == 01 01` | `Anc.kt:50-69` |
| 电量查 | `0x0106`（seq `0xF0`）→ `0x8106`，`[00][count][idx,raw]`，idx 1/2/3=左/右/仓 | `Battery.kt:13-43` |
| 电量编码 | `level = raw & 0x7F`，`charging = raw & 0x80`，`level 0` = 未知（按 null） | `Battery.kt:17,35` |
| 佩戴 | `0x0109`（seq `0xF2`）→ `0x8109`，`st==4` 在仓 | `Battery.kt:101,146-150` |
| 产品 ID | `0x0103` → `0x8103`，payload `00 <id 3B LE>` | `Capabilities.kt:64-74` |
| 能力位 | `0x8100` bitmap 决定后续能发哪些命令；`ALWAYS` 白名单例外 | `Capabilities.kt:20-47` |
| 序号 | 通用 `0x01..0xBF`；引擎 `0xC0..0xDF`；固定：电量 `0xF0`、佩戴 `0xF2` | `Protocol.kt:157-161`、`BudsConnectionManager.kt:1055`、`Commands.kt:258-259` |
| 主动上报 | `0x0204` subType：`01` 电量 / `02` 佩戴 / `03` ANC / `04` 贴合 / `05` 游戏 / `0F` 公共 | `Protocol.kt:108-118` |

### 5.2 Free4 机型参数（挖到宝了）✅

`QuickBuds/app/src/main/assets/models.json` 里**就有这台耳机**：

| 机型 | productId | 行 |
|---|---|---|
| OPPO Enco Free4 | `068C10` | `models.json:108` |
| OPPO Enco Free4（丹拿版） | `06C010` | `models.json:120` |

- `statusQuery` 两版相同：`05 04 0B 11 18 06 1B 1C`
- `ancModes` 两版相同：ANC(slot 1) → Deep(4) / Medium(5) / Light(6) / Smart(7)；Adaptive(10, slot 11)；Transparency(2, 子 8)；Off(0, 子 3)
- 差异只在 EQ 预设与固件门槛（`eqPresetsByFirmware` mode 40 / minFirmware 136）

⇒ 原计划担心的「丹拿版要单独按机型参数处理」**实际不存在**：两版 ANC/电量逻辑一致，只是 productId 不同。

### 5.3 仍需真机确认（3 件）🔍

1. **降噪默认档**：设备中心只有一个「降噪」按钮，Free4 有 Light/Medium/Deep。建议固定 **Medium（bit 5）**——依据 `Anc.kt:31` 实测「ANC on → `0x0020` = Medium」。**需机主拍板。**
2. Free4 是否上报**仓电量**（`0x8106` 里有没有 idx=3）。
3. Free4 实际 **SDP UUID**（家族通用值大概率一致，以实测为准）。

### 5.4 心跳问题已结案 ✅

`PROTOCOL.md:260-261` 原文：*"Was: a keep-alive poll. Is: none. No stale link seen `[TESTED]`."*

- 应用层**没有心跳**，「心跳间隔 / 超时判连」这类参数不存在。
- 判连靠：连接 12 s 看门狗、单次连接 5 次 socket 尝试、掉线 8 次重连、单次写 5 s 上限、耳机侧 `0x0500` 无应答 3–5 s 重置链路。
- 被动保活：电量推送每 **1–6 分钟**（耳机自己的节奏），ANC / 佩戴 / 游戏模式随时推送。
- **对移植的含义**：`HemSession` 不用写心跳定时器；断开判定靠 socket `read()<0` + 合盖特征（推送先归零，收尾 `01 00 02 00 03 00`，约 1 s 后断链）。

### 5.5 文档侧缺口（`docs/protocol-evidence.md` 已列 109 条证据，以下是「文档没写」的）✅

| 项 | 结论 |
|---|---|
| 校验 | `PROTOCOL.md:89` 明写 `No checksum.` —— 协议层无和校验/CRC，别自己加 |
| Free4 机型条目 | **PROTOCOL.md 全文零命中**（只有 Enco Free3 `066010`）；机型参数只在 `assets/models.json` 里 ✅ |
| 「三档枚举」 | 不存在。实际是「按机型 `ancModes` 的位掩码」，且 **SET 表与上报表是两套**（`0x0008`=Off、`0x0002`=ANC、`0x0100`=Transparency 仅上报） |
| ANC 值绝对偏移 | 文档只给字段顺序，未给绝对字节偏移（帧内 payload 从 index 9 起） |
| 能力位 | 没有「支持 ANC / 支持仓电量」专用位；只能用 `0x8100` 位图 + 机型 `ancModes` + 实报推断 |
| 主动断开 | 协议层没有优雅关闭命令（唯一 `disconnect` 是双连管理 `0x0429 02 <MAC>`，语义不同） |
| 写间隔 | 文档只写 `model-specific interval`，**没给毫秒数**；200 ms 是初始化节拍 |
| 配对鉴权 | 应用层配对/鉴权/加密流程未给出（`0x041C` Account key 在 §22 标 "Not built"） |

**移植必踩的四个坑（文档明确写了，代码里也有对应处理）**：
1. 能力位是**硬闸门**：位图里没有的命令不许发。
2. 订阅必须包含 `03`，否则收不到耳机侧 ANC 变更推送。
3. `01 01 02 02` 会被误判成「count 1，只有电量」→ 永远收不到佩戴推送。
4. 电量**主动推送 `01` 没有状态字节**，而查询回复 `0x8106` **有** `00` 状态字节——两个解析器不能共用。

---

## 6. 阶段计划

### Phase -1：环境（✅ 完成，除设备连接）

- [x] 克隆两仓库、记录 commit
- [x] 打通代理出网
- [x] 装 JDK 21 + Android SDK（Platform 34 / Build-Tools 34.0.0 / platform-tools）
- [x] 首次构建通过（**注意**：上游把 `*.png` 图标 gitignore 了，克隆后必崩，本仓库已补矢量图标）
- [ ] `adb devices` 能看到 Magic7 Pro —— 无设备，待机主插线

**门槛**：`./gradlew :app:assembleDebug` 成功 ✅ ／ `adb devices` 非空 ⏳

### Phase 0：基线侦察（需真机）

1. 读 `docs/magicos-recon.md`，核对 11 环注入点与类名。
2. 装原版模块，勾 5 个作用域，确认全部注入成功。
3. 记录 Free4 的 SDP 服务列表、蓝牙名、MAC。
4. 抓一次「欢律切降噪」的 btsnoop 作为对照样本。

**门槛**：5/5 进程注入成功；`setNoiseCtrlMode`（`ControlCenterNoiseHook.kt:95`）可 hook；抓到至少一组完整 ANC 请求/响应帧。

### Phase 1：协议层移植（✅ 代码完成，真机待验）

> 本阶段代码已落地：`MagicFree4/app/src/main/java/io/github/yingjfd/magicorgin/pods/hem/`
> （`HemWire` / `HemCommands` / `HemAnc` / `HemBattery`），控制器 `RfcommController` 已切换，
> 14 条 JVM 单测覆盖帧往返、分片、电量、回声帧、档位映射，全部通过。
> **未完成**：真机上的握手成功、电量与欢律一致这两条门槛。

1. 在 MagicOriG 内新建欢律协议包（建议 `pods/hem/`）：
   - `HemWire.kt` —— 帧编解码（`0xAA` + LEB128 + 分片；**无校验**）
   - `HemCommands.kt` —— 命令常量 + payload 构造（`0x0100`/`0x0103`/`0x0404`/`0x010C`/`0x0106`/`0x0109`）
   - `HemAnc.kt` —— Free4 的 slot 树 + 回声帧过滤 + bit↔档位映射
   - `HemBattery.kt` —— `0x8106` 解析（`level 0` → null，允许缺耳）
2. 改造 `pods/RfcommController.kt`：保留连接生命周期，替换帧收发/状态查询/电量解析；**对外方法签名尽量不变**。
3. 旧 `pods/Packets.kt` 退役（`0x4E`、`0x0201` 全部下线，防残留）。
4. 纯 JVM 单测：帧往返、分片重组、回声帧被拒、电量边界（0/100/充电位/缺右耳）、ANC bit↔档位。

**门槛（真机）**：单测全绿；能连 Free4 完成 `0x0100` 握手；读到当前档位与左右耳电量，与欢律一致（±1%）。

> GPL-3.0：移植代码必须保留许可证头与出处注释，仓库根放 GPL-3.0 全文。

### Phase 2：下发闭环

1. `setNoiseCtrlMode` 拦截 → 荣耀 status → 欢律 bit 映射表（独立文件 + 单测）。
2. 走原广播契约（`utils/MagicOriGAction.kt`）到蓝牙进程下发。
3. **写后回读**：下发 → 读 `0x010C` → 以读回值广播；失败报错，不假装成功。
4. QS 磁贴（`hook/QuickSettingsAncTile.kt`）三档循环，文案跟读回值。
5. 电量桥：在「设备信息出口」喂 `DeviceInfo.batteryData`（v1.1.2 做法）；`Settings.Global` 同步。
6. SWS 通知追加电量；断开清除。

**门槛**：三档听感正确、UI 与读回一致、重连显示真实档位、磁贴循环正确、通知带电量且断开消失。

### Phase 3：身份注入

1. prodId 策略：沿用 `00VVD9`（复用图标）——默认沿用，记录理由。
2. 改三处设备识别：`BluetoothUpstreamHeadsetHook.kt:426-427`、`HeadsetStateDispatcher.kt:242-244`、`HonorEarphoneHook.kt:287,291`，匹配 `OPPO Enco Free4`（含丹拿版）。
3. 模块 App 文案、自检页显示协议族 `HeyMelody`。

**门槛**：球位出现、图标正常、服务卡可开、无幽灵设备。

### Phase 4：稳定性回归

- 保护窗口**双向可读**（v1.1.3 的坑）
- 状态同步 / 高亮刷新**不下发**
- SystemUI 更新后必须重启
- 电量喂入点在设备信息出口
- 临时 root 重启后恢复步骤文档化

**门槛**：静置 100 s 零下发；连点 20 次不 ANR；重连状态正确；重启 + 重 root 后能恢复。

### Phase 5：打包合规

- `module.prop` 全键保留（含 `targetApiVersion`）
- `LICENSE` = GPL-3.0；README 标注 QuickBuds 出处 + MagicOriG MIT 声明
- 发布说明写清适用边界（11.0.0.110 / Magic7 Pro / Free4 普通版与丹拿版）

---

## 7. 真机验收总表（机主执行）

| # | 场景 | 通过标准 | Phase |
|---|---|---|---|
| A1 | 注入 | 5 进程全命中 | 0 |
| A2 | 连接 | 握手成功，无重连风暴 | 1 |
| A3 | 电量 | 左右耳与欢律一致（±1%） | 1 |
| A4 | 降噪 | 三档听感 + UI 一致 | 2 |
| A5 | 回读 | 关耳机再开显示真实档位 | 2 |
| A6 | QS | 三档循环，重启后正确 | 2 |
| A7 | 通知 | 带电量，断开即清 | 2 |
| A8 | 球位 | 图标正常可点开 | 3 |
| A9 | 离线 | 球变离线无幽灵 | 3 |
| A10 | 静置 | 100 s 零下发 | 4 |
| A11 | 压力 | 连点 20 次不 ANR | 4 |
| A12 | 重启 | 重 root 后按步骤恢复 | 4 |
| A13 | 丹拿版 | 记录支持结论（参数已确认相同） | 3 |
| A14 | 卸载 | 系统回原样 | 5 |

---

## 8. 风险

| 风险 | 触发信号 | 缓解 |
|---|---|---|
| .110 上 hook 点变化 | Phase 0 命中 <5/5 | 停手做探针定位 |
| ANC 回声帧被当真事件 | 面板莫名跳到 Light | 强制校验 `payload[1..2]==01 01` |
| 降噪档策略选错 | 点「降噪」听感偏弱/偏强 | 默认 Medium，可配置 |
| RFCOMM 被欢律抢占 | 连不上 / 频繁断 | 测试期禁用欢律 |
| GPL 合规疏漏 | 发布包无 LICENSE | Phase 5 门禁 |
| 无 SDK 卡住构建 | gradle 失败 | 先做纯 JVM 单测，代码自洽 |

---

## 9. 开放问题

1. **降噪默认档**（Light / Medium / Deep）—— 需机主决定（建议 Medium）。
2. Free4 是否上报仓电量 🔍
3. Free4 实际 SDP UUID 🔍
4. 本机是否现在装 Android SDK / adb（约 1 GB 下载）

---

## 10. 命令速查

```bash
# 出网
export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897

# 构建 / 安装
cd MagicOriG && ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 日志
adb logcat -v time -s MagicOriG:V | tee magicfree4.log

# 抓包
adb pull /data/misc/bluetooth/logs/btsnoop_hci.log

# 关键进程重启（更新模块后）
adb shell am force-stop com.android.systemui
```

---

## 11. 本鱼的自评

- **已确证**：帧格式、无校验、UUID、握手、ANC 读写与回声坑、电量布局、能力位、Free4/丹拿版参数、MagicOriG 全部关键行号。
- **仍未确证**：真机行为（Phase 0/1 靠机主）、仓电量、SDP UUID 实测、降噪默认档策略。
- **环境**：能写代码和跑纯 JVM 单测；编译 APK 需先装 SDK。
