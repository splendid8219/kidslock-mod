# 儿童锁屏 · 电视认字锁屏

一款专为小米电视（Android TV）设计的儿童锁屏应用。

## 功能

- **开机自启**：电视开机后自动启动计时（默认开启）
- **后台默认运行**：无需手动操作，开机即按设定时长自动开始倒计时
- **定时锁屏**：15/30/45/60/90/120 分钟六档可选，到时自动锁屏
- **重启仍锁**：锁屏后即使关机重启，电视仍保持锁屏状态
- **认字解锁**：答对五年级水平汉字拼音才能解锁
- **家长PIN**：家长可用 PIN 码快速解锁（默认 1234）

## 安装方法

### 方法一：U盘安装（推荐）

1. 如果之前安装过旧版，建议先在电视上卸载旧版（防止旧版入口残留）
2. 将新的 `kidslock-release.apk` 复制到 U盘
3. U盘插入小米电视 USB 口
4. 在电视上打开「高清播放器」或「文件管理」
5. 找到 U盘中的 apk 文件，点击安装
6. 安装前需在「设置 → 账号与安全」中开启「允许安装未知来源应用」

> **提示**：安装完成后，在电视桌面或「全部应用」中找「儿童锁屏」图标。如果找不到，重启一次电视即可。

### 方法二：adb 安装

```bash
adb connect <电视IP>:5555
adb install kidslock-release.apk
```

## 使用流程

1. **首次打开**：从电视桌面或「全部应用 / 应用中心」找到「儿童锁屏」图标打开
2. **设置参数**：
   - 开机自启：已开启（默认）
   - 观看时长：选择 15/30/45/60/90/120 分钟（默认30分钟）
   - 解锁规则：首次答对 1 题即解锁；每答错 1 次所需连对题数 +1，封顶 5 题（固定，无需设置）
   - 家长PIN：默认 1234，建议修改
3. **开始计时**：按「开始计时」按钮
4. **自动锁屏**：时间到自动弹出锁屏界面
5. **认字解锁**：
   - 屏幕显示一个五年级水平汉字
   - 4个拼音选项，用遥控器方向键选择，按确认键提交
   - 答对继续，答错重置计数
   - 连对达到当前所需题数（首答 1 题，答错累加至最多 5 题）后自动解锁
6. **家长解锁**：在锁屏界面选「家长解锁」，输入4位PIN码

## 锁屏防绕过机制

- 锁屏时显示全屏悬浮窗覆盖层，遮挡桌面
- 按遥控器 HOME 键 → 被悬浮窗拦截，无法回到桌面
- 按返回键 → 无效
- 关机重启 → 锁屏界面自动弹出
- 计时器与锁屏状态保存在 SharedPreferences 中，重启不丢失

## 项目结构

```
电视锁屏/
├── app/src/main/
│   ├── AndroidManifest.xml          # 清单文件
│   ├── java/com/kidslock/app/
│   │   ├── MainActivity.java        # 设置界面
│   │   ├── LockScreenActivity.java  # 锁屏认字界面
│   │   ├── LockService.java         # 计时前台服务
│   │   ├── BootReceiver.java        # 开机自启接收器
│   │   ├── CharacterBank.java       # 五年级汉字题库（140字）
│   │   └── PrefManager.java         # 持久化状态管理
│   └── res/
│       ├── layout/                   # 布局XML
│       ├── drawable/                 # 按钮选择器、图标、TV banner
│       └── values/                   # 颜色、尺寸、字符串、主题
├── kidslock-release.apk              # 签名正式版 APK
└── kidslock-debug.apk                # 调试版 APK
```

## 技术参数

- 最低支持：Android 5.0 (API 21)
- 目标版本：Android 13 (API 33)
- 编译版本：API 34
- 签名密钥有效期：10000天
- 架构：armeabi-v7a / arm64-v8a（覆盖所有小米电视）

## 编译方法

```bash
# 需要 JDK 17 和 Android SDK
export JAVA_HOME=<JDK路径>
export ANDROID_HOME=<SDK路径>

gradle assembleRelease

# 签名（使用自己的 keystore）
zipalign -f 4 app-universal-release-unsigned.apk aligned.apk
apksigner sign --ks <你的.keystore> --out kidslock-release.apk aligned.apk
```

> 已发布的 kidslock-release.apk 由作者签名，如需自行编译发布请替换为自己的签名密钥。

## 注意事项

- 安装后需在电视设置中给予「开机自启」和「后台运行」权限
- 小米电视的「安全中心」可能会清理后台，需将本应用加入白名单
- 部分小米电视系统可能限制 HOME launcher 替换，如遇问题请使用家长PIN解锁
- 汉字题库包含约 140 个五年级水平汉字，随机出题

---

## 本分支改造：全学科适龄版（家长定制）

在原版「认字解锁」基础上，将锁屏题目升级为**按年龄出适龄全学科题**：

- 小学：语文 / 数学 / 英语
- 中学起（初中/高中）：在语数英基础上加入 物理 / 化学 / 生物 / 历史 / 地理 / 政治（全学科）
- 其余锁屏能力（真锁屏、防 HOME 绕过、重启仍锁、计时、家长 PIN）**原样保留**

### 主要改动文件
- `java/com/kidslock/app/Question.java`：新增题目数据类（题干 + 4 选项 + 科目/年级标签）。
- `java/com/kidslock/app/QuestionBank.java`：新增题库核心。数学按年级**程序化生成**；其余科目从 `assets/questions/*.json` 加载并按年级（`low/high/c`/`h`）筛选适龄题。
- `java/com/kidslock/app/LockScreenActivity.java`：渲染题目文本 + 科目标签（替换原"认字"界面）。
- `java/com/kidslock/app/MainActivity.java`：设置页新增**学段 / 年级 / 科目模式**三项。
- `java/com/kidslock/app/PrefManager.java`：新增学段/年级/科目持久化。
- `assets/questions/*.json`：各科目题库，可随时增删不改代码。
- `.github/workflows/build-apk.yml`：GitHub Actions 云构建工作流（免本机装环境）。

> 原 `CharacterBank.java`（五年级汉字）仍保留但已不再被调用。

### 构建
原仓库未带 Gradle Wrapper，云构建工作流会先 `gradle wrapper` 再 `assembleDebug`；
本地可用 Android Studio 直接打开 `src/` 构建（Build APK）。
详见 `../构建与安装指南.md`。
