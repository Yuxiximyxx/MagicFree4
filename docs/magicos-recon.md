# MagicOriG 注入点取证清单（源自 `magicos11-integration.md`）

取证对象：`MagicOriG/docs/magicos11-integration.md`（1657 行）
取证原则：每条均带 `magicos11-integration.md:行号`；类名/方法/字段/键名原样照抄；文档未写的一律标「文档未给出」，不作推测。

设备基线（`magicos11-integration.md:3-4`）：HONOR AAK-AN00 / MagicOS 11.0.0 / Android 17 (API 37)，
`ro.build.display.id = AAK-AN00 11.0.0.109(SP9C00E109R202P2)`。文档自称「全部来自真机 `pm path` + `dexdump` 实测，非推测」（`:6`）。

---

## 0. 关于「11 环」的取证结论（先说清楚）

**文档没有给出编号化的 11 环节清单。** 全文仅两处出现环号：

| 出现位置 | 原文 | 行号 |
|---|---|---|
| 小节标题 | `### 4. c2/s 注册表兜底（第10环，最后一环）` | `magicos11-integration.md:1511` |
| 小节标题 | `### 8. 第 10-11 环：在线态断根 + ★ CNFE（ClassLoader 教训）` | `magicos11-integration.md:1541` |

「第 1 环」到「第 9 环」的编号与环节名称：**文档未给出**。
因此下表**不是**文档原有的 11 环表，而是**从文档正文逐条抄出的可证环节**，序号为本清单自编（与文档里的「第10环」「第10-11环」不对应）。
文档里唯一一处「各环」字样的上下文也确认它只是指本文实测日志（`magicos11-integration.md:1518`）。

---

## 1. 注入点总表

### 1.1 面板链路 —— 进程 `com.hihonor.audioaccessorymanager`

> 该进程 APK：`/system/app/AudioAccessoryManager/AudioAccessoryManager.apk`（`magicos11-integration.md:17`）。
> 耳机功能 UI 全部在该进程内（`magicos11-integration.md:27`）。

| 序号 | 环节名称 | 目标进程/包名 | 类名#方法 | 下游作用 | 行号 |
|---|---|---|---|---|---|
| 1 | 模块入口分支 | `com.hihonor.audioaccessorymanager` | `HookEntry` 增加对应分支 → `HonorEarphoneHook` | 作用域扩到面板进程 | `:336-337`，日志 `I loadHook pkg=com.hihonor.audioaccessorymanager hook=HonorEarphoneHook` `:346` |
| 2 | MBB 智能连接发起 | 同上 | `MbbApi.getSmartConnect(String, y8.c)` / `MbbApi.getSmartConnect(y8.c)` | 拦截只能观察"发起"，真正结果走回调 | `:347-348`，`:353-355`，`:363-364` |
| 3 | 回调 onFailed | 同上 | `f4.b$a.a(I)V` | `onFailed(code)`，实测 `code=2001`；只是打日志 `getSmartConnect onFailed`，并不 `finish()` | `:388`，`:401`，`:414` |
| 4 | 回调 onSuccess 合成（桥方法） | 同上 | `f4.b$a.b(Object)V` + `f4.b$a.c(Ljava/lang/Boolean;)V` | 载荷 = `java.lang.Boolean`；**这是协议桥要伪造的点** | `:402`，`:410-414`，`:421-422` |
| 5 | 同族包装类族 | 同上 | `MbbApi$a` `$c` `$s` `$u` `$w` `$x`（`Integer`）/ `MbbApi$o`（`String`）/ `MbbApi$n`（`Boolean`+`Boolean`）/ `MbbApi$b`（`z3/m`）… | 形态统一：`<init>(y8.c)` + `a(I)V` + `b(Object)V` + `c(载荷)V`，`c` 的参数类型即载荷类型 | `:475-494` |
| 6 | 设备信息应答（第二条 MBB 通道） | 同上 | 日志 tag `5a0107`；`MbbProtocolMultiChannelApi` / `ResultListenerAdapter` | 5 秒超时 → `ResultListenerAdapter onFailed2001` → `--get device info error`；注意这两个是**日志 TAG 字符串不是类名** | `:443-451`，`:496-505` |
| 7 | `--get device info error` 归属类 | 同上 | `y2.c$b`：字段 `a : Ly2/c;`、`<init>(Ly2/c;)V`、`a(I)V`(onFailed)、`b(Object)V`(onSuccess 桥)、`c(Lf3/d;)V`（载荷 = `f3.d`） | 文档给出两种处理：`a(I)` 吞掉不 `proceed()`，或伪造 `c(f3/d)` | `:516`，`:519-527`，`:549`，`:579-582` |
| 8 | 「是否受支持」判定 | 同上 | `y2.c.f(Bundle)` / `y2.c.g(Bundle)` / `y2.c.h(String):boolean` / `y2.c.i(Bundle,int,Messenger)` | 字符串含 `third_device_result` / `result` / `Current device's modelID： ` / `is supported by AudioKit:` / `00VVD9` / `not supported by AudioKit:`；`i` 涉及 `"MenuKey"/"status"/"progress"` | `:531-540` |
| 9 | 改写 `y2.c.h` 的实测结论 | 同上 | `y2.c.h(String):boolean` | 强制 `true` 后设备信息仍 5 秒超时（`y2.c$b.onFailed 2001`）→ 不是唯一卡点 | `:557-558`，`:570-573` |
| 10 | 连接状态 / 设备信息查询 | 同上 | `b2.a.a(String):int`（`checkConnectionState`）、`b2.a.d(String):Bundle`（`getDeviceInfo`）、`b2.a.l(String):boolean`、`b2.a.k(Application):void`（初始化）、`c/e/f/g/j/i/h/b`（各类查询） | `b2.a` = 日志 TAG `EarphoneHelper`；`d` 返回 null 是空值来源 | `:742-750`，`:766-768` |
| 11 | uiconfig 加载器 | 同上 | `j2.a.r0:(Ljava/lang/String;)Lcom/hihonor/earphonesdk/uiconfig/entity/UiConfigLite;`、初始化入口 `j2.a.q:()V` | `"uiconfig/" + prodId + ".json"`；`q()` 调 `r0` 并调 `i2.x.q0(File)` 解析 | `:701-710`，`:724-727` |
| 12 | uiconfig 访问器（FCPG 注入点） | 同上 | `i2.x`（Superclass `i2.y`）：字段 `b : UiConfigLite`、`B(UiConfigLite): UiConfigLite$Homepage`、`E`/`K(Homepage): NoiseControl`、`H`/`J(NoiseControl): Integer`、`<init>(Lb2/b;)V` | 入参为 null 时反射取 `homepage` 返回，绕过 `j2.a.q()` 死代码链 | `:827-841`，实测 `installed: i2.x.B(UiConfigLite) [注入 FCPG 配置]` `:843-845` |
| 13 | productId / supportJsonName 提供方 | 同上 | `com.hihonor.commonutils.bean.SupportEarphoneListOuc$EarphoneBaseInfo#getProductId()` / `#getSupportJsonName()` | 让第三方耳机返回 `FCPG` → 加载 `uiconfig/FCPG.json` | `:172-175`，`:185`，`:250-251`，`:319` |
| 14 | 顶层菜单表解析 | 同上 | `com.hihonor.commonutils.o0`：`i(String):boolean`(`isSupportDeviceListOUC`)、`d(String):String`(`getBaseJsonNameFromProductId`，读 `assets/<name>.json`)、`e`/`f`/`g`/`h`/`j` | `o0.d` 返回 `FCPG` 会加载不存在的资源（`FCPG.json` 只在 `assets/uiconfig/` 子目录） | `:665-674`，`:661-662`，`:680-682` |
| 15 | Activity 入口 MAC 解析 | 同上 | `com.hihonor.earphone.ui.activity.BaseActivity.d0(Intent):String` | MAC 来源优先级：`getStringExtra("mac")` → `"mac".toUpperCase(Locale.ROOT)` 即 `"MAC"` → `getBundleExtra("menuParameter")` 的 `b.getString("mac","")` | `:302-311`，`:317` |
| 16 | MAC 合法性校验 | 同上 | `com.hihonor.commonutils.c.a(String):boolean` | 为假则 `LogUtils.e("BaseActivity","onCreate: mac is invalid, return")` + `finish()` | `:290-297`，`:318` |
| 17 | 面板启动 action / 组件 | 同上 | action `com.hihonor.audioaccessorymanager.EARPHONE_DETAILS` → `com.hihonor.earphone.homepage.EarphoneDetailsActivity` | 必须显式 `-n` 指定组件，否则解析到 Settings | `:272-278`，`:689-694` |
| 18 | 下发层（不可直接 hook） | 同上 | `productconnect.bluetoothlib.c`（方法全为 `a..z`）；`MbbApi.NOISE_LIST` 静态字段 = `{1,2,0}`，模式映射 `0,1,2,3,4,-1` | 真正下发指令的类 | `:320-322` |
| 19 | 面板类清单（入口全景） | 同上 | `EarphoneDetailsActivity` / `EarphoneDetailsViewModel` / `NoiseControlViewModel` / `BaseViewModel` / `FunctionControlActivity`/`ViewModel` / `GestureControlActivity`/`ViewModel` / `FindDeviceActivity` / `EarphoneService` / `BaseModeSwitchActivity`（字符串 `"NoiseCancelingState"` 在此）/ `UiConfigLite$NoiseControl` / `DeviceProperty` / `MbbApi` | 面板全部 UI 类 | `:30-42` |
| 20 | `NoiseControlViewModel` 混淆情况 | 同上 | 被混淆成 `R(I)V` / `U(I)V` / `V(I)V` / `W(I)V` / `Y(ChoiceUiInfo,[Integer,I)V` | 需面板能打开后再定位；具体读写签名**文档未给出** | `:321`，`:120`，`:195` |

### 1.2 降噪 UI 真身 —— 进程 `com.android.settings`

> `com.android.settings` → `/system/priv-app/Settings/Settings.apk`（`magicos11-integration.md:14`）。
> `Round 11–14` 结论：荣耀降噪 UI 全套在 `com.android.settings` 里（`magicos11-integration.md:913`）。

