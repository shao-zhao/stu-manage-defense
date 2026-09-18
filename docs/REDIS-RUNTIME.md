# Redis 本机运行说明

默认路径是 Compose 的 Redis 容器，绑定 127.0.0.1:6379 并用命名卷持久化。若 Docker Desktop 不可用，scripts/start.ps1 会使用 runtime/redis/ 中已经校验的 portable Redis；该目录和所有运行数据都被 Git 忽略。

本次采用 redis-windows/redis-windows 8.10.2 的非 Service 包 Redis-8.10.2-Windows-x64-cygwin.zip。固定下载地址为 https://github.com/redis-windows/redis-windows/releases/download/8.10.2/Redis-8.10.2-Windows-x64-cygwin.zip，发布页 SHA-256：

    6de5cc7f5adbf97b5928b13766383d4ad424626ef3d8b313ffff12d820ec6fc1

新 clone 运行 scripts/install-portable-redis.ps1；它下载、核验并解压。脚本以隐藏进程运行并传入 --bind 127.0.0.1 --port 6379 --appendonly yes，数据写入 runtime/redis-data/，不会安装 Windows 服务或暴露到公网。
