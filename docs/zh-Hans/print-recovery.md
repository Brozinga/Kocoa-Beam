# 断电续打 —— 断电后恢复打印

**语言：[English](../print-recovery.md) · [Português (BR)](../pt-br/print-recovery.md) · [简体中文](print-recovery.md)**

打印中如果打印机断电、USB 线松脱或 Klipper 关机，Kocoa Beam 可以让你**从中断处继续打印**。这是尽力而为的功能：模型仍牢固粘在热床上时效果最好。手机有电池，因此即使打印机断电，打印位置也不会丢失。

## 工作方式

1. 打印时，每 **2 秒**（可配置）把 G-code 文件位置、喷嘴位置、温度、风扇、速度/流量、Z 偏移和热床网格保存到一个小文件。
2. 打印机恢复且 Klipper 就绪后，Fluidd 和 Mainsail 会弹出 **"Print interrupted"** 窗口询问是否继续。
3. 点击 **Resume print** 后，打印机会：
   1. 加热热床；
   2. 抬高喷嘴，**仅** X/Y 回零，移动到 **X 限位处**（远离模型）；
   3. 在那里加热喷嘴，避免耗材滴在模型上；
   4. 可选：挤出少量耗材（清洗）；
   5. 回到保存的位置，从文件保存的字节处继续打印。

**Discard** 放弃该打印。关闭窗口后可在控制台运行 `PRINT_RECOVERY_STATUS` 再次显示（有待恢复的打印时每分钟也会再次提示）。

## 启用

在 `printer.cfg` 中添加并重启：

```ini
[print_recovery]
```

以下选项均为可选，显示的是默认值：

```ini
[print_recovery]
snapshot_interval: 2      # 保存间隔（秒，0.5 - 300）
park_enable: True         # True：X/Y 回零并在 X 限位处加热喷嘴
                          # False：不移动 X/Y，原地加热（见下）
park_x:                   # 加热/清洗时的 X（默认：X 最小值）
park_y:                   # 加热/清洗时的 Y（默认：回零后的位置）
park_speed: 100           # 移动速度 mm/s
lift_z: 10                # X/Y 回零前抬高喷嘴的毫米数
purge: True               # True/False：继续前是否清洗（仅 park_enable 为 True 时）
purge_length: 20          # 清洗耗材长度 mm
purge_speed: 5            # mm/s
purge_retract: 2          # 清洗后回抽 mm
min_extruded: 5           # 首次保存前需挤出的 mm
language: auto            # 窗口/控制台语言（见下）
prompt: True              # 是否显示 Fluidd/Mainsail 窗口
prompt_repeat: 60         # 提醒间隔（秒，0 = 仅一次）
```

手动恢复时可覆盖：`PRINT_RECOVERY_RESUME PARK=1 PARK_X=-6 PURGE=0 PURGE_LENGTH=10 LIFT_Z=5`（`PARK`、`PURGE` 接受 `1`/`0` 或 `True`/`False`）。未指定的项使用 `printer.cfg` 中的值或上述默认值。

### 不移动到 X 限位（`park_enable: False`）

喷嘴**不会**回零或移动：抬高 `lift_z` 后在原地（模型上方）加热，并跳过清洗（否则耗材会落在模型上）。X、Y、Z 均假定未变化，仅在确定没有移动时使用。加热时喷嘴可能会滴料。

### 语言

窗口和控制台消息跟随 Fluidd 或 Mainsail 所选语言（英语、葡萄牙语、俄语、简体/繁体中文；其他语言显示英语）。可用 `language: zh`（或 `en`、`pt`、`ru`、`zh-TW`）强制指定。

## 命令

| 命令 | 作用 |
|---|---|
| `PRINT_RECOVERY_STATUS` | 显示中断的打印并再次弹窗 |
| `PRINT_RECOVERY_RESUME` | 恢复 |
| `PRINT_RECOVERY_DISCARD` | 放弃 |

## 局限

- **假定 Z 未变化。** 断电后电机失去力矩，若龙门下沉或被移动，喷嘴可能碰到模型，因此移动前会先抬高喷嘴（`lift_z`）。
- **模型会冷却**，可能翘边或脱落，恢复处通常会留下一条纹路。
- 中断前几秒的内容会被重打（G-code 比运动提前读取），最多留下很小的凸起。
- 主板没有断电检测，无法在断电瞬间抬起或回抽。
- G-code 文件被删除或修改时会拒绝恢复。
- 延时摄影会保留中断前的画面；Moonraker 会把续打记为新的历史任务。
