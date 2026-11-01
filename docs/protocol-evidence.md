# 取证台账：QuickBuds 耳机线协议（PROTOCOL.md）

## 0. 取证元信息

| 项 | 值 |
|---|---|
| 唯一事实来源 | `QuickBuds/docs/PROTOCOL.md`（1206 行） |
| 辅助核对 | `QuickBuds/docs/PACKET-CAPTURE.md`（209 行，仅用于交叉核对，未提供协议字段事实） |
| 取证方式 | 全文逐段通读（1-1206 行），并对 `Free4` / `丹拿` / `Dynaudio` / `interval` / `keep-alive` / `watchdog` 等关键词做全文检索 |
| 引用格式 | `PROTOCOL.md:行号`；多行为 `:起-止` |
| 不确定项 | 一律写「文档未给出」，不做折算、不推算字节偏移、不补枚举 |

**文档自述的证据分级（原样抄录，`PROTOCOL.md:13-17`）：**

> **Evidence tags:**
> - `[VENDOR]`: HeyMelody's behaviour, or realme Link's where a line says so. The project studied the vendor apps for interoperability only.
> - `[CAPTURE]`: a frame in a capture on our own buds.
> - `[TESTED]`: a manual test on our own buds.
> - `[GUESS]`: unverified.

`PROTOCOL.md:19`：

> Never promote a claim without evidence. Never remove a `[GUESS]` tag to tidy up.

**文档声明的测试设备（`PROTOCOL.md:21`）：**

> **Test device:** OnePlus Buds 4, firmware `B4.1-260810-1153` (HeyMelody shows `138.138.105`). Other models differ in feature ids, button ids and ANC bits. "Wired, unverified" means the app sends it but no owner of that model has read a write back.

**文档声明的协议版本（`PROTOCOL.md:3`、`:5-6`）：**

> Matches QuickBuds 5.4.5 (version code 52).

> This is the wire format of the OPPO / OnePlus / realme earbud RFCOMM link.

---

## P1 帧头魔数、长度域位置与含义、序号/流水号字段

### 结论

- 帧头魔数是单字节 `AA`；完整帧为 `AA <TotalLen> <Slice> 00 [<Fsn>] <Cmd LE> <Seq> <PayLen LE> <Payload...>`。
- 长度域紧跟在 `AA` 之后（第 2 字节起），长度 **1-3 字节、LEB128**（每字节 7 位、低位在前、bit7 置位表示还有后续字节），语义是**统计它自身之后的全部字节数**。
- 整包帧 `TotalLen = 7 + PayLen`；分片帧 `TotalLen = 3 + 分片数据长度`。
- 分片有独立的 1 字节 `Fsn`（分片计数器，从 `00` 起），整包帧没有该字段。
- 序号字段 `Seq` 是 1 字节，由发送方挑选，回复原样回显；`FF` 表示耳机主动推送。
- 归一化后帧头固定 9 字节：**cmd 在 4-5、seq 在 6、payload length 在 7-8、payload 从索引 9 开始**。

### 原文与行号

**E-P1-01**（`PROTOCOL.md:63`）整包帧格式：

> ```
> AA  <TotalLen>  <Slice> 00  [<Fsn>]  <Cmd LE>  <Seq>  <PayLen LE>  <Payload...>
> ```

**E-P1-02**（`PROTOCOL.md:68-76`）字段表（原样抄录）：

> | Field | Size | Meaning |
> |---|---|---|
> | `AA` | 1 | Frame start |
> | TotalLen | 1-3 | LEB128: 7 bits per byte, low bits first, bit 7 set = more bytes. Counts every byte after itself |
> | Slice | 1 | `00` whole message, `01` first slice, `02` middle slice, `03` last slice `[VENDOR]` |
> | `00` | 1 | Always zero |
> | Fsn | 1 | Slice counter, from `00`. Present in every slice, absent in a whole message `[VENDOR]` |
> | Cmd | 2 | Command id, little endian |
> | Seq | 1 | Picked by the sender; the reply echoes it. `FF` = a push from the buds `[CAPTURE]` |
> | PayLen | 2 | Payload length, little endian |
> | Payload | PayLen | The message body |

**E-P1-03**（`PROTOCOL.md:86`）长度计算公式：

> - Whole message: `TotalLen = 7 + PayLen`. Slice: `TotalLen = 3 + slice length` `[CAPTURE]`.

**E-P1-04**（`PROTOCOL.md:87`）LEB128 语义与实例（十六进制照抄）：

> - **TotalLen is standard LEB128** `[CAPTURE]`: one byte below 128, else 7 bits per byte, low first, high bit = more. A length that subtracts 1 first does not fit. A 124-byte payload goes out as `AA 83 01` (131). A 250-byte reply arrives as `AA 81 02` (257). Golden Sound (section 20) uses frames over 127 bytes.

**E-P1-05**（`PROTOCOL.md:88`）归一化后的固定偏移（关键：移植时的切片位置）：

> - The framer normalises every frame to a one-byte length. After that the header is a fixed 9 bytes: **cmd at 4-5, seq at 6, payload length at 7-8, payload from index 9**. A wrong offset gives garbage, not an error.

**E-P1-06**（`PROTOCOL.md:100`）分片帧头与 `Fsn` 的存在条件：

> - `Fsn` (slice counter, from `00`) is present in every slice, never in a whole message. `TotalLen` is then `3 + data`, not `2 + data`.

**E-P1-07**（`PROTOCOL.md:102-103`）长度合法区间与噪声丢弃阈值：

> - A slice frame has `TotalLen` 4 or more. A whole message has `TotalLen` 7 or more.
> - The receiver reads a byte stream, not separate reads. It discards bytes before `AA`, reads `TotalLen`, and waits for that many bytes. The Kotlin framer treats a `TotalLen` below 4 (slice) or 7 (whole message) as noise: it drops the `AA` and searches on. It drops a frame above 2048 bytes. The core framer drops a `Len` below 2 or above 4096.

**E-P1-08**（`PROTOCOL.md:74`）`Seq` 语义（回显 / `FF` 推送 / 推送示例）：

> ```
> AA 0C 00 00 04 02 FF 05 00 03 01 01 08 00   ANC push (Seq FF)
> ```

（注：上行引自 `PROTOCOL.md:83`，是 `[CAPTURE]` 示例块中的一行，`Seq` 为 `FF`。）

**E-P1-09**（`PROTOCOL.md:116-123`）序号分配区间表（原样抄录）：

> | Seq | Use |
> |---|---|
> | `00` | The status poll `0x010D`, and the connect and plain status reads |
> | `01`-`BF` | The generic counter of the other commands |
> | `C0`-`DF` | The feature-sync engine; wire seq = `C0 + (n mod 0x20)` |
> | `F0` | The battery query `0x0106` |
> | `F2` | The wearing query `0x0109` |
> | `FF` | A push from the buds |

**E-P1-10**（`PROTOCOL.md:78-84`）完整帧实例（十六进制照抄，含 3 条）：

> ```
> AA 07 00 00 00 01 01 00 00                  handshake 0x0100, PayLen 0
> AA 0A 00 00 04 04 06 03 00 01 01 01         set ANC Off
> AA 0C 00 00 04 02 FF 05 00 03 01 01 08 00   ANC push (Seq FF)
> ```

---

## P2 校验方式（和/CRC8/CRC16）与覆盖范围

