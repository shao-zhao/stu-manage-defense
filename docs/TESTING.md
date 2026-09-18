# 运行与验证记录

本项目的验证分为环境、后端、前端与完整业务链路四层。所有命令均从仓库根目录运行。

## 环境与服务

```powershell
./scripts/check-environment.ps1
./scripts/start.ps1
./scripts/check-environment.ps1 -WaitForApplications
```

检查项包括 Java、Node.js、Docker 或 portable Redis 回退、MySQL 3306、回环 Redis 6379、后端 `/actuator/health` 与前端 5173。Redis 使用 Compose 时由 healthcheck 校验；portable 模式通过 redis-cli PING 与 AOF 状态校验。

### 已实际执行（2026-09-19）

- portable Redis 8.10.2 已按 SHA-256 校验，PONG 与 AOF 均正常，监听仅为 127.0.0.1:6379。
- 后端健康检查返回 HTTP 200 / UP；管理员连续读取 dashboard，缓存从 `hit:false` 变为 `hit:true`，backend 为 UP、TTL 60 秒。
- `scripts/check-environment.ps1 -WaitForApplications` 已通过本机 Java、Node、MySQL、Redis、后端与前端检查。
- 最新完整 `scripts/test-smoke.ps1` 已通过 11 个跨角色步骤：选退重选、成绩退回再提、审核发布、学分幂等、学生与教师越权、冻结 JWT 失效和账号恢复。
- 带 Origin `http://127.0.0.1:5173` 的无效 Bearer 请求返回 401，并包含对应的 CORS allow-origin 响应头。
- 管理员携带 Bearer 建立 SSE 连接后，教师提交“Web 开发实训（通知演示）”课程成绩，连接实际收到 `event:notification`；该课程保留为答辩时的审核演示数据。
- 前端已实际验证成绩权重回填与草稿保存、PNG 上传和教师端预览/删除、管理员模板下载，以及携带 Bearer 的 SSE 心跳连接；`npm run build` 通过。
- Excel 导入已实际验证：单行导入创建的 `excel-demo-2026` 可用初始密码登录；“先新增、后重复”的两行文件返回 400，新增行查询为 0，证明事务完整回滚。

## 自动 smoke 测试

```powershell
./scripts/test-smoke.ps1
```

该脚本以种子中的三类账号登录，创建时间戳课程，再按顺序核验：

1. 教师创建并发布课程；学生成功选课、退课，再次选课。
2. 教师录入并提交分数；管理员退回；教师再次提交；管理员审核并发布。
3. 学生读取已发布成绩、已获学分和 GPA；再次调用发布端点后，学分不重复增加。
4. 学生调用管理员端点、教师调用审核端点，确认均收到 HTTP 403；冻结学生后，其旧 JWT 收到 HTTP 401，脚本随即恢复账号。

脚本只增加独有课程码，避免覆盖演示种子；测试失败会抛出异常并给出所处步骤。测试结果应连同运行日期和控制台输出保存到答辩记录，不应把本机日志或密码提交到仓库。

## 开发验证

后端在 `stu-backend` 目录运行 `mvnw.cmd test`；前端在 `stu-frontend` 目录运行 `npm run build`。提交前还应执行 `git grep` 检查凭据、检查发布仓库没有 `node_modules`、`target`、`uploads`、`.env` 与嵌套 `.git`。
