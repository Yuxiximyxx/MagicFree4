# MagicPods

让 **OPPO Enco Free4**（含丹拿版）和 **OPPO Enco Air4 Pro** 在荣耀 **MagicOS 11** 上像原生耳机一样工作。

一个 LSPosed 模块：fork 自 [MagicOriG](https://github.com/yingjfd/MagicOriG)（MIT，荣耀侧注入框架），
把耳机协议层替换为 **OPPO 欢律（HeyMelody）协议**——协议实现参考
[QuickBuds](https://github.com/spizganed/QuickBuds)（GPL-3.0）。

- 包名：`io.github.yuxiximyxx.magicfree4`（改显示名时故意不动，避免已装模块的作用域和升级断掉）
- 版本：见 [Releases](https://github.com/Yuxiximyxx/MagicPods/releases)
- 适用：MagicOS 11 + OPPO Enco Free4 / Free4 丹拿版 / Enco Air4 Pro
- 仓库：https://github.com/Yuxiximyxx/MagicPods

两款耳机走同一套欢律帧和位掩码。连上之后按蓝牙名，再用 product id 确认是哪一款：

| 机型 | 蓝牙名片段 | Product ID | 降噪 | 均衡器 |
|---|---|---|---|---|
| Enco Free4 | `Enco Free4`、`Free4` | `068C10` | 关闭 / 降噪 / 自适应 / 通透 | 四档预设 + 自定义 |
| Enco Free4 丹拿版 | 同上 | `06C010` | 与普通版相同 | 与普通版相同 |
| Enco Air4 Pro | `Enco Air4 Pro`、`Air4` | `067C10` | 没有自适应，该档落到中档降噪 | 只有至臻原音 / 纯享人声 / 澎湃低音，没有自定义 |

Air4 Pro 的适配说明和实机验证来自酷安用户「你们都没有我迪奥大」（HONOR AAK-AN00 / MagicOS 11 / 当时的模块 2.9.4）。
Free4 仍是默认行为：没认出机型时按 Free4 显示和处理。

## 功能

### 系统集成

- **设备中心球位 + 服务卡**：像原厂耳机一样出现在控制中心，名字跟着当前机型走
- **噪声控制**：Free4 四档（关闭 / 降噪 / 自适应 / 通透）；Air4 Pro 三档（没有自适应）
- **双耳独立电量**：左耳 / 右耳 / 充电仓，服务卡与详情页同步
- **响铃查找**：点一下高亮 + 耳机响，再点熄灭 + 停止
- **QS 磁贴**：控制中心快捷切档
- **SWS 通知**：音频切换通知换成当前机型名并追加电量，断开自动清除
- **记忆式 ANC**：重连后自动恢复你上次选的档位；Air4 Pro 上如果上次是自适应，会落到中档降噪
- **低电量提醒**：电量 ≤20% 时通知（30 分钟限频）

### 耳机详情页（设置 → 更多设置）

深度对齐荣耀系统设计语言（纯黑底 / 卡片 / 顶部渐变模糊 / 圆形返回 / 跟随深色模式）：

| 页面 | 内容 |
|---|---|
| 概览 | 三环电量、噪声档位（Air4 Pro 不显示自适应）、功能开关（低延迟 / 高音质 / 黄金听感 / 佩戴检测） |
| 均衡器 | Free4：至臻原音 / 纯享人声 / 澎湃低音 / 活力动感，外加自定义预设。Air4 Pro 只有前三档，没有自定义 |
| 耳机手势 | 左耳/右耳 × 主按键/通话中，读 + 写（`0x0108` / `0x0401`） |
| 耳机设置 | 佩戴检测、黄金听感、手感设置、提示音音量、固件版本、贴合度检测、个性化降噪状态 |
| 设备双连 | 开关 + 已连接设备列表（`0x0112`） |
| 电量历史 | 最近 40 条电量快照 |
| 关于耳机 | 型号 / 蓝牙地址 / 固件 / 协议 / 支持的固件命令数 |

### 已验证（真机）

Free4（Magic7 Pro / MagicOS 11.0.0.110）：降噪四档与高亮、电量、响铃开关、手势读写、均衡器预设与自定义、设备双连列表、电量历史、ANC 记忆、黄金听感、固件能力探测。

Air4 Pro（贡献者机器 HONOR AAK-AN00 / MagicOS 11，基于 2.9.4 的改名版本）：设备中心卡片、降噪面板、音频切换通知电量、磁贴、断联自清。本仓库把两款耳机合成同一模块之后，Air4 Pro 路径还没有在这台构建上复验。

### 固件不支持（已探测确认，不做）

听力优化 `0x38`、防风噪 `0x1A`、省电 `0x17`、自适应音量 `0x30`、睡眠暂停 `0x3A`、
个性化降噪测试流程 `0x0C`、切换主设备 `0x0429`。

## 安装

1. 下载 [Releases](https://github.com/Yuxiximyxx/MagicPods/releases) 里的 APK 安装
2. LSPosed 中**启用本模块**（作用域：蓝牙、系统界面、音频配件管理、SWS、控制中心，由 `staticScope` 声明）
3. 重启蓝牙栈让注入生效：
   ```bash
   adb shell svc bluetooth disable && sleep 2 && adb shell svc bluetooth enable
   adb shell am force-stop com.hihonor.controlcenter
   ```

## 开发

```bash
export JAVA_HOME=/path/to/jdk21
./gradlew :app:assembleDebug
```

构建环境：JDK 21、Android SDK Platform 34、Kotlin。

## 协议速查

- 帧：`AA | len(LEB128) | slice | 00 | cmd(2LE) | seq | payLen(2LE) | payload`，**无校验**
- SPP UUID：`0000079A-…` / `00001107-…`，握手 `0x0100`
- ANC 写 `0x0404 [01,01,<bit>]`，读 `0x010C`；上报位（bit3 关闭 / bit8 通透）≠ SET 位
- 写入会回一帧 `0x0204` subType `0x03`，payload 是 `03 02 01 <mask>`。只有 `payload[1]==payload[2]==0x01` 才是真状态，回声必须丢掉
- 电量 `0x0106`→`0x8106`：`[idx(1/2/3), raw]`，`level=raw&0x7F`，`charging=raw&0x80`
- EQ 预设用 **slot**（Free4 为 0/1/2/7），不是 models.json 里的 mode（26/28/29/34）
- 荣耀 status → slot 和上报 bit → status 是两张表。Air4 Pro 上多个 bit 都落到中档，不能用一张表反推
- 更多：`docs/protocol-evidence.md`、`docs/magicos-recon.md`

## 许可

- 本仓库：**GPL-3.0**
- 荣耀侧注入框架：[MagicOriG](https://github.com/yingjfd/MagicOriG)（MIT）
- 欢律协议实现参考：[QuickBuds](https://github.com/spizganed/QuickBuds)（GPL-3.0）
