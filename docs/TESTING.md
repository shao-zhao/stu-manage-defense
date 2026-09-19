# 运行与验证记录

本项目的验证分为环境、后端、前端与完整业务链路四层。所有命令均从仓库根目录运行。

## V2 便携版验证（2026-09-19）

- Java 17、独立 MySQL `13306`、Redis `16379`、同源应用 `18090` 在英文含空格目录实际运行，应用健康状态 UP。
- 后端 **12 项测试全部通过，失败 0、错误 0、跳过 0**。包含课程编辑/删除/停开权限、并发选课、发布幂等、演示数据幂等、MySQL `ONLY_FULL_GROUP_BY` 三角色统计，以及带通知时间字段的真实 HTTP Redis 双读。
- 实际便携 HTTP 复核：缓存首次 `backend=UP, hit=false, TTL=59`，两秒后 `hit=true, TTL=57`，`generatedAt` 不变；四种生命周期演示课程存在，`student01` 可查询已发布演示成绩。
- 11 步跨角色业务 smoke 已在 `18090` 通过。另行验证未发布课程完整编辑/删除、已发布描述编辑及核心字段保护、停开禁止新增选课但允许草稿期退课、恢复选课和已发布课程禁止删除。
- MySQL 默认严格模式发现的统计分组歧义已修复；通知 LocalDateTime 不能缓存的问题已补 JavaTime 序列化与回归测试。没有通过关闭严格模式或伪造 Redis 状态规避问题。
- 备份恢复已用中文数据库字段和中文名媒体文件实测，媒体 SHA-256 一致；备份前暂停本包应用，MySQL 和 Redis 保持运行，结束后自动恢复原应用状态。
- 生产 JAR 已核对包含最新前端 dist 与缓存修复，Vue 深链接可刷新，缺失 API 和静态资源返回 404。

上述验证发生在本机独立便携副本，不能等同于另一台实体电脑验收。Docker 引擎本机不可用，仅完成 Compose 配置校验，未声称容器运行通过。最终交付压缩包的 SHA-256 和解压启动结果见压缩包旁的交付校验记录。

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

以下是开发机的源码测试，使用 PowerShell 7（脚本使用 `SkipHttpErrorCheck`）。它不是目标电脑运行便携包的前置条件；便携包的 Start、Stop、Check、Backup、Restore 均按 Windows PowerShell 5.1 验证。

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
