# FIX-003：最后一环——createDeviceBall 在本机构建上原生返回 null

> 仓库：https://github.com/Yuxiximyxx/MagicFree4 （基线 e1cd421 / FIX-002 已落地）
> 证据：机主 2026-11-02 导出的 LSPosed 全量日志（modules 日志 12:12–13:35，含 2.0.1 与最新版两个会话）

## 全链实证状态（controlcenter 进程）

通的（日志逐条在案）：
- 设备识别、真实 MAC 发布（88:92:CC:E5:00:8D）、主 hook 触发、RFCOMM/SPP 欢律帧收发、ANC 回读、电量 L100/R100 写入 Global
- 球列表三出口注入（getAllDeviceInfo / getDeviceInfoList / getShowRemoteDeviceInfoList，均含 Free4）
- 双耳电量喂入 ×111（左100% 右100%, ability=0x3）
- selectLayout 修正后 createSections(config=REMOTE_EARPHONE) → size=5
- 12:28:40 服务卡管线完整组装过一次：genDeviceServiceInfo sections=5、createEarphoneNoiseSection/createEarphoneBatterySection 均返回 SEC、createDeviceCard isOnline=true

**唯一断点**：
- `▶ createDeviceBall(id=88:92:CC:E5:00:8D) → NULL(失败)` ×14，两个会话无一成功
- 该 hook 是纯观察（chain.proceed 后打日志），**null 是原厂方法自己的返回值**：列表里有设备、布局留了位，但球视图工厂建不出来 → 渲染为空白占位

## 任务 1（先行，最低成本）：本机真实设备字段 diff

本机构建与作者 AAK 基线存在版本漂移（见任务 3），伪造 DeviceInfo 的字段集很可能缺了本机构建 createDeviceBall 要读的字段。先取证再改：

1. 在球列表注入点把**列表里每个 DeviceInfo 的全字段**反射 dump 到日志（字段名=值，含 prodId/deviceType/icon 相关字段与内部 map 键）
2. 把 `ControlCenterProvider.getDeviceList` 返回的 ext JSON 全量打日志
3. 将本机真实渲染成功的设备（列表里的 HUAWEI FreeBuds 5 等）与伪造 Free4 条目逐字段 diff，差异点即嫌疑清单

## 任务 2（主体）：反编译本机构建的 createDeviceBall，逐门修

1. 从机主手机提取本机 controlcenter APK（`pm path com.hihonor.controlcenter` 后 pull），jadx 反编译 `DeviceBallLayoutManager.createDeviceBall` 及其调用的 lookup，**逐条列出 return null 的分支条件与所读数据源**
2. 按定位结果对症（以下为预判分支，实际以反编译为准）：
   - 若内部直读 DeviceBallManager 私有 map（公开 getter 的「map 兜底」hook 覆盖不到直读）→ 把伪造 DeviceInfo 同时塞进真 map
   - 若按 prodId 查图标/产品配置表而本机构建配置里没有 00VVD9 → 改用任务 1 中本机存在的耳机 prodId，或补注配置
   - 若读某个我们没设的字段（iconType/category/index 等）→ 在 buildOurDeviceInfo 补齐
3. 修后判据：日志出现 `createDeviceBall(id=88:92…) → OK` 与 `★ setIsOnline(true) on adapter`，设备中心出现带名球

## 任务 3：版本漂移三处特征化（同源问题）

本机构建混淆/签名与上游基线不一致，写死的名字已有三处失效：

1. `c2.s.o hook 失败: ClassNotFoundException: c2.s`（controlcenter）——注册表类在本机构建换了名。改为按特征定位：静态方法、入参 String(deviceId)、返回 LinkDevice 的类；或从 genDeviceServiceInfo 的调用图反查
2. `install getDeviceList failed: ClassNotFoundException: com.hihonor.controlcenter.superdevice.ControlCenterProvider` ×3——controlcenter 进程内该 hook 已装上（日志有 installed），这 3 次是其他进程的噪音，可忽略；但类路径定位改特征化（ContentProvider 子类且含 getDeviceList 方法）更稳
3. `MbbApi.getSmartConnect` NoSuchMethodException（audioaccessorymanager，参数 y8.c 对不上）——面板/详情页链路，**次优先**，本单有余力再按本机构建签名适配，没有就留待 FIX-004

## 验证（机主真机）

1. 装新版 → 强停并重开设备中心（或重启 controlcenter 进程）→ 打开设备中心
2. 日志判据：`createDeviceBall → OK`、`setIsOnline(true)`、球列表注入照常
3. 目视判据：设备球带名出现（不再是空白占位）→ 点球出服务卡 → 降噪三档可切且耳机真实响应 → 电量显示 ≈100%

## 备注

- 不要动已实证通过的环节：触发链（FIX-002）、MAC 桥（FIX-001）、协议层、列表注入、电量喂入、selectLayout 修正
- 任务 1 的 dump 日志属临时取证，定位后收敛为一条 diff 结论写入 CHANGELOG，dump 代码可保留为 debug 开关
