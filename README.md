# ColorOS17安装器净化

适用于 Vector 的应用安装器净化模块，保留 `com.oplus.appdetail` 原生全屏安装界面，精简扫描、广告、更新提醒和重复确认。

- 保留应用信息、权限数量及原生权限详情。
- 只需一次“安装”确认，完成后显示原生安装结果。
- 同一模块根据安装器版本自动选择规则，桌面入口隐藏。

## 支持版本

| 应用安装器 | 验证情况 |
| --- | --- |
| 5.17.62_os17（51762） | 已完成实机安装验证 |
| 5.23.0（52302） | 已通过静态检查和独立进程验证，未完成实机安装验证 |

底层安装器适配版本：`com.android.packageinstaller` 17.0.1（17000001）。其他版本保留原行为。

## 使用

1. [下载并安装模块 APK](https://github.com/AstriaFR/ColorOS17-InstallerClean/releases/latest/download/ColorOS17-InstallerClean-v1.0.apk)。
2. 在 Vector 中启用模块，作用域选择 `com.oplus.appdetail` 和 `com.android.packageinstaller`。
3. 重新启动安装器；若未生效，可重启手机。

模块名称为 **ColorOS17安装器净化**，显示版本 **1.0**。权限信息来自 APK 本地解析，模块不会自动授予权限；系统签名检查及真实安装错误仍由系统处理。

## 构建

需要 JDK 17+、Android SDK Platform / Build Tools 37.0.0，以及 libxposed API 102.0.0 AAR。

```powershell
./build.ps1 -Sdk /path/to/android-sdk -Jdk /path/to/jdk -LibXposedAar /path/to/api-102.0.0.aar
```

默认生成未签名 APK；签名参数见脚本。`profile.json` 保存版本规则，生成的 `Profile.java` 已包含在源码中。可选的 `verify_profile.py` 用于核对本地原厂 APK 并重新生成规则，需要 `requirements.txt` 中的依赖。原厂 APK 和签名私钥不随仓库分发。
