# Kocoa Beam - Android 上的 Klipper

<p align="center">
  <a href="https://github.com/Brozinga/Kocoa-Beam/releases/latest"><img src="https://img.shields.io/github/v/release/Brozinga/Kocoa-Beam?label=最新版本&color=E0A030" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/平台-Android%205.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/许可证-GPL--3.0-4B8BBE" alt="许可证：GPL-3.0">
  <img src="https://img.shields.io/badge/kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin / Jetpack Compose">
</p>

**语言: [English](README.md) · [Português (BR)](README.pt-br.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)**

<p align="center">
  <img src="docs/images/principal-screen.png" alt="Kocoa Beam 主界面" width="324">
  <img src="docs/images/log-screen.png" alt="Kocoa Beam Logs 标签页" width="324">
</p>

> **只想安装？** 直接到 [Releases 页面](https://github.com/Brozinga/Kocoa-Beam/releases/latest)
> 下载最新 APK —— 不确定选哪个的话，选 `armv7`（详见下方
> [选择正确的安装包](#选择正确的安装包)）。

<details>
<summary><strong>📑 目录</strong></summary>

- [名字的由来](#名字的由来)
- [为什么选择 Kocoa Beam?](#为什么选择-kocoa-beam)
- [本项目改变了什么](#本项目改变了什么)
- [选择正确的安装包](#选择正确的安装包)
- [快速开始](#快速开始)
- [截图](#截图)
- [文档](#文档)
- [IP:端口是什么?](#ip端口是什么)
- [内置了什么?](#内置了什么)
- [更新](#更新)
- [Android 扩展](#android-扩展)
- [自动启动](#自动启动)
- [后台活动说明](#后台活动说明)
- [支持 Android TV 吗?](#支持-android-tv-吗)
- [用哪种 USB 集线器?](#用哪种-usb-集线器)
- [限制](#限制)
- [构建](#构建)
- [致谢](#致谢)
- [贡献](#贡献)

</details>

## 名字的由来

**Kocoa Beam** 的名字来源于可可豆——巧克力顺滑、浓郁的核心原料。正如可可豆被加工成温暖美味的巧克力一样，Kocoa Beam 也将 [Beam Klipper](https://github.com/utkabobr/BeamKlipper) 的原始能量提炼成更柔和、更甜美的体验。

"K" 代表 Kotlin 与 Klipper 的传承。"Beam" 则致敬原始的 [Beam Klipper](https://github.com/utkabobr/BeamKlipper)（由 [ProtonKicker](https://github.com/ProtonKicker) 创建）。两者结合，是一个如同热可可一般温暖亲切的名字。

Kocoa Beam 可以让你在任何支持 OTG 的 Android 5.0+ 设备上运行 [Klipper](https://github.com/KevinOConnor/klipper) 或 [Kalico](https://github.com/KalicoDTU/kalico) 主机软件。

## 为什么选择 Kocoa Beam?

Kocoa Beam 是 Beam Klipper 的全面升级，包含三大改进：

### 1. Kotlin 重写
整个应用已从 Java 迁移到 Kotlin，带来：
- **空安全** — 编译时防止 NullPointerException
- **协程** — 自动清理后台线程，无泄漏
- **不可变数据类** — 线程安全的事件总线消息和数据库实体
- **智能转换和穷举检查** — Bug 在编译时捕获，而非运行时

### 2. 体积大幅减小
Kocoa Beam 比原始 Beam Klipper 小很多：

| 组件 | Beam Klipper | Kocoa Beam |
|------|-------------|------------|
| FFmpeg 延时摄影 | 捆绑二进制文件（约 40 MB） | Android MediaCodec API（内置） |
| 应用大小 | 约 138 MB（arm64） | 约 38 MB（arm64 / armv7），约 41 MB（x86_64） |

FFmpeg 延时摄影组件已被 Android 原生 MediaCodec API 取代，每个架构节省约 40 MB。

### 3. 全新的 UI
Kocoa Beam 具有完全的 UI 重新设计：
- 粗野主义 Bento 风格，「纸/蜜/墨」配色
- 硬质偏移阴影和粗边框
- 现代 Jetpack Compose 实现
- 改进的布局和可用性

## 本项目改变了什么

在 Beam Klipper 基础上新增或修复的全部内容（详情见链接的指南）：

**平台与界面**
- [x] Kotlin 重写
- [x] 全新的 UI
- [x] 10 个并发打印机实例
- [x] Klipper 与 Kalico 固件引擎
- [x] 仅本地运行（移除 Beam Cloud）
- [x] 巴西葡萄牙语应用界面
- [x] 应用内 Logs 标签页
- [x] 崩溃日志
- [x] 每个前端使用独立的网页端口

**内置软件**
- [x] 支持 Klipper 0.13 — [`build-firmware.md`](docs/zh-Hans/build-firmware.md)
- [x] 更新 Moonraker（0.11.0）
- [x] 更新 Fluidd（1.37.5）
- [x] 更新 Mainsail（2.19.0）
- [x] 更新 Happy Hare（v4.0.0）
- [x] 新增 Voyager UI (v0.23)
- [x] Klipper 附加模块：KAMP、LED Effect、Z Calibration、Auto Speed、TMC Autotune — [`mods/klipper-addons.md`](docs/zh-Hans/mods/klipper-addons.md)
- [x] 无加速度计的 input shaper — [`mods/input-shaper-manual.md`](docs/zh-Hans/mods/input-shaper-manual.md)
- [x] 起始 `printer.cfg` 模板与打印机配置 — [`getting-started.md`](docs/zh-Hans/getting-started.md)
- [x] MCU 固件编译工具（Docker 与本地脚本）— [`build-firmware.md`](docs/zh-Hans/build-firmware.md)

**功能**
- [x] 原生延时摄影（用 MediaCodec 取代 FFmpeg）— [`timelapse.md`](docs/zh-Hans/timelapse.md)
- [x] USB 摄像头支持、摄像头分辨率与旋转 — [`webcam.md`](docs/zh-Hans/webcam.md)
- [x] 摄像头实时预览、缩放与点击对焦 — [`webcam.md`](docs/zh-Hans/webcam.md)
- [x] 弱 WiFi 下的推流稳定性 — [`webcam.md`](docs/zh-Hans/webcam.md)
- [x] 断电续打 — [`print-recovery.md`](docs/zh-Hans/print-recovery.md)
- [x] 新增 OctoEverywhere 远程访问 — [`octoeverywhere.md`](docs/zh-Hans/octoeverywhere.md)
- [x] 新增 Obico 远程访问 — [`obico.md`](docs/zh-Hans/obico.md)

**修复**
- [x] 修复延时摄影渲染 — [`timelapse.md`](docs/zh-Hans/timelapse.md)
- [x] 修复 G-code 元数据与缩略图
- [x] 修复 Fluidd/Mainsail 静态资源（MIME 类型）
- [x] 修复 Klipper 宏不执行任何操作的问题
- [x] 修复 Klipper 因标准 `[mcu]` 选项而启动中止的问题


## 选择正确的安装包

Kocoa Beam 提供三个 APK 版本：

| 架构 | 包名称 | 适用场景 |
|------|--------|----------|
| arm64 | `KocoaBeam_*_arm64.apk` | 现代 64 位设备 |
| armv7 | `KocoaBeam_*_armv7.apk` | 旧式 32 位设备 —— 大多数设备均可使用（不确定时推荐） |
| x86_64 | `KocoaBeam_*_amd64.apk` | x86_64 平板、Chromebook、Android 模拟器 |

**如何检查设备架构：**
- 前往「设置」>「关于手机」>「架构」或「内核架构」
- 或安装 CPU 信息 App 如「CPU-Z」或「AIDA64」
- 如有疑问，请选择 armv7 — 它兼容的设备范围最广

## 快速开始

**从这里开始：[`docs/zh-Hans/getting-started.md`](docs/zh-Hans/getting-started.md)** —— 带截图的分步指南，涵盖安装 Kocoa Beam 并首次运行（APK、第一台打印机、MCU 固件、打开网页界面）。

> **卡住了？** 应用内的 **Logs** 标签页（上方截图）显示 Klipper、Moonraker 和应用自身的日志，并可以直接复制/分享，不需要电脑 —— 如果哪一步没按预期工作，先去看看日志。

## 截图

**手机/平板上**

| 主界面 | 设置 | 摄像头实时预览 | 缩放（2×） |
|:-:|:-:|:-:|:-:|
| <img src="docs/images/app-main-running.png" width="216"> | <img src="docs/images/app-settings-frontend-camera.png" width="216"> | <img src="docs/images/camera-preview-tab.png" width="216"> | <img src="docs/images/camera-preview-zoom.png" width="216"> |
| 启动/停止打印机，显示网页地址 | 固件引擎、网页前端、USB、摄像头、远程访问、语言 | 新标签页：查看摄像头画面 | 可选缩放档位取决于所选摄像头 |

**浏览器中** —— 网页界面由设备本身提供，此时打印机已连接、摄像头画面为实时：

<p align="center"><b>Fluidd</b><br><img src="docs/images/fluidd-dashboard-webcam.png" alt="带实时摄像头的 Fluidd 仪表盘" width="800"></p>
<p align="center"><b>Mainsail</b><br><img src="docs/images/mainsail-dashboard-webcam.png" alt="带实时摄像头的 Mainsail 仪表盘" width="800"></p>
<p align="center"><b>Voyager UI</b><br><img src="docs/images/voyager-dashboard-webcam.jpg" alt="带实时摄像头的 Voyager UI 仪表盘" width="800"></p>

**断电续打** —— 断电后，Fluidd 和 Mainsail 会提示是否继续打印（[指南](docs/zh-Hans/print-recovery.md)）：

<p align="center"><img src="docs/images/powerless-recovery.png" alt="Fluidd asking whether to resume an interrupted print" width="720"></p>

应用内 **Logs** 标签页见本页顶部。

## 文档

以下内容均在 [`docs/zh-Hans/`](docs/zh-Hans/index.md)（另有 English 与 Português 版本）：

| 我想要… | 阅读 |
|---|---|
| 设置摄像头、USB 摄像头、预览与缩放 | [`webcam.md`](docs/zh-Hans/webcam.md) |
| 远程访问打印机 | [`octoeverywhere.md`](docs/zh-Hans/octoeverywhere.md) · [`obico.md`](docs/zh-Hans/obico.md) |
| 构建/烧录 MCU 固件 | [`build-firmware.md`](docs/zh-Hans/build-firmware.md) |
| 自己构建 APK | [`build-app.md`](docs/zh-Hans/build-app.md) |
| 启用 Klipper 附加模块 / 调 input shaper | [`mods/klipper-addons.md`](docs/zh-Hans/mods/klipper-addons.md) · [`mods/input-shaper-manual.md`](docs/zh-Hans/mods/input-shaper-manual.md) |

## IP:端口是什么?

任意实例运行时，主页面都会显示该地址。每个前端有各自的端口，跟随主界面上的前端切换开关：

- Fluidd => `http://IP:4408/`
- Mainsail => `http://IP:4409/`
- Voyager UI => `http://IP:4410/`

<p align="center"><img src="docs/images/fluidd-screen-klipper-version.png" alt="从主界面显示的 IP:端口打开的 Fluidd" width="960"></p>

## Android 扩展

Kocoa Beam 提供了一些附加扩展功能，用于控制内置功能。

### 摄像头

在 printer.cfg 中加入 `[kocoa_camera]`

`SET_CAMERA_FLASHLIGHT ENABLED=true/false` - 开关闪光灯

`SET_CAMERA_FOCUS AUTOFOCUS=true/false FOCUS_DISTANCE=0...?` - 设置摄像头自动对焦状态；关闭自动对焦时可设置焦距。`FOCUS_DISTANCE` 单位为屈光度，因设备而异。

### 蜂鸣器

在 printer.cfg 中加入 `[include kocoa_beeper.cfg]`

使用[文档中定义](https://marlinfw.org/docs/gcode/M300.html)的 `M300` 宏。

## 自动启动

将需要的打印机设置为自动启动，**并将应用设为默认桌面**，即可实现开机自启。

如果设备已加密（大多数设备默认开启），你**必须**移除锁屏 PIN 码。

## 后台活动说明

部分厂商可能会限制应用的后台进程或性能。可以将应用设为默认桌面并允许所有后台任务来规避。

## 支持 Android TV 吗?

支持，应该可以正常工作。但请注意，部分廉价电视盒子不支持直接将 Kocoa Beam 设为桌面，需要先用 ADB 或 root 禁用系统桌面。

## 用哪种 USB 集线器?

作者使用的是绿联（UGREEN）Type-C 集线器（非广告，只是在等绿联来合作 :D），只要能同时充电且与你的设备兼容，任何集线器都可以。

## 限制

- Web 服务器无法使用默认端口，因为 Android/Linux 不允许用户空间应用绑定 1024 以下的端口，而默认的 `http://IP` 需要 80 端口
- 部分设备在固件重启后会重置设备路径，这种情况下请使用 VID/PID 命名
- 不支持 SSH（也因此无法在设备上编译固件或运行额外的自启服务）
- 部分设备不支持同时 OTG 和充电，这种情况只能直接焊接到电池引脚（或者换一台设备，随你）
- 仅支持 250000 波特率（不想把这个设置转发到 Android USB 驱动，几乎所有配置都用 250000 而已）

> **最常见的坑：** 如果手机没法一边充电一边和打印机通信，就是上面说的 OTG+充电
> 限制 —— 用一个自带供电的 USB 集线器（见[用哪种 USB 集线器?](#用哪种-usb-集线器)）就能解决。

## 构建

一键环境搭建（安装固定版本的 SDK / NDK / CMake、Chaquopy 所需的 Python 3.10，并写入 `local.properties`）：

- Linux / macOS：`./scripts/setup.sh`
- Windows：`.\scripts\setup.ps1`

然后运行 `./gradlew :app:assembleArm64Debug`，或者用 Android Studio 打开项目并点击 Run。详细步骤、手动配置和签名见 [`docs/zh-Hans/build-app.md`](docs/zh-Hans/build-app.md)。

## 致谢

- **[ProtonKicker/Kocoa-Beam](https://github.com/ProtonKicker)** —— 将应用移植到 Kotlin 并重做了界面。
- **[Beam Klipper](https://github.com/utkabobr/BeamKlipper)** —— 本项目的原始来源。
- Klipper、Kalico、Moonraker、Fluidd、Mainsail 及其他内置组件归各自作者所有（见[内置了什么?](#内置了什么)）。
- **[Voyager UI](https://github.com/ozancs/voyager-ui)**，作者 [ozancs](https://github.com/ozancs) —— 除 Fluidd 和 Mainsail 之外可选的第三个网页前端。

## 贡献

欢迎提交 Pull Request！