| 序号 | 环节名称 | 目标进程/包名 | 类名#方法 | 下游作用 | 行号 |
|---|---|---|---|---|---|
| 21 | 页面入口与三次早退 | `com.android.settings` | `DeviceProfilesSettings.onCreate` | 早退① `getArguments()==null` → `SettingsPreferenceFragment.finish()`（`:950`）；早退② `mManager == null`（`:974`）；早退③ `"Device not found, cannot connect to it"` → `finish()`（`:977`） | `:941-957`，`:969-981` |
| 22 | controller 列表字段 | 同上 | `DeviceProfilesSettings` 字段 `.n: java.util/List` | `LdacSettingsController` / `profilesettings.a` / `NoiseControlController`(→`.O`) / `NoiseControlPanelController`(→`.P`) 全 `List.add(.n)` | `:944`，`:953-954`，`:960` |
| 23 | ★ 最终开关（数据到达） | 同上 | `DeviceProfilesSettings$a.onGetDataSucceed(String)` | 「`onGetDataSucceed` 就是最终开关」；方法表另有 `onGetDataFailed(String)` / `onSetConfigFailed(String,String)` / `onSetConfigSucceed(String,String)`，字段 `a : DeviceProfilesSettings`，在 `<init>` 偏移 0034 被 new | `:1016`，`:1026-1032`，`:1042-1044` |
| 24 | 实例捕获 + 主动触发 | 同上 | hook `DeviceProfilesSettings$a.<init>` + `Fragment.onCreate` 内 `postDelayed(1500ms)` → `invoke(onGetDataSucceed, mac)` | 首次打通全链（`:1050-1056`） | `:1046-1047` |
| 25 | 显示用 Runnable A | 同上 | `DeviceProfilesSettings$$ExternalSyntheticLambda2.run()` → `M2(Settings,[B)V` → `j3([B)V` → `NoiseControlController.displayPreference(PreferenceScreen)` | 由 `DeviceProfilesSettings.q2(String, byte[])` 在 |0019 处 new | `:989`，`:993-994`，`:1018-1019` |
| 26 | 显示用 Runnable B | 同上 | `DeviceProfilesSettings$a$a.run()` → `P2(Settings,String)V` → `b3(String)V` → `NoiseControlController.displayPreference` | 由 `DeviceProfilesSettings$a.onGetDataSucceed(...)` 在 |0028 处 new | `:990`，`:1016-1017` |
| 27 | 降噪开关行 controller | 同上 | `hwcontrollers/ancsettings/AncSwitchController` | 降噪开关行；已 hook「强制可用 + displayPreference 观察」 | `:919`，`:936` |
| 28 | 降噪控制行 controller | 同上 | `hwcontrollers/profilesettings/NoiseControlController` | 静态 `KEY_BT_NOISE_CONTROL` / `KEY_BT_NOISE_CONTROL_CATEGORY` / `ORDER_NOISE_CONTROL` / `ORDER_NOISE_CONTROL_CATEGORY` / `TAG`；字段 `mActiveDevice(Lx7/a;)` / `mBluetoothAddress(String)` / `mHwPreferenceCategory(HwPreferenceCategory)` / `mPreference(Preference)`；方法 `<init>(Context,x7.a)` / `buildPreference()` / `buildPreferenceCategory(PreferenceScreen)` / `initExtras()` / `displayPreference(PreferenceScreen)` / `getAvailabilityStatus()I` / `getIntentFilter()` / `hasAsyncUpdate()` / `isSliceable()` / `release()` | `:920`，`:936`，`:1082-1088` |
| 29 | 降噪面板行 controller | 同上 | `hwcontrollers/profilesettings/NoiseControlPanelController` | 实现 `$a.onMultiStateClicked(IB)V`；另有 `q2(String,[B)` 调 `onDataReceive(String,[B)` | `:921`，`:937`，`:954`，`:957`，`:1236` |
| 30 | 长按降噪行 | 同上 | `hwcontrollers/profilesettings/LongPressNoiseController` / `LongPressNoiseTipController` | 长按相关行 | `:922` |
| 31 | 「通用设置」容器 | 同上 | `hwcontrollers/generalsettings/ProfileContainerPreferenceCategoryController` | 「通用设置」容器行 | `:923` |
| 32 | 其它行 controller | 同上 | `hwcontrollers/profilesettings/HighQualityMenuController` / `RenameDeviceController` / `LdacSettingsController` | 高音质 / 重命名 / LDAC 行 | `:924`，`:978` |
| 33 | 另一降噪入口 | 同上 | `com.android.settings.bluetooth.BluetoothAncSettings` | 「另一个降噪入口」 | `:925` |
| 34 | 降噪设置页（fragment） | 同上 | `BluetoothNoiseControlSettings`；`NoiseControlSettings (+$a)` | `BluetoothNoiseControlSettings` 未在 manifest 声明 → `am start -n` 报 `does not exist`；只能走 `com.hihonor.settingslib.SubSettings` + `--es :settings:show_fragment` | `:916-918`，`:1185-1186`，`:1195-1205` |
| 35 | ★ displayPreference 内最终开关 | 同上 | `x7.a.r():Boolean` | `mActiveDevice.r():Z`，`0008 if-nez` → `r()==false` 直接 `return`，preference 永不 add；强制 `true` 后降噪行出现 | `:1096-1097`，`:1108-1113`，`:1247` |
| 36 | 三态开关控件 | 同上 | `com.android.settings.preference.MultiStateSwitchingPanelPreference`：`onClick(View)` / `Q(B)` / `R(a)` | `tag=view.getTag()` → index；`item=a.get(index)`；`mode=$b.a(item):B`；再 `this.Q(mode)` + `cb.onMultiStateClicked(index, mode)`；hook `R(a)` 抓实例后对 `declaredMethods` 动态 hook | `:1233-1235`，`:1248-1250` |
| 37 | 主行 preference key | 同上 | `NoiseControlController.buildPreference()`：`new StatusPreference(mContext)`、`setKey("key_bt_noise_control")`、`setPersistent(false)`、`setWidgetLayoutResource(0x7f0d057b)`、`setOrder(410)` | 主行 key 是 `key_bt_noise_control`（**不是** `key_denoise`）；`key_denoise`/`key_hear_through`/`key_denoise_close` 是 widget 内三个按钮；无 `setEnabled`/`setOnPreferenceClickListener` | `:1168-1180` |
| 38 | 主行点击去向 + extras | 同上 | `NoiseControlController.initExtras()` | `setFragment("...BluetoothNoiseControlSettings")`；`putString("current_device_id", mBluetoothAddress)`；`putString("current_device_modelid", ...)` | `:1182-1191` |
| 39 | 下发回调接口 | 同上 | `$a.onMultiStateClicked(IB)V`（接口，**不可 hook**，实现类 `NoiseControlPanelController`） | Settings → 广播的出口 | `:1249`，`:1236` |

### 1.3 指令下发行 —— 进程 `com.android.bluetooth`

> `com.android.bluetooth` → `/apex/com.android.bt/app/Bluetooth@CP2A.260605.016/Bluetooth.apk`（`magicos11-integration.md:15`）。

| 序号 | 环节名称 | 目标进程/包名 | 类名#方法 | 下游作用 | 行号 |
|---|---|---|---|---|---|
| 40 | 广播接收 | `com.android.bluetooth` | `RfcommController.safeHandleBroadcast`（TAG = `MagicOriG-Rfcomm`） | 接收 `ACTION_ANC_SELECT` | `:1238`，`:1251-1252`，`:1270` |
| 41 | UI 事件分流 | 同上 | `safeHandleUIEvent` → `setANCMode(status)` | 转成 ANC 模式 | `:1239` |
| 42 | 下发方法 | 同上 | `RfcommController.setANCMode(Int)` | → `sendPacketSafe`；内部有 `if (mode == currentAnc) return` 早退 | `:1253`，`:1268` |
| 43 | SPP 帧编码 | 同上 | `Enums.ANC_*` → `sendPacketSafe` → SPP `0x4E` 帧 | 耳机切换档位 | `:1240` |
| 44 | 读回状态 | 同上 | `RfcommController.currentAnc` | 早期方案里作为「读」的返回值 | `:114`，`:188` |
| 45 | 登记开关（已推翻为主路径） | 同上 | `com.hihonor.earphonesdk.uitl.SupportedDeviceSettings`：`b(ContentResolver): Set<String>`、`d(ContentResolver, String)`(`saveSupportedDevice`) | 读写 `Settings.Global["supported_earphone_device"]`；调用方 `EarphoneService`、`BatteryAppWidgetProvider`、`earphonesdk.uitl.j` | `:59-76` |
| 46 | 连接标志（门控） | 同上 | `HeadsetStateDispatcher` A2DP 状态回调（CONNECTED→1 / DISCONNECTED→0） | 写 `Settings.Global[magicorig_connected]` | `:1612-1615` |
| 47 | 通知停用 | 同上 | `showBtNotification`（停用 + cancel 清残留） | 电量通知改写战场迁移 | `:1597` |

### 1.4 通知改写 —— 进程 `com.android.systemui`

> `com.android.systemui` → `/system/priv-app/SystemUI/SystemUI.apk`（`magicos11-integration.md:16`）。

| 序号 | 环节名称 | 目标进程/包名 | 类名#方法 | 下游作用 | 行号 |
|---|---|---|---|---|---|
| 48 | SystemUI 入口 | `com.android.systemui` | `SystemUIService.onCreate` + 候选列表 | 替代不存在的 `CentralSurfacesImpl` | `:600` |
| 49 | 通知内容改写（终极方案） | 同上 | `RemoteViews.apply` / `RemoteViews.reapply` | View 树 = 渲染前最后一刻；实拍「原道 OriG in / 左耳 100% 右耳 100%」 | `:1606-1608` |

### 1.5 设备中心链路 —— 进程 `com.hihonor.controlcenter`

> `/system/priv-app/DeviceControlCenter/DeviceControlCenter.apk`，文档称其为「降噪卡渲染地：`NoiseControl x12`、`Earphone x172`」（`magicos11-integration.md:1302`）。
> 用户指正：设备中心在 `com.hihonor.nearby` / `com.hihonor.synergy`，卡片渲染在 controlcenter，数据在 nearby（`magicos11-integration.md:1293-1294`）。

