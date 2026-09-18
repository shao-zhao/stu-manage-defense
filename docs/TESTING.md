# 运行与验证记录

本项目的验证分为环境、后端、前端与完整业务链路四层。所有命令均从仓库根目录运行。

## 环境与服务

```powershell
./scripts/check-environment.ps1
./scripts/start.ps1
./scripts/check-environment.ps1 -WaitForApplications
```

检查项包括 Java、Node.js、Docker 守护进程、MySQL 3306、回环 Redis 6379、后端 `/actuator/health` 与前端 5173。Redis 由 `compose.yaml` 的 healthcheck 校验 `PONG`，数据写入命名卷 `redis-data`。

## 自动 smoke 测试

```powershell
./scripts/test-smoke.ps1
```

该脚本以种子中的三类账号登录，创建时间戳课程，再按顺序核验：

1. 教师创建并发布课程；学生看到课程并成功选课。
2. 教师录入分数并提交；管理员审核并发布。
3. 学生读取已发布成绩、已获学分和 GPA；再次调用发布端点后，学分不重复增加。
4. 学生调用管理员端点，确认收到 HTTP 403。

脚本只增加独有课程码，避免覆盖演示种子；测试失败会抛出异常并给出所处步骤。测试结果应连同运行日期和控制台输出保存到答辩记录，不应把本机日志或密码提交到仓库。

## 开发验证

后端在 `stu-backend` 目录运行 `mvnw.cmd test`；前端在 `stu-frontend` 目录运行 `npm run build`。提交前还应执行 `git grep` 检查凭据、检查发布仓库没有 `node_modules`、`target`、`uploads`、`.env` 与嵌套 `.git`。
