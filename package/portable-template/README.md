# 学生管理系统 Windows x64 离线便携包

适用 Windows 10/11 x64。请完整解压到英文目录，例如 `D:\Student Demo\stu-manage-portable`，路径可以含空格，不能含中文。不要在压缩包内直接运行。

无需安装 Node.js、Maven、Git 或系统 Java。首次双击 `START.cmd`，等待浏览器打开 `http://127.0.0.1:18090`。首次启动会在本目录的 `data` 下初始化 MySQL、生成数据库/JWT 密钥并写入演示数据；后续启动保留修改。

演示账号：管理员 `admin`、教师 `teacher01`、学生 `student01`，密码都是 `123456`。管理员侧栏“技术演示”可观察 Redis、ECharts 和 SSE。多角色同时演示请使用普通窗口与无痕窗口，避免同一浏览器标签页共享令牌。

`STOP.cmd` 只停止本包记录的进程，绝不结束其它程序。端口 `18090`、`13306`、`16379` 被外部程序占用时会明确失败。

在本目录打开 PowerShell 可运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Backup.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Restore.ps1 -BackupPath '.\data\backups\stu-manage-实际时间'
```

包内的 MySQL 需要 Microsoft Visual C++ 2015–2022 x64 运行库。若裸 Windows 缺少该运行库，执行一次 `runtime\vc_redist.x64.exe`（来自 Microsoft 官方 aka.ms 链接）后重试；无需联网。许可证、来源和 SHA-256 指纹见 `THIRD-PARTY-NOTICES.md` 与 `SOURCES-SHA256.txt`。

天气功能依赖外网，离线时会显示不可用；课程、成绩、Redis 缓存、SSE、媒体和 Excel 功能仍可使用。

结束演示请运行 `STOP.cmd` 并等待停止后，再移动整个文件夹或拔出 U 盘。日志在 `logs`，业务数据及备份在 `data`。迁移已使用的环境时，不要遗漏 `data`。

更多教程：[运行与排错](docs/V2-PORTABLE.md)、[技术栈演示](docs/TECH-DEMO.md)、[五分钟演示稿](docs/DEFENSE.md)、[代码学习路线](docs/LEARNING.md)。文档中的源码路径对应原工程或 [私有 GitHub 仓库](https://github.com/shao-zhao/stu-manage-defense)，运行包内主要提供已构建应用。