### 结论

- **文档明确写：没有校验和（No checksum）。** 帧层不存在和校验、CRC8 或 CRC16。
- 覆盖范围问题因此**无对象**：文档未给出任何帧级校验覆盖范围，因为文档声明不存在帧级校验字段。
- 文档中 `crc` 字样只出现在 **`0x0121` 音色列表字典结构**里（`<crc u32>`，属于音色文件元数据，不是帧校验）。

### 原文与行号

**E-P2-01**（`PROTOCOL.md:89`）唯一直接结论：

> - No checksum.

**E-P2-02**（`PROTOCOL.md:88`）文档给出的替代性完整性判断只有"偏移错了就出垃圾"：

> A wrong offset gives garbage, not an error.

**E-P2-03**（`PROTOCOL.md:311`）`crc` 一词在全文的唯一出现位置（音色列表，非帧校验）：

> | `0x0121` | Tone list | — → `00 <count>` then `<id u32> <valid> <crc u32> <selected> <nameLen> <name>` | 22 |

**E-P2-04**（`PROTOCOL.md:1140`）同上，`0x0121` 的布局在 §22 重复一次：

> | `0x0121` | — | `00 <count>` then `<id u32> <valid> <crc u32> <selected> <nameLen> <name>` | Tone list. In the Buds 4 batch. |

**E-P2-05** 未给出项：CRC8 / CRC16 / 校验和覆盖范围 / 校验失败的处置 —— **文档未给出**（全文检索无 CRC8、CRC16、checksum 的其他出现）。

---

## P3 RFCOMM/SDP 服务 UUID 与通道号

### 结论

- 传输层是 **经典蓝牙 RFCOMM/SPP，非 BLE**，单条长连接。
- UUID 有两个，顺序固定：**先 `0000079A-D102-11E1-9B23-00025B00A5A5`**（Buds 4 上可用），**再 `00001107-D102-11E1-9B23-00025B00A5A5`**（Buds 4 上从不连上，约 5 s 超时）。
- 连接顺序是四个组合：`079A` secure → `1107` secure → `079A` insecure → `1107` insecure；之后**反射方式拿 RFCOMM 通道 15**。
- 桌面端从**耳机的 SDP 记录**里取通道号。
- 连接预算：12 s 看门狗关闭阻塞中的连接；一次连接总共 5 次 socket 尝试后停止；掉线后 8 次重连。

### 原文与行号

**E-P3-01**（`PROTOCOL.md:41`）传输层定性：

> Classic Bluetooth **RFCOMM / SPP**, not BLE. One long-lived socket. The buds push events at any time.

**E-P3-02**（`PROTOCOL.md:43-46`）UUID 表（原样抄录）：

> | UUID | Use |
> |---|---|
> | `0000079A-D102-11E1-9B23-00025B00A5A5` | Tried first. The one that works on Buds 4 `[CAPTURE]`, as HeyMelody does `[VENDOR]` |
> | `00001107-D102-11E1-9B23-00025B00A5A5` | Tried second. Never connects on Buds 4 (~5 s timeout); kept for other models |

**E-P3-03**（`PROTOCOL.md:48-51`）连接顺序、通道号、看门狗、尝试次数：

> The connect order is both UUIDs secure first, then both insecure: `079A` secure, `1107` secure, `079A`
> insecure, `1107` insecure. Then RFCOMM channel 15 by reflection. A 12 s watchdog closes a connect that
> blocks. A connect makes 5 socket tries in total, then stops. A lost link gets 8 reconnect tries `[CAPTURE]`. The desktop app tries
> the two UUIDs in the same order and takes the channel from the buds' SDP record.

**E-P3-04**（`PROTOCOL.md:53`）合盖结束 socket：

> A case lid close ends the socket (section 13) `[CAPTURE]`.

**E-P3-05**（`PROTOCOL.md:55-56`）代码位置：

> **Code:** `protocol/Protocol.kt` holds the UUID constants. The socket code is in `bluetooth/`.
> The core crate has no transport code.

**E-P3-06**（`PACKET-CAPTURE.md:99`）交叉核对：RFCOMM 通道号逐连接变化，不能用来过滤（说明"通道 15"是发起侧取值，抓包侧不稳定）：

> - Do not filter on `btrfcomm.channel`: it changes per connection.

---

## P4 握手/鉴权/初始化序列

### 结论

- 初始化是"**等待 300 ms，然后每 200 ms 一条消息**"的固定序列，全部 `[CAPTURE]`（Buds 4，两个 app 一致）。
- 序列：`0x0100` 握手 → `0x0103` 产品 id → `0x0200` 广播码 → `0x0205` 订阅 → `0x010D` 特性 → `0x010C 01 01` ANC → `0x010C 04 01` Smart 档（Smart 机型）→ `0x0130` → `0x0106` 电量 → `0x0109` 佩戴 → `0x0108` 手势表 → `0x010C 02 01` 保持循环 → `0x0105` 固件 → `0x012A` 空间类型 → `0x0123` 编解码列表 → `0x0114` 编解码 → `0x012B` 游戏音效 → `0x0134` 头部手势 → `0x041E 00` 3D 音频源。
- 握手 `0x0100` 的回复就是**能力位图**（`00` + 按位低有效字节），此后**每条命令都要过能力位闸门**："没有对应位就不发"。
- 若能力位 56 存在，订阅之后的读全部合并进一条 `0x012F` 批量查询；之后的单次状态读单独发。
- 初始化后**不再轮询**。
- 鉴权/配对：文档只提到"secure / insecure"两种 socket 尝试，**没有**任何配对、密钥、账号鉴权的握手流程 —— 该部分**文档未给出**（`0x041C` Account key 出现在 §22 "Not built"，见 E-P4-09）。

### 原文与行号

**E-P4-01**（`PROTOCOL.md:243`）时序基线：

> Init sequence `[CAPTURE]` (Buds 4, both apps): wait 300 ms, then one message every 200 ms.

**E-P4-02**（`PROTOCOL.md:246-252`）完整初始化序列（顺序照抄）：

> ```
> 0x0100 handshake → 0x0103 product id → 0x0200 broadcast codes → 0x0205 subscribe →
> 0x010D features → 0x010C 01 01 ANC → 0x010C 04 01 Smart level (Smart models) →
> 0x0130 alert volume → 0x0106 battery → 0x0109 wear → 0x0108 gesture table →
> 0x010C 02 01 hold cycle (02 03 / 02 04 on per-bud holds) → 0x0105 firmware →
> 0x012A spatial type → 0x0123 codec list → 0x0114 codec → 0x012B game sound →
> 0x0134 head motion → 0x041E 00 3D audio on the buds (spatialToggle models)
> ```

**E-P4-03**（`PROTOCOL.md:254-258`）批量合并与读序：

> With bitmap bit 56, every read after the subscription goes out in one `0x012F` frame. A later status
> read goes alone. The read order starts with `0x010D`, `0x010C 01 01` and `0x010C 04 01` on Smart
> models. Then `0x0130`, `0x0106`, `0x0109`, `0x0108` and the hold-cycle reads. Then `0x0105`,
> `0x012A`, `0x0123`, `0x0114`, `0x012B` and `0x0134`. A model with `spatialToggle` gets `0x041E 00`
> after the reads.

**E-P4-04**（`PROTOCOL.md:260-261`）初始化后不轮询：

