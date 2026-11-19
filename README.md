# Dsh Linux Studio（安卓本地开发环境）

一款面向安卓设备的**独立 Android APP**：无需 root、无需先安装 Termux，APP 自带 PRoot 运行时，可在手机本地启动完整的 Ubuntu 24.04 arm64 用户态环境，安装 npm 包、运行 Shell 命令、使用 ` apt ` 安装开发工具，并提供本地代码工作台。

第一个完整功能链路是：**一键安装 DeepSeek Harness（dsh）并在 APP 内打开它的 Web 工作台**。

## 已实现能力

| 模块 | 说明 |
|---|---|
| Linux 环境 | 下载并校验 Ubuntu 24.04.5 arm64 根文件系统，用 PRoot 启动；支持 apt、bash、git、curl、python3、build-essential |
| Node.js | 下载并校验官方 Node.js v24.22.3 arm64 二进制，写入 `/opt/node` 并配置 PATH |
| DeepSeek Harness | `npm install -g @deepseek-ai/dsh@latest`，通过前台服务启动 `dsh web`；自动捕获启动日志中带一次性 token 的登录链接并在 WebView 中打开 |
| API Key 安全 | 使用 Android Keystore 加密保存，启动 dsh 时仅通过进程环境变量注入 |
| 本地开发 | 内置 Linux 终端；可选安装 code-server，在手机上获得 VS Code Web 体验 |
| 进程保活 | 使用 Android 前台服务托管安装任务与 dsh / code-server 服务 |

## 快速使用

1. 用 Android Studio 打开本项目，或执行构建命令生成 APK。
2. 在设置页填写 DeepSeek API Key。
3. 在工作台页依次点击：
   1. **安装 Linux 环境**
   2. **安装 DeepSeek Harness**
   3. **启动 DSH 服务**
   4. **打开 DSH 工作台**
4. 首次进入 dsh Web UI 后，按官方流程选择工作目录；APP 已把 `~/workspace` 映射到 Linux 的 `/root/workspace`。

## 构建

```bash
# 1. 准备 arm64 PRoot 二进制（仅需一次）
scripts/fetch-runtime-binaries.sh

# 2. 构建 Debug APK
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`。已复制一份到项目根目录：`DshLinuxStudio-arm64-v8a-debug.apk`。

当前版本：v0.1.1（修复 dsh Web 一次性 token 登录链接自动打开问题）。

本次构建产物 SHA256：`6bdf264636607bd8edbfa6b3b1f2fc06eb4bff7e23b9af9b7580557619df2f46`

## 技术实现

- Kotlin + Jetpack Compose
- PRoot 5.1.107.102，来自 Termux 官方仓库，以 `lib*.so` 形式打包以绕过 Android 10+ 的 W^X 限制
- Ubuntu 24.04.5 arm64 base rootfs，SHA256 校验
- Node.js v24.22.3 linux-arm64 官方包，SHA256 校验
- Apache Commons Compress 负责 tar.gz 解压，APK 内不携带 30MB 以上 rootfs
- DeepSeek Harness Web UI 默认监听 `127.0.0.1:3080`，仅本机可见

## 限制

- 当前仅支持 **arm64-v8a** 设备。
- 首次安装需要下载约 30MB 根文件系统 + 30MB Node.js + npm 依赖，建议连接 Wi-Fi 并预留 3GB 以上空间。
- PRoot 不是虚拟机；它提供完整 Linux 用户态，但内核仍是 Android 内核，不能运行需要特定内核模块或真实 root 的软件。
- DeepSeek Harness 处于开发者预览阶段，npm 包名与 CLI 参数可能变化；本项目锁定的版本见 `LinuxRuntime.kt`。