| 序号 | 环节名称 | 目标进程/包名 | 类名#方法 | 下游作用 | 行号 |
|---|---|---|---|---|---|
| 50 | 设备列表总闸 | `com.hihonor.controlcenter` | `ControlCenterProvider.getDeviceList(): DccResult` | `dm = profile.getDeviceManager()`；`dm == null || !dm.hasConnected()` 返 err；`for (Device d : dm.getDevices())` 是**唯一设备来源** → `new LinkDevice(getDeviceUdid(d), name, type, nodeId, false, online, prodId)`；`result.setExt(JsonUtils.sGson.toJson(list))` | `:1349-1356` |
| 51 | 单设备查询 | 同上 | `ControlCenterProvider.getLinkDeviceFromDeviceManager(udid)` | `dm.getDeviceByUdid(udid)`，null 即消失 | `:1357-1359` |
| 52 | 列表注入（Hook #1） | 同上 | `ControlCenterProvider.getDeviceList()` | ext JSON 数组 append 我们的 `LinkDevice`（纯字符串、已含则跳过、ext 异常不破坏） | `:1376` |
| 53 | 降噪卡数据（Hook #2） | 同上 | `EarphoneNoiseData.fromBatteryData(BatteryData)` | 原返回 **null 才**注入 `[NOISE_CANCELLATION, AWARENESS, OFF]` + 按 `Settings.Global[magicorig_last_anc]` 恢复 currentMode；非 null 不干预 | `:1377`，`:1320-1328` |
| 54 | 卡内下发（Hook #3） | 同上 | `PropertyUtils.setNoiseCtrlMode(String, int, cb)`（static） | `nc1→readLastAnc()`(用户默认档位 5/4/3)、`nc2→2`、`nc0→1` → 广播 `ACTION_ANC_SELECT` → 复用 bluetooth SPP `0x4E` 链路；`proceed()` 保留原回调复位 `isSetting` | `:1337-1338`，`:1378` |
| 55 | 点击入口（Hook #4 观察点） | 同上 | `EarphoneNoiseSectionController.onNoiseModeClick(NoiseMode)` | 4 个早退：`showStatus==DISABLED` / `isSetting` / `isWearStateDisabledMode(mode)` / 已是当前模式 | `:1333-1334`，`:1379` |
| 56 | 卡片数据装配 | 同上 | `DeviceServiceCardDataMgr.createDeviceServiceCard` / `genDeviceServiceInfo(devId,…)` / `DeviceServiceCardOffline.genOfflineDeviceServiceInfo(…)` | `online = ballView.isOnline()` 是分流开关；`dev = c2.s.n().o(devId)`；`dev == null` → `log("linkDevice is null")` + `return null` → sections=null 根因 | `:1480-1489` |
| 57 | 三道门 | 同上 | `createSections(config, selfDev, remoteDev, rule)`：门① `i2.b.g(dev)` 位掩码；门②③ `config.hasSection(EARPHONE_BATTERY / EARPHONE_NOISE)` | 门① `(type.index & EarPhone.index) \|\| (type.index & Third_EarPhone.index)`；`REMOTE_EARPHONE` 的 mask 由 `<clinit>` or 进两段；`selectLayout` 对 `Third_EarPhone` 正确进 `REMOTE_EARPHONE` | `:1490-1495` |
| 58 | 卡片段创建 | 同上 | `createEarphoneBatterySection(LinkDevice)` / `createEarphoneNoiseSection(LinkDevice)` / `DeviceServiceInfo{deviceId, deviceName, batteryLevel, serviceSections, isOnline}` | 无条件建卡，数据靠 `fromBatteryData` 注入；`createEarphoneNoiseSection` 唯一退出 = `LinkDevice` null | `:1309-1314`，`:1317-1328`，`:1495` |
| 59 | 渲染分发 | 同上 | `DeviceServiceCardAdapter.bindServiceSections` → `addServiceSection(LinearLayout, ServiceSection)` → `EarphoneNoiseSectionController.addEarphoneNoiseSection(layout, section, str)` | 卡片段落到 View | `:1313-1314` |
| 60 | 球数据出口（注入点①②③） | 同上 | `DeviceBallManager`：`getDeviceInfoList()` / `getAllDeviceInfo()`（注入点①）、`getShowRemoteDeviceInfoList()`（注入点②）、`getShowRemoteDeviceNum()`（注入点③ 0→1）、`loadFromProvider()`/`loadDeviceDataJson()` | 数据源与 UI 过滤出口 | `:1432-1436` |
| 61 | 球创建与 gridIndex | 同上 | `DeviceBallLayoutManager.createRemoteDeviceBalls()` + `SortUtils.DeviceSortUtils(list)` + `new BasicDeviceBallViewAdapter(context, info)` | `gridIndexMap` 从同一 list 生成；`无 id → NPE`；`BasicDeviceBallViewAdapter` 是「★ 球出生点」 | `:1437-1448` |
| 62 | DeviceInfo 反射补字段 | 同上 | 7 参构造 `(id,name,type,index,isOnline,isSelf,isShow)` + 反射 `setProdId("00VVD9")` / `setBtMacAddress(MAC)` / `setName` / `setIndex(1)` | 该构造**不含 prodId/btMac**；`prodId null → 图标解析失败静默不渲染` | `:1450-1451` |
| 63 | deviceInfoMap 兜底 | 同上 | `DeviceBallManager.deviceInfoMap`（HashMap，与列表出口独立）、`getDeviceInfoById`、`updateDeviceOnlineStatus` | map 无我们的设备 → `getDeviceInfoById` 返 null → `updateDeviceOnlineStatus` 空转 / `createEarphoneNoiseSection` 拿不到 `batteryData` | `:1505-1509` |
| 64 | LinkDevice 注册表兜底（文档称「第10环」） | 同上 | `c2.s.o(deviceId): LinkDevice`（另有 `c2.s.n().o(devId)`、`c2.s.n().u(...)`） | 查不到 → gen 早退 return null → createSections 永不执行；null 时注入 7 参 `(udid=MAC, name, type=Third_EarPhone, nodeId=MAC, isLocal=false, online=[], prodId=00VVD9)` | `:1511-1516`，`:1484`，`:1488` |
| 65 | 在线态断根（文档称「第 10-11 环」） | 同上 | `BasicDeviceBallView.isOnline()` | `chain.thisObject` + `getDeviceId()==OUR_MAC` → 恒 true；根因是 `DeviceProductManager$3.onPropertyInfoChange` 会把 `setIsOnline(true)` 重置回 false | `:1543-1546` |
| 66 | 在线态主动设置 | 同上 | `adapter.setIsOnline(true)` + `DeviceBallManager.updateDeviceOnlineStatus(MAC, true)` | 离线卡只有响铃/连接/设置，无电量无降噪 | `:1500-1503` |
| 67 | 卡片电量打桩 | 同上 | `createDeviceServiceCard` 电量段（`0055 isOnline` 门 → `007d getBatteryLevel`）；`genDeviceServiceInfo` 返回前 `info.setBatteryLevel(跨进程电量)`；`ballView.getBatteryLevel` 断根 | 电量段实测零触发 → 升级为上游打桩；三重保险见 `:1642-1644` | `:1617-1622`，`:1642-1644` |
| 68 | 模块作用域接线 | 同上 | `HookEntry` 分支 / `scope.list` / `strings.xml` xposedscope 均加 `com.hihonor.controlcenter` | scope 生效 | `:1381`，`:1407-1408` |

---

## 2. 各进程关键类清单

### 2.1 `com.android.settings`（`/system/priv-app/Settings/Settings.apk`，`:14`）

| 类/入口 | 说明 | 行号 |
|---|---|---|
| `Settings$DeviceProfilesSettingsActivity` | 蓝牙详情页（AOSP 的，重命名/设备类型/音频开关）；转发目标 | `:236`，`:567`，`:858`，`:867` |
| `DeviceProfilesSettings` | 荣耀降噪 UI 宿主；字段 `.n: java.util/List`、`.O`、`.P`、`.a` | `:941-960`，`:975` |
| `DeviceProfilesSettings$a` | `onGetDataSucceed` 所属；字段 `a : DeviceProfilesSettings`，`<init>` 偏移 0034 | `:1016`，`:1042-1044` |
| `DeviceProfilesSettings$a$a` / `DeviceProfilesSettings$$ExternalSyntheticLambda2` | 两个显示用 Runnable | `:989-990` |
| `DeviceProfilesSettings.M2` / `P2` / `j3` / `b3` / `q2` | `(LDeviceProfilesSettings;[B)V` / `(…;Ljava/lang/String;)V` / `j3([B)V` / `b3(String)V` / `q2(String,[B)` | `:989-994`，`:1018` |
| `BluetoothNoiseControlSettings` | 降噪控制页（fragment，manifest 未声明） | `:916`，`:1185-1186`，`:1195` |
| `NoiseControlSettings (+$a)` | 降噪设置页 | `:918` |
| `BluetoothAncSettings` | 另一个降噪入口 | `:925` |
| `AncSwitchController` / `NoiseControlController` / `NoiseControlPanelController` | 三个降噪 Controller（hook 成功记录） | `:919-921`，`:936` |
| `LongPressNoiseController` / `LongPressNoiseTipController` / `HighQualityMenuController` / `RenameDeviceController` / `LdacSettingsController` / `ProfileContainerPreferenceCategoryController` | 各 preference controller | `:922-924` |
| `com.hihonor.settingslib.SubSettings` | 承载 `DeviceProfilesSettingsActivity` 的页面容器；未导出，需 `su -c` | `:871`，`:894`，`:1197-1198` |
| `com.android.settings.preference.MultiStateSwitchingPanelPreference` | 三态开关控件 | `:1248` |
| `x7.a.r():Boolean` | 降噪行最终开关 | `:1247` |

### 2.2 `com.android.bluetooth`（`/apex/com.android.bt/...Bluetooth.apk`，`:15`）

| 类/入口 | 说明 | 行号 |
|---|---|---|
| `RfcommController` | `safeHandleBroadcast` / `safeHandleUIEvent` / `setANCMode(Int)` / `currentAnc` / `sendPacketSafe`；TAG = `MagicOriG-Rfcomm` | `:1238-1240`，`:1252-1253`，`:1268`，`:1270` |
| `Enums.ANC_*` | ANC 模式常量 | `:1240` |
| `A2dpService` | 继承链无 `mContext`，实例本身即 `ContextWrapper` | `:599` |
| `HeadsetStateDispatcher` | A2DP 状态回调源 | `:1613` |
| `showBtNotification` | 已停用的通知出口 | `:1597` |
| `com.hihonor.earphonesdk.uitl.SupportedDeviceSettings` | `supported_earphone_device` 读写 | `:59` |
| `BatteryAppWidgetProvider` / `earphonesdk.uitl.j` / `EarphoneService` | `SupportedDeviceSettings` 调用方 | `:76` |

### 2.3 `com.android.systemui`（`/system/priv-app/SystemUI/SystemUI.apk`，`:16`）

| 类/入口 | 说明 | 行号 |
|---|---|---|
| `SystemUIService.onCreate` | hook 入口（MagicOS 无 `CentralSurfacesImpl`） | `:600` |
| `RemoteViews.apply` / `RemoteViews.reapply` | 通知改写终极落点 | `:1606-1608` |

### 2.4 `com.hihonor.audioaccessorymanager`（`/system/app/AudioAccessoryManager/AudioAccessoryManager.apk`，`:17`）

见 §1.1 表；另有类清单原文 `:30-42`。补充：

| 类/入口 | 说明 | 行号 |
|---|---|---|
| `MbbApi` | 蓝牙能力层；方法 `getNoiseControlFunction` / `setNoiseControlFunction` / `getNoiseControlLoopList` / `setNoiseControlLoopList` / `getNoiseControlLoopFocus` / `setNoiseControlLoopFocus` / `setNoiseControlLevel` / `setNoiseControlLoop` / `setNoiseControlState, noiseControlState=...` | `:42`，`:45-53` |
| `MbbApiHelper`（`$a`~`$j` 十个内部类） | 非混淆外层类，「很可能是协议桥的正确切入点」 | `:377` |
| `MbbApiHeper$a..j` | 同构（注意 Honor 拼写是 `Heper` 不是 `Helper`） | `:493` |
| `b2.a` | 日志 TAG `EarphoneHelper` | `:742` |
| `y2.c` / `y2.c$b` | 「是否受支持」判定 / 设备信息失败归属 | `:510-540` |
| `f4.b$a` | `getSmartConnect` 回调实现（运行时类名，静态分析会误判） | `:384-403` |
| `j2.a` / `i2.x` / `i2.y` | uiconfig 加载器 / 访问器 | `:698-710`，`:827-837` |
| `s0.b$a` | `--response not success` 归属类 | `:793` |
| `com.hihonor.commonutils.o0` / `com.hihonor.commonutils.c` | 支持列表解析 / MAC 校验 | `:176`，`:318` |
| `com.hihonor.earphone.homepage.BaseViewModel` / `EarphoneDetailsViewModel` | 面板 VM | `:31`，`:33` |
| `com.hihonor.productconnect.bluetoothlib.c` | 真正下发指令的类，方法全 `a..z`，不可直接 hook | `:322` |

