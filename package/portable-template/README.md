# 学生管理系统 Windows x64 离线便携包

解压后无需 Node.js、Maven、Git、联网或系统 Java。首次运行双击 `START.cmd`，浏览器打开 `http://127.0.0.1:18090`。首次启动会只在本目录的 `data` 下初始化 MySQL、生成数据库/JWT 密钥并写入演示数据；后续启动保留所有业务数据。

`STOP.cmd` 只停止本包记录的进程，绝不结束其它程序。端口 `18090`、`13306`、`16379` 被外部程序占用时会明确失败。

在 PowerShell 中可运行：

```powershell
.\tools\Check.ps1
.\tools\Backup.ps1
.\tools\Restore.ps1 -BackupFile .\data\backups\stu_manage-YYYYMMDD-HHMMSS.sql
```

包内的 MySQL 需要 Microsoft Visual C++ 2015–2022 x64 运行库。若裸 Windows 缺少该运行库，执行一次 `runtime\vc_redist.x64.exe`（来自 Microsoft 官方 aka.ms 链接）后重试；无需联网。许可证、来源和 SHA-256 指纹见 `THIRD-PARTY-NOTICES.md` 与 `SOURCES-SHA256.txt`。

天气功能依赖外网，离线时会显示不可用；课程、成绩、Redis 缓存、SSE、媒体和 Excel 功能仍可使用。
