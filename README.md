# 行迹空间 (Campus Atlas)

行迹空间是一个面向 Android 平台的地理位置模拟、轨迹回放与 LBS 场景测试工具。本项目旨在帮助开发者在室内或无实际位移环境下，开展基于地图选点、路径规划、位置监听容错以及应用隔离沙盒相关的开发与自动化测试。

---

## 核心特性

- **地图选点与坐标转换**：基于高德地图 SDK 提供底图浏览、点位选取、关键词搜索与 WGS84 / GCJ-02 坐标系转换。
- **动态路线规划与平滑回放**：支持路线节点录入、自定义配速与更新频率，依托 Android 前台服务（`specialUse` 类型）实现稳定的后台定时位置推送。
- **课表与行程文本地点提取**：集成 Google ML Kit 中文 OCR 文本识别技术，自动解析日程表单中的教学楼、校区及活动场所关键词，并支持手动坐标关联。
- **应用容器与位置注入**：内嵌轻量级 Android 虚拟化引擎，支持在隔离容器内启动待测应用，并通过进程间通信直接向目标实例投递模拟位置。
- **诊断探针 (Location Probe)**：提供独立的定位回调诊断工具，用于精确验证系统 Mock Location 标志位及位置回调时间戳。

---

## 项目架构

```
.
├── app/                  # Android 宿主主应用（UI、前台服务、地图呈现）
├── core/                 # 核心算法库（轨迹插值状态机、坐标计算、课表解析）
├── vendor/blackbox/      # 经定制与加固的轻量级虚拟化容器引擎
├── location-probe/       # 独立的定位诊断与回调验证应用
├── scripts/              # 清单安全检测等辅助维护脚本
├── LICENSE               # 项目开源许可证 (Apache-2.0)
└── THIRD-PARTY-NOTICES.md # 第三方组件许可与版权说明
```

---

## 构建与配置说明

### 环境要求

- **操作系统**：Windows / macOS / Linux
- **JDK**：OpenJDK 17 或更高版本
- **Android SDK**：API 35 (Android 15)
- **Build Tools**：35.0.0
- **Android NDK**：29.0.13846066
- **架构支持**：ARM64 (arm64-v8a)

### 高德地图 Key 配置

地图和地点搜索需要高德 Android SDK Key。请在 [高德开放平台控制台](https://console.amap.com/) 创建应用并添加 **Android 平台 SDK** Key，填写安装包的包名和签名证书 SHA-1；具体步骤可参考[高德获取 Key 指南](https://lbs.amap.com/api/android-sdk/guide/create-project/get-key)。

使用本项目提供的 1.0.0 开源版安装包或 2.0.0 开发版安装包时，填写：

- 包名：`dev.campus.atlas.oss`
- 签名 SHA-1：`E7:EA:23:7B:CA:7A:18:40:09:04:C8:14:F3:EB:8C:1B:45:60:17:F4`

**已安装应用**：打开「定位」页，点击「配置高德 Key」，粘贴申请的 Key 并保存；然后阅读并同意地图隐私说明。Key 保存在应用的本机私有存储中，不需要修改安装包。使用有效 Key 前，仍可手动输入坐标，但无法使用高德地图底图和地点搜索。

**自行编译源码**：以实际构建的 `applicationId` 和签名证书 SHA-1 为准；使用自己的签名时不要直接使用上方指纹。可将项目根目录下的 `local.properties.example` 复制为 `local.properties`，填写：

```properties
AMAP_API_KEY=您的高德地图Android_Key
```

也可以通过 `AMAP_API_KEY` 环境变量或 Gradle 属性配置。`local.properties` 不应提交到版本控制；构建时配置的 Key 会进入 APK，应在高德控制台限制其使用范围。自行编译的安装包若未配置内置 Key，也可在应用内填写。

### 编译指令

使用项目内置的 Gradle Wrapper 执行编译与测试：

```bash
# 执行单元测试
./gradlew :core:test :app:testDebugUnitTest

# 构建 Debug 版本安装包
./gradlew :app:assembleDebug
```

---

## 开源许可证与第三方声明

本项目自身遵循 **[Apache License 2.0](LICENSE)** 协议开源。

项目集成了部分第三方开源项目及专有商业 SDK，具体版权归属与许可条款详见 **[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)**：
- **BlackBox**：遵循 Apache-2.0 许可证，修改说明详见 `vendor/blackbox/NOTICE-ATLAS.md`；
- **xDL**：遵循 MIT 许可证，Copyright (c) 2020-2024 HexHacking Team；
- **Dobby**：遵循 Apache-2.0 许可证，Copyright (c) 2017-2024 jmpews；
- **FreeReflection**：遵循 Apache-2.0 许可证，Copyright (c) 2019-2024 Tiann；
- **toml4j**：遵循 MIT 许可证，Copyright (c) 2015 Francois Blanc；
- **高德地图 SDK**：专有商业闭源 SDK，依据高德开放平台服务条款动态链接使用，本项目不重新分发其二进制原包；
- **Google ML Kit**：依据 Google APIs 条款与 ML Kit 许可使用。

---

## 免责与合规声明 (Legal Disclaimer)

1. **研发测试用途**：本项目仅供移动开发者、安全研究人员及地理信息系统相关学习者进行技术研究、LBS 应用边界测试与异常容错排查使用。
2. **严禁非法利用**：任何个人或组织**严禁**将本项目及其衍生版本用于虚假考勤打卡、校园跑或体育考核舞弊、篡改第三方平台营运数据、规避监管风控等任何侵害他人合法权益、违反法律法规或破坏计算机信息系统正常运行的行为。
3. **免责条款**：使用者须自行承担因使用本软件所产生的一切技术、商业及法律后果。因使用者违规操作引发的任何纠纷或法律责任，与本项目开发者及贡献者无关。