### 2.5 `com.hihonor.controlcenter`（`/system/priv-app/DeviceControlCenter/DeviceControlCenter.apk`，`:1302`）

| 类/入口 | 说明 | 行号 |
|---|---|---|
| `ControlCenterProvider.getDeviceList()` | 设备列表总闸 | `:1350`，`:1376` |
| `ControlCenterProvider.getLinkDeviceFromDeviceManager(udid)` | 单设备查询 | `:1357` |
| `DeviceServiceCardDataMgr` | `genDeviceServiceInfo` / `createDeviceServiceCard` | `:1309`，`:1480` |
| `EarphoneNoiseSectionController` | `addEarphoneNoiseSection` / `onNoiseModeClick(NoiseMode)` / `highlightSingleMode` | `:1314`，`:1333-1339` |
| `EarphoneNoiseData` | `fromBatteryData(BatteryData)` / `toNcModeValue(mode)` / `setSupportedModes` / `setCurrentMode` | `:1320-1324`，`:1336`，`:1342` |
| `NoiseMode`（枚举，`:NoiseMode` 内） | `NOISE_CANCELLATION` / `AWARENESS` / `OFF` / `ADAPTIVE` / `UNKNOWN` | `:1345` |
| `PropertyUtils.setNoiseCtrlMode(String,int,cb)` | 静态下发；回调类型 `c2.s$b` | `:1337-1338` |
| `DeviceBallManager` | `getInstance` / `loadFromProvider` / `loadDeviceDataJson` / `getDeviceInfoList` / `getAllDeviceInfo` / `getShowRemoteDeviceInfoList` / `getShowRemoteDeviceNum` / `updateDeviceOnlineStatus` / `deviceInfoMap` / `getDeviceInfoById` | `:1432-1436`，`:1500-1509` |
| `DeviceBallLayoutManager.createRemoteDeviceBalls` | 球创建 | `:1437` |
| `SortUtils.DeviceSortUtils` | 过滤 `isSelf` → `hasValidIndex(index≥0)` → `assignAndFixIndexes` / `assignIndexesBySort` → `result.put(info.getId(), info.getIndex())` | `:1439-1442` |
| `BasicDeviceBallViewAdapter` / `BasicDeviceBallView` | 球 View / `isOnline()` 断根 | `:1447`，`:1545` |
| `c2.s` | `n()` / `o(deviceId)` / `u(...)` 注册表 | `:1484`，`:1513` |
| `i2.b.g(dev)` | 门① 位掩码判定 | `:1491` |
| `DeviceLayoutConfig.selectLayout(type, isSelf, true)` / `config.hasSection` | 布局与段位掩码 | `:1487`，`:1492` |
| `DeviceProductManager$3.onPropertyInfoChange` | 在线态被重置的根源 | `:1544` |
| `NewControlHomeActivity` / `DeviceManagerCenterActivity` / `ShowHiddenDevicesActivity` | 拉起入口（`honor.action.control_home`） | `:1425-1427` |
| `DFXUtils.reportEarphoneModeAsync` | 点击后 DFX 上报 | `:1339` |
| `c2.s$b` | 回调 `onResult(String)` / `onError(Exception)` / `onResultWithKey(Map)` | `:1338` |

### 2.6 `com.hihonor.nearby` / `com.hihonor.synergy`（数据侧）

| 项 | 说明 | 行号 |
|---|---|---|
| `com.hihonor.nearby` → `/system/app/HnNearby/HnNearby.apk` (45MB) | 内嵌 devicemanager 服务族 `DeviceManagerService` / `BleDeviceManagerService` / `DeviceInfoService` + `message/EarphoneCfgChangeMessage`；dex 中文仅"耳机"×2，**无降噪 UI** | `:1300` |
| `com.hihonor.synergy` → `/system/app/Synergy/Synergy.apk` | 纯协同服务（通知/消息共享），与耳机降噪无关 | `:1301` |

---

## 3. prodId / uiconfig 机制

### 3.1 设备伪装（文档描述的路径演进）

| 阶段 | 内容 | 行号 |
|---|---|---|
| 方案①（后被推翻） | 耳机连接时把 MAC 追加进 `Settings.Global["supported_earphone_device"]` | `:89-90`，`:111-112`，`:183` |
| ❌ 推翻 | 该键**不是**荣耀自家耳机的登记机制：X6 ACL 已连接时该键仍为 `null` → 「**不能作为接入的主路径**」 | `:211-220` |
| 方案② | hook `SupportedDeviceSettings.b()` 让返回值恒含该 MAC | `:112` |
| 方案③（文档推荐路线 A） | hook `SupportEarphoneListOuc$EarphoneBaseInfo#getProductId()`/`#getSupportJsonName()`，让第三方耳机返回 `FCPG` → 加载 `uiconfig/FCPG.json` | `:185`，`:250-251` |
| 方案④ | hook `UiConfigLite` 解析，给 `homepage` 注入 `noiseControl: {noiseSwitchNum: 4}` | `:186` |
| 实测结论 | 设备被判定为 modelId `00VVD9` = Third（`baseSupportDeviceList.json` 的第三方兜底项）；`Third` → `assets/Third.json`，只有 `SWITCH_CONNECT_DIALOG` + `goto_magichome`，**没有 `noiseControl`** | `:676-679` |
| 修正 | `FCPG.json` 只在 `assets/uiconfig/` 子目录，顶层没有 → 改写 `o0.d` 返回 `FCPG` 会加载不存在的资源（已回退为纯观察）→ **正确的 hook 点是 uiconfig 加载器** | `:680-682` |

### 3.2 图标资源查找

| 文档内容 | 行号 |
|---|---|
| 蓝牙设置列表对照：X6 = 荣耀自家耳塞专用图标，OriG in = 通用耳机图标 | `:226` |
| `ImageLoadUtils: --Third res state === REQUEST_SUCCESS`（Third 资源已加载） | `:778` |
| `DeviceInfo` 注入时 `prodId null → 图标解析失败静默不渲染` | `:1451` |

> 图标资源的具体查找方法/类名（如按 prodId 查 drawable 的方法）：**文档未给出**。

### 3.3 uiconfig 结构

```jsonc
"homepage": {
  "noiseControl": { "noiseSwitchNum": 4, "wearNoiseSwitchPolicy": 1 }
}
```
（`magicos11-integration.md:154-159`，示例为 `FCPG.json`）

| 事实 | 行号 |
|---|---|
| `assets/` 下按 **4 位机型码** 存能力配置 | `:128` |
| `assets/baseSupportDeviceList.json`（荣耀/华为自家）字段：`modelId` / `productName` / `productId` / `supportJsonName` | `:132-137` |
| 第三方专用兜底项：`{ "modelId": "00VVD9", "productName": "Third", "productId": "Third" }` | `:139` |
| `assets/earphone_support_list_builtin.json`（含第三方 AirPods 与华为 FreeBuds）：`00FCPG` AirPods 4 降噪版、`00G64H` AirPods 4 非降噪版、`00M0VQ` AirPods Pro 2、`00EFYB` AirPods Pro 2 USB-C、`00S91A` AirPods Pro 1、`00FDAQ` AirPods Pro 3、`00SWEH` FreeBuds 6、`00GS26` FreeBuds Pro 4 | `:141-148` |
| `assets/uiconfig/<CODE>.json` → `UiConfigLite` | `:152` |
| **`homepage.noiseControl` 存在与否 = 面板显不显示降噪**，`noiseSwitchNum` = 档位数 | `:166` |
| `G64H.json`（AirPods 4 非降噪版）没有 `noiseControl` 键 | `:162-163` |
| `uiconfig/` 目录下只有 AirPods 系列：`EFYB/FCPG/FDAQ/G64H/M0VQ/MRCA/OSFI/PRUH/S91A/WEQW/X6MG` | `:167` |
| `HONOR Earbuds X6` 无降噪 → `Y3DR.json` 是菜单表（`menutype`/`menuinfo` 结构），且无 `uiconfig/Y3DR.json` | `:169-170` |
| `SupportEarphoneListOuc$EarphoneBaseInfo` 字段 `supportJsonName` / `productId` | `:174-175` |
| `com.hihonor.commonutils.o0` 读 `baseSupportDeviceList.json` 并按 `productId` 解析 | `:176` |
| `MagicHomeManager` 里常量 `"Third"` / `"THIRD_AUDIO_DEVICE"` | `:177` |
| 加载器反汇编：`"uiconfig/" + prodId + ".json"`、`"loadAssetsConfig: assets file empty for prodId: "`、`"loadAssetsConfig: json parse error for prodId: "`、`"loadAssetsConfig: success for prodId: "`、`"AirpodsUiConfig init for prodId: "`、`"init: local config invalid, fallback to assets"`、`"init: no local and assets config, sync download from cloud"` | `:701-710` |
| `j2.a.q()` 在全部 6 个 dex 中无任何调用方（死代码）→ `r0` 永不触发 | `:790-791` |
| `mUiConfigVersion='null'` → `UiConfigLite` 从未加载 | `:721`，`:784` |
| `b2.a.d(String):Bundle` 写入的 key：`device_general_name`(→`""`) / `device_submodel`(→`"0"`) / `device_uiconfig_version`(← `mUiConfigVersion` 的真正来源) / `device_earphone_manu` / `"00VVD9"` | `:797-803` |
| `i2.x` 字段 `b : UiConfigLite` 是「配置持有者（因 q() 死代码恒为 null）」；`i2.x.q0(File): UiConfigLite` 是解析器 | `:822-823`，`:831` |

### 3.4 `00VVD9` 全部出现位置

