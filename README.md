# MagicFree4

让 **OPPO Enco Free4**（含丹拿版）在荣耀 **MagicOS 11** 上像原生耳机一样工作。

一个 LSPosed 模块：fork 自 [MagicOriG](https://github.com/yingjfd/MagicOriG)（MIT，荣耀侧注入框架），
把耳机协议层替换为 **OPPO 欢律（HeyMelody）协议**——协议实现参考
[QuickBuds](https://github.com/spizganed/QuickBuds)（GPL-3.0）。

- 包名：`io.github.yuxiximyxx.magicfree4`
- 版本：见 [Releases](https://github.com/Yuxiximyxx/MagicFree4/releases)
- 适用：MagicOS 11（已在 Magic7 Pro / 11.0.0.110 验证）+ OPPO Enco Free4 / Free4 丹拿版

## 功能

### 系统集成

- **设备中心球位 + 服务卡**：像原厂耳机一样出现在控制中心
- **四档噪声控制**：关闭 / 降噪 / 自适应 / 通透，写后回读校验
- **双耳独立电量**：左耳 / 右耳 / 充电仓，服务卡与详情页同步
- **响铃查找**：点一下高亮 + 耳机响，再点熄灭 + 停止
- **QS 磁贴**：控制中心快捷切档
- **SWS 通知**：音频切换通知追加电量，断开自动清除
- **记忆式 ANC**：重连后自动恢复你上次选的档位
- **低电量提醒**：电量 ≤20% 时通知（30 分钟限频）

### 耳机详情页（设置 → 更多设置）

深度对齐荣耀系统设计语言（纯黑底 / 卡片 / 顶部渐变模糊 / 圆形返回 / 跟随深色模式）：

| 页面 | 内容 |
|---|---|
| 概览 | 三环电量、四档噪声、功能开关（低延迟 / 高音质 / 黄金听感 / 佩戴检测） |
| 均衡器 | 内置预设（至臻原音 / 纯享人声 / 澎湃低音 / 活力动感）+ 自定义预设（新建 / 编辑 6 段增益 / 重命名 / 长按删除，上限 3 个由固件决定） |
| 耳机手势 | 左耳/右耳 × 主按键/通话中，读 + 写（`0x0108` / `0x0401`） |
| 耳机设置 | 佩戴检测、黄金听感、手感设置、提示音音量、固件版本、贴合度检测、个性化降噪状态 |
| 设备双连 | 开关 + 已连接设备列表（`0x0112`） |
| 电量历史 | 最近 40 条电量快照 |
| 关于耳机 | 型号 / 蓝牙地址 / 固件 / 协议 / 支持的固件命令数 |

### 已验证（真机）

降噪四档与高亮、电量、响铃开关、手势读写、均衡器预设与自定义（增删改查）、设备双连列表、电量历史、
ANC 记忆、黄金听感、固件能力探测。

### 固件不支持（已探测确认，不做）

听力优化 `0x38`、防风噪 `0x1A`、省电 `0x17`、自适应音量 `0x30`、睡眠暂停 `0x3A`、
个性化降噪测试流程 `0x0C`、切换主设备 `0x0429`。

## 安装

1. 下载 Releases 里的 APK 安装
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
- 电量 `0x0106`→`0x8106`：`[idx(1/2/3), raw]`，`level=raw&0x7F`，`charging=raw&0x80`
- EQ 预设用 **slot**（0/1/2/7），不是 models.json 里的 mode（26/28/29/34）
- 更多：`docs/protocol-evidence.md`（109 条证据）、`docs/magicos-recon.md`（68 个注入点）

## 许可

- 本仓库：**GPL-3.0**
- 荣耀侧注入框架：[MagicOriG](https://github.com/yingjfd/MagicOriG)（MIT）
- 欢律协议实现参考：[QuickBuds](https://github.com/spizganed/QuickBuds)（GPL-3.0）
