# iptv

原生 Kotlin / Compose Android TV 播放器，针对 Android 14（targetSdk 34），最低 API 26，compileSdk 37。动态维护直播源，单源播放，按节目汇聚线路并跨源收藏。主界面不显示品牌、宣传或操作说明。

## 构建与安装

Android Studio 打开本目录。使用支持 AGP 9.4.1 的 Android Studio、Gradle 9.8、JDK 25 和 SDK Platform 37。SDK 路径放在忽略的 local.properties。AGP 内置 Kotlin。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

APK 输出到 `app/build/outputs/apk/debug`、`app/build/outputs/apk/release`，文件名为 `iptv-构建类型-v版本-时间戳.apk`。本次交付副本为 `app/build/outputs/iptv-debug.apk` 和 `app/build/outputs/iptv-release.apk`。

```powershell
adb install -r app/build/outputs/iptv-debug.apk
adb shell am start -n cn.hdfk7.android.iptv.debug/cn.hdfk7.android.iptv.app.MainActivity
```

Debug 包名 `cn.hdfk7.android.iptv.debug`、启动器名称 iptv-debug；Release 包名 `cn.hdfk7.android.iptv`、名称 iptv。电视需开启开发者选项并完成 ADB 授权。macOS/Linux 使用 `./gradlew`。

最初的 APK 使用临时调试签名；如新签名覆盖旧包发生冲突，需先卸载旧包，卸载将清除其数据。新版带 .debug 后缀的包可与最初无后缀包并存。

## 签名、打包与 Git

实际 APK 签名文件在 `signing/sign`。gradle.properties 中 APK_SIGN_STORE_PATH、APK_SIGN_STORE_PASSWORD、APK_SIGN_KEY_PASSWORD_DEBUG_ALIAS、APK_SIGN_KEY_PASSWORD_DEBUG、APK_SIGN_KEY_PASSWORD_PROD_ALIAS、APK_SIGN_KEY_PASSWORD_PROD 配置签名。Debug / Release 分别使用 debug / prod signingConfig。按用户要求，签名文件及口令作为项目文件保留，不忽略；日志和文档不显示口令。

APP_DISPLAY_NAME 控制启动器名称和 APK 文件名；APP_DEBUG_SWITCH 生成 BuildConfig 开关。使用 Java 11 编译级别、JDK 25 daemon、Debug applicationIdSuffix、Release optimization.enable=false 与 proguard 配置。

Git 提交消息使用 `type:中文说明`，每次聚焦一个逻辑变更；PR 说明模块、行为和验证。忽略构建输出、IDE 缓存和 local.properties。当前目录尚未初始化 Git，本地文件准备不代表已经创建提交。

## 源管理

顶部当前源或源管理按钮打开源面板，支持增加、修改名称/地址、删除和启用。始终只启用一个源，普通源不会混入其他源节目。新增不自动切源；删除当前源后使用剩余首个源，没有其他源时进入收藏源。切源停止原源播放，列表回到顶部。

支持 HTTP(S) M3U/M3U8 直链、GitHub blob 文件链接，以及两个内置仓库的主页地址；其他仓库需提供文件链接。重复地址、空名称和不支持的地址会显示错误。

## 节目与轮询

按节目名称聚合记录，不按分组拆分节目。地址去重并保留出现顺序。名称统一全角/半角、大小写、空格，常见 CCTV1 / CCTV-1 / CCTV1综合 / CCTV1高清 合并。CCTV5 与 CCTV5+、CCTV4 与 CCTV4K、CCTV4欧洲与 CCTV4美洲保持区分；其他名称不猜测别名。

上一节目、下一节目和频道键切换节目，从新节目的第一条地址开始依次尝试。首条进入可播状态的线路持续播放；错误、持续缓冲超时或直播意外结束后换下一条。尝试全部候选地址，没有八条上限；全部失败后显示错误，可重试或切台。

超时在 gradle.properties 中配置，单位毫秒，使用正整数，修改后重新打包：

```properties
STREAM_CONNECT_TIMEOUT_MS=5000
STREAM_READ_TIMEOUT_MS=5000
STREAM_PROBE_TIMEOUT_MS=8000
```

分别为连接、停滞读取、每条线路持续缓冲的最大等待时间。READY 后取消探测计时，播放中再次缓冲时重新计时；单线路时限也约束内部重试。慢源可能被跳过，可按网络条件调整。

## 跨源收藏

频道旁或当前节目旁星标可收藏，保存每个“源 ID + 节目名称”的地址快照。不同源收藏同名节目后，收藏源只显示一个节目，合并这些已收藏来源的不同地址并标明来源，不加入未收藏源的地址。

普通源取消收藏移除当前源的贡献；收藏源取消一个节目移除该节目全部贡献。收藏源可独立启用，切台按聚合后的节目顺序操作。