| 行号 | 上下文 |
|---|---|
| `:139` | `baseSupportDeviceList.json` 第三方兜底项 `modelId` |
| `:536` | `y2.c` 反汇编字符串之一 |
| `:542` | 「面板查 modelID → `00VVD9` 才算 Third 支持」 |
| `:547` | 路线 A：让我们的 MAC 对应到 `00VVD9`（Third） |
| `:661` | `o0.i(arg=00VVD9) => true`（`isSupportDeviceListOUC` 本就 true） |
| `:678` | 设备被判定为 modelId `00VVD9` = Third |
| `:682` | 让 `00VVD9/Third` 命中 `uiconfig/FCPG.json` |
| `:716` | `EarphoneInfo` 实测 `mModelId='00VVD9', mProductId='Third'` |
| `:737` | `b2.a` 日志 `mModelId='00VVD9'` |
| `:802` | `b2.a.d` 反汇编写出的字符串 |
| `:813` | 合成 Bundle `putString("modelId", "00VVD9")` |
| `:1204` | 启动参数 `--es current_device_modelid 00VVD9` |
| `:1207` | 说明来源：`EarphoneHelper` 实测的 `mModelId='00VVD9'` |
| `:1370` | 我们设备 `prodId=00VVD9` |
| `:1451` | 反射 `setProdId("00VVD9")` |
| `:1515` | 注入 7 参 LinkDevice `prodId=00VVD9` |

---

## 4. ANC 状态流转

### 4.1 Settings 侧：UI index → status → AncMode → SPP 帧（文档最终映射）

| UI 按钮 | index | status | `AncMode` | SPP 帧 | 行号 |
|---|---|---|---|---|---|
| **降噪** | 0 | **5** | **`EXPERIMENT` `0x10`** | `4E 05 00 00 01 02 10 00` | `:1259`，`:1263-1264` |
| 透传 | 1 | 2 | `TRANSPARENT` `0x01` | `4E 05 00 00 01 02 01 00` | `:1260` |
| 关闭 | 2 | 1 | `OFF` `0x00` | `4E 05 00 00 01 02 00 00` | `:1261` |

补充：改动前是 `status=3 → 0x02 NORMAL`（`magicos11-integration.md:1263`）；实测日志 `→→ SPP 写出 len=8 hex=4E 05 00 00 01 02 10 00 socket=true`（`:1264`）。

### 4.2 荣耀内部 status / 档位数值（文档给出的全部数字）

| 数字 | 文档给出的语义 | 行号 |
|---|---|---|
| `1` | 关闭（对应 `AncMode.OFF 0x00`） | `:1261` |
| `2` | 透传（对应 `AncMode.TRANSPARENT 0x01`） | `:1260` |
| `3` | `NORMAL`（`0x02`），「改动前」的降噪档 | `:1263` |
| `5` | 降噪（用户要求用实验性降噪 → `AncMode.EXPERIMENT 0x10`） | `:1259`，`:1263` |
| `5 / 4 / 3` | `readLastAnc()` 返回的「用户默认档位」 | `:1378` |
| `4` 的语义 | **文档未给出**（仅在 `5/4/3` 并列中出现） | — |

关于「荣耀内部 status 枚举的完整定义/名称表」：**文档未给出**。文档只给出上表这些点到点的数值对应。

### 4.3 controlcenter 侧：`toNcModeValue` 与 `NoiseMode`

- 反汇编 `{ordinal→value}` = `{0:1, 1:2, 2:0, 3:3, 4:-1}`（`magicos11-integration.md:1342`）
- 文档「推定枚举序」→ **`1=降噪(nc) 2=AWARENESS 0=OFF 3=ADAPTIVE -1=UNKNOWN`**（`:1343`）
- `NoiseMode` 枚举常量：`NOISE_CANCELLATION / AWARENESS / OFF / ADAPTIVE / UNKNOWN`（`:1345`）
- 映射函数 `EarphoneNoiseData.toNcModeValue(mode)`（`:1336`）
- 下发时 `nc1→readLastAnc()(用户默认档位5/4/3)`、`nc2→2`、`nc0→1`（`:1378`）

### 4.4 `PropertyUtils.setNoiseCtrlMode` 签名与调用方

| 项 | 文档原文/内容 | 行号 |
|---|---|---|
| 签名（调用形态） | `PropertyUtils.setNoiseCtrlMode(deviceId, ncValue, cb)`，注释「★ 下发（static）」 | `:1337` |
| 签名（hook 形态） | `PropertyUtils.setNoiseCtrlMode(String,int,cb)` | `:1378` |
| 回调类型 | `cb: c2.s$b { onResult(String), onError(Exception), onResultWithKey(Map) }` | `:1338` |
| 唯一调用方 | `EarphoneNoiseSectionController.onNoiseModeClick(NoiseMode)` | `:1333-1337`，`:1636` |
| 再上游 | `onNoiseModeClick` 唯一调用方 = `addEarphoneNoiseSection` 的 4 个按钮 lambda | `:1637` |
| 卡死根因 | 荣耀 MBB 通道对第三方设备**永不回调**（`onResult`/`onError` 都不来）→ `onNoiseModeClick` 的 `isSetting` 永真 → 一次点击后全部按钮被早退 | `:1582-1583` |
| 修法 | 广播后**模拟 `cb.onResult("")`**，并 `return@intercept null` 跳过原 MBB 调用 | `:1584-1585` |
| 返回值/参数类型的更多细节（如返回值类型） | **文档未给出** | — |

### 4.5 广播契约

| 项 | 值 | 行号 |
|---|---|---|
| action | `io.github.yingjfd.magicorgin.ACTION_ANC_SELECT` | `:1237` |
| extra 键名 | `status` | `:1251`，`:1579` |
| 方向 | Settings → bluetooth（「跨进程桥」） | `:1251` |
| 发送端 | `NoiseControlPanelController.onMultiStateClicked(int, byte)` | `:1236` |
| 接收端 | `RfcommController.safeHandleBroadcast` → `safeHandleUIEvent` → `setANCMode(status)` | `:1238-1239` |
| 类型陷阱 | `status` 曾是 **String** → `putExtra("status","1")` → bluetooth 端 `getIntExtra("status",0)` 类型不符返回 0 → `setAncMode(0)` 不在映射表 → 不发帧。**修：status 改非空 Int**（Kotlin `Int?` 会错选 Serializable 重载） | `:1579-1581` |
| 依赖 | receiver 依赖 `connectPod`；A2DP 未回连时广播无人接收（表现为「广播 result=0 但无日志」） | `:1271` |

### 4.6 完整链路（文档最终结论）

```
荣耀设置页 [噪声控制: 降噪|透传|关闭]
  → NoiseControlController.displayPreference          ← x7.a.r() 强制可用后才 add
  → MultiStateSwitchingPanelPreference.onClick(View)
       tag=view.getTag() → index ; item=a.get(index) ; mode=$b.a(item):B
       → this.Q(mode) ; cb.onMultiStateClicked(index, mode)
  → NoiseControlPanelController.onMultiStateClicked(int, byte)
  → 广播 io.github.yingjfd.magicorgin.ACTION_ANC_SELECT (status)
  → [bluetooth 进程] RfcommController.safeHandleBroadcast
  → safeHandleUIEvent → setANCMode(status)
  → Enums.ANC_* → sendPacketSafe → SPP 0x4E 帧 → 耳机切换
```
（`magicos11-integration.md:1230-1241`）

---

## 5. 电量数据源

| 项 | 文档内容 | 行号 |
|---|---|---|
| 读取点 | `data = EarphoneNoiseData.fromBatteryData(info.getBatteryData());   // DeviceBallManager 查` | `:1320` |
| 注入签名 | `EarphoneNoiseData.fromBatteryData(BatteryData)` | `:1377` |
| 注入条件 | 原返回 **null 才**注入 `[NOISE_CANCELLATION, AWARENESS, OFF]` + 按 `Settings.Global[magicorig_last_anc]` 恢复 currentMode；非 null 不干预 | `:1377` |
| 空值分支 | `if (data == null)` → `new EarphoneNoiseData()`、`setSupportedModes(new ArrayList<>())`（空 → 按钮全灰）、`setCurrentMode(NoiseMode.UNKNOWN)` | `:1321-1324` |
| 显示条件 | `DeviceServiceInfo.hasBatteryInfo() = batteryLevel > 0`（`if-lez`） | `:1642` |
| 电量字段/方法 | `setBatteryLevel(...)` / `getBatteryLevel()` / `ballView.getBatteryLevel` | `:1619-1622`，`:1642-1644` |
| 卡片电量段 | `createDeviceServiceCard` 电量段（`0055 isOnline` 门 → `007d getBatteryLevel`）实测**零触发**，子类无覆写（虚调用必达），最后嫌疑是 `ballView.getDeviceId() ≠ OUR_MAC` | `:1619-1620` |
| 上游打桩 | `genDeviceServiceInfo` 返回前直接 `info.setBatteryLevel(跨进程电量)` | `:1621` |
| 三重保险 | ① `genDeviceServiceInfo` 上游打桩；② `ballView.getBatteryLevel` 断根；③ `setBatteryLevel(≤0)` → `proceed(newArgs)` 兜底替换真实电量 | `:1643-1644` |
| 断链根因 | `DeviceBallManager.deviceInfoMap` 无我们的设备 → `getDeviceInfoById` 返 null → `updateDeviceOnlineStatus` 空转 / `createEarphoneNoiseSection` 拿不到 `batteryData` | `:1507-1509` |
| 实拍结果 | 通知栏「原道 OriG in / 左耳 100% 右耳 100%」 | `:1608` |
| 对照数据 | X6 电量「左 100%，右 100%」；OriG in 为「100%（单一值）」 | `:228` |

### `DeviceInfo.batteryData` 结构与「每次取设备信息的出口」

- `DeviceInfo.batteryData` 的**字段级结构（字段名/类型）**：**文档未给出**。文档只给出了它的**读取方法** `info.getBatteryData()`（`:1320`）、`fromBatteryData` 的**参数类型** `BatteryData`（`:1320`，`:1377`），以及派生量 `batteryLevel`（`:1642`）与 `setBatteryLevel`/`getBatteryLevel`（`:1619-1622`）。
- 「每次取设备信息的出口」：**文档未给出确切方法名**。相关表述为「hook 查询出口返回缓存 DeviceInfo」（`magicos11-integration.md:1509`），但未指名方法。文档中出现的候选出口有：`DeviceBallManager.getDeviceInfoById`（`:1508`）、`getDeviceInfoList()` / `getAllDeviceInfo()`（`:1434`）、`getShowRemoteDeviceInfoList()`（`:1435`）、`getShowRemoteDeviceNum()`（`:1436`）。**此条为阻塞项。**

---

## 6. 设备名识别（YUANDAO / OriG / NiceHCK）

**核心取证结论：文档没有给出任何「按蓝牙名匹配 YUANDAO / OriG / NiceHCK」的类或方法。** 这些字符串在文档中只出现在「记录/展示/合成」语境：

| 行号 | 出现语境 | 是否匹配逻辑 |
|---|---|---|
| `:101` | 「目标耳机 `YUANDAO / OriG in / NiceHCK` 尚未配对，需要先配对拿到 MAC」 | 否（待办描述） |
| `:258` | 「完整 MAC：`YUANDAO OriG in = 18:5C:A1:52:10:36`」 | 否（MAC 记录） |
| `:809` | 合成 Bundle：`putString("device_general_name", "YUANDAO OriG in")` | 否（伪造设备信息写入） |
| `:1118` | 设备详情页标题「原道 OriG in」 | 否（截图描述） |
| `:1370` | 我们设备 `name=原道 OriG in` | 否（注入数据表） |
| `:1608` | 通知栏「原道 OriG in / 左耳 100% 右耳 100%」 | 否（实拍描述） |
| `:121`，`:196` | NiceHCK 侧 ANC 取值 `0x00~0x11`（缺与荣耀的映射） | 否（协议值域） |
| `:590`，`:1003` | NiceHCK `0x4E` SPP 帧 | 否（协议帧） |

