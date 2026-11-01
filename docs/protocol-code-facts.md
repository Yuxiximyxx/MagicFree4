# 欢律协议 · 代码级取证台账

证据源：`QuickBuds` @ `8e8d3e9309cfd4f648c4ffc8f8d0bd2b61d0cc8b`（GPL-3.0）

本文件只记录**代码里能读到的**事实，每条带 `文件:行号`。文档（`docs/PROTOCOL.md`，1206 行）侧的取证另见 `docs/protocol-evidence.md`。二者冲突时以真机抓包为准。

---

## 0. 最重要的发现：Free4 已在机型库里

`QuickBuds/app/src/main/assets/models.json` 里**直接有这台机器**，不是要「按丹拿版猜」，是照抄即可：

| 机型 | productId | 位置 |
|---|---|---|
| OPPO Enco Free4 | `068C10` | `models.json:108` |
| OPPO Enco Free4（丹拿版） | `06C010` | `models.json:120` |

两者共有的关键字段（`models.json:108` / `:120` 原文）：

- `"statusQuery":"05 04 0B 11 18 06 1B 1C"` —— 连接后批量查状态的 feature 位图（两个版本**完全一致**）
- `"ancModes"`：ANC(5, slot 1) → 子档 Deep(4, slot 4) / Medium(8, slot 5) / Light(3, slot 6) / Smart(7, slot 7)；Adaptive(10, slot 11)；Transparency(2, slot 2, 子 slot 8)；Off(1, slot 0, 子 slot 3)
- `"eqKind":4`、`"autoVolume":1`、`"customEq":1`、`"hearingScan":1`
- 差异只在 EQ 预设：丹拿版多 `eqPresetsByFirmware` 的 `mode 40 / slot 8 / minFirmware 136`

> **对项目的直接影响**：ANC 三档映射与「丹拿版参数差异」不再是未知项。Free4 普通版与丹拿版**ANC 表相同**，差异只在 EQ 预设与固件门槛——MagicsFree4 只需处理 ANC/电量，因此两版可共用一套协议逻辑，仅 productId 不同。

---

## 1. 帧层（`protocol/Wire.kt`）

| 事实 | 证据 |
|---|---|
| 帧起始字节 `0xAA` | `Wire.kt:16`（`private const val START = 0xAA`） |
| 长度域 `TotalLen` 为 **LEB128**（7 bit/字节，最高位续位） | `Wire.kt:19`（`lenSize`）、`:163` 注释 |
| `TotalLen = 2 + (分片?1:0) + 数据长度` | `Wire.kt:22` |
| 单帧结构：`AA | len(1..3, LEB128) | slice | 0x00 | [fsn] | data` | `Wire.kt:26-41`（`frame()`） |
| **整帧无校验字节**（和校验/CRC 均未出现） | `Wire.kt:26-41` 逐字节写出，无 checksum 计算 |
| 消息体（data 部分）7 字节头：`cmd(2 LE) | seq(1) | payLen(2 LE)` + payload | `Wire.kt:46-49` |
| 规整后固定 9 字节头：`AA | len | slice | 00 | cmd(2LE) | seq | payLen(2LE) | payload` | `Wire.kt:71-81`（`message()`） |
| 分片语义：`0`=整条消息，`1`=首片，`2`=中间，`3`=末片 | `Wire.kt:131-133`、`:152-166`（`join()`） |
| 发送预算 `BUDGET = 660` 字节，超了才分片 | `Wire.kt:15` |
| 帧长上限 2048；非法长度丢 1 字节重扫 | `Wire.kt:135-139` |
| 帧头前的噪声字节丢弃并可上报 | `Wire.kt:108-117` |

## 2. 序号（seq）

| 事实 | 证据 |
|---|---|
| 通用计数器 `0x01..0xBF` 循环，到 0xBF 回 0x01 | `Protocol.kt:157-161`（`nextSeq()`） |
| 引擎专用区间 `0xC0..0xDF`（`0xC0 + seq % 0x20`） | `BudsConnectionManager.kt:1055-1056` |
| 固定 seq：电量查询 `0xF0`、佩戴查询 `0xF2` | `Commands.kt:258-259` |
| 少数机型（realme Buds T100）回复不回显 seq，靠 `seqless:1` 兼容 | `BudsConnectionManager.kt:209-216` |

## 3. 连接与链路（P3）