刷新原源按稳定节目名称更新收藏地址；在收藏源刷新及执行每日任务时，刷新仍然存在的贡献源。删除源或新列表找不到节目时保留最后快照，不静默丢失收藏；删除的源不再刷新。源、当前选择和收藏使用私有目录原子 JSON，重启保留，关闭备份和设备迁移。

## 全屏与遥控器

底部全屏图标进入全屏。按确认键、方向键、INFO 或 MENU 显示节目名、来源、收藏及播放控件；6 秒无操作隐藏浮层。方向键在显示的控件间移动，确认键执行；返回退出全屏。频道键直接换节目并显示新节目信息。媒体键播放/暂停，系统音量键保留原生行为。

列表页底部左侧为上一节目、播放/暂停、下一节目、重试；右侧为全屏、音量减/加。固定音量设备提示使用电视/功放遥控器。离开前台释放播放器，无后台媒体服务或通知权限。

## 自动更新和缓存

进入前台自动刷新，也可手动更新。没有每日定时任务；升级时取消旧版 daily-playlist 任务，WorkManager 依赖仅用于这项迁移清理。更新失败保留缓存和收藏。

按地址哈希独立缓存，切源先显示相应缓存。旧源延迟响应不覆盖当前源，修改 URL 不混用旧地址缓存。旧版 playlist.json 可迁移到首个内置源。有效非空列表原子写入成功后才更新时间，网络、格式、空列表和写盘失败保留旧缓存。

列表下载连接/读取各 15 秒、4 MiB 上限，不跟随入口重定向。仅支持 HTTP(S) 播放，拒绝用户名密码 URL。公共流含 HTTP，因此允许明文流量。不执行认证、Cookie、Referer 或解密指令，不使用第三方代理。EPG/台标属性不影响解析，本版不加载远程台标或节目单。

## 源仓库检查（2026-11-05）

| 仓库 | 默认列表 | 检查结果 |
| --- | --- | --- |
| [best-fan/iptv-sources](https://github.com/best-fan/iptv-sources) | [cn_all.m3u8](https://raw.githubusercontent.com/best-fan/iptv-sources/main/cn_all.m3u8) | 根目录 8 个 M3U8，近期每日自动提交；README 声明 MIT，未发现独立 LICENSE |
| [suxuang/myIPTV](https://github.com/suxuang/myIPTV) | [ipv4.m3u](https://raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u) | 包含 IPv4、IPv6、移动/APTV 列表和图片/播放器资源；人工不定期更新，未发现独立 LICENSE |

均通过 Git clone 检查，默认分支 main。myIPTV 文件头含 x-tvg-url，已适配；部分 IPv6/运营商专用线路依赖相应网络，默认 IPv4。未来更新频率不保证。

应用仅打包入口，不嵌入播放列表或流媒体。观看权利和可用性取决于源提供方，仓库许可不代表节目授权。请遵守来源条款与适用法律，仅在获得必要授权时使用。不绕过访问控制，不保证所有节目可播。

## 架构与验证

- app：宿主、生命周期、任务注册和遥控器事件。
- core/model：源、节目、收藏、名称归一化及线路聚合。
- core/data/local、network、repository：原子持久化、限长下载/解析、业务规则和刷新隔离。
- core/ui/theme、components：主题和统一焦点组件。
- feature/player：节目、全屏播放与收藏；feature/source：源列表、表单和删除确认。

Kotlin 使用显式参数和命名回调，无尾随 Lambda；界面单一根组件，文本在 strings.xml，行为与布局分离。

24 项 JUnit 5 测试覆盖解析、名称合并/区分、线路去重、源增删改、单源激活、地址校验、跨源收藏、地址刷新、离线/重启、写盘失败、URL 缓存隔离和延迟响应竞争。Debug/Release 构建及 Lint 通过；Android 14 电视模拟器用于安装和实际界面验证，尚未在小米实体电视验证。

本次模拟器验收：源新增/编辑/删除、单源切换、频道键从 CCTV1 切到 CCTV2、跨源 CCTV1 合并为一个收藏节目并汇聚 17 条不同地址、实际直播播放、全屏信息自动隐藏/INFO 和确认键唤出、唤出不误触收藏、返回列表均已验证。Debug 和 Release 的 APK 签名均与项目内对应证书匹配。机器可读验证记录在 `app/build/outputs/verification.json`。

## HTTP 代理

首页右上角打开“HTTP 代理”，填写 IPv4/IPv6、端口及可选用户名/密码，开启并保存。支持 HTTP 转发和 HTTPS CONNECT、Basic 代理认证，不支持 SOCKS。订阅下载、视频清单及分片均使用同一代理；保存后重连当前节目并刷新当前源。关闭后直接连接。代理故障不会静默改为直连，更新失败保留缓存。配置在本机通过 Android Keystore AES-GCM 加密保存，不备份，不输出认证信息。