文档中与「名字/厂商」相关的**判定点**（均未说明按名字匹配）：

| 判定点 | 文档描述 | 行号 |
|---|---|---|
| `checkIsHwHeadphones` | 日志 `Settings calling: checkIsHwHeadphones`，注释「「是否华为/荣耀耳机」判定」；被列为「另有两个可疑点可一并处理」之一 | `:444`，`:460` |
| `b2.a` 的 manufacturer 分支 | 日志 `b2.a: manufacturer passed in is empty, use default type` | `:737` |
| `EarphoneInfo.mDeviceName` | 实测 `mDeviceName=''`（空） | `:737` |
| `LinkDevice` 名称字段 | Gson 字段含 `deviceName`；注入 7 参构造含 `name` | `:1368`，`:1515` |

> 若需要「蓝牙名匹配」的实现依据：**文档未给出**，属阻塞项。

---

## 7. 文档中的坑与注意事项（逐条抄录）

### 7.1 作用域与 hook 对象

| # | 内容 | 行号 |
|---|---|---|
| 1 | `com.hihonor.bluetooth` 存在但只是**联系人导入工具**，与蓝牙栈无关，不要 hook | `:21` |
| 2 | MagicOS 10 上 Honor 改过的 `PackageParser`（`HnPackageUtils.notNeedCustomedPermission()` → `ActivityManager.isSystemReady()`）会让 lspd 守护进程 NPE，导致 **system_server 作用域的模块被静默跳过**；参考 `mengwuzhuanshou/HonorLsposedFix` | `:200-205` |
| 3 | `y8.c` 是接口不是类 —— dexdump 显示 `Interfaces -` + `Superclass Object` 具有误导性；LSPosed 报 `Cannot hook abstract methods`，**必须 hook 它的实现类** | `:360-362` |
| 4 | `MbbApi.getSmartConnect` 本身**快速返回**，真正失败（错误码 2001）经回调异步回来 | `:363-364` |
| 5 | 静态 dexdump 分析会误判运行时回调类名（实际是 `f4.b$a`） | `:381` |
| 6 | `MbbApi` 包装类是 `MbbApiHeper$a..j`（Honor 拼写是 `Heper` 不是 `Helper`） | `:493` |
| 7 | 日志里的 tag `MbbProtocolMultiChannelApi` / `ResultListenerAdapter` 是**日志 TAG 字符串，不是类名**，在 dex 里只作为字面量存在（正则搜类描述符无结果），实际类已混淆 | `:498-499` |
| 8 | `j2.a.q()` 是**死代码**（全部 6 个 dex 无调用方） | `:790-791` |
| 9 | `FCPG.json` 只在 `assets/uiconfig/` 子目录，顶层没有 → 改写 `o0.d` 返回 `FCPG` 会加载不存在的资源 | `:680` |
| 10 | `productconnect.bluetoothlib.c` 方法全为 `a..z`，**不可直接 hook** | `:322` |
| 11 | LSPosed **UI 勾选状态与 db 不同步**：APK scope.list ✓、db scope 表 ✓（enabled=1）、软重启 zygote ✓ 仍零注入；db 写入不算数，UI 里该项是**灰色未勾**；解法是 uiautomator 进作用域页勾选 | `:1400-1404` |
| 12 | 改作用域后 force-stop 面板进程即生效（`staticScope=true` 不会自动覆盖已存储的作用域，需在 LSPosed 里勾一次新包） | `:338-340` |
| 13 | `HookEntry` 的诊断日志（在 `isFirstPackage` 判断**之前**打印）是定位关键：没有它无法区分「没注入」和「注入后静默 return」 | `:1411-1412` |

### 7.2 判定「面板是否真的打开」的方法学（文档反复强调）

| # | 内容 | 行号 |
|---|---|---|
| 14 | `dumpsys topDisplayFocusedRootTask` **不可靠**：它报 AAM 任务聚焦，实际画面是 Settings 的 `DeviceProfilesSettingsActivity` | `:466-467` |
| 15 | 一度用「`EarphoneDetailsActivity` 在 dumpsys 里存在 30 秒」判定面板存活 —— **错的**；`mLastPausedActivity ... t-1 f` 的 `t-1` 表示已脱离任务栈（正在结束） | `:561-564` |
| 16 | 唯一可靠判据是 `am start -W` 的 `Status: ok` + `dumpsys ... state=` + `finishing=false`；**不要**用 `topResumedActivity` / `topDisplayFocusedRootTask` / `mLastPausedActivity` 三者判断 | `:1132-1134`，`:1281` |
| 17 | 面板**从未 finish**（`finishing=false`），它 `startActivity` 跳到 `com.hihonor.intent.action.DEVICE_PROFILES_SETTINGS` 然后 `STOPPED` 让位；`topResumedActivity` 恒为 Settings 不是「面板没打开」 | `:865-869` |
| 18 | 面板渲染不稳定的真实原因：**手机正被使用**（连续前台 = bilibili → 崩坏：星穹铁道 → 崩铁，`am start` 拉起即被抢占）；Round 25 的 `getArguments()==null` 猜测被 Round 26 推翻 | `:1209-1216` |
| 19 | `am start -a ...EARPHONE_DETAILS` **会解析到** `com.android.settings/.Settings$DeviceProfilesSettingsActivity`（多接收者），**必须用显式组件** `-n com.hihonor.audioaccessorymanager/com.hihonor.earphone.homepage.EarphoneDetailsActivity` | `:691-694` |
| 20 | `BluetoothNoiseControlSettings` **不是 Activity**（dex 有类、manifest 未声明）→ `am start -n` 报 `does not exist`；正确走 `com.hihonor.settingslib.SubSettings` + `--es :settings:show_fragment`，但它**未导出**，shell 报 `not exported from uid 1000` → **必须 `su -c`** | `:1195-1198` |

### 7.3 截图 / UI 自动化

| # | 内容 | 行号 |
|---|---|---|
| 21 | 截图前必须 `input keyevent KEYCODE_WAKEUP` + `input swipe 600 2300 600 700 200`，否则拿到的是锁屏（`wm dismiss-keyguard` 对人脸/指纹锁无效） | `:470-471` |
| 22 | `screencap` 会返回**旧帧**（状态栏时间是判据；上一张 21:46 而日志已 21:55） | `:1129` |
| 23 | 正确姿势：`KEYCODE_WAKEUP` → `input swipe 600 2300 600 700 200` → 等 → `rm -f` 目标 → `screencap` 到**新文件名** → 核对文件时间晚于最新 hook 日志 | `:1130-1131` |
| 24 | **`uiautomator dump` 要写 `/sdcard`**（`/data/local/tmp` 配 su 会 pull 失败）；`Get-Content` 必须 `-Encoding UTF8` | `:1275` |
| 25 | **布局会变** —— 降噪按钮 y 在 22:29 是 1415、23:10 是 1920，**坐标不能写死**，应每次 dump 或直接主动调用 `onClick` | `:1276` |
| 26 | **USB 连接方式对话框**每次插线弹出且盖在最上层 → 所有 `input tap` 被它拦截，曾造成「hook 零触发」假象；验证前必先 `input tap 370 2519`（取消） | `:1530-1531` |
| 27 | **焦点是多 display 的**：`dumpsys window \| grep mCurrentFocus` 第一行可能是另一 display 的 null —— 取**含 `controlviewnew` 的行**判断前台，别取 `$cur[0]` | `:1532-1533` |
| 28 | **不要用日志判 UI**（荣耀降噪路径不打 logcat）；用**选中态对比**截图 | `:1280` |
| 29 | 时间窗口：`T = yyyy-MM-ddTHH:mm:ss`，只看 `≥ T` 的日志，才能把 `input tap` 和用户手工点击区分开 | `:1282` |
| 30 | LSPosed UI 自动化路径：`am start -n org.lsposed.manager/.ui.activity.MainActivity` → 模块 tab (503,2612) → 搜索框输入 Magic → 点卡片 → 作用域列表；关 USB 对话框点「取消」(370,2519)；`input text` 会乱序 → 改短词或分段 | `:1416-1418` |

### 7.4 值/类型陷阱

| # | 内容 | 行号 |
|---|---|---|
| 31 | `checkConnectionState` 陷阱：hook `b2.a.a` 实测 **original = 2**（不是 0/false），而 app 日志却打 `checkConnectionState: false` → **日志里的 `false` 来自 `a()` 内部的另一个布尔量（或 `l()`），不等于返回值 2**；枚举很可能是 `0=断开 / 1=连接中 / 2=已连接`，强制改成 `1` 反而可能把状态降级 | `:752-756` |
| 32 | 教训：`Log` 打印的布尔与方法返回值不是同一个量，不能拿日志里的 `false` 去推断返回值该改成什么 | `:758` |
| 33 | `setANCMode` 的 `if (mode == currentAnc) return` 早退 —— 与目标值相同就**不发包**；改为记录但仍下发 | `:1268` |
| 34 | 下发桥 `status` 曾是 **String** → 类型不符返回 0 → `setAncMode(0)` 不在映射表 → 不发帧；必须改**非空 Int**（Kotlin `Int?` 会错选 Serializable 重载） | `:1579-1581` |
| 35 | `chain.args` 是**只读 List**（`Collections$UnmodifiableList.set` 抛异常，日志在赋值前打所以「看似成功」）；正确姿势 `chain.args.toTypedArray()` → `chain.proceed(newArgs)` | `:1600-1603`，`:1628` |
| 36 | `RemoteViews.setTextViewText` 打不到（通知走 custom RemoteViews）；荣耀自有渲染类 `android.media.QuaentCrearsaisSusts`（R8 混淆 + **动态 classloader**，onHook/轮询 30s 均 ClassNotFoundException）覆盖一切上游改写 | `:1599-1605` |
| 37 | ★★ CNFE：`Class.forName(MODE_CLS)` **默认走模块自身 ClassLoader**（DexPathList 含模块 apk 是实锤）→ 找不到宿主类 → `ProtectiveHooker` 捕获拦截 → 注入静默失败 → `supportedModes` 保持空、UI 按钮永远不亮；**修复：`Class.forName(X)` → `findClass(X)`**（`Class.forName(name, false, appClassLoader)` = 宿主 ClassLoader） | `:1548-1561` |
| 38 | 规律：hook 回调里访问**宿主进程的类/资源**一律用 `findClass`/宿主 loader；`Class.forName` 只对 `android.*` framework 类安全；`enumConstants` 在 `forName(…, false, …)` 下安全（反射取 VALUES 字段会触发 `<clinit>`） | `:1563-1565` |
| 39 | `SettingsHeadsetHook` 原用 `.onFailure { Log.w(...); return }` —— **非局部 `return` 直接退出整个 `onHook()`**，其后所有 hook 都没装上（日志只有 `target class absent` 一行、无任何 `installed`）；改为 `isSuccess` + if/else | `:930-932` |
| 40 | `SettingsHeadsetHook` 假成功：`HeadsetIDConstants` 不存在 → 先探测类存在性再挂钩，如实报告 | `:604` |

