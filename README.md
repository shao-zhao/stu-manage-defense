# 学生管理系统（答辩版）

面向管理员、教师和学生的教学管理系统。系统覆盖账号与状态管理、课程发布、容量受保护的选退课、成绩审核发布、学分与 GPA 汇总，以及通知、教学资料、数据导入导出等完整业务流程。

## 功能与扩展

- 三角色登录与权限：管理员、教师、学生使用 JWT 登录；每次请求校验账号状态。
- 教学业务：未发布课程可编辑或删除；发布后仅说明性字段可改。停开课程保留历史记录且禁止新增选课，草稿成绩期间仍可退课；选课和退课受事务及容量约束，成绩经历草稿、提交、审核、退回和发布。
- 成绩结果：发布时一次性更新成绩、已获学分和加权 GPA，重复发布不会重复累计。
- 五项扩展：ECharts 数据看板、Redis 缓存状态、基于 HTTP 长连接的 SSE 通知、Open-Meteo 实时天气、图片/视频上传预览。

## 换电脑演示：优先使用便携版

在 Windows 10/11 x64 上，将 `stu-manage-portable-windows-x64.zip` **完整解压**到英文目录，例如 `D:\Student Demo\stu-manage-portable`，然后双击 `START.cmd`。启动成功后访问 `http://127.0.0.1:18090`。结束时双击 `STOP.cmd`，等待退出后再复制文件夹或拔出 U 盘。

包内包含 Java 17、MySQL、Redis 和已构建应用，不需要安装 Node.js、Maven、Git，也不需要联网下载依赖。若电脑缺少 VC++ x64 运行库，先运行包内 `runtime/vc_redist.x64.exe`。天气功能需要网络，其他本地业务可以离线演示。目录可含空格，但不能含中文；不要直接在压缩包内运行。

演示账号：`admin`、`teacher01`、`student01`，密码均为 `123456`。便携包首次启动会生成独立演示数据；以后的启动保留修改。数据、上传文件和密钥在包内 `data/`，不要只复制 JAR。

详细步骤、端口冲突排查与备份恢复见 [便携版运行教程](docs/V2-PORTABLE.md)。这里的支持范围是 Windows x64；未宣称已在另一台实体电脑测试。

## 从源码开发运行

前提：MySQL 8（本机 3306）、Java 17、Node.js 22.18+ 或 24.12+。本项目在 Windows 使用 Java 17 验证。Redis 优先由 Docker Compose 在 **127.0.0.1:6379** 启动；Docker 不可用时，启动脚本使用校验过的本机 portable Redis，同样只绑定回环地址。

1. 复制 `.env.example` 为 `.env`，填写 `MYSQL_PASSWORD`。`.env` 不会提交。后端首次启动会在本机生成 `jwt-secret.local`，无需把 JWT 密钥写进 `.env`。
2. 确认 MySQL 可用。应用使用独立数据库 `stu_manage`，不会修改旧的 `stu` 数据库。
3. 在项目根目录运行：

```powershell
./scripts/start.ps1
./scripts/check-environment.ps1 -WaitForApplications
```

前端默认地址为 `http://127.0.0.1:5173`，后端为 `http://127.0.0.1:9090`。首次后端启动会执行 `stu-backend/src/main/resources/schema.sql` 创建表及演示数据。

演示账号均为初始密码 `123456`：`admin`（ADMIN）、`teacher01`（TEACHER）和 `student01`（STUDENT）。演示结束后可从系统内修改密码。

停止应用：

```powershell
./scripts/stop.ps1
./scripts/stop.ps1 -WithRedis
```

`start.ps1` 会在新克隆项目中自动执行 `npm ci`，将前后端放在隐藏后台进程中，并等待健康检查成功；进程记录在 `runtime/`。日志位于 `runtime/logs/`，均不纳入版本控制。首次从源码运行需要联网安装依赖；离线演示请使用预先构建的便携包。

## 验收与答辩材料

- [运行与验证记录](docs/TESTING.md)
- [评分点与可核验依据](docs/SCORING-EVIDENCE.md)
- [5 分钟演示稿与核心代码讲解](docs/DEFENSE.md)
- [Redis、SSE 等技术的实际演示步骤](docs/TECH-DEMO.md)
- [按请求、鉴权、插槽、事务学习代码](docs/LEARNING.md)

完整业务 smoke 测试使用独立、带时间戳的课程记录；它不会重置演示种子：

```powershell
./scripts/test-smoke.ps1
```

## 项目结构

```text
stu-backend/    Spring Boot 4.1.1 + MySQL（核心业务服务使用 JdbcTemplate；保留 MyBatis-Plus 依赖）
stu-frontend/   Vue 3 + TypeScript + Vite + Element Plus + ECharts
scripts/        启动、停止、环境检查与端到端 smoke 测试
docs/           答辩稿、验证记录、评分依据
compose.yaml    仅本机 Redis（持久化命名卷、回环地址绑定）
```

## 安全与运行边界

仓库不包含真实数据库密码、JWT 密钥、上传文件、运行日志、构建产物、portable Redis 二进制或嵌套 Git 历史。`application-example.properties` 与 `.env.example` 仅包含占位值。上传接口验证类型和大小，凭据不会出现在 API 响应中。

日常开发在本目录 `stu-manage` 完成。`stu-manage-defense` 仅是排除嵌套 Git 历史与本地数据后的 GitHub 整合发布目录；请勿在两个目录之间混合运行服务。
