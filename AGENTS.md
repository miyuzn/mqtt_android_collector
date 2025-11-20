# Repository Guidelines (Android App)

## Communication
- 默认用简体中文回复；如需代码注释/UI 文案，请保持与现有多语言资源一致。

## 多语言支持
- 所有新增 UI 字符串必须同时提供中文、日文、英文；其他系统语言需回落到英文。

## Project Structure & Modules
- `app/`: Android 应用工程（Kotlin），包含 UDP 采集、MQTT 桥接、本地存储与 UI。
- `core/` (under `app/src/main/java/...`): 配置、解析等核心逻辑。
- `service/`: 前台服务、控制器、通知。
- `data/`: 设备注册表、本地 CSV 存储。
- 资源：`res/layout`, `res/values*`（多语言字符串）。

## Build, Test, and Development Commands
- 编译 Debug 版：`./gradlew assembleDebug`
- 单元测试（JVM）：`./gradlew testDebugUnitTest`
- 如果需要 Android Instrumentation 测试：`./gradlew connectedDebugAndroidTest`（需连接设备/模拟器）

## Coding Style & Naming
- Kotlin：遵循官方 Kotlin/Android 风格，4 空格缩进，使用 Jetpack/协程优先。
- 保持解析和网络/I/O 分层，核心逻辑可在 JVM 测试覆盖。
- 配置放在 `BridgeConfig` 与持久化仓库中，避免硬编码。

## Testing Guidelines
- 优先覆盖：UDP 解析、队列/丢包策略、本地 CSV 写入、MQTT 或本地模式切换。
- 新增逻辑应至少有 JVM 单元测试；UI 变更可用手动验证截图/录屏说明。

## Commit & PR Guidelines
- 提交信息简洁、祈使句（如 “Add local CSV sink”）。
- PR 需说明变更目的、测试情况，UI 变更附截图/录屏。

## Security & Configuration
- 不提交密钥/证书。MQTT/存储路径等通过配置或安全存储处理。
- 前台服务需遵守通知权限/省电策略；文件存储遵循 Scoped Storage。

## Debugging
- 使用 Logcat/`adb shell` 进行调试；网络抓包可用 Android Studio Network Inspector 或本地代理。