### 7.5 运行期时序

| # | 内容 | 行号 |
|---|---|---|
| 41 | **bluetooth 进程跑的是旧 APK** —— 装完必须 `svc bluetooth disable/enable` 重启进程，否则新代码不生效 | `:1269` |
| 42 | **grep 用错 TAG** —— `RfcommController` 是 **`MagicOriG-Rfcomm`**，一直 grep `MagicOriG-BT` 导致恒为空、误导 4 轮 | `:1270` |
| 43 | **receiver 依赖 `connectPod`** —— A2DP 未回连时广播无人接收，表现为「广播 result=0 但无日志」 | `:1271` |
| 44 | **`onHook` 早于 `Application` 创建** —— `currentApplication` 为 null，需 `Handler(main).postDelayed` 延迟注册 | `:1272` |
| 45 | **`Log.d` 不进模块日志** —— `safeConnect` 里 `RFCOMM connect initiated` 是 debug 级，看不见；已改 `Log.i` | `:1273` |
| 46 | `Log.module` 赋值过晚 → `ENTRY LOADED` 永远丢失 → 移到 `onPackageReady` 最前 | `:602` |
| 47 | `android.util.Log` 不进 logcat（buffer 回溯到 17:05 却查不到 17:18 调用）→ 全部改走 `module.log()` | `:603` |
| 48 | `A2dpService` 继承链无 `mContext`（`mContext not available`）→ 实例本身即 `ContextWrapper`，三级兜底 | `:599` |
| 49 | MagicOS 无 `CentralSurfacesImpl` → 改 `SystemUIService.onCreate` + 候选列表 | `:600` |
| 50 | 蓝牙进程无权改状态栏图标（`RemoteException ... enforceStatusBar`）→ 图标交给 SystemUI hook，蓝牙侧只记日志 | `:601` |
| 51 | 在线态被外部重置：`setIsOnline(true)` 会被荣耀属性监听 `DeviceProductManager$3.onPropertyInfoChange` 重置回 false | `:1543-1544` |
| 52 | `isSetting` 永真卡死：MBB 通道对第三方设备永不回调 → 一次点击后全部按钮被早退 | `:1582-1583` |
| 53 | **skip 窗口**：Settings 侧 skip 窗口 5.5s 关，档位恢复 `Q(byte)` 在 6.0s 调 → Q 同步回调 `onMultiStateClicked` → 广播不被拦 → 进页面自动切降噪；修：**Q 调用包进新的 skip 窗口**（6.0s 开、+800ms 关） | `:1590-1592` |
| 54 | 断连标志挂错信号：`Settings.Global[magicorig_connected]` 最初挂在 SPP 电量回调上，回连后长时间不触发 → 门控误杀；改挂 **`HeadsetStateDispatcher` A2DP 状态回调**（CONNECTED→1 / DISCONNECTED→0） | `:1612-1615` |
| 55 | 「一打开就被切到降噪」是误判 = 卡片固定显示注入的 `currentMode=NOISE_CANCELLATION` 默认值造成的**显示误导**；设备中心不存在自动下发路径 | `:1635-1639` |

### 7.6 工具/工程

| # | 内容 | 行号 |
|---|---|---|
| 56 | **扫中文必须按 bytes 搜（UTF-8/UTF-16LE）或 UTF8 解码**，Latin1 解码搜中文恒为 0，会误判 | `:1304` |
| 57 | dexdump 方法体头格式：`\|[offset] com.pkg.Class.method:(args)Ret`（**点号分隔类名**，如 `i2.b.g:(...)Z`）；类声明是 `Class descriptor: 'Lcom/pkg/Class;'`（**斜杠 descriptor**）—— 两者 pattern 不可混用（曾误判「方法体不存在」） | `:1569-1571` |
| 58 | invoke 行是 `Lcom/.../Class;.method:`（**带分号**）—— 两种格式不通用（多次空搜的根因） | `:1646-1647` |
| 59 | 反汇编 `buildPreference()` / `initExtras()` 因 dexdump 输出格式不一致未能取到 → 改运行时 hook；下一轮改用 `-like` 精确匹配方法头，勿再 `Escape` 正则 | `:1150`，`:1158` |
| 60 | **`Set-Content -Encoding UTF8`（PS5.1）会破坏 UTF-8 源文件**（加 BOM/乱码）—— 曾把 build.gradle.kts 写坏并推送；改 Kotlin/Gradle 文件一律用编辑工具，禁用 PS 写文件 | `:1626-1627` |
| 61 | **Windows Store 版 Python 的 urllib TLS 必断**（`SSL: UNEXPECTED_EOF` 12 连败），但 `curl.exe` 到同一 api.github.com 是 200 → GitHub API 发布一律走 curl | `:1630-1631` |
| 62 | Release 文案要**人话**；每次 push 后 bump 版本 + 发 Release | `:1629` |
| 63 | 签名注意：原 `magicorig.jks` 不在仓库（被 `.gitignore` 排除），本机按 `build.gradle.kts` 重新生成了一把 → 已装版本需卸载后才能重装 | `:262-263` |
| 64 | 一键回归脚本：`tools/full_verify.ps1`（部署 → 信号读取 → 开设备中心 → 点球 → 日志/截图） | `:1645` |

### 7.7 环境/硬件限制

| # | 内容 | 行号 |
|---|---|---|
| 65 | **不要重启手机**；需要重启时必须先征得同意，且只用**软重启**（`setprop ctl.restart zygote`，不重启内核，KernelSU/Zygisk 模块保持加载） | `:652-653`，`:1286` |
| 66 | 全程未软重启 zygote、未重启手机；坑 1–9 全靠 `svc bluetooth disable/enable`、`force-stop Settings` 解决 | `:1287` |
| 67 | 已在 LSPosed 中启用模块但 **systemui 未重启** —— hook 尚未生效，需软重启后验证 | `:654` |
| 68 | 没有可对照的降噪参考：手上唯一的荣耀耳机 X6 本身不支持降噪，**从未见过这台机器上的降噪面板长什么样** | `:239-241` |
| 69 | `logcat` 全局搜索 `NoiseControl` / `EarphoneDetails` / `SupportedDeviceSettings` **零命中** | `:242-243` |
| 70 | `EarphoneDetailsActivity` 还需 `ACCESS_BIND_AUDIOACCESSORYMANAGER` 权限；shell 无该权限，必须 root | `:238`，`:272` |
| 71 | `dumpsys` 输出被荣耀掩码（MAC 显示为 `18:5C:*:*:36`），完整 MAC 取自 root 读 `/data/misc/bluedroid/bt_config.conf` | `:259` |
| 72 | 环境：`KernelSU 3.2.5`，`adb shell su -c` 可用；`LSPosed v2.2.0 (7854)` 装在 KSU 模块 `zygisk_lsposed` | `:260-261` |
| 73 | `systemui` 不含 `controlviewnew` 类（AAR 假设不成立，双保险 hook 但 ClassNotFound） | `:1469` |
| 74 | `NewControlHomeActivity`「设备中心」页 = 荣耀**账号**互联空间（空态文案要登录荣耀账号），蓝牙耳机不归它管 → hook 触发但不渲染；真正目标是**控制中心面板的「设备中心」卡片条球区** | `:1470-1471` |

### 7.8 用户点名但文档未给出的条目（阻塞项）

| 用户要求记录项 | 文档实际情况 |
|---|---|
| `module.prop` 的 `targetApiVersion` | **文档未给出**。全文检索 `targetApiVersion`、`module.prop` 均无命中（0 处）。 |
| 「保护窗口」 | **文档未给出该术语**。文档只有「skip 窗口」：Settings 侧 skip 窗口 5.5s 关、`Q(byte)` 在 6.0s 调 → 改为 6.0s 开、+800ms 关（`magicos11-integration.md:1590-1592`）。是否即「保护窗口」：文档未给出对应关系。 |
| 「SystemUI 长寿进程」 | **文档未给出该说法**。文档对 SystemUI 只有两处：`SystemUIService.onCreate` 作为 hook 入口（`:600`）、`RemoteViews.apply/reapply` 作为通知改写落点（`:1606-1608`）；未讨论 SystemUI 进程生命周期/长寿特性。 |

---

## 8. 文档结构目录

全文只有 **1 个一级标题**，其余为二级标题（`##`）及其下级小节（`###` / `####`）。

| 行号 | 级别 | 标题 |
|---|---|---|
| `:1` | `#` | MagicOS 11 耳机降噪接入 —— 真机逆向结论 |
| `:10` | `##` | 1. 作用域：包名是 AOSP 的，不用改 |
| `:25` | `##` | 2. 荣耀耳机面板的位置（新增目标） |
| `:57` | `##` | 3. 「是否受支持」的开关（可直接写，已验证） |
| `:94` | `##` | 4. 参照设备与待办 |
| `:109` | `##` | 5. 接入方案 |
| `:126` | `##` | 6. 补充：机型能力配置在 APK assets 里（决定"有没有降噪栏"） |
| `:181` | `##` | 7. 最终接入方案（据以上证据） |
| `:200` | `##` | 8. LSPosed 在 MagicOS 上的坑 |
| `:209` | `##` | 9. 实测修正与现状（2026-10-06） |
| `:267` | `##` | 10. ✅ 面板入口与早退条件（已反汇编定位） |
| `:332` | `##` | 11. 进度：作用域已扩到面板进程，MBB 调用流已抓到（2026-10-06 18:26） |
| `:595` | `##` | 12. 已修掉的运行期问题（均有日志实证） |
| `:609` | `##` | 13. ✅ 面板已成功打开 —— 卡点移到 MBB 协议（16:32 实测） |
| `:650` | `##` | ⚠️ 操作约束（务必遵守） |
| `:658` | `##` | 14. 实测：设备已归类为 Third，但 `Third.json` 没有降噪配置（19:49） |
| `:1077` | `##` | 🎉 Round 18–20：降噪面板**成功出现**（21:57 实拍） |
| `:1166` | `##` | Round 24–30：主行入口与降噪控制页已定位（逆向成果） |
| `:1226` | `##` | 🎉 最终成功：端到端链路全通（用户实测确认） |
| `:1291` | `##` | 任务4：设备中心适配逆向（round 1–5，代码完成待验证） |
| `:1396` | `##` | 任务4实战记录（round 6–15）：LSPosed scope 根因与球渲染链 |
| `:1475` | `##` | 任务4实战记录续（round 17–22）：降噪 section 的三道门与注册表兜底 |
| `:1575` | `##` | 第二阶段 bug 修复记录（2026-10-10，v1.0.1 → v1.0.2） |