| 事实 | 证据 |
|---|---|
| SPP UUID 主：`00001107-D102-11E1-9B23-00025B00A5A5` | `Protocol.kt:33` |
| SPP UUID 备：`0000079A-D102-11E1-9B23-00025B00A5A5` | `Protocol.kt:34` |
| 连接尝试顺序：两个 UUID + 反射 `createRfcommSocket(15)`，共 5 次 | `BudsConnectionManager.kt:377-385`、`:428` |
| 全部失败后 `fetchUuidsWithSdp()` 刷 UUID，10 s 超时 | `BudsConnectionManager.kt:72`、`:479-535` |
| **只在音频链路（A2DP/HFP）起来后连 RFCOMM**；裸 ACL 仅 8 s 后兜底 | `KeepAliveReceiver.kt:15-35` |
| 链路断开（`read()<0`）即重连；关盖场景先 `disconnect()` 等开盖 | `BudsConnectionManager.kt:1680-1700` 区间（`ConnectedThread.run` 异常分支） |

## 4. 握手与初始化序列（P4）

`runInitSequence()`（`BudsConnectionManager.kt:614-669`）逐步 **blocking 写 + 200 ms 间隔**：

| 步 | 命令 | payload | 证据 |
|---|---|---|---|
| 1 | `0x0100` handshake（连上后 300 ms） | 空 | `Commands.kt:13` |
| 2 | `0x0103` 查 productId | 空 | `Commands.kt:14` |
| 3 | `0x0200` 查广播码表 | 空 | `Commands.kt:15` |
| 4 | `0x0205` 注册主动上报（无能力位时降级 `0x0201` 逐个注册） | id 列表 | `BudsConnectionManager.kt:631-639`、`Protocol.kt:187` |
| 5+ | 依次 `0x010D` status / `0x010C` ANC / `0x010C 04 01` smart 档 / `0x0130` 提示音音量 / `0x0106` 电量 / `0x0109` 佩戴 / `0x0108` 按键功能 … | 见 `Commands.kt` | `:640-658` |
| 末 | 支持 `0x012F` 批次时把上述查询合并成一帧，回复 `0x812F` | — | `:624-659`、`Protocol.kt:51-52` |

**能力位门禁**：`0x8100` 回复是「固件接受的命令位图」，HeyMelody 拒绝发位图外的命令；`ALWAYS` 白名单例外（`0x0100/0101/0102/0103/0104/0106/010B/010D/0F00/0F03/0F04`）。见 `Capabilities.kt:20-47`。

## 5. 降噪（P5 / P6）

| 事实 | 证据 |
|---|---|
| 写：`0x0404`，payload `01 01 <mask>`，mask 为**单 bit**（bit = 该档的 slot） | `Commands.kt:87-95` |
| 读当前档：`0x010C`，payload `01 01` | `Commands.kt:153`、`:158-161` |
| 读切换列表：`0x010C`，payload `02 <type>` | `Commands.kt:150-151` |
| 读 Smart 实际档：`0x010C`，payload `04 01` | `Commands.kt:156` |
| 上报/回读：`0x810C` 或 `0x0204` subType `0x03`，payload `03 01 01 <value LE,1-4B>` | `Anc.kt:8-21`、`:64-81` |
| **坑**：`0x0404` 写入本身会回声一帧 subType `0x03`，payload 是 `03 02 01 <mask>`（不是真事件）；必须校验 `payload[1]==0x01 && payload[2]==0x01` | `Anc.kt:50-69` |
| **值不是三档枚举**，是型号自己的 ANC bitmask：ANC-On 停在上次 level（`0x0010`=Deep bit4，`0x0020`=Medium bit5） | `Anc.kt:22-43` |
| modeType → 语义：1 Off / 2 Transparency / 3 ANC-Light / 8 ANC-Medium / 4 ANC-Deep / 5 ANC / 10 Adaptive / 7 ANC-Smart / 6 Voice(按 Transparency 显示) | `AncModes.kt:55-66` |
| 上报值查表规则：`numberOfTrailingZeros(raw)` → 树里查；查到 `ANC` 父节点时用「当前 level」或最低档 | `AncModes.kt:38-43` |
| 未知机型 = 空表 = 拒绝设置（防止静默设错档） | `AncModes.kt:105-118` |

**Free4 的 slot 表**（`models.json:108`，与丹拿版一致）：

```
ANC(5,slot1) ├ Deep(4,slot4) ├ Medium(8,slot5) ├ Light(3,slot6) └ Smart(7,slot7)
Adaptive(10,slot11)
Transparency(2,slot2) └ child(slot8)
Off(1,slot0) └ child(slot3)
```

> 因此 Free4 的「降噪 / 通透 / 关闭」三档 SET bit 分别是 **4 或 5 或 6（取决于选哪个降噪档）/ 2 / 0**。⚠️ 待真机确认默认优先哪个降噪档（QuickBuds UI 提供分级，荣耀设备中心只有一档「降噪」，需定策略：固定 Medium=5 或记住上次档位）。

