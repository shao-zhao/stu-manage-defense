# 第三方组件与许可

| 组件 | 包内位置 | 来源与许可 |
|---|---|---|
| Alibaba Dragonwell 17 | `runtime/java` | https://dragonwell-jdk.io/；其随附许可位于 `licenses/java/legal` |
| MySQL Community Server 8.0.29 | `runtime/mysql` | https://dev.mysql.com/downloads/mysql/；GPL-2.0，完整许可位于 `licenses/mysql/LICENSE` |
| Redis Windows 8.10.2 Cygwin build | `runtime/redis` | https://github.com/redis-windows/redis-windows/releases/tag/8.10.2；Redis 许可说明：https://redis.io/legal/licenses/；上游 README 位于 `licenses/redis/README.md` |
| Microsoft Visual C++ Redistributable x64 | `runtime/vc_redist.x64.exe` | https://aka.ms/vs/17/release/vc_redist.x64.exe；仅为 MySQL 的 Windows 运行时前置项 |

`SOURCES-SHA256.txt` 列出本次打包时计算的二进制指纹，以及已校验的 Redis 上游 ZIP 哈希。