> The app does not poll after that. Battery, wearing, ANC and game mode arrive as pushes `[TESTED]`.
> Was: a keep-alive poll. Is: none. No stale link seen `[TESTED]`.

**E-P4-05**（`PROTOCOL.md:265`）握手回复即能力位图，且是硬闸门：

> - **Bitmap** `0x8100` (section 5) `[VENDOR]`: `00`, then bytes read LSB first. Bit n enables a fixed list of commands. The app checks every command against the bitmap. It does not send a command with no bit.

**E-P4-06**（`PROTOCOL.md:270-274`）订阅请求的实际内容与拒绝处理：

> - **Subscribe** `0x0205 <count> <codes>`, count first `[CAPTURE]`. The app asks for `01 02 03`. It adds `04` when the bitmap has `0x0405`, `08` when it has `0x040D`, and `0B` when it has `0x0412`. It keeps only the codes the buds offered, as the vendor app registers exactly the offered list `[VENDOR]`.

> - The `0x8205` ack: `01 02 01 00 02 00` and, for five ids, `01 05 01 00 02 00 03 00 04 00 08 00` (Buds 4): `01 <count>`, then `<id> 00` per id `[GUESS]`. Fewer ids than asked means a part was rejected.

**E-P4-07**（`PROTOCOL.md:271-272`）两个已知陷阱（移植必看）：

> - Never send `01 01 02 02`: that is "count 1, battery only". The buds accept it and never push wearing.
> - Never drop `03` when offered. Without it Buds 4 sends no ANC change made on the buds.

**E-P4-08**（`PROTOCOL.md:476-479`）耳机反向请求 `0x0500` 必须应答，否则部分固件会重置链路（属于初始化的一部分）：

> It resets the link 3-5 s after connect, every time, if no answer comes.
> Every answer repeats the request's `Seq` and sets bit 15 of its command.

**E-P4-09**（`PROTOCOL.md:1112`）"鉴权类"命令在文档中被列为未实现（说明它不是初始化必需环节）：

> | `0x041C` | Account key (`01 01 <len> <key>` binds, `02` clears) |

**E-P4-10** 未给出项：应用层配对/绑定/密钥鉴权流程、加密、登录态 —— **文档未给出**。

---

## P5 降噪三档枚举值与语义映射（降噪/通透/关闭）

### 结论

- 文档**没有**给"三档枚举"这种形态；它给的是一套**按机型树（`ancModes`）的位掩码**：`slot` 决定 `SET` 用哪一位，而**上报值属于另一张表**。
- Buds 4 的 `SET` 表（写 `0x0404 01 01 <mask>`）：**关闭 Off = bit 0 → `01 01 01`；降噪 ANC = bit 1 → `01 01 02`；通透 Transparency = bit 2 → `01 01 04`**；另有多档 Deep=4、Medium=5、Light=6、Smart=7、Adaptive=11。
- Buds 4 的**上报表**是另一套：`0x0008`=Off、`0x0002`=ANC 通用、`0x0100`=Transparency、`0x0200`=带语音增强的通透、`0x0800`=Adaptive。**两张表不能互抄**。
- realme 机型：写表与上报表**相同**，Off `01`、Transparency `02`、Light `04`、Deep/普通 ANC `08`、Medium `10`、Smart `20`。
- 因此"降噪/通透/关闭 三档"的**统一枚举值：文档未给出**；能给的只是上述按机型/按表的位掩码。

### 原文与行号

**E-P5-01**（`PROTOCOL.md:553`）ANC 命令族总述：

> Query `0x010C <what> <arg>`, reply `0x810C 00 <what> <arg-or-kind> <value>`, push `03`, write `0x0404`.

**E-P5-02**（`PROTOCOL.md:557`）当前模式读：

> | `010C 01 01` | `01 01 <mode LE, 1-4 bytes>` | Current mode, a bitmask (report table below) `[CAPTURE]` |

**E-P5-03**（`PROTOCOL.md:566-580`）SET 表（Buds 4，原样抄录；十六进制照抄）：

> ### SET — `0x0404 01 01 <mask>`
>
> One bit set `[CAPTURE]`, `bit / 8 + 1` mask bytes, low byte first `[CAPTURE]`. The bit is the mode's `slot` in the
> model list, never the mode's position in a list.
>
> | Buds 4 mode | Bit | Payload |
> |---|---|---|
> | Off | 0 | `01 01 01` |
> | ANC (last level) | 1 | `01 01 02` |
> | Transparency | 2 | `01 01 04` |
> | Deep | 4 | `01 01 10` |
> | Medium | 5 | `01 01 20` |
> | Light | 6 | `01 01 40` |
> | Smart | 7 | `01 01 80` |
> | Adaptive | 11 | `01 01 00 08` |

**E-P5-04**（`PROTOCOL.md:582-583`）Adaptive 是 bit 11 而非 bit 8：

> Adaptive is bit 11 (mask `0x0800`), not bit 8. Bit 8 gives `01 01 00 01`, a different mode. Take the bit
> from the model's `slot`, never from a mode's position in a list.

**E-P5-05**（`PROTOCOL.md:587-596`）上报表（原样抄录）：

> The report table is not the SET table `[CAPTURE]`. A report names a child of the mode that was set `[CAPTURE]`:
>
> | Raw (LE) | Mode |
> |---|---|
> | `0x0008` | Off |
> | `0x0002` | ANC, generic |
> | `0x0010` `0x0020` `0x0040` `0x0080` | Deep, Medium, Light, Smart |
> | `0x0100` | Transparency |
> | `0x0200` | Transparency with voice enhance |
> | `0x0800` | Adaptive |

**E-P5-06**（`PROTOCOL.md:598`）值是位掩码不是枚举：

> - The ANC stop reports whichever level is active. The value is a bitmask, not an enum.

**E-P5-07**（`PROTOCOL.md:599`）模式 id 枚举（与 slot 不同）：

> - The mode tree is per model (`ancModes` in the model list): `mode`, `slot`, `variants` `[VENDOR]`. Mode ids: `1` Off, `2` Transparency, `3` Light, `8` Medium, `4` Deep, `5` noise reduction, `7` Smart, `10` Adaptive, `6` transparency with voice.

**E-P5-08**（`PROTOCOL.md:603`）realme 机型：写表即上报表：

> - **realme models** `[VENDOR]`: the write table and the report table are the same. Off `01`, Transparency `02`, Light `04`, Deep and plain ANC `08`, Medium `10`, Smart `20`. The Air7 Pro's owner confirms them `[TESTED]`. The others are unverified. The Air7 Pro log's `0x810C 01 01` value `02` = Transparency.

**E-P5-09**（`PROTOCOL.md:990`）Buds 4 的树（slot 逐项列出）：

> - Buds 4 `065414` tree: `Off 0`, `ANC 1`, `Transparency 2`, `Deep 4`, `Medium 5`, `Light 6`, `Smart 7`, `Adaptive 11` `[CAPTURE]`. Off has a report child at 3. Transparency has one at 8.

**E-P5-10**（`PROTOCOL.md:629-631`）"两张表、三条规则"：

> - An ANC change raises push `03`. Subscribe to `03`; without it, the change is silent.
> - SET and report use different bits. Do not copy a report value into a SET payload.
> - Adaptive is bit 11. Bit 8 is a different mode.

