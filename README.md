# JBusDriver

JAVBUS 的第三方 Android 客户端，受 [JAViewer](https://github.com/SplashCodes/JAViewer) 启发编写。
原项目：<https://github.com/Ccixyj/JBusDriver.git>，本仓库是其修复分支。

## 功能

- 影片：有码 / 无码 / 高清 / 字幕四类
- 搜索：有码 / 无码 / 女优 / 导演 / 制作商 / 发行商 / 系列七类结果分页
- 详情页：封面预览、磁力链接、相关论坛帖子；磁力解析内置多个站点解析器
- 论坛：首页轮播 + 热帖 + 分组板块，帖子楼层原生阅读并支持翻页
- 收藏夹、浏览历史
- 两套外壳：侧边抽屉 / 底部导航，设置里切换后重启生效

## 模块

| 模块 | 说明 |
|---|---|
| `app` | 主程序与全部页面 |
| `component_magnet` | 磁力解析库 |
| `libraries/library_base` | 网络、图片、MVP 基类与通用控件 |
| `libraries/library_common_bean` | 公共数据模型 |

## 构建

需要 JDK 17。版本号与 SDK 配置来自共享基线仓库的 `libs.versions.toml`，本仓库需与之**同级** clone，
否则 `settings.gradle` 解析不到 catalog；本地 SDK 路径写在 `local.properties`（已 gitignore）的 `sdk.dir`。

```bat
gradlew.bat assembleDebug      :: 调试包
gradlew.bat assembleRelease    :: 混淆 + 资源压缩，产物 JubsDriver-v<versionName>.apk
```

签名与网络代理是机器专属项，不入库，配置见 `gradle.properties.example`。

## 发版

改 `app/build.gradle` 的 `defaultConfig` 版本号与 `app/src/main/assets/properties.json` 的
`latest_version_code`，两者必须一致，tag 打 `v<versionName>`。检查更新走 master 上的 `properties.json`，
**须先合入 master 再发 Release**。

## 备注

- `app/native-libs/` 放的是 Umeng 的 x86_64 `libumeng-spy.so`（官方只发布到 x86），缺失时纯 x86_64 设备会 `INSTALL_FAILED_NO_MATCHING_ABIS`。
- 站点地址写死在本地，不再走云端下发与探活换站。
