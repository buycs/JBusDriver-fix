# JBusDriver

JAVBUS 的第三方 Android 客户端，受 [JAViewer](https://github.com/SplashCodes/JAViewer) 启发编写，感谢原作者。

原项目地址：<https://github.com/Ccixyj/JBusDriver.git>

本仓库为持续维护的修复版本：在原有功能基础上修复图片加载、缓存更新等问题，并补充论坛热帖的原生阅读体验。

- 技术栈：`Kotlin + MVP + RxJava2 + Retrofit2 + OkHttp3 + jsoup + Glide`
- 架构：自 1.2.14 起采用 [CC 组件化](https://github.com/luckybilly/CC)，1.2.16 起采用 [Phantom 插件化](https://github.com/ManbangGroup/Phantom)

## 功能

- 有码 / 无码 / 欧美 三大分类，女优、类别、高清、字幕筛选
- 影片搜索、详情页（封面预览、磁力链接、相关论坛帖子）
- 磁力解析（内置磁力组件 + 可独立升级的磁力插件）
- 收藏夹、浏览历史、 actress 收藏
- 论坛热帖原生阅读：详情页帖子直达，楼层支持翻页加载
- 大图浏览、站点公告与版本更新提示

## 项目结构

| 模块 | 说明 |
|---|---|
| `app` | 主程序，聚合各组件与页面 |
| `component_magnet` | 磁力解析组件（CC 组件） |
| `component_interceptors` | 网络/图片请求拦截器组件 |
| `component_plugin_manager` | Phantom 插件管理组件 |
| `libraries/library_base` | 基础库（网络、图片、MVP 基类） |
| `libraries/library_common_bean` | 公共数据模型 |
| `plugins/plugin_magnet` | 磁力插件（独立打包，可热更新） |
| `buildscripts` | CC 组件化构建脚本 |

## 构建

环境要求：

- JDK 8（`org.gradle.java.home` 需指向 JDK 8，Gradle 5.4.1 不支持高版本 JDK）
- Android SDK（compileSdk / buildTools 28.0.3）
- Gradle 5.4.1（使用仓库内 wrapper 即可，无需本机安装）

配置 `local.properties`（已加入 `.gitignore`，不会提交）：

```properties
sdk.dir=C\:\\Users\\<you>\\android-sdk-windows
org.gradle.java.home=C\:\\<path-to-jdk8>
```

签名配置在 `gradle.properties` 中通过 `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEYSTORE_ALIAS` / `KEY_PASSWORD` 提供；未配置时会回退到环境变量 `KEYSTORE_FILE` / `KEYSTORE_PWD` / `KEYSTORE_ALIAS` / `KEYSTORE_ALIAS_PWD`。仅本地调试时可删除 `gradle.properties` 中的签名项，改用默认 debug 签名（需要自行调整 `signingConfigs`）。

常用命令（Windows）：

```bat
gradlew.bat assembleDebug        :: 调试包（渠道 gayhub）
gradlew.bat assembleRelease      :: 混淆签名包，产物命名 JubsDriver[vX.Y.Z]_(code_N).apk
```

Release 的 `versionName` / `versionCode` 由 git tag 与提交数自动生成，打 tag 后再出包。
