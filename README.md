# JBusDriver

JAVBUS 的第三方 Android 客户端，受 [JAViewer](https://github.com/SplashCodes/JAViewer) 启发编写。

原项目：<https://github.com/Ccixyj/JBusDriver.git>，本仓库是其修复分支。

## 功能

- 影片：有码 / 无码 / 高清 / 字幕四类
- 搜索：有码 / 无码 / 女优 / 导演 / 制作商 / 发行商 / 系列七类结果分页
- 详情页：封面预览、磁力链接、相关论坛帖子
- 磁力解析：内置多个站点解析器
- 论坛：首页轮播 + 热帖 + 分组板块，帖子楼层原生阅读并支持翻页
- 收藏夹、浏览历史
- 两套外壳：侧边抽屉 / 底部导航，在设置里切换后重启生效

## 模块

| 模块 | 说明 |
|---|---|
| `app` | 主程序与全部页面 |
| `component_magnet` | 磁力解析库 |
| `libraries/library_base` | 网络、图片、MVP 基类与通用控件 |
| `libraries/library_common_bean` | 公共数据模型 |

## 构建

Gradle / AGP / Kotlin / SDK 版本统一来自共享基线仓库的 `libs.versions.toml`，
本仓库需与该基线仓库**同级** clone，否则 `settings.gradle` 解析不到 catalog。

- JDK 17，Gradle 8.13（wrapper 自带，无需本机安装），AGP 8.9.3，Kotlin 2.1.0
- compileSdk 36 / targetSdk 35 / minSdk 23 / buildTools 35.0.0

本地 SDK 路径写在 `local.properties`（已 gitignore）的 `sdk.dir`。

签名信息不入库。四项 `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEYSTORE_ALIAS` / `KEY_PASSWORD`
放在**用户级** `$GRADLE_USER_HOME/gradle.properties`（仓库外，不会被提交），模板见 `gradle.properties.example`；
也可以不放文件、改用环境变量 `KEYSTORE_FILE` / `KEYSTORE_PWD` / `KEYSTORE_ALIAS` / `KEYSTORE_ALIAS_PWD`。
`KEYSTORE_FILE` 相对 app 模块解析，`../jbus.jks` 即仓库根目录。
配置齐全时 debug 与 release 共用该签名，否则 release 不签名、debug 用默认签名。
网络代理同样是机器专属项，`systemProp.http(s).proxyHost/Port` 也放用户级配置，不要写进仓库这份。

```bat
gradlew.bat assembleDebug      :: 调试包
gradlew.bat assembleRelease    :: 混淆 + 资源压缩，产物 JubsDriver-v<versionName>.apk
```

版本号写死在 `app/build.gradle` 的 `defaultConfig`，不再从 git tag 推导。

## 备注

- `app/native-libs/` 放的是 Umeng 的 x86_64 `libumeng-spy.so`（官方只发布到 x86），缺失时纯 x86_64 设备会 `INSTALL_FAILED_NO_MATCHING_ABIS`。
- 站点地址写死在本地，不再走云端下发与探活换站。
