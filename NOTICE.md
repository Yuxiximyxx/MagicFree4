# NOTICE — 第三方作品与许可归属

MagicFree4 是两件上游作品的衍生品。本文件记录归属，**不得删除**。

---

## 1. MagicOriG（MIT）

- 上游：https://github.com/yingjfd/MagicOriG
- 用途：本项目的基座（荣耀 MagicOS 侧 hook 框架、设备中心注入、广播契约、模块 App）。
- 许可：MIT（上游 README §License 声明，仓库内无独立 LICENSE 文件）。
- 版权归其原作者（yingjfd）所有。

```
MIT License

Copyright (c) yingjfd (https://github.com/yingjfd/MagicOriG)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 2. QuickBuds（GPL-3.0）— 协议层移植来源

- 上游：https://github.com/spizganed/QuickBuds
- 用途：欢律（OPPO / HeyMelody）协议实现，移植到
  `app/src/main/java/io/github/yingjfd/magicorgin/pods/hem/`：

  | 本仓库文件 | 上游来源 |
  |---|---|
  | `HemWire.kt` | `protocol/Wire.kt`（帧编解码、分片重组） |
  | `HemCommands.kt` | `protocol/Protocol.kt`、`protocol/Commands.kt`、`protocol/Capabilities.kt`（命令号与 payload 形状） |
  | `HemAnc.kt` | `protocol/AncModes.kt`、`protocol/Anc.kt`、`assets/models.json`（Free4 机型表） |
  | `HemBattery.kt` | `protocol/Battery.kt`（电量与降噪上报解析） |

- 许可：**GPL-3.0**。因为这些文件是本项目的衍生作品，**MagicFree4 整体以 GPL-3.0 发布**，
  全文见仓库根 `LICENSE`。
- 版权归 QuickBuds 作者（spizganed）及其上游参考项目（QuickBuds 文档中列出的
  Zhaoyi-ya/OppoPodsManager、Star-ZER0/Pods-Protocol-Reverse-Engineering 等）所有。

## 3. 合规要点

1. 分发本项目的任何二进制或源码，必须同时提供 GPL-3.0 全文与本源代码。
2. 不得移除 `pods/hem/*.kt` 内的 `SPDX-License-Identifier: GPL-3.0-only` 头部与来源注释。
3. MagicOriG 的 MIT 声明必须保留（本文件即为其载体）。
4. 上游商标（OPPO、Enco、荣耀、MagicOS）归各自权利人，本项目与其无隶属关系。
