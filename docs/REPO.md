# 仓库托管与发布

本仓库目前在本地初始化，**远端地址待定**（尚未推送到任何托管平台）。
本文件记录「推到远端」和「发版」的标准动作，避免以后忘步骤。

## 1. 当前 git 结构

```
origin    （未设置 —— 等确定托管地址后添加）
upstream  https://github.com/yingjfd/MagicOriG   （只想同步上游时用）
```

- 分支：`main`
- 首个提交：MagicFree4 2.0.0（协议层替换 + 身份改造 + 单元测试 + 文档）

## 2. 推送（二选一）

### GitHub

```bash
# 1) 网页上先建空仓库（不要勾 README/.gitignore/LICENSE）
git remote add origin git@github.com:<你的账号>/MagicFree4.git
git push -u origin main
git push origin v2.0.0
```

### Gitee（国内更快）

```bash
git remote add origin git@gitee.com:<你的账号>/magicfree4.git
git push -u origin main
git push origin v2.0.0
```

> 国内网络下 `git push` 常被掐 TLS：先
> `export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897`。

## 3. 发布 APK

```bash
export JAVA_HOME=<jdk21>
export ANDROID_HOME=<android-sdk>       # platform-34 + build-tools 34.0.0
./gradlew :app:assembleDebug            # 产物 app/build/outputs/apk/debug/app-debug.apk
```

- 未提供 `app/signing/magicorig.jks` 时，release 会自动回退 **debug 签名**（可安装，不可上商店）。
- 想用正式签名：把 keystore 放到 `app/signing/magicorig.jks`（已 gitignore），
  凭据写在 `app/build.gradle.kts` 的 `signingConfigs.release`。
- 发布时要一起给：APK、`versionName`、对应的 `docs/PLAN.md` 验收结论（真机过没过哪几项）。

## 4. 同步上游 MagicOriG

```bash
git fetch upstream
git log --oneline HEAD..upstream/main      # 看看上游有什么新东西
git cherry-pick <commit>                   # 只挑 hook 层的修复，不要整包合并协议层
```

⚠️ 上游的 `pods/` 是原道协议，整体合并会把欢律协议层覆盖掉。

## 5. 发版检查清单

- [ ] `./gradlew :app:testDebugUnitTest` 全绿
- [ ] `./gradlew :app:assembleDebug` 成功
- [ ] `app/src/main/resources/META-INF/xposed/module.prop` 里 `targetApiVersion` 还在
- [ ] `LICENSE`（GPL-3.0）与 `NOTICE.md` 未被删改
- [ ] `pods/hem/*.kt` 的 `SPDX-License-Identifier` 头还在
- [ ] README「现状」表里的真机验证状态与实际一致（没验就说没验）