**E-P5-11** 未给出项：全局统一的"降噪/通透/关闭 = 数值 X/Y/Z"三档枚举 —— **文档未给出**（值一律依赖机型的 `ancModes` 树与"写表/上报表"之别）。

---

## P6 ANC 读命令与响应字段偏移

### 结论

- 读命令：`0x010C <what> <arg>`；读当前模式为 **`0x010C 01 01`**。
- 响应（`0x810C`）的字段**按顺序**为：`00`（状态）+ 回显 `<what>` + `<arg-or-kind>` + `<value>`；原文档对 `0x010C 01 01` 明确写成 `00 01 01 <mode LE>`（状态、回显、值）。
- 值本身是 **1-4 字节的小端位掩码**。
- 帧级绝对偏移由固定头给出：**cmd 在 4-5、seq 在 6、paylen 在 7-8、payload 从 9 开始**；ANC 的数值在 payload 内的**序号位置是第 4 个字节起**（`00`→`what`→`arg`→`value`），但**文档没有给出"value 位于帧第 12 字节"这类绝对偏移** —— 属于**文档未给出**，本台账不作推算。
- 主动上报为 push `03`，且**只有代码后两字节为 `01 01` 时才当模式**；`03 02 <kind> <mask>` 是保持循环掩码，不是模式。

### 原文与行号

**E-P6-01**（`PROTOCOL.md:557`）读命令与响应形状：

> | `010C 01 01` | `01 01 <mode LE, 1-4 bytes>` | Current mode, a bitmask (report table below) `[CAPTURE]` |

**E-P6-02**（`PROTOCOL.md:562`）响应字段顺序（状态/回显/值）：

> The query `0x010C 01 01` returns `00 01 01 <mode LE>` (status, echo, value) `[CAPTURE]`. Read it on every
> connect, so a reconnect corrects the display. The vendor app also reads `01 05 <mask>` as a mode mask.
> QuickBuds has not seen it and does not decode it `[GUESS]`.

**E-P6-03**（`PROTOCOL.md:553`）通用响应布局 `0x810C 00 <what> <arg-or-kind> <value>`：

> Query `0x010C <what> <arg>`, reply `0x810C 00 <what> <arg-or-kind> <value>`, push `03`, write `0x0404`.

**E-P6-04**（`PROTOCOL.md:558-559`）另两条 ANC 读：

> | `010C 02 <kind>` | `02 <kind> <mask LE>` | Modes the hold cycles. `kind` `01` shared, `03` left, `04` right `[CAPTURE]` |
> | `010C 04 01` | `04 01 <mask LE>` | The level Smart chose, in SET bits `[CAPTURE]` |

**E-P6-05**（`PROTOCOL.md:88`）帧层偏移（决定 payload 起点）：

> **cmd at 4-5, seq at 6, payload length at 7-8, payload from index 9**

**E-P6-06**（`PROTOCOL.md:428`）push `03` 的载荷布局：

> | `03` | ANC | `01 01 <mode LE, 1-4 bytes>` current mode; `02 <kind> <mask LE>` hold-cycle mask; `04 01 <mask LE>` the level Smart chose `[CAPTURE]` |

**E-P6-07**（`PROTOCOL.md:605-606`）push 判定规则与历史 bug：

> - A push is a mode only when its two bytes after the code are `01 01`. A hold-cycle write also raises `03 02 <kind> <mask>`: that is the new cycle mask, not a mode. In the Buds 4 capture `<kind>` is `01` `[CAPTURE]`.
> - `AncEventParser` accepts a push only when bytes 1-2 are `01 01`. Was: the app decoded the hold-cycle push as a mode, and the display stuck on a false mode. Is: the app treats it as the hold-cycle mask.

**E-P6-08**（`PROTOCOL.md:604`）无耳佩戴时读回真实模式（响应语义补充）：

> - With no bud in an ear, the buds refuse a SET. The reply `0x8404` has status `0E` `[CAPTURE]`. One bud in an ear is enough. The app then reads `0x010C 01 01` again, so the display shows the real mode, and says to put a bud in.

**E-P6-09** 未给出项：ANC 值字段的**绝对帧字节偏移**（如"第 12 字节"）—— **文档未给出**。

---

## P7 电量命令与响应布局（左/右/仓、高/低位、充电标志）

### 结论

- 查询 `0x0106`，回复 `0x8106`，推送 `01`（push `0x0204` 事件码 `01`）；查询使用固定 `Seq = F0`。
- 布局：查询回复 `00 <count> <part> <raw>...`；推送 `<count> <part> <raw>...`（**推送没有状态字节**）。
- `part`：**`01` 左、`02` 右、`03` 仓**。
- 位含义：**电量 = `raw & 7F`，充电 = `raw & 80`**（高 1 位是充电标志，低 7 位是百分比）。
- 实例 `03 01 64 02 64 03 50`。
- 电量 `0` 表示"未知"，app 会隐藏该部件；合盖时仓与仓内耳机不上报；推送节奏由耳机自定，**1-6 分钟**一次。

### 原文与行号

**E-P7-01**（`PROTOCOL.md:519-521`）命令与三种报文形态：

> **Battery** — query `0x0106`, reply `0x8106`, push `01`. Reply: `00 <count> <part> <raw>...`. Push:
> `<count> <part> <raw>...` `[CAPTURE]`. A count of 0, or a list that runs past the end, is no list.
> The parsers ignore the message and clear nothing.

**E-P7-02**（`PROTOCOL.md:523`）`part` 与位域（核心）：

> - `part`: `01` left, `02` right, `03` case. Level = `raw & 7F`, charging = `raw & 80`. Example: `03 01 64 02 64 03 50`.

**E-P7-03**（`PROTOCOL.md:524`）电量 0 的语义：

