# Windows x64 离线便携交付与 Docker

## 到另一台电脑运行

1. 准备 Windows 10/11 x64 电脑，将 ZIP 完整解压到本地英文目录，例如 `D:\Student Demo\stu-manage-portable`。目录允许空格，不支持中文。不要在压缩文件浏览窗口里直接双击启动。
2. 双击 `START.cmd`，等待首次数据库初始化和应用启动。成功后浏览器打开 `http://127.0.0.1:18090`。首次启动比以后慢，不要在初始化期间关闭窗口或反复双击。
3. 如提示缺少 VC++ 运行库，运行 `runtime\vc_redist.x64.exe` 完成离线安装，再启动。依赖安装器可能需要系统管理员权限。
4. 分别使用 `admin`、`teacher01`、`student01` 登录，初始密码都是 `123456`。便携演示包含已发布成绩、待审核成绩、停开和未发布课程；管理员可从“技术演示”看到真实服务状态。
5. 演示结束双击 `STOP.cmd`，等待服务结束再移动目录或拔出 U 盘。复制已使用的演示环境时要复制整个文件夹，包括 `data`；仅复制 JAR 不会携带数据。

首次运行会在当前目录生成数据库和密钥，不会改原电脑的 MySQL 服务。正常重启不会重置已编辑的数据，也不会重新插入用户已经删除的演示课程。天气依赖互联网；离线演示重点放在课程、成绩、缓存、实时通知和媒体。

## 检查、备份与恢复

在解压目录打开 PowerShell，使用下面的命令。这种写法只对本次脚本运行设置执行策略，不需要改系统全局设置。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Backup.ps1
```

检查应显示 MySQL 查询成功、Redis PONG、应用 health UP。备份会暂停本包应用、保存业务 SQL 与上传文件到 `data\backups\stu-manage-...`，然后按原状态恢复应用。不要只复制 `database.sql` 而遗漏同目录的媒体和清单。

恢复会覆盖当前业务数据，先保留需要的备份目录，再执行以下命令，将路径换成实际生成的目录：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Restore.ps1 -BackupPath '.\data\backups\stu-manage-实际时间'
```

按提示输入 `RESTORE` 确认。脚本会先备份当前状态，再恢复所选 SQL 和上传文件；恢复使用目标机器自己的数据库凭据，不复制旧机器的数据库密码或 JWT 密钥。换电脑恢复时，先在新电脑成功启动一次，再放入整个备份目录并执行恢复。

| 现象 | 处理方法 |
|---|---|
| 提示目录不支持 | 把完整目录放到英文路径；数据库内的中文姓名、课程和媒体文件内容仍受支持。 |
| 提示端口已占用 | 确认上一份便携包已停止；在原目录运行它的 STOP.cmd。脚本不会强行终止占用端口的其他程序。 |
| 首次启动中断 | 再次启动会使用已保存凭据继续；若提示数据库文件不完整，保留旧目录，重新解压干净包。不要随意删除已有业务数据。 |
| 应用启动失败 | 查看 `logs\app.out.log`、`logs\app.err.log`；数据库问题看 `logs\mysql.err`，Redis 看 `logs\redis.log`。 |
| 天气不可用 | 检查互联网连接；天气故障不影响本地教学业务。 |

## 开发机重新打包

`package/build-portable.ps1` 从已完成的同源可执行 JAR 生成本机交付物。它复制 Java 17、MySQL 8.0.29、Redis 8.10.2、Microsoft VC++ x64 运行库、JAR 和许可证，再写入 ZIP；不复制 `data`、数据库、上传文件、`.env` 或任何密钥。脚本会先检查 JAR 包含 `BOOT-INF/classes/static/index.html`，因此不会把前后端分离的半成品打成离线包。

在开发机完成后执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\package\build-portable.ps1 -OutputDirectory .\package\out-v2
```

本次交付产物位于 `package/out-v2/stu-manage-portable-windows-x64.zip`，该目录和 ZIP 都被忽略，不会提交到 Git。脚本拒绝覆盖已有输出；再次构建时使用新的输出目录。MySQL 数据、Redis AOF、上传文件、数据库/JWT 密钥位于解压根目录 `data`，日志位于 `logs`。MySQL、Redis 分别只监听 `127.0.0.1:13306` 与 `127.0.0.1:16379`。停止脚本核对 PID、启动时间和本包程序路径，避免误停复用 PID 的其他程序。

包内脚本按 Windows PowerShell 5.1 编写。MySQL Windows 二进制要求 Microsoft Visual C++ x64 运行库；包携带 Microsoft 官方离线安装器，缺失时运行一次 `runtime/vc_redist.x64.exe`。`SOURCES-SHA256.txt` 记录打包时的二进制指纹；许可证和来源见 `licenses` 和 `THIRD-PARTY-NOTICES.md`。

离线包不需要 Docker，也不会触碰本机 Docker 数据目录。天气接口依赖互联网，离线会显示不可用；课程、成绩、Redis、SSE、媒体和 Excel 继续工作。已在干净的英文 ASCII 路径（含空格）副本验证初始化；MySQL 8.0 Windows 官方限制安装和数据路径必须处于当前 ANSI 代码页，中文路径会被启动脚本明确拒绝，需解压到英文路径。除非另有记录，不宣称已在第二台实体电脑验证。

## Docker 全栈

Docker 方案使用 `docker-compose.full.yaml`，容器包含应用、MySQL、Redis、上传卷和健康检查。首次运行需要联网拉取 Node、Maven、Java、MySQL、Redis 镜像和依赖；这是与离线 ZIP 的区别。复制安全的环境值后运行：

```powershell
docker compose -f docker-compose.full.yaml up --build
```

应用同样仅映射到 `127.0.0.1:18090`。运行前应修改其中的 `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` 与 `JWT_SECRET`，MySQL 服务与应用的 MYSQL_PASSWORD 要保持一致，不将真实值提交。当前机器 Docker 引擎不可用，因此未完成 Docker 容器启动验收；这套文件是在线部署的备选方案。离线交付以实际验收的 Windows 便携包为准，不需要删除或修改 Docker 全局目录。
