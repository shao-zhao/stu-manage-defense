# Windows x64 离线便携交付与 Docker

`package/build-portable.ps1` 从已完成的同源可执行 JAR 生成本机交付物。它复制 Java 17、MySQL 8.0.29、Redis 8.10.2、Microsoft VC++ x64 运行库、JAR 和许可证，再写入 ZIP；不复制 `data`、数据库、上传文件、`.env` 或任何密钥。脚本会先检查 JAR 包含 `BOOT-INF/classes/static/index.html`，因此不会把前后端分离的半成品打成离线包。

在开发机完成后执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\package\build-portable.ps1
```

产物位于 `package/out/stu-manage-portable-windows-x64.zip`，该目录和 ZIP 都被忽略，不会提交到 Git。包使用相对目录，并将业务数据放在解压根目录的 `data`：MySQL 数据、Redis AOF、上传文件、日志、首次生成的数据库/JWT 密钥都只存在这里。首次双击 `START.cmd` 后访问 `http://127.0.0.1:18090`；MySQL、Redis 分别只监听 `127.0.0.1:13306` 与 `127.0.0.1:16379`。`STOP.cmd` 只终止本包记录的 PID，端口冲突会失败并不会终止外部程序。

包内脚本专门按 Windows PowerShell 5.1 编写：`tools/Check.ps1` 检查监听和健康状态，`tools/Backup.ps1` 创建 SQL 备份，`tools/Restore.ps1 -BackupFile ...` 恢复。MySQL Windows 二进制要求 Microsoft Visual C++ 2015–2022 x64 运行库；包携带 Microsoft 官方离线安装器，缺失时运行一次 `runtime/vc_redist.x64.exe`。`SOURCES-SHA256.txt` 记录打包时的二进制指纹；完整许可证在 `licenses` 和 `THIRD-PARTY-NOTICES.md`。

离线包不需要 Docker，也不会触碰本机 Docker 数据目录。天气接口依赖互联网，离线会显示不可用；课程、成绩、Redis、SSE、媒体和 Excel 继续工作。已在干净的英文 ASCII 路径（含空格）副本验证初始化；MySQL 8.0 Windows 官方限制安装和数据路径必须处于当前 ANSI 代码页，中文路径会被启动脚本明确拒绝，需解压到英文路径。除非另有记录，不宣称已在第二台实体电脑验证。

## Docker 全栈

Docker 方案使用 `docker-compose.full.yaml`，容器包含应用、MySQL、Redis、上传卷和健康检查。首次运行需要联网拉取 Node、Maven、Java、MySQL、Redis 镜像和依赖；这是与离线 ZIP 的区别。复制安全的环境值后运行：

```powershell
docker compose -f docker-compose.full.yaml up --build
```

应用同样仅映射到 `127.0.0.1:18090`。运行前应修改其中的 `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` 与 `JWT_SECRET`，不将真实值提交。当前机器 Docker 服务异常时，使用离线便携包即可，不需要删除或修改 Docker 全局目录。