### 二级标题下的三级/四级小节（供定位用）

| 行号 | 级别 | 标题 |
|---|---|---|
| `:78` | `###` | 真机验证（已执行并还原） |
| `:118` | `###` | 仍然未知（需要耳机连接后确认） |
| `:130` | `###` | 两张设备表 |
| `:150` | `###` | 决定降噪栏的字段（已对比验证） |
| `:172` | `###` | 配置解析入口 |
| `:191` | `###` | 待真机确认 |
| `:211` | `###` | ❌ 推翻：`supported_earphone_device` 不是荣耀自家耳机的登记机制 |
| `:222` | `###` | 对照实验：X6 vs 原道 OriG in（蓝牙设置列表，16:11 实拍） |
| `:233` | `###` | 关键结论 |
| `:247` | `###` | 两条可选路径 |
| `:256` | `###` | 其它已确认事实 |
| `:269` | `###` | 打开方式 |
| `:280` | `###` | 为什么之前会自己关掉 |
| `:299` | `###` | `d0(Intent)` 完整解码（MAC 来源优先级） |
| `:313` | `###` | 明确的 hook 目标 |
| `:324` | `###` | 未完成 |
| `:334` | `###` | 已完成 |
| `:342` | `###` | 注入与调用流实测 |
| `:358` | `###` | 关键修正 |
| `:366` | `###` | 回调实现类候选（都含 `b(Ljava/lang/Object;)V`） |
| `:379` | `###` | ✅ 已确认：回调实现类与失败时序（18:43 实测） |
| `:405` | `###` | ✅✅ 已实现 onSuccess 合成，2001 超时消失（18:56 实测） |
| `:431` | `###` | ⚠️ 仍未解决：第二条 MBB 通道 `5a0107`（设备信息） |
| `:464` | `###` | 面板 UI 的视觉确认方法（重要教训） |
| `:473` | `###` | 🔑 关键资产：`MbbApi` 查询包装类 × 载荷类型地图 |
| `:496` | `###` | `5a0107` 在更下层（不经过 MbbApi 的 y8.c） |
| `:510` | `###` | 🎯 定位：`--get device info error` 的归属类 = `y2.c$b` |
| `:529` | `###` | 🎯🎯 更关键：`y2.c` 就是「是否受支持」的判定，且有第三方兜底 modelId |
| `:551` | `###` | ⚠️ 再次更正：`y2.c.h` 改写生效，但**面板仍在退出** |
| `:570` | `###` | 结论 |
| `:575` | `###` | 下一步（明确） |
| `:584` | `###` | 下一步 |
| `:620` | `###` | 新的真正卡点：MBB 协议握手失败 |
| `:636` | `###` | 因此最终 hook 方案（在 MbbApi 层做协议转换） |
| `:676` | `###` | 结论 |
| `:684` | `###` | 关闭机制（已定性） |
| `:689` | `###` | 启动方式（易错） |
| `:696` | `###` | Round 4：找到 uiconfig 加载器，但它没被调用 |
| `:729` | `###` | Round 5：根因链已完整，但 `checkConnectionState` 的值有陷阱 |
| `:761` | `###` | Round 6：`getDeviceInfo` 返回 null —— 配置加载仍未触发 |
| `:788` | `###` | Round 7：`j2.a.q()` 是死代码；拿到 getDeviceInfo 的 Bundle key |
| `:825` | `###` | Round 8：`i2.x` 访问器已定位，FCPG 注入点已装但未触发 |
| `:851` | `###` | Round 9：⚠️ 重大框架更正 —— 面板不是关闭，而是**转发** |
| `:887` | `###` | Round 10：页面层级已走通，「通用设置」里没有降噪 |
| `:911` | `###` | Round 11–14 汇总（逆向成果，务必保留） |
| `:967` | `###` | Round 14–15：降噪行的触发条件 = **收到设备降噪数据后的 Runnable** |
| `:996` | `###` | 🔑 结论 |
| `:1008` | `###` | Round 16：✅ 触发点精确定位 = `DeviceProfilesSettings$a.onGetDataSucceed` |
| `:1040` | `###` | 🎯 Round 17：触发链**首次全通**（日志实证） |
| `:1061` | `###` | ❌ 但截图仍无降噪行（21:46） |
| `:1079` | `###` | 1. `NoiseControlController` 完整结构（dexdump） |
| `:1092` | `###` | 2. `displayPreference` 最终开关（完整反汇编） |
| `:1105` | `###` | 3. 日志链全通（21:55:00） |
| `:1116` | `###` | 4. ✅ 视觉证据（21:57:50 新帧 `f215749.png`） |
| `:1127` | `###` | 5. 抓图方法论（本 session 反复踩坑，务必照做） |
| `:1136` | `###` | 6. 仍待验证 |
| `:1138` | `####` | Round 21：点击「降噪」**无任何下发**（重要否定） |
| `:1148` | `####` | Round 22：`Preference.setEnabled` 观察已装 |
| `:1168` | `###` | 1. `NoiseControlController.buildPreference()`（dexdump 已成功取到） |
| `:1182` | `###` | 2. `initExtras()` —— 点击去向 + 必需 extras |
| `:1193` | `###` | 3. 启动方式（实测） |
| `:1209` | `###` | 4. 面板渲染不稳定的真实原因（修正 Round 25/26 的反复误判） |
| `:1218` | `###` | 5. 待办（下一步） |
| `:1228` | `###` | 1. 完整链路（每步都有日志/反汇编佐证） |
| `:1243` | `###` | 2. 关键类/方法清单 |
| `:1255` | `###` | 3. UI index → status → SPP 帧（最终映射） |
| `:1266` | `###` | 4. 踩过的坑（排障顺序，务必按这个查） |
| `:1278` | `###` | 5. 验证方法（以后照做） |
| `:1284` | `###` | 6. 操作约束 |
| `:1296` | `###` | 1. APK 归属（aapt2 + 字节扫描实测） |
| `:1306` | `###` | 2. 降噪卡渲染链（controlcenter dex 反汇编） |
| `:1317` | `###` | 3. `createEarphoneNoiseSection` 关键结论（唯一退出 = LinkDevice null） |
| `:1330` | `###` | 4. 点击链与 4 个早退 |
| `:1347` | `###` | 5. 设备列表总闸（"没添加进去"的根因） |
| `:1364` | `###` | 6. 注入所需数据（全部实测拿到） |
| `:1372` | `###` | 7. 适配实现 `ControlCenterNoiseHook`（scope: `com.hihonor.controlcenter`） |
| `:1383` | `###` | 8. 待设备验证（设备连续离线 5 轮） |
| `:1398` | `###` | 1. ★ LSPosed scope 根因（最重要的一课） |
| `:1421` | `###` | 2. 进程拉起方式 |
| `:1429` | `###` | 3. 球渲染链（反汇编全程） |
| `:1453` | `###` | 4. 实测日志（全部成功层） |
| `:1461` | `###` | 5. 待最终验证（屏幕锁屏时被挡） |
| `:1467` | `###` | 6. 排除的死胡同 |
| `:1477` | `###` | 1. 卡片数据装配链（全反汇编） |
| `:1498` | `###` | 2. 在线态分流（离线卡根因） |
| `:1505` | `###` | 3. deviceInfoMap 兜底 |
| `:1511` | `###` | 4. c2/s 注册表兜底（第10环，最后一环） |
| `:1518` | `###` | 5. 实测日志（各环触发证据） |
| `:1528` | `###` | 6. 验证流程的两个大坑 |
| `:1535` | `###` | 7. 待最终验证 |
| `:1541` | `###` | 8. 第 10-11 环：在线态断根 + ★ CNFE（ClassLoader 教训） |
| `:1567` | `###` | 9. 反汇编备忘：方法体头格式 |
| `:1577` | `###` | 1. 设备中心点击无效 + 点一次卡死（✅ 实测修复） |
| `:1588` | `###` | 2. 进页面自动下发降噪（bug3，代码修复） |
| `:1595` | `###` | 3. 电量通知迁移（✅ v1.0.2 实拍终证） |
| `:1610` | `###` | 4. 断连球消失（✅ 正反实测） |
| `:1617` | `###` | 5. 卡片电量（进行中 → 上游打桩） |
| `:1624` | `###` | 6. 工程教训 |
| `:1633` | `###` | 7. 第二阶段补遗（Round 15–17 结论） |

文档末尾 `:1649-1657` 为空行（无内容）。

---

## 9. 明确缺失信息汇总（阻塞项）

1. **11 环编号表本身**：文档只出现「第10环」「第 10-11 环」，1–9 环的编号/名称文档未给出（`magicos11-integration.md:1511`，`:1541`）。
2. **`NoiseControlViewModel` 的读写方法签名**：文档明列为「仍然未知」/「未完成」/「待真机确认」；已知被混淆成 `R(I)V` / `U(I)V` / `V(I)V` / `W(I)V` / `Y(ChoiceUiInfo,[Integer,I)V`（`:120`，`:195`，`:321`，`:328`）。
3. **荣耀降噪档位值 ↔ NiceHCK `0x00~0x11` 的映射**：文档明列为待确认（`:121`，`:196`，`:254`）。
4. **荣耀内部 status 枚举的完整定义**：只有点对点数字（1/2/3/5 + `5/4/3`），`4` 的语义文档未给出（`:1259-1263`，`:1378`）。
5. **`commonutils.c.a()` 的具体判定逻辑**（是否查 `supported_earphone_device` / 支持列表）：文档列为未完成（`:327`）。
6. **`MbbApi` 最终落到哪个蓝牙接口**：文档称若要复用它需继续逆向；走 SPP 则可绕过（`:122`）。
7. **`DeviceInfo.batteryData` 的字段级结构**：文档未给出。
8. **「每次取设备信息的出口」的确切方法名**：文档只写「hook 查询出口返回缓存 DeviceInfo」，未指名（`:1509`）。
9. **蓝牙名（YUANDAO/OriG/NiceHCK）匹配逻辑**：文档未给出任何匹配类/方法（详见 §6）。
10. **图标资源查找方法**：文档未给出（只有 `prodId null → 图标解析失败静默不渲染`，`:1451`）。
11. **`module.prop` 的 `targetApiVersion`、「保护窗口」、SystemUI 长寿进程**：文档均未给出（详见 §7.8）。
12. **`PropertyUtils.setNoiseCtrlMode` 的返回值类型**：文档只给出参数侧 `(String,int,cb)`（`:1378`）。
13. **面板「更多 ›」子页内容**、**三按钮点击后是否真能下发**的历史待验证项：文档列为待验证（`:1160-1162`）。
14. **`DeviceProfilesSettings` 主页面的行装配来源**：文档称 `Third.json` 没有「通用设置」，需另找装配点（`:906-909`）；`NoiseControlController.buildPreference` 的 `setEnabled`/`onClick` 条件亦未取到（`:1150`，`:1157-1158`）。
