# 口头禅哨兵（Catchphrase Sentinel）

一个完全离线的 Android 口头禅监测应用。它在手机本地持续监听麦克风，用 Vosk 中文语音模型实时识别，发现你设定的口头禅就计数、保存那段语音并提醒你。

所有识别都在设备本地完成，音频不会上传到任何服务器。

## 功能

- **实时监测**：前台服务 + 常驻通知，息屏后继续监听
- **离线识别**：内置 Vosk 中文模型，无网络也能用
- **口头禅词库**：最长 4 个字，建议同时监测 3~5 个
- **发音校准**：录入时暂停监听，每个词录 3~5 遍发音，用于校准阈值、学习发音变体
- **统计概览**：今日捕获次数、各口头禅分布、近 7 天趋势
- **时间线**：按时间倒序记录每次命中，可回听命中前后约 2 秒的原始语音
- **提醒方式**：震动、提示音、亮屏，可在设置中单独开关
- **灵敏度可调**：阈值 0~100，越高越严格（误报更少，漏检更多）

## 技术栈

- Kotlin + Jetpack Compose（Material 3，深色仪表盘界面）
- [Vosk](https://alphacephei.com/vosk/) 离线语音识别（`vosk-android` + `vosk-model-small-cn-0.22`）
- Room（检测记录、词库）
- DataStore（设置项）
- 前台 Service + `AudioRecord` 环形缓冲（命中时回捞最近 2 秒音频）

## 项目结构

```
app/src/main/java/com/audiokit/catchphrase/
├── MainActivity.kt / CatchphraseApp.kt
├── audio/            # 采集、识别、匹配、模型解压
│   ├── AudioCapture.kt     # 16kHz 采集 + 5 秒环形缓冲
│   ├── VoskEngine.kt       # Vosk 流式识别
│   ├── PhraseMatcher.kt    # 口头禅匹配与置信度
│   └── ModelManager.kt     # 首次启动解压 assets 里的模型
├── data/             # Room 实体、DAO、仓库、设置
├── service/          # 前台服务、通知、提醒
└── ui/               # Compose 界面：概览 / 时间线 / 词库 / 设置
```

## 构建

环境要求：

- JDK 17
- Android SDK 35（`compileSdk = 35`，`minSdk = 26`）

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 离线模型

识别模型不随仓库提交（`app/src/main/assets/model-cn.zip` 约 42MB），首次构建时会自动下载：

- 来源：<https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip>
- 大小：43,898,754 字节
- SHA-256：`3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba`
- 校验通过后落盘，失败会直接中断构建并提示

`preBuild` 会依赖 `downloadVoskModel`，因此直接 `assembleDebug` 即可。也可以单独执行：

```bash
./gradlew downloadVoskModel
```

如果需要换镜像或官方地址更新，可以覆盖参数：

```bash
./gradlew assembleDebug \
  -PvoskModelUrl=https://your-mirror/vosk-model-small-cn-0.22.zip \
  -PvoskModelSha256=<对应的 sha256>
```

国内网络下载较慢时，可给 Gradle 配代理：

```bash
./gradlew assembleDebug \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10809
```

模型已存在且大小正确时会跳过下载；想强制重新下载，删掉 `app/src/main/assets/model-cn.zip` 即可。

## 使用

1. 安装后首次启动，点「开始监听」，授予麦克风与通知权限
2. 到「词库」新增口头禅，按提示录 3~5 遍发音完成校准
3. 回到「概览」查看今日次数；到「时间线」回听每次命中的片段
4. 在「设置」里按需调整灵敏度、是否保存片段、提醒方式（震动 / 提示音 / 亮屏）

## 隐私

- 识别 100% 在本地完成，不联网
- 命中片段只保存在应用私有目录，不会上传
- 时间线里可以逐条删除记录与音频

## 已知限制

- 模型是 small 版，安静环境下效果好，嘈杂环境准确率会下降
- 口头禅最长 4 个汉字，暂不支持整句检测
- 长时间后台监听会增加耗电，建议在需要时开启

## 第三方资源

- Vosk 及 `vosk-model-small-cn-0.22`，遵循 Apache License 2.0
