# 按虚拟屏读取和点击无障碍节点

这是本地复刻新增的 Android instrumentation 工具，沿用上游按显示屏枚举无障碍窗口的做法。适用于已有 ADB 连接和 scrcpy 虚拟屏的情形。每次命令运行时临时获取 UiAutomation，保留已有无障碍服务。只接受正数显示屏 ID，并要求目标包名与窗口匹配。

## 构建与运行

构建需要 JDK 17 或 21、Android SDK Platform 36 和 Build Tools 36.0.0。脚本按 `ANDROID_HOME`、`ANDROID_SDK_ROOT`、macOS 的 Android Studio SDK 默认目录和 Apple Silicon 的 Homebrew SDK 默认目录查找 SDK。若 JDK 已通过 Homebrew 安装，可以通过 `JAVA_HOME` 选定版本。

```sh
cd scripts/vd-accessibility
JAVA_HOME="$(brew --prefix openjdk@21)" ./build.sh /tmp/vd-accessibility.apk
```

运行前从本次 scrcpy 的日志确认显示屏 ID，并用 `adb shell dumpsys activity activities` 核实前台任务。`serial` 和 `display_id` 由调用方填写。

```sh
adb -s "$serial" shell pm path local.codex.vdaccess
# 上一步为空时安装本次构建的辅助包
adb -s "$serial" install -t --no-incremental /tmp/vd-accessibility.apk
adb -s "$serial" shell am instrument -w -e display "$display_id" -e pkg com.openai.chatgpt local.codex.vdaccess/.Probe
adb -s "$serial" shell am instrument -w -e display "$display_id" -e pkg com.openai.chatgpt -e click Codex local.codex.vdaccess/.Probe
```

读取结果列出屏幕像素边界、文字和内容描述，跳过标记为密码的子树与可编辑框的值。界面其他位置仍可能含个人资料，应按需筛选。标签点击要求匹配唯一节点，从节点向上寻找可点击的父节点。返回 Click accepted 只表示 Android 接受动作，需要再次读取页面确认跳转。若 WebView 接受动作后停在原页，可重新核实显示屏、包名和节点边界，使用明确指定显示屏的 ADB 点击节点中心，再读取结果。

邮箱页可传入 `-e email` 和本次授权使用的邮箱，助手仅在有“电子邮件地址”或“Email address”标签且只有一个可见非密码编辑框时设置文本，并回读比较。仅适用于上述页面结构。

本次安装的辅助包可用以下命令移除。每次构建会使用新临时测试签名，复用已安装的同名包前应先确认其归属。
```sh
adb -s "$serial" uninstall local.codex.vdaccess
```
