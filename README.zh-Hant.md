# Kocoa Beam - Android 上的 Klipper

<p align="center">
  <a href="https://github.com/Brozinga/Kocoa-Beam/releases/latest"><img src="https://img.shields.io/github/v/release/Brozinga/Kocoa-Beam?label=最新版本&color=E0A030" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/平台-Android%205.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/授權-GPL--3.0-4B8BBE" alt="授權：GPL-3.0">
  <img src="https://img.shields.io/badge/kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin / Jetpack Compose">
</p>

**語言: [English](README.md) · [Português (BR)](README.pt-br.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)**

<p align="center">
  <img src="docs/images/principal-screen.png" alt="Kocoa Beam 主畫面" width="270">
  <img src="docs/images/log-screen.png" alt="Kocoa Beam Logs 分頁" width="270">
</p>

> **只想安裝？** 直接到 [Releases 頁面](https://github.com/Brozinga/Kocoa-Beam/releases/latest)
> 下載最新 APK —— 不確定選哪個的話，選 `arm64`（詳見下方
> [選擇正確的安裝包](#選擇正確的安裝包)）。

<details>
<summary><strong>📑 目錄</strong></summary>

- [名字的由來](#名字的由來)
- [為什麼選擇 Kocoa Beam?](#為什麼選擇-kocoa-beam)
- [選擇正確的安裝包](#選擇正確的安裝包)
- [本專案改變了什麼](#本專案改變了什麼)
- [快速入門](#快速入門)
- [截圖](#截圖)
- [韌體（MCU）版本](#韌體mcu版本)
- [文件](#文件)
- [安裝 Kocoa Beam 後裝置還能正常使用嗎?](#安裝-kocoa-beam-後裝置還能正常使用嗎)
- [IP:連接埠是什麼?](#ip連接埠是什麼)
- [內建了什麼?](#內建了什麼)
- [更新](#更新)
- [Android 擴充功能](#android-擴充功能)
- [自動啟動](#自動啟動)
- [背景活動說明](#背景活動說明)
- [支援 Android TV 嗎?](#支援-android-tv-嗎)
- [用哪種 USB 集線器?](#用哪種-usb-集線器)
- [限制](#限制)
- [建置](#建置)
- [致謝](#致謝)
- [貢獻](#貢獻)

</details>

## 名字的由來

**Kocoa Beam** 的名字來源於可可豆——巧克力順滑、濃郁的核心原料。正如可可豆被加工成溫暖美味的巧克力一樣，Kocoa Beam 也將 [Beam Klipper](https://github.com/utkabobr/BeamKlipper) 的原始能量提煉成更柔和、更甜美的體驗。

"K" 代表 Kotlin 與 Klipper 的傳承。"Beam" 則致敬原始的 [Beam Klipper](https://github.com/utkabobr/BeamKlipper)（由 [ProtonKicker](https://github.com/ProtonKicker) 創建）。兩者結合，是一個如同熱可可一般溫暖親切的名字。

Kocoa Beam 可以讓你在任何支援 OTG 的 Android 5.0+ 裝置上執行 [Klipper](https://github.com/KevinOConnor/klipper) 或 [Kalico](https://github.com/KalicoDTU/kalico) 主機軟體。

## 為什麼選擇 Kocoa Beam?

Kocoa Beam 是 Beam Klipper 的全面升級，包含三大改進：

### 1. Kotlin 重寫
整個應用程式已從 Java 遷移到 Kotlin，帶來：
- **空安全** — 編譯時防止 NullPointerException
- **協程** — 自動清理背景執行緒，無洩漏
- **不可變資料類別** — 執行緒安全的事件匯流排訊息和資料庫實體
- **智慧轉型和窮舉檢查** — Bug 在編譯時捕獲，而非執行時

### 2. 體積大幅減小
Kocoa Beam 比原始 Beam Klipper 小很多：

| 元件 | Beam Klipper | Kocoa Beam |
|------|-------------|------------|
| FFmpeg 縮時攝影 | 捆綁二進位檔案（約 40 MB） | Android MediaCodec API（內建） |
| 應用程式大小 | 約 138 MB（arm64） | 約 38 MB（arm64 / armv7），約 41 MB（x86_64） |

FFmpeg 縮時攝影元件已被 Android 原生 MediaCodec API 取代，每個架構節省約 40 MB。

### 3. 全新的 UI
Kocoa Beam 具有完全的 UI 重新設計：
- 野獸派 Bento 風格，「紙/蜜/墨」配色
- 硬質偏移陰影和粗邊框
- 現代 Jetpack Compose 實作
- 改進的版面配置和可用性

### 額外功能
- **10 個並行執行個體** — 同時執行最多 10 個印表機設定檔（對比 Beam Klipper 的 4 個）
- **雙韌體支援** — 執行 Klipper 或 Kalico 韌體引擎
- **原生縮時攝影** — 使用 Android 硬體 MediaCodec 而非捆綁 FFmpeg
- **本機運作** — 無雲端連線，所有資料留在裝置上（已移除 Beam Cloud 支援）

## 選擇正確的安裝包

Kocoa Beam 提供三種 APK 版本：

| 架構 | 套件名稱 | 適用情境 |
|------|----------|----------|
| arm64 | `KocoaBeam_*_arm64.apk` | 現代 64 位元裝置（建議使用） |
| armv7 | `KocoaBeam_*_armv7.apk` | 舊式 32 位元裝置 |
| x86_64 | `KocoaBeam_*_amd64.apk` | x86_64 平板、Chromebook、Android 模擬器 |

**如何檢查裝置架構：**
- 前往「設定」>「關於手機」>「架構」或「核心架構」
- 或安裝 CPU 資訊 App 如「CPU-Z」或「AIDA64」
- 如有疑問，先嘗試 arm64 — 2015 年後發布的裝置大多支援

## 本專案改變了什麼

本專案讓內建的 Klipper / Moonraker / Fluidd / Mainsail / Happy Hare 保持最新，並加入裝置端診斷、可選的 Klipper 附加模組和韌體工具。詳情：

- [`docs/zh-Hans/whats-new.md`](docs/zh-Hans/whats-new.md)（簡體）— 完整變更清單
- [`docs/zh-Hans/build-firmware.md`](docs/zh-Hans/build-firmware.md)（簡體）— 為任意主機板建置 MCU 韌體
- [`docs/zh-Hans/mods/klipper-addons.md`](docs/zh-Hans/mods/klipper-addons.md)（簡體）— 內建的附加模組
- [`docs/zh-Hans/mods/input-shaper-manual.md`](docs/zh-Hans/mods/input-shaper-manual.md)（簡體）— 無加速度計調校 input shaper
- [`docs/zh-Hans/`](docs/zh-Hans/index.md)（簡體）— 文件索引

> 目前完整文件只有簡體中文版，繁體使用者也能順利閱讀；歡迎協助翻譯成繁體。

## 快速入門

1. **MCU 韌體** — 燒錄印表機主機板，可以使用：
   - [Beam Klipper 韌體清單](https://github.com/utkabobr/klipper/releases) 中的預先編譯映像檔
     （`prebuilt-v0.12.0` 系列涵蓋大多數主機板），**或者**
   - 全新編譯的 Klipper 0.13 —— 透過
     [`docs/zh-Hans/build-firmware.md`](docs/zh-Hans/build-firmware.md) 一條指令完成（Docker
     或本機腳本，支援任意受支援的主機板）。

   建議使用 Klipper 0.13；較舊的預先編譯映像檔同樣可用。
2. 從 [Releases 頁面](https://github.com/Brozinga/Kocoa-Beam/releases/latest) 安裝對應 CPU 架構的 APK。
3. 授予所需的權限。
4. 新增印表機執行個體（清單中沒有你的印表機時，選擇 `generic-*.cfg`）。
5. 啟動該執行個體。
6. 開啟網頁介面：Fluidd `http://IP:4408/` 或 Mainsail `http://IP:4409/` —— 目前生效的
   網址會顯示在主畫面上。序列埠會自動偵測。

> **卡住了？** 應用程式內的 **Logs** 分頁（上方截圖）會顯示 Klipper、Moonraker
> 和應用程式本身的記錄，並可直接複製/分享，不需要電腦 —— 如果上面哪一步沒有
> 按預期運作，先去看看記錄檔。

## 截圖

**手機/平板上** —— 主畫面、設定（相機、遠端存取、語言）以及應用內 Logs 分頁：

<p align="center">
  <img src="docs/images/principal-screen.png" alt="主畫面" width="200">
  <img src="docs/images/camera-octoeverywhere-settings.png" alt="設定畫面" width="200">
  <img src="docs/images/log-screen.png" alt="Logs 分頁" width="200">
</p>

**瀏覽器中** —— 由裝置本身提供的網頁介面。Fluidd（系統頁面）與 Mainsail（含即時相機畫面的儀表板）：

<p align="center">
  <img src="docs/images/fluidd-screen-klipper-version.png" alt="由 Kocoa Beam 提供的 Fluidd" width="420">
  <img src="docs/images/mainsail-webcam-dashboard.png" alt="含即時相機的 Mainsail" width="300">
</p>

## 韌體（MCU）版本

印表機主機板（MCU）需要自己的 Klipper 韌體，只需在電腦上燒錄**一次**。有三種方式：

| 方式 | 適合 | 做法 |
|---|---|---|
| **預編譯映像檔** | 新手 —— 無需編譯 | 從 [Beam Klipper 韌體發布頁](https://github.com/utkabobr/klipper/releases) 下載對應主機板的檔案（`prebuilt-v0.12.0` 系列涵蓋許多主機板），依主機板慣例燒錄（SD 卡、DFU 等） |
| **Docker 建置** | 進階使用者、最新 Klipper | `docker compose -f firmware/docker-compose.yml run --rm fw <主機板>` |
| **本機腳本** | 同上，無需 Docker | `./scripts/build_firmware.sh <主機板>` |

- 建議使用 **Klipper 0.13**，但較舊的預編譯映像檔（如 0.12）同樣可用：Klipper 對 MCU 與主機沒有嚴格的版本鎖定。
- 沒有你的主機板？用 `make menuconfig` 儲存 `.config`，再傳給建置腳本。

完整指南：[`docs/zh-Hans/build-firmware.md`](docs/zh-Hans/build-firmware.md)（簡體）。

## 文件

以下內容皆在 [`docs/zh-Hans/`](docs/zh-Hans/index.md)（簡體；另有 English 與 Português 版本）：

| 我想要… | 閱讀 |
|---|---|
| 了解相較 Beam Klipper 有哪些改動 | [`whats-new.md`](docs/zh-Hans/whats-new.md) |
| 設定相機、USB 網路攝影機、預覽與縮放 | [`webcam.md`](docs/zh-Hans/webcam.md) |
| 遠端存取印表機 | [`octoeverywhere.md`](docs/zh-Hans/octoeverywhere.md) · [`obico.md`](docs/zh-Hans/obico.md) |
| 建置/燒錄 MCU 韌體 | [`build-firmware.md`](docs/zh-Hans/build-firmware.md) |
| 自行建置 APK | [`build-app.md`](docs/zh-Hans/build-app.md) |
| 啟用 Klipper 附加模組 / 調校 input shaper | [`mods/klipper-addons.md`](docs/zh-Hans/mods/klipper-addons.md) · [`mods/input-shaper-manual.md`](docs/zh-Hans/mods/input-shaper-manual.md) |

## 安裝 Kocoa Beam 後裝置還能正常使用嗎?

**當然可以！**

Kocoa Beam 不會對 Android 系統做任何更動，它以一般 Android 應用程式的形式執行在使用者空間。

## IP:連接埠是什麼?

任何執行個體執行時，主畫面都會顯示該位址。每個前端有各自的連接埠，跟隨主畫面上的前端切換開關：

- Fluidd => `http://IP:4408/`
- Mainsail => `http://IP:4409/`

相機位址：
- /webcam/?action=stream => `http://IP:8889/`
- /webcam/?action=snapshot => `http://IP:8889/snapshot`

Fluidd 建議使用 mjpeg-**stream**（非 adaptive mjpeg）相機設定，Mainsail 建議使用 UV4L-MJPEG。

<p align="center"><img src="docs/images/fluidd-screen-klipper-version.png" alt="從主畫面顯示的 IP:連接埠開啟的 Fluidd" width="480"></p>

## 內建了什麼?

Kocoa Beam 內建了：
- [Klipper](https://github.com/KevinOConnor/klipper)
- [Kalico](https://github.com/KalicoDTU/kalico)
- [Moonraker](https://github.com/Arksine/moonraker)
- [Fluidd](https://github.com/fluidd-core/fluidd)
- [Mainsail](https://github.com/mainsail-crew/mainsail)
- [Happy Hare](https://github.com/moggieuk/Happy-Hare)
- [Klipper TMC Autotune](https://github.com/andrewmcgr/klipper_tmc_autotune)
- [Moonraker-timelapse](https://github.com/mainsail-crew/moonraker-timelapse)

## 更新

本專案內建元件的版本：

| 元件 | 版本 |
|---|---|
| Klipper / Kalico | 目前上游版本（MCU 韌體目標：0.13） |
| Moonraker | 0.11.0 |
| Fluidd | 1.37.5 |
| Mainsail | 2.19.0 |
| Happy Hare | v4.0.0 |
| OctoEverywhere | 隨附的伴生程式，經改造原生執行於 Android |
| Obico | 隨附的伴生程式，經改造原生執行於 Android（Cloud 或自架伺服器） |

可選啟用的 Klipper 附加模組也已內建（KAMP、LED Effect、Z Calibration、Auto Speed、TMC Autotune）—— 見 [`docs/zh-Hans/mods/klipper-addons.md`](docs/zh-Hans/mods/klipper-addons.md)。完整變更清單：[`docs/zh-Hans/whats-new.md`](docs/zh-Hans/whats-new.md)。

### 近期新增

- **通用 USB 相機支援** — 自動偵測已連接的 USB UVC 相機，並優先使用它而非內建
  相機，支援即時熱插拔切換、能區分多個鏡頭的選擇器（主鏡頭/超廣角/望遠，依
  35mm 等效焦距區分），以及旋轉控制。指南：
  [`docs/zh-Hans/webcam.md`](docs/zh-Hans/webcam.md)。
- **OctoEverywhere 遠端存取** — 真實的 OctoEverywhere Klipper 伴生程式，經改造
  以獨立 Android 處理程序執行，而不是它通常安裝的 systemd 服務。在「設定 →
  遠端存取」中開啟，透過 QR code 連結帳號。指南：
  [`docs/zh-Hans/octoeverywhere.md`](docs/zh-Hans/octoeverywhere.md)。
- **多語言應用程式介面** — 新增巴西葡萄牙語作為完整的應用程式內語言，與英文/
  俄文/中文（簡體與繁體）並列。
- **應用程式內 OctoEverywhere 記錄** — 它的記錄現在與 Klipper/Moonraker 一起顯示
  在 Logs 分頁中，不需要 adb 即可排查問題。
- **相機解析度設定 + 串流穩定性** — 新增可設定的解析度（低/中/高），加上旋轉
  控制，並修正了 WiFi 壅塞時串流卡頓/延遲的問題（依觀看者做背壓控制，避免單一
  慢速連線拖垮所有人的畫面；JPEG 畫質會依網路狀況自動調整）。
- **Obico 遠端存取** — 真實的 Obico Klipper/Moonraker 伴生程式，經改造以獨立
  Android 處理程序執行，連線到 Obico Cloud 或自架的 Obico Server。在「設定 →
  遠端存取」中開啟；應用程式會自行產生並顯示連結驗證碼（與 Obico「Klipper,
  self-installed」導引流程相同），也提供手動輸入驗證碼的替代方式。指南：
  [`docs/zh-Hans/obico.md`](docs/zh-Hans/obico.md)。

- **相機即時預覽分頁** —— 啟用相機伺服器後，Logs 旁會出現新分頁，顯示即時畫面（與 Fluidd/Mainsail 取得的相同）。需要時會請求相機權限，離開分頁即中斷。
- **相機縮放** —— 設定 → 相機 → 相機縮放。只提供所選相機真正支援的縮放檔位（手機的超廣角、長焦或 USB 攝影機限制各不相同）。指南：[`docs/zh-Hans/webcam.md`](docs/zh-Hans/webcam.md)（簡體）。

## Android 擴充功能

Kocoa Beam 提供了一些附加擴充功能，用於控制內建功能。

### 相機

在 printer.cfg 中加入 `[kocoa_camera]`

`SET_CAMERA_FLASHLIGHT ENABLED=true/false` - 開關手電筒

`SET_CAMERA_FOCUS AUTOFOCUS=true/false FOCUS_DISTANCE=0...?` - 設定相機自動對焦狀態；關閉自動對焦時可設定焦距。`FOCUS_DISTANCE` 單位為屈光度，因裝置而異。

### 蜂鳴器

在 printer.cfg 中加入 `[include kocoa_beeper.cfg]`

使用[文件中定義](https://marlinfw.org/docs/gcode/M300.html)的 `M300` 巨集。

## 自動啟動

將需要的印表機設定為自動啟動，**並將應用程式設為預設桌面**，即可實現開機自啟。

如果裝置已加密（大多數裝置預設開啟），你**必須**移除鎖定畫面 PIN 碼。

## 背景活動說明

部分廠商可能會限制應用程式的背景處理程序或效能。可以將應用程式設為預設桌面並允許所有背景工作來規避。

## 支援 Android TV 嗎?

支援，應該可以正常運作。但請注意，部分廉價電視盒不支援直接將 Kocoa Beam 設為桌面，需要先用 ADB 或 root 停用系統桌面。

## 用哪種 USB 集線器?

作者使用的是綠聯（UGREEN）Type-C 集線器（非廣告，只是在等綠聯來合作 :D），只要能同時充電且與你的裝置相容，任何集線器都可以。

## 限制

- Web 伺服器無法使用預設連接埠，因為 Android/Linux 不允許使用者空間應用程式繫結 1024 以下的連接埠，而預設的 `http://IP` 需要 80 連接埠
- 部分裝置在韌體重新啟動後會重設裝置路徑，這種情況下請使用 VID/PID 命名
- 不支援 SSH（也因此無法在裝置上編譯韌體或執行額外的自啟服務）
- 部分裝置不支援同時 OTG 和充電，這種情況只能直接焊接到電池接腳（或者換一台裝置，隨你）
- 僅支援 250000 鮑率（不想把這個設定轉送到 Android USB 驅動程式，幾乎所有設定都用 250000 而已）

> **最常見的問題：** 如果手機沒辦法一邊充電一邊跟印表機通訊，就是上面說的
> OTG+充電限制 —— 用一個自帶供電的 USB 集線器（見[用哪種 USB 集線器?](#用哪種-usb-集線器)）就能解決。

## 建置

一鍵環境設定（安裝固定版本的 SDK / NDK / CMake、Chaquopy 所需的 Python 3.10，並寫入 `local.properties`）：

- Linux / macOS：`./scripts/setup.sh`
- Windows：`.\scripts\setup.ps1`

接著執行 `./gradlew :app:assembleArm64Debug`，或用 Android Studio 開啟專案並點選 Run。詳細步驟、手動設定與簽署見 [`docs/zh-Hans/build-app.md`](docs/zh-Hans/build-app.md)（簡體）。

## 致謝

- **[ProtonKicker/Kocoa-Beam](https://github.com/ProtonKicker)** —— 將應用程式移植到 Kotlin 並重做了介面。
- **[Beam Klipper](https://github.com/utkabobr/BeamKlipper)** —— 本專案的原始來源。
- Klipper、Kalico、Moonraker、Fluidd、Mainsail 及其他內建元件歸各自作者所有（見[內建了什麼?](#內建了什麼)）。

## 貢獻

歡迎提交 Pull Request！
