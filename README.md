# 学生管理系统（答辩版）

面向管理员、教师和学生的教学管理系统。系统覆盖账号与状态管理、课程发布、容量受保护的选退课、成绩审核发布、学分与 GPA 汇总，以及通知、教学资料、数据导入导出等完整业务流程。

## 功能与扩展

- 三角色登录与权限：管理员、教师、学生使用 JWT 登录；每次请求校验账号状态。
- 教学业务：课程发布后锁定；选课和退课受事务及容量约束；成绩经历草稿、提交、审核、退回和发布。
- 成绩结果：发布时一次性更新成绩、已获学分和加权 GPA，重复发布不会重复累计。
- 五项扩展：ECharts 数据看板、Redis 缓存状态、基于 HTTP 长连接的 SSE 通知、Open-Meteo 实时天气、图片/视频上传预览。

## 本地运行

前提：MySQL 8（本机 3306）、Java 17+、Node.js、Docker Desktop。Redis 由 Compose 在 **127.0.0.1:6379** 启动，未暴露到局域网或公网。

1. 复制 `.env.example` 为 `.env`，填写 `MYSQL_PASSWORD` 和一个足够长的 `JWT_SECRET`。`.env` 不会提交。
2. 确认 MySQL 可用。应用使用独立数据库 `stu_manage`，不会修改旧的 `stu` 数据库。
3. 在项目根目录运行：

```powershell
./scripts/start.ps1
./scripts/check-environment.ps1 -WaitForApplications
```

前端默认地址为 `http://127.0.0.1:5173`，后端为 `http://127.0.0.1:9090`。首次后端启动会执行 `stu-backend/sql/schema.sql` 创建表及演示数据。

演示账号均为初始密码 `123456`：`admin`（ADMIN）、`teacher01`（TEACHER）和 `student01`（STUDENT）。演示结束后可从系统内修改密码。

停止应用：

```powershell
./scripts/stop.ps1
./scripts/stop.ps1 -WithRedis
```

`start.ps1` 将前后端放在隐藏后台进程中，并记录在 `runtime/`；为 Java 26 的 Windows 临时 Unix-domain socket 问题传入项目内的绝对临时目录。日志位于 `runtime/logs/`，均不纳入版本控制。

## 验收与答辩材料

- [运行与验证记录](docs/TESTING.md)
- [评分点与可核验依据](docs/SCORING-EVIDENCE.md)
- [5 分钟演示稿与核心代码讲解](docs/DEFENSE.md)

完整业务 smoke 测试使用独立、带时间戳的课程记录；它不会重置演示种子：

```powershell
./scripts/test-smoke.ps1
```

## 项目结构

```text
stu-backend/    Spring Boot 4.1.1 + MyBatis-Plus + MySQL
stu-frontend/   Vue 3 + TypeScript + Vite + Element Plus + ECharts
scripts/        启动、停止、环境检查与端到端 smoke 测试
docs/           答辩稿、验证记录、评分依据
compose.yaml    仅本机 Redis（持久化命名卷、回环地址绑定）
```

## 安全与运行边界

仓库不包含真实数据库密码、JWT 密钥、上传文件、运行日志、构建产物或嵌套 Git 历史。`application-example.properties` 与 `.env.example` 仅包含占位值。上传接口验证类型和大小，凭据不会出现在 API 响应中。