## 6. 电量（P7）

| 事实 | 证据 |
|---|---|
| 查询：`0x0106`（固定 seq `0xF0`） | `Commands.kt:250-251`、`:258` |
| 回复 `0x8106`：payload `[status=00][count][idx, raw]...` | `Battery.kt:13-27` |
| idx：`1`=左、`2`=右、`3`=仓 | `Battery.kt:16`、`:36-40` |
| 解析：`level = raw & 0x7F`，`charging = (raw & 0x80) != 0` | `Battery.kt:17`、`:35` |
| `level == 0` 视为**未知**（Air7 Pro 关盖），按 null 处理 | `Battery.kt:18`、`:35` |
| 主动上报：`0x0204` subType `0x01`，payload `[01][count][idx,raw]...` | `Battery.kt:45-75` |
| 佩戴/在仓：查询 `0x0109`（seq `0xF2`）→ `0x8109`；`st==4` = 在仓 | `Battery.kt:80-99`、`:101` |
| 产品 ID：`0x0103` → `0x8103`，payload `00 <id 3B LE>` | `Capabilities.kt:64-74` |

## 7. 能力位（P8）

| 事实 | 证据 |
|---|---|
| `0x8100` payload `00 <bitmap>`，bit n → 命令集合 | `Capabilities.kt:54-62` |
| 位图缺命令 ⇒ UI 隐藏该行（同 HeyMelody） | `Capabilities.kt:76-79` |
| `0x810D` 状态回复列出固件支持的 `0x0403` 开关 id | `Capabilities.kt:87-103` |

## 8. 写入路径与并发（P11）

| 事实 | 证据 |
|---|---|
| 所有写最终走 `ConnectedThread.write()`：`outputStream.write` + `flush` | `BudsConnectionManager.kt:1627-1633` |
| `sendRaw()` 起独立线程；`sendRawBlocking()` 在当前线程 | `BudsConnectionManager.kt:1546-1580` |
| 初始化序列靠**调用方 `delay(200)`** 拉开间隔 | `BudsConnectionManager.kt:618-659` |
| 代码中**没有**全局写队列/最小间隔常量 | 全仓 grep 无结果（本鱼实测） |
| 先写后记日志（避免快速 ACK 与 TX 日志顺序倒转） | `BudsConnectionManager.kt:1560-1575` 注释 |

## 9. 心跳（P9）——代码侧未见

- `BudsConnectionManager.kt` 与 `KeepAliveReceiver.kt` 中**没有**周期性心跳/status 轮询定时器（grep `heartbeat`/`POLL` 无命中）。
- 保活实际靠：`ACTION_ACL_CONNECTED` / A2DP 状态广播触发连接（`KeepAliveReceiver.kt:15-35`），以及断链后 `reconnectAfterLoss()` 递增延迟重连（`BudsConnectionManager.kt:671-690`）。
- 🔍 PROTOCOL.md 是否写了协议级心跳，待 `docs/protocol-evidence.md` 核实。

## 10. 主动上报子类型（0x0204）汇总

| subType | 含义 | 证据 |
|---|---|---|
| `0x01` | 电量 | `Battery.kt:8` |
| `0x02` | 佩戴/在仓 | `Protocol.kt:111` |
| `0x03` | ANC 变化 | `Anc.kt:48` |
| `0x04` | 贴合检测结果 | `Protocol.kt:113` |
| `0x05` | 游戏模式 | `Protocol.kt:112` |
| `0x08` | Golden Sound 状态 | `Protocol.kt:117` |
| `0x0E` | 耳道扫描 | `Protocol.kt:118` |
| `0x0F` | 公共事件（bit0 提示音音量 / bit4 敲击等级） | `Protocol.kt:114-116` |

## 11. 尚未取证（阻塞 Phase 1 的部分）

1. **P9 协议级心跳**：代码没有，文档待核。
2. **降噪默认档策略**：设备中心只有一个「降噪」按钮，Free4 有 Light/Medium/Deep 三级 —— 取哪一级需要产品决策（建议 Medium=bit5，与 `Anc.kt:31` 的实测「ANC on → 0x0020」一致）。
3. **仓电量**：Free4 是否在 `0x8106` 里返回 idx=3，需真机读一次。
4. **SPP UUID 是否需要按 Free4 实测**：主/备 UUID 是 OPPO 家族通用，Free4 大概率相同，但要以真机 SDP 为准。
5. `statusQuery`（`05 04 0B 11 18 06 1B 1C`）每个 slot 的具体字段含义，待 PROTOCOL.md §对应章节核实。
