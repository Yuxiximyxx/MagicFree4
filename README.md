# MagicFree4

让 **OPPO Enco Free4**（含丹拿版）在荣耀 **MagicOS 11** 上，像荣耀自家耳机一样工作：

- 出现在**设备中心**球位，可打开服务卡
- 服务卡内切换降噪（降噪 / 通透 / 关闭），**写后回读**校验
- 显示**左右耳独立电量**（有仓电量则一并显示）
- 控制中心 **QS 磁贴**循环切档
- 音频切换（SWS）通知追加电量，断开自动清除

## 现状（诚实版）

| 项 | 状态 |
|---|---|
| 代码 | ✅ 协议层移植完成，可编译出可安装的 APK |
| 编译 | ✅ `./gradlew :app:assembleDebug` 通过（JDK 17 + SDK Platform 34） |
| 真机 | ⚠️ **未验证**。首次交付没有在真机上跑过，功能不可宣称可用 |
| 原道协议残留 | 游戏模式 / 低延迟 / 双设备 / EQ / 抗风噪 / 入耳检测 → 明确 no-op（欢律侧未实现） |

## 它是什么

一个 LSPosed 模块，fork 自 [MagicOriG](https://github.com/yingjfd/MagicOriG)（MIT，荣耀侧注入框架），
把其中的耳机协议层从原道 NiceHCK 私有协议替换为 **OPPO 欢律（HeyMelody）协议**。

欢律协议实现移植自 [QuickBuds](https://github.com/spizganed/QuickBuds)（GPL-3.0），
包括帧层（`0xAA` + LEB128 + 分片、**无校验**）、命令表、降噪位掩码映射与电量解析。

## 降噪档位映射

Free4 的降噪有三级（Light / Medium / Deep），荣耀面板只有「降噪」一个按钮，因此：

| 荣耀 status | 欢律 bit | 含义 |
|---|---|---|
| 1 | 0 | 关闭 |
| 2 | 2 | 通透 |
| 5（面板「降噪」） | **5** | Medium（实测固件 ANC-on 会恢复到该档） |
| 4 | 4 | Deep（预留） |

要改成 Deep，只改 `HemAnc.bitForStatus()` 一个地方。

## 构建

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk      # 需要 platform-34 + build-tools 34.0.0
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

发布签名：`app/signing/magicorig.jks` 不存在时自动回退 debug 签名（仍可安装）。

## 安装

1. 手机上装 LSPosed（需 root），安装本 APK；
2. LSPosed 里启用 **MagicFree4**，作用域勾选 5 个进程：
   `com.android.bluetooth`、`com.android.systemui`、`com.hihonor.audioaccessorymanager`、
   `com.hihonor.imedia.sws`、`com.hihonor.controlcenter`
3. 重启对应进程（或重启手机）；
4. 连接 Free4，看模块自检页日志。

## 已知限制 / 待验证

- **真机零验证**：首次交付仅保证可编译、可安装。
- **与上游 MagicOriG 同包名**（`io.github.yingjfd.magicorgin`），两者不能同时安装；
  若需共存，改 `app/build.gradle.kts` 的 `namespace` / `applicationId` 并同步移动源码目录。
- 音频切换通知的文案匹配按 `OPPO Enco Free4` 写死；若系统通知标题不同需调整
  `SystemUIHeadsetIconHook`。
- 游戏模式 / EQ 等入口为 no-op。
- 仓电量取决于固件是否在 `0x8106` 里返回 `idx=3`。

## 文档

- [`docs/PLAN.md`](docs/PLAN.md) — 执行计划、阶段门槛、真机验收清单
- [`docs/protocol-code-facts.md`](docs/protocol-code-facts.md) — 欢律协议代码级取证（带行号）
- [`docs/protocol-evidence.md`](docs/protocol-evidence.md) — QuickBuds `PROTOCOL.md` 文档侧取证（109 条）
- [`docs/magicos-recon.md`](docs/magicos-recon.md) — MagicOS 注入点侦察（68 条）
- [`docs/magicos11-integration.md`](docs/magicos11-integration.md) — 上游 MagicOriG 的逆向文档

## 许可

本项目整体以 **GPL-3.0** 发布（移植了 GPL-3.0 的 QuickBuds 协议代码）。
MagicOriG 的 MIT 声明与版权归原作者保留，详见 [`NOTICE.md`](NOTICE.md)。
