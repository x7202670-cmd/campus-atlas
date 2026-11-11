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

出于商业合规与安全考虑，本代码仓库**不包含任何私有 API Key**。在构建运行前，请按以下步骤配置您自己的高德开放平台密钥：

1. 登录 [高德开放平台控制台](https://console.amap.com/)，创建 Android 应用凭证；
2. 绑定包名 `dev.campus.atlas` 及您本地构建签名的 SHA-1 指纹；
3. 将根目录下的 `local.properties.example` 复制为 `local.properties`，并填入您的 Key：
   ```properties
   AMAP_API_KEY=您的高德地图Android_Key
   ```
   *(注：`local.properties` 已受 `.gitignore` 保护，切勿提交至版本控制系统)*

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