> - Level `0` means "not known": the app leaves that part out. The realme Buds Air7 Pro sends the case at `0` with the lid closed `[CAPTURE]` (issue #9). HeyMelody hides a part at `0` `[VENDOR]`.

**E-P7-04**（`PROTOCOL.md:525`）缺失部件保持旧值：

> - A part left out keeps its last level unless the "Hide old battery levels" setting is on.

**E-P7-05**（`PROTOCOL.md:526`）合盖时只报仓外耳机：

> - The case, and a bud inside it, report only while the lid is open. With the lid closed, the query returns only the bud outside (`01 01 5A`) and the buds push nothing `[CAPTURE]`. The app does not show case charging, by decision.

**E-P7-06**（`PROTOCOL.md:527`）推送节奏：

> - Pushes come every 1-6 minutes on the buds' own clock, not tied to `0x0500` `[CAPTURE]`.

**E-P7-07**（`PROTOCOL.md:121`）电量查询的固定序号：

> | `F0` | The battery query `0x0106` |

**E-P7-08**（`PROTOCOL.md:295`）查询表里的同一布局：

> | `0x0106` | Battery | — → `00 <count> <part> <raw>...` | 13 |

**E-P7-09**（`PROTOCOL.md:1068`）机型侧交叉核对（Buds 4）：

> - OnePlus Buds 4 `065414`: case and a bud inside report only with the lid open `[CAPTURE]`; closed lid returns only the outside bud `01 01 5A` `[CAPTURE]`.

---

## P8 能力位（Capabilities）含义，如何判断"支持 ANC""支持仓电量"

### 结论

- 能力位 = **`0x0100` 握手回复的位图**（`0x8100`）：首字节 `00`，其后字节**按最低位优先**逐位解释；第 n 位置位表示一组固定命令可用；位表共 **69 位**。
- 闸门语义：**没有对应位就绝不下发该命令**。另外有一组"始终启用"的命令：`0x0100`-`0x0104`、`0x0106`、`0x010B`、`0x010D`、`0x0F00`、`0x0F03`、`0x0F04`。超出耳机位图长度的位一律视为 0。
- 判断"支持 ANC"：文档里 ANC 走 `0x010C`（读）/`0x0404`（写），二者同属**位 8**；同时还需机型有 `ancModes` 树（无树的机型"没有噪声控制"）。**文档未给出一个名为 "supports ANC" 的专用能力位**，只有上述命令位 + 机型树两个条件。
- 判断"支持仓电量"：文档**没有**独立的仓电量能力位；电量命令 `0x0106` 属于"始终启用"且对应位 1。仓电量是否存在只能从**上报里出现的 `part 03`** 及机型行为判断；`0x8200` 提供的事件码 `01`（电量）也不区分仓。→ "专用能力位：**文档未给出**"。

### 原文与行号

**E-P8-01**（`PROTOCOL.md:154-156`）位图格式与"始终启用"集合：

> The reply to `0x0100` is `00` and then bytes read least significant bit first. Bit n, when set, enables the commands
> in the table. The vendor app refuses a command with no bit and never sends it. Always enabled: `0x0100`-`0x0104`,
> `0x0106`, `0x010B`, `0x010D`, `0x0F00`, `0x0F03`, `0x0F04`.

**E-P8-02**（`PROTOCOL.md:158-159`）位表长度与 Buds 4 实测位图（十六进制照抄）：

> The table has 69 bits. Bits past the buds' bitmap length are off `[VENDOR]`. Buds 4 `[CAPTURE]`:
> `00 FF 77 5A EA 67 0E 20 07`. That is every command the app uses, not `0x0422` / `0x012A`.

**E-P8-03**（`PROTOCOL.md:171`）ANC 相关命令所在的位（关键）：

> | 8 | `0x010C` `0x0404` |

**E-P8-04**（`PROTOCOL.md:164`）电量命令所在的位：

> | 1 | `0x0106` |

**E-P8-05**（`PROTOCOL.md:265`）闸门语义复述：

> The app checks every command against the bitmap. It does not send a command with no bit.

**E-P8-06**（`PROTOCOL.md:993`）"支持 ANC"的第二个条件（机型树）：

> - A model with no `ancModes` (79 of 143 in `models.json`) gets no noise control. Before detection, the app uses the Buds 4 tree `[VENDOR]`.

**E-P8-07**（`PROTOCOL.md:233-235`）位图有位 ≠ 会用（payload 未知）：

> Bits 35, 64, 67 and 68 enable `0x0419`, `0x0136`, `0x0433`, `0x0432`, `0x0135`, `0x0435` and `0x0117` `[VENDOR]`.
> QuickBuds never sends these, because their payloads are unknown.

**E-P8-08**（`PROTOCOL.md:269-270`）另一层"能力"是事件码清单 `0x8200`（不是命令位图）：

> - **Offered codes** `0x8200` = `00 <count> <codes>`. Buds 4 `[CAPTURE]`: `00 09 01 02 03 04 08 0B F1 F2 F3`. Read it before deciding that an event does not exist: silence usually means "not subscribed".

**E-P8-09**（`PROTOCOL.md:730`）位图有位 ≠ 功能可用（反例，Enco Buds3 Pro）：

> - An id in the status reply does not prove the switch. The OPPO Enco Buds3 Pro lists `1B` and reports `1B=1` after a write. Its bitmap has no `0x012A`, and two owners hear no 3D audio (reports #60 #61). QuickBuds hides the row through its `statusQuery` (`05 11 06`) `[CAPTURE]`.

**E-P8-10**（`PROTOCOL.md:972`）同一位图在不同机型长度不同（Enco Buds2 只有 4 字节）：

> - OPPO Enco Buds2 (model entry `064810`, reported product id `060C12`): 4-byte bitmap `00 9B 2C 50 80` `[CAPTURE]`.

**E-P8-11** 未给出项：名为"支持 ANC""支持仓电量"的**专用能力位/标志位** —— **文档未给出**。

---

## P9 心跳/保活间隔与超时判连

### 结论

- **没有应用层心跳/保活轮询**：文档明确"Was: a keep-alive poll. Is: none."，并称"未见过链路陈旧"。
- 因此"保活间隔"这个参数**不存在**，"心跳超时判连"**文档未给出**。
- 文档给出的判连/超时相关数值只有这些：连接阻塞 **12 s 看门狗**；第二个 UUID 约 **5 s 超时**；单次连接共 **5 次** socket 尝试；掉线 **8 次**重连；单次写 **5 s** 上限；耳机侧 `0x0500` 无应答则 **3-5 s** 后重置链路。
- 一种"被动保活"事实存在：电量推送每 **1-6 分钟**；ANC/佩戴/游戏模式以推送形式随时到达。

### 原文与行号

**E-P9-01**（`PROTOCOL.md:260-261`）结论行：

> The app does not poll after that. Battery, wearing, ANC and game mode arrive as pushes `[TESTED]`.
> Was: a keep-alive poll. Is: none. No stale link seen `[TESTED]`.

**E-P9-02**（`PROTOCOL.md:49-50`）12 s 看门狗与尝试预算：

> A 12 s watchdog closes a connect that
> blocks. A connect makes 5 socket tries in total, then stops. A lost link gets 8 reconnect tries `[CAPTURE]`.

**E-P9-03**（`PROTOCOL.md:46`）第二个 UUID 的 5 s 超时：

> | `00001107-D102-11E1-9B23-00025B00A5A5` | Tried second. Never connects on Buds 4 (~5 s timeout); kept for other models |

**E-P9-04**（`PROTOCOL.md:106`）写操作 5 s 上限：

> - One write at a time. Wait for the write to finish (5 s limit) and pause a model-specific interval between slices.

**E-P9-05**（`PROTOCOL.md:476`）耳机侧反向超时（无应答则重置链路 3-5 s）：

> It resets the link 3-5 s after connect, every time, if no answer comes.

**E-P9-06**（`PROTOCOL.md:527`）唯一的周期性流量（电量推送 1-6 分钟）：

> - Pushes come every 1-6 minutes on the buds' own clock, not tied to `0x0500` `[CAPTURE]`.

**E-P9-07**（`PROTOCOL.md:543`）合盖即断连（不是超时判定）：

> - Lid close `[CAPTURE]`: about 1 s before the socket drops, the pushes fall to `0`, ending with `01 00 02 00 03 00`. No lasting "closed" state exists. The app logs `Case closed` and skips the reconnect retries.

**E-P9-08** 未给出项：心跳间隔、心跳丢失阈值、基于心跳的断线判定 —— **文档未给出**。

---

## P10 主动断开/回仓收尾

### 结论

- **回仓（合盖）收尾**文档写得很明确：合盖 → socket 结束；断前约 1 s 推送归零并以 `01 00 02 00 03 00` 收尾；不存在持久的 "closed" 状态；app 记 `Case closed` 并**跳过重连重试**；开盖带来 `04 04 04` 与仓电量。
- **app 主动断开**：文档**没有**给出"主动断开 socket / 主动关连接"的命令或流程 —— **文档未给出**。文档里唯一的 `disconnect` 动词属于**双连设备管理**语义（`0x0429 02 <MAC>`，断开的是"另一台设备"，不是本机链路）。
- 另有两条"被动断链"路径：节能开关 `17`（写后耳机重启，链路约 12 s 后回来）；编解码写入后耳机重启（约 4 s 后回来）。

### 原文与行号

**E-P10-01**（`PROTOCOL.md:53`）合盖断链：

> A case lid close ends the socket (section 13) `[CAPTURE]`.

**E-P10-02**（`PROTOCOL.md:543`）合盖前的最后一串推送与收尾行为：

> - Lid close `[CAPTURE]`: about 1 s before the socket drops, the pushes fall to `0`, ending with `01 00 02 00 03 00`. No lasting "closed" state exists. The app logs `Case closed` and skips the reconnect retries. Opening the lid brings `04 04 04` and the case battery.

**E-P10-03**（`PROTOCOL.md:888`）`disconnect` 的唯一出现：属于双连设备管理，非本机链路主动断开：

> - Device manager `[VENDOR]`, wired, unverified: models whose `dualDevice` lists it (11 models; bitmap bit 59 `0x0429` `0x0132`. Buds 4 lacks it). `0x0429 01 <MAC>` connect, `02 <MAC>` disconnect, `03 <MAC>` unpair (unused).

**E-P10-04**（`PROTOCOL.md:758`）写后耳机重启造成的断链（12 s 恢复）：

> - **Power saving `17`** `[CAPTURE]`: either write restarts the buds. The link returns about 12 s later and `0x810D` shows the new value.

**E-P10-05**（`PROTOCOL.md:859`）编解码写入后重启（约 4 s 恢复）：

> - A refused `18` write changes nothing. An accepted write restarts the link. The buds return about 4 s later `[CAPTURE]`.

**E-P10-06**（`PACKET-CAPTURE.md:68-71`）交叉核对：耳机同时只服务一个控制 app，"让出"由用户/系统终止进程，而非协议内主动断开命令：

> The buds serve one control app at a time, so QuickBuds has to let go first.
> 1. Stop QuickBuds from Quick Settings › **Active apps** › Stop. (`am force-stop` is not enough: the
>    app reconnects when the audio link comes back.)

**E-P10-07** 未给出项：协议层"主动断开"命令、优雅关闭握手、断开 ACK —— **文档未给出**。

---

## P11 并发写限制（是否串行队列、最短间隔、序号回绕）

### 结论

- **串行**：文档明确"一次只写一条"，写完要等完成（**5 s 上限**），分片之间还要"按机型暂停一个间隔"。
- **最短间隔的具体数值：文档未给出**。文档只给了"model-specific interval"这个说法，未列任何具体毫秒数；初始化阶段另有"每 200 ms 一条"的节奏，但那是**初始化读序列**的节拍，不是通用写间隔。
- 帧预算：默认 **660 字节**，超预算才分片；`0x0107` 的回复可下调该预算（`00 <u16 LE>`）；文档说明"部分批量传输用更大预算，QuickBuds 一个都不用"。
- **序号回绕**：文档只对 feature-sync 引擎给出显式取模回绕公式 `C0 + (n mod 0x20)`；通用计数器 `01`-`BF` 的回绕规则**文档未给出**（`FF` 恒为推送、`F0`/`F2`/`00` 为固定号，不参与回绕）。

### 原文与行号

**E-P11-01**（`PROTOCOL.md:106`）串行 + 5 s + 机型相关间隔：

> - One write at a time. Wait for the write to finish (5 s limit) and pause a model-specific interval between slices.

**E-P11-02**（`PROTOCOL.md:105`）帧预算与分片触发：

> - The sender splits a message when it exceeds the frame budget. The budget is 660 bytes by default. The reply to `0x0107` (`00 <u16 LE>`) can lower it `[VENDOR]`. Some bulk transfers use a larger budget. QuickBuds sends none.

**E-P11-03**（`PROTOCOL.md:296`）帧预算读命令：

> | `0x0107` | Frame budget | — → `00 <u16 LE>` | 2 |

**E-P11-04**（`PROTOCOL.md:243`）唯一的固定节拍（初始化 200 ms，非写间隔）：

> Init sequence `[CAPTURE]` (Buds 4, both apps): wait 300 ms, then one message every 200 ms.

**E-P11-05**（`PROTOCOL.md:120`）唯一显式的取模回绕：

> | `C0`-`DF` | The feature-sync engine; wire seq = `C0 + (n mod 0x20)` |

**E-P11-06**（`PROTOCOL.md:119`）通用计数器区间（无回绕描述）：

> | `01`-`BF` | The generic counter of the other commands |

**E-P11-07**（`PROTOCOL.md:128`）回复匹配键（并发下靠 (Seq, Cmd&0x7FFF) 区分）：

> - The app matches a reply on `(Seq, Cmd & 0x7FFF)` `[VENDOR]`.

**E-P11-08**（`PROTOCOL.md:101`）分片拼接是接收侧串行语义（乱序/失头处理）：

> - The receiver joins the slice data in order. It parses the message when `Slice` is `03`. A middle or last slice with no first slice before it is dropped. A declared `PayLen` larger than the bytes present cuts the payload to the bytes present `[VENDOR]`.

**E-P11-09** 未给出项：分片/写命令之间的**具体最短间隔毫秒数**、通用序号 `01`-`BF` 的回绕点 —— **文档未给出**。

---

## P12 机型差异（尤其 OPPO Enco 系列、Free4 是否列出、丹拿版）

### 结论

- 机型差异是**结构性的**，文档在第 21 章按主题分 12 小节列出：产品 id/型号查找（21.1）、能力位图（21.2）、ANC 树（21.3）、手势（21.4）、特性开关（21.5）、EQ（21.6）、编解码/空间/高解析（21.7）、电量佩戴仓（21.8）、游戏模式（21.9）、金标音/贴合/个性化 ANC（21.10）、双连（21.11）、统计（21.12）。
- 文档总述：**不同机型在 feature id、button id、ANC 位上不同**。
- **OPPO Enco 系列**在文档中被点名的机型（按首次出现）：Buds2（条目 `064810`，实报产品 id `060C12`）、Buds3 Pro `06A850`、Clip2 `06E010`、Air5 Pro `06C410`、Enco X `061410`、Enco X2 `063C10`、Enco Air3 Pro `065C10`、Enco Free3 `066010`、Enco R `063010`、Air2 `063410`、Enco X3 `067410`、Enco X4 `06EC10`。
- **"Free4"：全文（PROTOCOL.md 与 PACKET-CAPTURE.md）检索 `Free4` / `Free 4` 零命中 → 文档未给出。** 仅有 `Enco Free3`（`066010`）。
- **"丹拿版"（Dynaudio）/ 联名版：检索 `丹拿` / `Dynaudio` 零命中 → 文档未给出。**
- 另：文档**没有**任何 "MagicFree4" 字样，也没有 "OPPO Enco Free4" —— 均**文档未给出**。

### 原文与行号

**E-P12-01**（`PROTOCOL.md:21`）差异总述：

> **Test device:** OnePlus Buds 4, firmware `B4.1-260810-1153` (HeyMelody shows `138.138.105`). Other models differ in feature ids, button ids and ANC bits.

**E-P12-02**（`PROTOCOL.md:956`）第 21 章的定位（每条规则都点名机型 id、字节、值）：

> Every rule in this section names the model id, the byte and the value. A topic section points here.

**E-P12-03**（`PROTOCOL.md:267`）型号查找的"名称优先于 id"陷阱（OPPO Enco Buds2 报的是 realme 的 id）：

> Name before id matters: OPPO Enco Buds2 report `060C12`, the realme Buds Q2s id (issue #5) `[CAPTURE]`. A renamed device matches no name and falls back to its id.

**E-P12-04**（`PROTOCOL.md:962-967`）OPPO Enco / OnePlus 机型 id 清单（原样抄录）：

> - OPPO Enco Buds2: the model entry is `064810`, but the buds report product id `060C12`, the realme Buds Q2s id (issue #5) `[CAPTURE]`. A renamed device matches no name and falls back to its id.
> - OnePlus Buds 4: model id `065414`, product id `00 14 54 06` `[CAPTURE]`.
> - OnePlus Nord Buds 3 Pro `064414` (issue #2) `[CAPTURE]`.
> - OnePlus Buds Pro `060C14` `[VENDOR]`.
> - OnePlus Buds `060414`. OnePlus Buds Z `060814` `[VENDOR]`.

**E-P12-05**（`PROTOCOL.md:975`）OPPO Enco Buds3 Pro `06A850` 的能力位/特性不一致：

> - OPPO Enco Buds3 Pro `06A850`: the bitmap has no `0x012A`, but the status reply lists `1B` and reports `1B=1` after a write (reports #60 #61) `[CAPTURE]`. Its `statusQuery` is `05 11 06`.

**E-P12-06**（`PROTOCOL.md:972-973`）OPPO Enco Buds2 与 Nord Buds 3 Pro 的位图/事件码差异：

> - OPPO Enco Buds2 (model entry `064810`, reported product id `060C12`): 4-byte bitmap `00 9B 2C 50 80` `[CAPTURE]`. It offers `02 01` and sends no `0x8205`. A long `0x010D` list dropped its link (issue #5). `02 05 0D` kept the link up `[CAPTURE]`.
> - OnePlus Nord Buds 3 Pro `064414`: offered codes `00 02 02 01` (wear, battery) `[CAPTURE]`. It sent no `0x8205` when asked for `01 02 03 04 08 0B`. It pushed an ANC change once without `03`.

**E-P12-07**（`PROTOCOL.md:991-992`）OPPO Enco 的 ANC 树形状（与 Buds 4 不同，位含义会串）：

> - Tree shape `NC 0, Off 1, Transparency 2` (9 models) `[VENDOR]`: Buds 4's Off bytes turn ANC on there `[VENDOR]`.
> - Tree shape `Off 0, Transparency 1, Light 2, Deep 3` (Enco X `061410`, Buds Z2 `061014`), plus `Smart 4` on Buds Pro `060C14` `[VENDOR]`.

**E-P12-08**（`PROTOCOL.md:1016`）OPPO Enco Air5 Pro `06C410` 的手势按钮 id 差异：

> - OPPO Enco Air5 Pro `06C410`: the `F1` gesture report carries button id `06` `[VENDOR]`.

**E-P12-09**（`PROTOCOL.md:1055-1056`）OPPO Enco 走编解码选择器 / 头部跟踪的机型：

> - Models with `hiRes` and `0x0123` `[VENDOR]`: Enco X2 `063C10`, Enco Air3 Pro `065C10`, Enco Free3 `066010`. They use the codec picker `0x041A` `[VENDOR]`.
> - Head tracking (`spatialModes` 2) `[VENDOR]`: Buds Pro 2 `062014`, Buds Pro 3 `064014`, Enco X3 `067410` `[VENDOR]`.

**E-P12-10**（`PROTOCOL.md:920`）OPPO Enco Air5 Pro 的耳道扫描拒绝（原因未知）：

> - The OPPO Enco Air5 Pro refuses an ear-scan start (`0x040D 04 01 <id>`) with status `01` on Windows (6 times, report #44). The cause is unknown. The wear state is a guess `[CAPTURE]`.

**E-P12-11**（`PROTOCOL.md:1020`）realme 颈挂形态（写单键为左耳）：

> - realme neckbands (Wireless 3 Neo `050C12`, 5 ANC `051412`, 6 `051C52`, 6 ANC `052052`, 6 Neo `051812`): one button written as the left bud, `01 01 <act> <fn>`.

**E-P12-12**（`PROTOCOL.md:1039`）OPPO Enco Clip2 `06E010` 仅出现在"丢失提醒"里：

> - `1E` log collection: asked on 16 models. `0F` zen mode: asked on 6 OnePlus and OPPO models. `13` sound record: asked on 5 models. `16` lost reminder: asked on 2 models (OPPO Enco Clip2 `06E010`, OnePlus Buds Clip `06E410`) `[VENDOR]`.

**E-P12-13**（`PROTOCOL.md:1090-1095`）跨机型统计（判断"多数机型没这功能"用）：

> - `ancModes`: 79 of 143 models in `models.json` have none `[VENDOR]`.
> - `holdMask`: 28 models use a per-bud hold `[VENDOR]`.
> - `eqPresets`: 41 of 143 models have none `[VENDOR]`.
> - `statusQuery`: 85 HeyMelody models, 30 realme models `[VENDOR]`.
> - `dualDevice`: 11 models. `personalAnc`: 11 models `[VENDOR]`.

**E-P12-14** 未给出项（三项，均为阻塞级）：**`Free4` / `MagicFree4` 机型条目**、**丹拿（Dynaudio）联名版差异**、**OPPO Enco Free 系列除 Free3 外的任何条目** —— 全部**文档未给出**（关键词全文零命中，检索命令见本文件第 0 节"取证方式"）。

---

## 附录 A：文档结构目录（PROTOCOL.md 一级/二级标题与行号）

| 行号 | 级别 | 标题（原样） |
|---|---|---|
| 1 | `#` | PROTOCOL.md — the earbud wire format |
| 39 | `##` | 1. Transport |
| 60 | `##` | 2. Frame format |
| 112 | `##` | 3. Sequence and replies |
| 134 | `##` | 4. Command classes |
| 152 | `##` | 5. Command bitmap |
| 241 | `##` | 6. Connection and init sequence |
| 283 | `##` | 7. Queries — `0x01xx` |
| 336 | `##` | 8. Subscriptions — `0x02xx` |
| 354 | `##` | 9. Writes — `0x04xx` |
| 419 | `##` | 10. Pushes — `0x0204` |
| 472 | `##` | 11. Requests from the buds and replies |
| 500 | `##` | 12. Batch query — `0x012F` |
| 517 | `##` | 13. Battery and wearing |
| 551 | `##` | 14. ANC (noise control) |
| 640 | `##` | 15. Gestures |
| 707 | `##` | 16. Feature switches |
| 807 | `##` | 17. Equalizer and BassWave |
| 838 | `##` | 18. Sound and device settings |
| 874 | `##` | 19. Dual connection |
| 897 | `##` | 20. Golden Sound, fit test, personalised ANC |
| 954 | `##` | 21. Per-model rules |
| 1103 | `##` | 22. Not built |
| 1177 | `##` | 23. Log lines |
| 1197 | `##` | 24. Open questions |

### 三级标题（便于按需精读）

| 行号 | 标题（原样） |
|---|---|
| 91 | Slicing `[VENDOR]` |
| 263 | What the buds are and accept |
| 456 | Feature push `0x0503` |
| 566 | SET — `0x0404 01 01 <mask>` |
| 585 | Report table |
| 610 | The hold's ANC cycle |
| 627 | Two tables, three rules |
| 642 | Gesture table |
| 667 | `fn` values |
| 687 | `F1` push |
| 899 | Golden Sound (hearing profile) |
| 926 | Fit test |
| 933 | Personalised ANC |
| 958 | 21.1 Product id and model lookup |
| 969 | 21.2 Bitmap and command support |
| 987 | 21.3 ANC |
| 1007 | 21.4 Gestures |
| 1025 | 21.5 Feature switches |
| 1042 | 21.6 Equalizer |
| 1053 | 21.7 Codecs, spatial sound and Hi-Res |
| 1063 | 21.8 Battery, wearing and the case |
| 1070 | 21.9 Game mode |
| 1075 | 21.10 Golden Sound, fit test, personalised ANC |
| 1083 | 21.11 Dual connection |
| 1088 | 21.12 Counts |

---

## 附录 B：对 MagicFree4 的直接影响

说明：文档中**没有任何 MagicFree4 / OPPO Enco Free4 条目**，以下每条只指认"若照此文档实现，会动到哪一层"。

| # | 影响（一句） | 作用层 |
|---|---|---|
| B1 | 帧层必须按 `AA` + LEB128 长度 + 可选 `Fsn` 实现，且归一化后按 `cmd@4-5 / seq@6 / paylen@7-8 / payload@9` 固定切片，否则"错位只会得到垃圾而不会报错"。 | 帧层（`PROTOCOL.md:63-88`） |
| B2 | 帧层不要留校验钩子：文档声明 `No checksum.`，任何 CRC8/CRC16 实现都与文档冲突。 | 帧层（`PROTOCOL.md:89`） |
| B3 | 连接层只能按 `079A → 1107 → 079A(insecure) → 1107(insecure) → 通道 15` 的顺序试，且要保留 12 s 看门狗、5 次总尝试、8 次重连。 | 连接（`PROTOCOL.md:48-51`） |
| B4 | 初始化必须复刻"300 ms 后每 200 ms 一条"的固定序列，并且 `0x0100` 位图闸门不通过就不得下发命令。 | 连接/初始化（`PROTOCOL.md:243-265`） |
| B5 | 订阅必须发 `01 02 03`（按位图加 `04/08/0B`），漏掉 `03` 就收不到耳机侧 ANC 变更、发成"count 1, battery only"就永远没有佩戴推送。 | 连接/事件（`PROTOCOL.md:270-272`） |
| B6 | 必须应答耳机反向请求 `0x0500`（回 `00 <Unix 秒 u32 LE>`，回显 Seq 并置命令 bit15），否则部分固件会在 3-5 s 后重置链路。 | 连接（`PROTOCOL.md:479-483`） |
| B7 | 不要实现保活轮询：文档为 "Is: none."，心跳超时判连无据可依。 | 连接（`PROTOCOL.md:260-261`） |
| B8 | ANC 层必须同时维护"SET 表"和"报告表"两套位，绝不能把 `0x0100`/`0x0002` 这类上报值直接当 SET 载荷写回。 | ANC（`PROTOCOL.md:566-596, 629-631`） |
| B9 | ANC 的位号必须来自机型 `ancModes` 的 `slot`（Adaptive 是 bit 11 而不是 bit 8），无树的机型只能退化为 Buds 4 树或直接不给噪声控制。 | ANC（`PROTOCOL.md:582-583, 993`） |
| B10 | 处理 ANC 推送必须先用"代码后两字节是否为 `01 01`"过滤，否则保持循环掩码会被误判成模式并让 UI 卡在假模式。 | ANC（`PROTOCOL.md:605-606`） |
| B11 | 写 ANC 失败必须按状态码分支处理：`0E` 表示没有耳机在耳，此时应重读 `0x010C 01 01` 让显示回真。 | ANC（`PROTOCOL.md:604`） |
| B12 | 电量层按 `part 01/02/03 = 左/右/仓`、`level = raw & 7F`、`charging = raw & 80` 解码，并把 `0` 当作"未知"隐藏而非 0%。 | 电量（`PROTOCOL.md:523-524`） |
| B13 | 查询电量用固定 `Seq F0`，且推送 `01` **没有**状态字节，两种报文形态要分别解析。 | 电量（`PROTOCOL.md:519-521, 121`） |
| B14 | 合盖场景必须接受"只有仓外耳机上报、仓与仓内耳机静默、仓电量可能为 0"这一现实，不能判为错误。 | 电量/连接（`PROTOCOL.md:524-526`） |
| B15 | 能力探测只能读 `0x0100` 位图 + `0x8200` 事件码清单；"支持 ANC""支持仓电量"没有专用能力位，必须用"命令位 + 机型树"和"实际上报的 `part 03`"推断。 | 连接/能力（`PROTOCOL.md:154-159, 171, 269, 993`） |
| B16 | 写通道必须串行：一次一条、等完成（5 s 上限）、分片间留机型间隔；并发发送没有任何文档依据。 | 帧层/写队列（`PROTOCOL.md:106`） |
| B17 | 序号策略要避开保留号：`FF` 是推送、`F0`/`F2`/`00` 有固定用途，`C0`-`DF` 归 feature-sync（`C0 + (n mod 0x20)`）。 | 帧层（`PROTOCOL.md:116-123`） |
| B18 | 若目标机型不在文档的 143 机型表内，`ancModes`/`statusQuery`/`eqPresets` 等机型表全部无据，只能按其产品 id 落回默认（无 `statusQuery` 时用 23 个 id + 可选 `1A`）。 | 连接/ANC/电量全局（`PROTOCOL.md:769, 979, 1090-1095`） |

---

## 附录 C：证据缺口汇总（阻塞项）

| 项 | 缺口 | 依据 |
|---|---|---|
| P2 | CRC8/CRC16/和校验的覆盖范围（因文档声明无校验，此项无对象） | `PROTOCOL.md:89` |
| P5 | 统一的"降噪/通透/关闭"三档枚举值 | `PROTOCOL.md:557, 571-596, 599` |
| P6 | ANC 值字段的绝对帧字节偏移 | `PROTOCOL.md:553, 562` |
| P8 | "支持 ANC""支持仓电量"的专用能力位 | `PROTOCOL.md:154-171` |
| P9 | 心跳间隔、心跳超时判连 | `PROTOCOL.md:260-261` |
| P10 | 协议层主动断开命令/优雅关闭握手 | `PROTOCOL.md:53, 543, 888` |
| P11 | 分片/写命令的最短间隔毫秒数、通用序号回绕点 | `PROTOCOL.md:106, 119-120` |
| P12 | OPPO Enco **Free4 / MagicFree4** 机型条目 | 全文零命中 |
| P12 | **丹拿（Dynaudio）联名版**差异 | 全文零命中 |
| P4 | 应用层配对/鉴权/加密流程 | `PROTOCOL.md:48, 1112` |

---

*本台账只记录 `PROTOCOL.md` 中真实存在的内容；所有"文档未给出"均为全文核对后的结论，未做任何补全或推测。*
